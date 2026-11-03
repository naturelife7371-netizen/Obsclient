package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

import static com.obsclient.ObsClient.*;

public class Nuker extends Module {
    public static String status = "Off";
    public static Nuker INSTANCE;

    // ==================== RANGE ====================
    final NumSetting range       = new NumSetting("Range", "Menzil", 4.5, 1.5, 5.0, 0.5);
    final NumSetting playerRange = new NumSetting("PlayerRange", "Oyuncu Menzil", 8.0, 2.0, 16.0, 0.5);
    final NumSetting perTick     = new NumSetting("Creative/Tick", "Creative/Tick", 8, 1, 20, 1);

    // ==================== MODE ====================
    final ModeSetting mode = new ModeSetting("Mode", "Mod", 0,
            new String[]{"TrapBreak", "PlayerOnly", "AllObsidian", "All"},
            new String[]{"Trap Kir", "Sadece Oyuncu", "Tum Obsidyen", "Hepsi"});

    // ==================== OPTIONS ====================
    final BoolSetting silent       = new BoolSetting("Silent", "Sessiz Donus", true);
    final BoolSetting flat         = new BoolSetting("Flat", "Ayak Seviyesi", false);
    final BoolSetting onlySelfTrap = new BoolSetting("OnlySelfTrap", "Sadece Trap", true);

    // ⚡ ESP'yi varsayılan olarak KAPATTIK (false)
    final BoolSetting showRender   = new BoolSetting("Render", "Gosterim (ESP)", false);

    final BoolSetting trackCount   = new BoolSetting("TrackCount", "Sayac", true);
    final BoolSetting colorFade    = new BoolSetting("ColorFade", "Renk Solma", true);
    final BoolSetting swingHand    = new BoolSetting("Swing", "Kol Salla", true);
    final BoolSetting holdAttackKey = new BoolSetting("HoldAttack", "Sol Tik Bas", true);

    // ==================== INTERNAL ====================
    private final HashMap<BlockPos, Long> brokenHistory = new HashMap<>();
    private int totalBroken = 0;
    private int recentBroken = 0;
    private long lastCleanup = 0;
    private BlockPos currentBreaking = null;
    private int breakTicks = 0;
    private boolean attackKeyHeld = false;
    private float breakProgressCurrent = 0f;

    public Nuker() {
        super("Nuker", "Nuker", "Break obsidian around trapped players.", "Trap oyuncularin obsidyenini kir.", Category.WORLD);
        settings.addAll(Arrays.asList(
                range, playerRange, perTick,
                mode,
                silent, flat, onlySelfTrap, showRender, trackCount, colorFade, swingHand, holdAttackKey
        ));
        INSTANCE = this;
    }

    @Override public String status() {
        if (!enabled) return "Off";
        return totalBroken + " broken";
    }

    @Override
    public void onEnable() {
        status = "Breaking";
        brokenHistory.clear();
        totalBroken = 0;
        recentBroken = 0;
        currentBreaking = null;
        breakTicks = 0;
        breakProgressCurrent = 0f;
        attackKeyHeld = false;
    }

    @Override
    public void onDisable() {
        status = "Off";
        currentBreaking = null;
        breakProgressCurrent = 0f;
        releaseAttackKey();
    }

