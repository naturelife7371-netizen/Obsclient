package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.Arrays;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class AutoMine extends Module {
    public static String status = "Off";
    public static AutoMine INSTANCE;

    // ===================== AYARLAR =====================
    final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzili", 6.0, 3.0, 10.0, 0.5);
    final NumSetting mineRange = new NumSetting("MineRange", "Kirma Menzili", 4.5, 2.0, 6.0, 0.1);

    final BoolSetting doubleSurround = new BoolSetting("DoubleSurround", "Cift Kat Kir", true);
    final BoolSetting silent = new BoolSetting("Silent", "Sessiz Kazma", true);
    final BoolSetting instant = new BoolSetting("Instant", "Paketle Aninda Kir", true);
    final BoolSetting swing = new BoolSetting("Swing", "Kol Salla", true);
    final BoolSetting render = new BoolSetting("Render", "Kirilmayi Goster", true);

    // ===================== STATE =====================
    private BlockPos targetPos = null;
    private Direction targetSide = null;
    private int originalSlot = -1;
    private boolean isMining = false;

    // Render State
    private BlockPos lastRenderedPos = null;
    private String lastBlockName = "";
    private float lastProgress = 0f;
    private long finishTime = 0;

    private static Field breakingProgressField = null;

    public AutoMine() {
        super("AutoMine", "AutoMine", "Breaks target's surround blocks.", "Rakibin etrafini kirar.", Category.PLAYER);
        settings.addAll(Arrays.asList(targetRange, mineRange, doubleSurround, silent, instant, swing, render));
        INSTANCE = this;
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Ready"; reset(); }
    @Override public void onDisable() { status = "Off"; reset(); }

    private void reset() {
        if (isMining && originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            }
        }
        targetPos = null;
        targetSide = null;
        originalSlot = -1;
        isMining = false;
        lastRenderedPos = null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) {
            reset();
            return;
        }

        // 1. Hedef Bul ve Kırılacak Bloğu Seç
        if (targetPos == null || mc.world.getBlockState(targetPos).isAir()) {
            targetPos = findTargetSurround(mc);
            if (targetPos != null) {
                targetSide = getBestSide(mc, targetPos);
                finishTime = 0;
            }
        }

        // 2. Hedef Geçerliyse İşlem Yap
        if (targetPos != null) {
            BlockState state = mc.world.getBlockState(targetPos);
            if (state.isAir()) {
                if (lastRenderedPos == null || !lastRenderedPos.equals(targetPos)) {
                    lastRenderedPos = targetPos;
                    lastBlockName = "Kirildi";
                    lastProgress = 1.0f;
                    finishTime = System.currentTimeMillis();
                }
                isMining = false;
                targetPos = null;
                if (silent.value) restoreSlot(mc);
                return;
            }

            int pickSlot = getBestTool(mc, targetPos);
            if (pickSlot != -1) {
                if (silent.value) {
                    if (!isMining) {
                        originalSlot = mc.player.getInventory().getSelectedSlot();
                        isMining = true;
                    }
                    if (mc.player.getInventory().getSelectedSlot() != pickSlot) {
                        mc.player.getInventory().setSelectedSlot(pickSlot);
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(pickSlot));
                    }
                }

                if (instant.value) {
                    // PAKETLE ANINDA KIR
                    int seq = mc.player.currentScreenHandler.getRevision();
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, targetSide, seq));
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, targetSide, seq));
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, targetPos, targetSide, seq));

                    if (swing.value) mc.player.swingHand(Hand.MAIN_HAND);
                    status = "Breaking...";
                } else {
                    // NORMAL KAZMA
                    mc.interactionManager.updateBlockBreakingProgress(targetPos, targetSide);
                    if (swing.value) mc.player.swingHand(Hand.MAIN_HAND);

                    if (getBreakingProgress(mc) > 0) {
                        lastRenderedPos = targetPos;
                        lastBlockName = state.getBlock().getName().getString();
                        lastProgress = getBreakingProgress(mc);
                    }
                    status = "Mining...";
                }
            } else {
                status = "No Tool!";
            }
        } else {
            if (isMining) {
                if (targetPos != null) {
                    int seq = mc.player.currentScreenHandler.getRevision();
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, targetPos, targetSide, seq));
                }
                if (silent.value) restoreSlot(mc);
                status = "Waiting";
            }
        }
    }

    private BlockPos findTargetSurround(MinecraftClient mc) {
        PlayerEntity target = null;
        double bestDist = targetRange.value;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.getHealth() <= 0 || isFriend(p)) continue;
            double dist = mc.player.distanceTo(p);
            if (dist < bestDist) {
                bestDist = dist;
                target = p;
            }
        }

        if (target == null) return null;

        BlockPos feet = target.getBlockPos();
        BlockPos bestPos = null;
        double bestMineDist = mineRange.value * mineRange.value;

        for (int y = 0; y <= (doubleSurround.value ? 1 : 0); y++) {
            BlockPos center = feet.up(y);
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos pos = center.offset(dir);
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir() || state.getHardness(mc.world, pos) < 0) continue;

                // Kendi etrafımızı kırmayalım
                if (pos.equals(mc.player.getBlockPos()) || pos.equals(mc.player.getBlockPos().up())) continue;

                double dist = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                if (dist <= bestMineDist) {
                    bestMineDist = dist;
                    bestPos = pos;
                }
            }
        }
        return bestPos;
    }

    private int getBestTool(MinecraftClient mc, BlockPos pos) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private Direction getBestSide(MinecraftClient mc, BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Direction best = Direction.UP;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            Vec3d hit = Vec3d.ofCenter(pos).add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
            double dist = eye.squaredDistanceTo(hit);
            if (dist < bestDist) {
                bestDist = dist;
                best = dir;
            }
        }
        return best;
    }

    private void restoreSlot(MinecraftClient mc) {
        if (originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            originalSlot = -1;
            isMining = false;
        }
    }

    private float getBreakingProgress(MinecraftClient mc) {
        try {
            if (breakingProgressField == null) {
                breakingProgressField = ClientPlayerInteractionManager.class.getDeclaredField("currentBreakingProgress");
                breakingProgressField.setAccessible(true);
            }
            return breakingProgressField.getFloat(mc.interactionManager);
        } catch (Exception e) {
            return 0f;
        }
    }

    // ===================== RENDER (3D ESP) =====================
    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (!render.value || mc.player == null || mc.world == null) return;

        BlockPos drawPos = (targetPos != null) ? targetPos : lastRenderedPos;
        if (drawPos == null) return;

        if (targetPos == null && finishTime != 0 && System.currentTimeMillis() - finishTime > 500) {
            lastRenderedPos = null;
            return;
        }

        Box box = new Box(drawPos);
        int[] r = projectBox(mc, box, delta);
        if (r == null) return;

        float progress = (targetPos != null && !instant.value) ? getBreakingProgress(mc) : (instant.value ? 1.0f : lastProgress);
        if (targetPos == null) progress = 1.0f;

        // Görseldeki gibi renkler
        int fillColor = 0x40B05CFF; // Yarı saydam Mor
        int outlineColor = 0xFFB05CFF; // Parlak Mor
        int progressColor = 0xFF55FF55; // Yeşil bar

        // 3D Tel Kafes (Çerçeve)
        draw3DWireframe(g, mc, box, delta, outlineColor);

        // İç Dolgu (Kutunun tamamı)
        g.fill(r[0], r[1], r[2], r[3], fillColor);

        // Alt Kısımda Kırılma Barı (Yeşil)
        int fillHeight = (int) ((r[3] - r[1]) * progress);
        // Eğer dolgu rengini değiştirmek istersen burayı açabilirsin, şimdilik sadece altta bar var
        // g.fill(r[0], r[3] - fillHeight, r[2], r[3], progressColor);

        // Çerçeve Tekrar Çiz (Dolguyu kapatmasın diye)
        draw3DWireframe(g, mc, box, delta, outlineColor);

        // Metin Çizimi
        String name = (targetPos != null) ? mc.world.getBlockState(targetPos).getBlock().getName().getString() : lastBlockName;
        if (name.length() > 15) name = name.substring(0, 13) + "..";

        String text = String.format("%s %d%%", name, (int)(progress * 100));
        int tw = mc.textRenderer.getWidth(text);
        int textX = (r[0] + r[2]) / 2 - tw / 2;
        int textY = (r[1] + r[3]) / 2 - 4; // Kutunun tam ortası

        g.fill(textX - 2, textY - 2, textX + tw + 2, textY + 10, 0xA0000000);
        g.drawText(mc.textRenderer, text, textX, textY, 0xFFFFFFFF, true);
    }

    // 3D Kutu Çizim Yardımcısı
    private void draw3DWireframe(DrawContext g, MinecraftClient mc, Box box, float delta, int color) {
        Vec3d[] corners = {
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
        };
        int[][] s = new int[8][];
        for (int i = 0; i < 8; i++) {
            s[i] = project(mc, corners[i], delta);
            if (s[i] == null) return;
        }
        // Alt yüz
        line(g, s[0][0], s[0][1], s[1][0], s[1][1], color);
        line(g, s[1][0], s[1][1], s[2][0], s[2][1], color);
        line(g, s[2][0], s[2][1], s[3][0], s[3][1], color);
        line(g, s[3][0], s[3][1], s[0][0], s[0][1], color);
        // Üst yüz
        line(g, s[4][0], s[4][1], s[5][0], s[5][1], color);
        line(g, s[5][0], s[5][1], s[6][0], s[6][1], color);
        line(g, s[6][0], s[6][1], s[7][0], s[7][1], color);
        line(g, s[7][0], s[7][1], s[4][0], s[4][1], color);
        // Dikey kenarlar
        line(g, s[0][0], s[0][1], s[4][0], s[4][1], color);
        line(g, s[1][0], s[1][1], s[5][0], s[5][1], color);
        line(g, s[2][0], s[2][1], s[6][0], s[6][1], color);
        line(g, s[3][0], s[3][1], s[7][0], s[7][1], color);
    }
}