    private void releaseAttackKey() {
        try {
            if (attackKeyHeld && mc.options != null) {
                mc.options.attackKey.setPressed(false);
                attackKeyHeld = false;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null || mc.interactionManager == null) return;
            if (mc.currentScreen != null) { releaseAttackKey(); return; }

            // Eski kayıtları temizle
            long now = System.currentTimeMillis();
            if (now - lastCleanup > 1000) {
                lastCleanup = now;
                brokenHistory.entrySet().removeIf(e -> now - e.getValue() > 30000);
                recentBroken = brokenHistory.size();
            }

            // 1) HEDEF OBSIDYENLERİ TOPLA
            List<BlockPos> targets = collectTargets(mc, p);
            if (targets.isEmpty()) {
                status = "No target";
                currentBreaking = null;
                breakTicks = 0;
                breakProgressCurrent = 0f;
                releaseAttackKey();
                return;
            }

            // En yakın hedef
            Vec3d eye = p.getEyePos();
            targets.sort(Comparator.comparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b))));
            BlockPos bp = targets.get(0);

            // Reach kontrolü
            double dist = eye.distanceTo(Vec3d.ofCenter(bp));
            if (dist > 4.5) {
                status = String.format("Out of reach %.1f", dist);
                releaseAttackKey();
                return;
            }

            // Kırıldı mı?
            if (mc.world.getBlockState(bp).isAir()) {
                recordBroken(bp);
                currentBreaking = null;
                breakTicks = 0;
                breakProgressCurrent = 0f;
                status = "Broken! Total: " + totalBroken;
                return;
            }

            // Hedef değişirse resetle
            if (currentBreaking == null || !currentBreaking.equals(bp)) {
                currentBreaking = bp;
                breakTicks = 0;
                breakProgressCurrent = 0f;
            }

            // 2) SESSİZ ROTASYON — Kamerayı KİLİTLEME
            Vec3d hitVec = Vec3d.ofCenter(bp);
            float[] angle = calcAngle(eye, hitVec);

            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                        angle[0], angle[1], p.isOnGround(), false));
            }

            Direction face = getFacingDirection(p, bp);
            breakTicks++;

            // 3) BLOK KIRMA
            if (p.isCreative()) {
                int n = 0;
                for (BlockPos target : targets) {
                    if (n >= perTick.i()) break;
                    if (eye.distanceTo(Vec3d.ofCenter(target)) > 5.0) continue;
                    Direction fd = getFacingDirection(p, target);

                    try {
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, fd));
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, fd));

                        mc.interactionManager.attackBlock(target, fd);
                        recordBroken(target);
                        n++;
                    } catch (Throwable ignored) {}
                }
                if (swingHand.value) p.swingHand(Hand.MAIN_HAND);
                status = "Creative x" + n + " | Total " + totalBroken;
                breakProgressCurrent = 1f;
                return;
            }

            // SURVIVAL MODU
            if (holdAttackKey.value) {
                try {
                    mc.options.attackKey.setPressed(true);
                    attackKeyHeld = true;
                } catch (Throwable ignored) {}
            }

            try {
                if (breakTicks <= 1) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, bp, face));
                } else {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, bp, face));
                }
                mc.interactionManager.updateBlockBreakingProgress(bp, face);
            } catch (Throwable ignored) {}

            breakProgressCurrent = MathHelper.clamp(breakTicks / 190f, 0f, 0.99f);

            // Blok kırıldı mı?
            if (mc.world.getBlockState(bp).isAir()) {
                recordBroken(bp);
                currentBreaking = null;
                breakTicks = 0;
                breakProgressCurrent = 0f;
                if (swingHand.value) p.swingHand(Hand.MAIN_HAND);
            }

            status = "Breaking " + bp.toShortString() + " | " + totalBroken;

        } catch (Throwable ignored) {}
    }

    private Direction getFacingDirection(ClientPlayerEntity p, BlockPos bp) {
        double dx = p.getX() - (bp.getX() + 0.5);
        double dy = p.getEyeY() - (bp.getY() + 0.5);
        double dz = p.getZ() - (bp.getZ() + 0.5);
        return Direction.getFacing(dx, dy, dz);
    }

    private List<BlockPos> collectTargets(MinecraftClient mc, ClientPlayerEntity p) {
        List<BlockPos> result = new ArrayList<>();
        String m = mode.value();

        if (m.equals("TrapBreak") || onlySelfTrap.value) {
            List<PlayerEntity> trapped = new ArrayList<>();
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == p || !o.isAlive() || o.isSpectator()) continue;
                if (isFriend(o)) continue;
                if (p.distanceTo(o) > playerRange.value) continue;
                if (isTrapped(mc, o)) trapped.add(o);
            }
            if (trapped.isEmpty()) return result;

            Set<Long> seen = new HashSet<>();
            for (PlayerEntity o : trapped) {
                BlockPos base = o.getBlockPos();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos bp = base.add(dx, dy, dz);
                            long key = bp.asLong();
                            if (seen.contains(key)) continue;
                            seen.add(key);
                            if (isBreakable(mc, p, bp)) result.add(bp);
                        }
                    }
                }
            }
            return result;
        }

        if (m.equals("PlayerOnly")) {
            List<PlayerEntity> nearby = new ArrayList<>();
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == p || !o.isAlive() || o.isSpectator()) continue;
                if (p.distanceTo(o) > playerRange.value) continue;
                nearby.add(o);
            }
            if (nearby.isEmpty()) return result;

            Set<Long> seen = new HashSet<>();
            for (PlayerEntity o : nearby) {
                BlockPos base = o.getBlockPos();
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -2; dy <= 3; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            BlockPos bp = base.add(dx, dy, dz);
                            long key = bp.asLong();
                            if (seen.contains(key)) continue;
                            seen.add(key);
                            if (isBreakable(mc, p, bp)) result.add(bp);
                        }
                    }
                }
            }
            return result;
        }

        if (m.equals("AllObsidian")) {
            int r = (int) Math.ceil(range.value);
            BlockPos base = BlockPos.ofFloored(p.getEyePos());
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos bp = base.add(dx, dy, dz);
                        if (isBreakable(mc, p, bp)) result.add(bp);
                    }
                }
            }
            return result;
        }

        int r = (int) Math.ceil(range.value);
        BlockPos base = BlockPos.ofFloored(p.getEyePos());
        Vec3d eye = p.getEyePos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos bp = base.add(dx, dy, dz);
                    BlockState bs = mc.world.getBlockState(bp);
                    if (bs.isAir() || bs.isOf(Blocks.BEDROCK) || !bs.getFluidState().isEmpty()) continue;
                    if (flat.value && bp.getY() != p.getBlockPos().getY()) continue;
                    if (eye.distanceTo(Vec3d.ofCenter(bp)) > range.value) continue;
                    result.add(bp);
                }
            }
        }
        return result;
    }

    private boolean isBreakable(MinecraftClient mc, ClientPlayerEntity p, BlockPos bp) {
        try {
            BlockState bs = mc.world.getBlockState(bp);
            if (bs.isAir()) return false;
            if (!bs.isOf(Blocks.OBSIDIAN) && !bs.isOf(Blocks.CRYING_OBSIDIAN)) return false;
            if (flat.value && bp.getY() != p.getBlockPos().getY()) return false;
            if (p.getEyePos().distanceTo(Vec3d.ofCenter(bp)) > range.value) return false;

            Long t = brokenHistory.get(bp);
            if (t != null && System.currentTimeMillis() - t < 1000) return false;
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private boolean isTrapped(MinecraftClient mc, PlayerEntity o) {
        try {
            BlockPos feet = o.getBlockPos();
            int solid = 0;

            for (Direction d : Direction.Type.HORIZONTAL) {
                if (!mc.world.getBlockState(feet.offset(d)).isAir()) solid++;
            }
            for (Direction d : Direction.Type.HORIZONTAL) {
                if (!mc.world.getBlockState(feet.up().offset(d)).isAir()) solid++;
            }
            if (!mc.world.getBlockState(feet.up(2)).isAir()) solid += 2;
            if (!mc.world.getBlockState(feet.down()).isAir()) solid++;

            return solid >= 5;
        } catch (Throwable ignored) { return false; }
    }

    private void recordBroken(BlockPos bp) {
        if (brokenHistory.containsKey(bp)) return;
        brokenHistory.put(bp, System.currentTimeMillis());
        totalBroken++;
        recentBroken = brokenHistory.size();
    }

    // ==========================================
    // RENDER (ESP)
    // ==========================================
    public void render(DrawContext g, MinecraftClient mc, float delta) {
        // ⚡ ESP ayarı KAPALIYSA HİÇBİR ŞEY ÇİZME (FPS düşürmez)
        if (!showRender.value || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        int MAX_AGE = colorFade.value ? 30000 : 15000;

        for (Map.Entry<BlockPos, Long> e : brokenHistory.entrySet()) {
            BlockPos bp = e.getKey();
            long age = now - e.getValue();
            if (age > MAX_AGE) continue;

            float t = colorFade.value ? Math.min(1f, age / (float) MAX_AGE) : 0.5f;
            int r = (int) (255 * (1 - t) + 48 * t);
            int gr = (int) (48 * (1 - t) + 255 * t);
            int b = (int) (48 * (1 - t) + 96 * t);
            int baseColor = 0xFF000000 | (r << 16) | (gr << 8) | b;
            int alpha = (int) (200 * (1f - t * 0.6f));

            Box box = new Box(bp.getX() + 0.05, bp.getY() + 0.05, bp.getZ() + 0.05,
                    bp.getX() + 0.95, bp.getY() + 0.95, bp.getZ() + 0.95);
            int[] rect = projectBox(mc, box, delta);
            if (rect == null) continue;

            int c1 = withAlpha(baseColor, alpha);
            int c2 = withAlpha(baseColor, alpha / 3);

            g.fill(rect[0], rect[1], rect[2], rect[3], c2);
            outline(g, rect[0], rect[1], rect[2], rect[3], c1);

            if (age < 1000) {
                g.fill(rect[0], rect[1], rect[2], rect[1] + 1, 0xFFFFFFFF);
                g.fill(rect[0], rect[3] - 1, rect[2], rect[3], 0xFFFFFFFF);
            }
        }

        if (currentBreaking != null && breakProgressCurrent > 0.001f) {
            float prog = MathHelper.clamp(breakProgressCurrent, 0f, 1f);

            double half = 0.5 * prog;
            double cx = currentBreaking.getX() + 0.5;
            double cy = currentBreaking.getY() + 0.5;
            double cz = currentBreaking.getZ() + 0.5;

            Box inner = new Box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
            Box outer = new Box(currentBreaking.getX() + 0.02, currentBreaking.getY() + 0.02, currentBreaking.getZ() + 0.02,
                    currentBreaking.getX() + 0.98, currentBreaking.getY() + 0.98, currentBreaking.getZ() + 0.98);

            int[] outerRect = projectBox(mc, outer, delta);
            int[] innerRect = projectBox(mc, inner, delta);

            int r = 255;
            int gr = (int) (255 * (1f - prog));
            int b = 0;
            int fillColor = 0x66 << 24 | (r << 16) | (gr << 8) | b;
            int lineColor = 0xFF << 24 | (r << 16) | (gr << 8) | b;

            if (outerRect != null) {
                outline(g, outerRect[0], outerRect[1], outerRect[2], outerRect[3], 0xAAFFFFFF);
            }
            if (innerRect != null) {
                g.fill(innerRect[0], innerRect[1], innerRect[2], innerRect[3], fillColor);
                outline(g, innerRect[0], innerRect[1], innerRect[2], innerRect[3], lineColor);
            }
            if (outerRect != null) {
                int barW = outerRect[2] - outerRect[0];
                int barX = outerRect[0];
                int barY = outerRect[1] - 3;
                int barFill = (int) (barW * prog);
                g.fill(barX, barY, barX + barW, barY + 2, 0x80000000);
                g.fill(barX, barY, barX + barFill, barY + 2, lineColor);
            }
        }
    }

    public int getTotalBroken() { return totalBroken; }
    public int getRecentBroken() { return recentBroken; }
}