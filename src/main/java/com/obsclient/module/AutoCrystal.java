package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings({"unused", "ConstantConditions"})
public class AutoCrystal extends Module {
    public static String status = "Off";
    public static AutoCrystal INSTANCE;

    // ===================== ANARŞİ AYARLARI (MAKSİMUM) =====================
    public final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzili", 12.0, 1.0, 20.0, 0.5);
    public final NumSetting placeRange  = new NumSetting("PlaceRange", "Yerlestirme", 5.9, 1.0, 6.0, 0.1); // Grim max
    public final NumSetting breakRange  = new NumSetting("BreakRange", "Patlatma", 5.9, 1.0, 6.0, 0.1);   // NCP max
    public final NumSetting minDamage   = new NumSetting("MinDamage", "Min Hasar", 3.0, 0.0, 20.0, 0.5);
    public final NumSetting maxSelfDmg  = new NumSetting("MaxSelfDmg", "Max Oto Hasar", 16.0, 0.0, 20.0, 0.5);
    public final NumSetting facePlaceHP = new NumSetting("FacePlaceHP", "FacePlace Can", 8.0, 0.0, 20.0, 0.5);
    public final NumSetting lethalMult  = new NumSetting("LethalMult", "Oldurucu Carpan", 1.5, 1.0, 3.0, 0.1);
    public final NumSetting extrapolate = new NumSetting("Extrapolate", "Gelecek Tahmini", 5.0, 0.0, 10.0, 1.0);

    public final ModeSetting bypassMode = new ModeSetting("Bypass", "Bypass Modu", 0,
            new String[]{"Grim+NCP", "Strict Grim", "NCP Old"}, new String[]{"Grim + NCP (Onerilen)", "Sadece Grim", "Eski NCP"});

    public final BoolSetting doPlace    = new BoolSetting("Place", "Kristal Koy", true);
    public final BoolSetting doBreak    = new BoolSetting("Break", "Kristal Patlat", true);
    public final BoolSetting antiSuicide= new BoolSetting("AntiSuicide", "Intihar Engelle", true);
    public final BoolSetting rotate     = new BoolSetting("Rotate", "Sessiz Donus", true);
    public final BoolSetting silentSwap = new BoolSetting("SilentSwap", "Gizli Slot", true);
    public final BoolSetting idPredict  = new BoolSetting("IDPredict", "ID Tahmini (Aninda Patlat)", true);
    public final BoolSetting allowEnderChest = new BoolSetting("EnderChest", "Ender Sandigi Ustu", true);

    // ===================== STATE =====================
    private EndCrystalEntity targetCrystal = null;
    private BlockPos targetPos = null;
    private long lastPlaceTime = 0;
    private long lastBreakTime = 0;

    public AutoCrystal() {
        super("AutoCrystal", "AutoCrystal", "Lethal Grim+NCP Crystal Aura.", "Olumcul Grim+NCP Kristal Aura.", Category.COMBAT);
        settings.addAll(Arrays.asList(
                targetRange, placeRange, breakRange, minDamage, maxSelfDmg, facePlaceHP, lethalMult, extrapolate,
                bypassMode, doPlace, doBreak, antiSuicide, rotate, silentSwap, idPredict, allowEnderChest
        ));
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        status = "Hazir";
        targetCrystal = null;
        targetPos = null;
    }

    @Override
    public void onDisable() {
        targetCrystal = null;
        targetPos = null;
    }

    // ===================== ANLIK PATLATMA (ID PREDICT) =====================
    public void onCrystalSpawn(MinecraftClient mc, EndCrystalEntity c) {
        if (!enabled || !doBreak.value || !idPredict.value) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (System.currentTimeMillis() - lastBreakTime < 30) return;

        LivingEntity target = findTarget(mc, mc.player);
        if (target == null || !canBreak(mc.player, c, target)) return;

        if (breakCrystal(mc, mc.player, c, true)) {
            lastBreakTime = System.currentTimeMillis();
            status = "§4ID Predict Patlama!";
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null || shouldPause(p)) return;

        LivingEntity target = findTarget(mc, p);
        if (target == null) {
            status = "Hedef yok";
            targetCrystal = null; targetPos = null;
            return;
        }

        // 1. Patlatma
        if (doBreak.value && System.currentTimeMillis() - lastBreakTime >= 30) {
            EndCrystalEntity best = findBestCrystal(mc, p, target);
            targetCrystal = best;
            if (best != null) {
                if (breakCrystal(mc, p, best, false)) {
                    lastBreakTime = System.currentTimeMillis();
                    status = "§cPatlatildi!";
                    return;
                }
            }
        }

        // 2. Yerlestirme
        if (doPlace.value && System.currentTimeMillis() - lastPlaceTime >= 30) {
            BlockPos bestPos = findBestPlacePos(mc, p, target);
            targetPos = bestPos;
            if (bestPos != null) {
                if (placeCrystal(mc, p, bestPos)) {
                    lastPlaceTime = System.currentTimeMillis();
                    status = "§aYerlestirildi!";
                }
            } else {
                status = "Pozisyon yok";
            }
        }
    }

    // ===================== HEDEF & LETHAL MANTIĞI =====================
    private LivingEntity findTarget(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.world == null) return null;
        LivingEntity best = null;
        double bestScore = -Double.MAX_VALUE;

        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double dist = p.squaredDistanceTo(o);
            if (dist > targetRange.value * targetRange.value) continue;

            // Lethal (öldürücü) hedef önceliği: Canı az olan + mesafe
            double score = (1.0 / (o.getHealth() + o.getAbsorptionAmount() + 1.0)) * 100.0 - dist;
            if (score > bestScore) { bestScore = score; best = o; }
        }
        return best;
    }

    private float getRequiredDamage(LivingEntity target) {
        float hp = target.getHealth() + target.getAbsorptionAmount();
        if (hp <= minDamage.value * lethalMult.value) return 1.0f; // Tek atışta öldür
        return hp < facePlaceHP.value ? 1.0f : (float) minDamage.value;
    }

    private boolean isSafe(ClientPlayerEntity p, double selfDmg) {
        if (!antiSuicide.value) return true;
        return selfDmg <= maxSelfDmg.value && selfDmg < p.getHealth() + p.getAbsorptionAmount() - 0.5;
    }

    private double blast(LivingEntity target, Vec3d pos) {
        double dist = target.getBoundingBox().getCenter().distanceTo(pos);
        if (dist > 12.0) return 0.0;
        double impact = (1.0 - (dist / 12.0));
        return ((impact * impact + impact) / 2.0) * 7.0 * 6.0 + 1.0;
    }

    // ===================== PATLATMA (GRIM + NCP) =====================
    private EndCrystalEntity findBestCrystal(MinecraftClient mc, ClientPlayerEntity p, LivingEntity target) {
        if (mc.world == null) return null;
        EndCrystalEntity best = null;
        double bestScore = -Double.MAX_VALUE;
        float reqDmg = getRequiredDamage(target);

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c) || c.isRemoved() || !c.isAlive()) continue;
            if (!canBreak(p, c, target)) continue;

            Vec3d cpos = c.getBoundingBox().getCenter();
            double targetDmg = blast(target, cpos);
            double selfDmg = blast(p, cpos);

            if (targetDmg < reqDmg || !isSafe(p, selfDmg)) continue;

            double score = targetDmg - (selfDmg * 0.8);
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    private boolean canBreak(ClientPlayerEntity p, EndCrystalEntity c, LivingEntity target) {
        double dist = p.getEyePos().distanceTo(c.getBoundingBox().getCenter());
        if (dist > breakRange.value) return false;
        float reqDmg = getRequiredDamage(target);
        double tDmg = blast(target, c.getBoundingBox().getCenter());
        double sDmg = blast(p, c.getBoundingBox().getCenter());
        return tDmg >= reqDmg && isSafe(p, sDmg);
    }

    private boolean breakCrystal(MinecraftClient mc, ClientPlayerEntity p, EndCrystalEntity c, boolean predict) {
        if (mc.getNetworkHandler() == null) return false;
        try {
            Vec3d aim = c.getBoundingBox().getCenter();
            if (rotate.value) {
                float[] a = calcAngle(p.getEyePos(), aim);
                // GrimAC Perfect Angle Bypass
                a[0] += (float)(Math.random() * 0.05 - 0.025);
                a[1] += (float)(Math.random() * 0.05 - 0.025);
                Rot.request(a[0], a[1], 50);
                Rot.flush(mc);

                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        p.getX(), p.getY(), p.getZ(), a[0], a[1], p.isOnGround(), p.horizontalCollision
                ));
            }

            if (predict && idPredict.value) {
                int id = c.getId();
                for (int i = 0; i <= 1; i++) {
                    PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.attack(c, p.isSneaking());
                    changeId(packet, id + i);
                    mc.getNetworkHandler().sendPacket(packet);
                }
            } else {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(c, p.isSneaking()));
            }

            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            p.swingHand(Hand.MAIN_HAND);
            return true;
        } catch (Throwable t) { return false; }
    }

    // ===================== YERLEŞTİRME (NCP RAYTRACE BYPASS) =====================
    private BlockPos findBestPlacePos(MinecraftClient mc, ClientPlayerEntity p, LivingEntity target) {
        if (mc.world == null || !hasCrystal(p)) return null;

        Vec3d targetPosVec = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d predictedTarget = targetPosVec.add(target.getVelocity().multiply(extrapolate.value));
        BlockPos origin = BlockPos.ofFloored(predictedTarget);

        List<BlockPos> candidates = new ArrayList<>();
        int r = 3;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (isValidPlace(mc, p, pos)) candidates.add(pos);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> -blast(target, Vec3d.ofCenter(pos))));

        float reqDmg = getRequiredDamage(target);
        for (BlockPos pos : candidates) {
            Vec3d center = Vec3d.ofCenter(pos);
            double tDmg = blast(target, center);
            double sDmg = blast(p, center);
            if (tDmg >= reqDmg && isSafe(p, sDmg)) return pos;
        }
        return null;
    }

    private boolean isValidPlace(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos) {
        if (mc.world == null) return false;
        BlockState base = mc.world.getBlockState(pos.down());

        // Ender Chest destegi eklendi
        boolean validBase = base.isOf(Blocks.OBSIDIAN) || base.isOf(Blocks.BEDROCK) ||
                (allowEnderChest.value && base.isOf(Blocks.ENDER_CHEST));

        if (!validBase) return false;
        if (!mc.world.isAir(pos) || !mc.world.isAir(pos.up())) return false;
        if (p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > placeRange.value * placeRange.value) return false;

        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
        if (!mc.world.getOtherEntities(null, box, e -> !e.isRemoved()).isEmpty()) return false;
        return true;
    }

    private boolean placeCrystal(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos) {
        if (mc.getNetworkHandler() == null || mc.interactionManager == null) return false;
        try {
            Hand hand = Hand.MAIN_HAND;
            int slot = -1;
            if (p.getOffHandStack().isOf(Items.END_CRYSTAL)) {
                hand = Hand.OFF_HAND;
            } else {
                slot = findHot(p, s -> s.isOf(Items.END_CRYSTAL));
                if (slot < 0) {
                    if (!pullToHotbar(mc, p, s -> s.isOf(Items.END_CRYSTAL))) return false;
                    slot = findHot(p, s -> s.isOf(Items.END_CRYSTAL));
                    if (slot < 0) return false;
                }
            }

            BlockHitResult hit = getStrictInteract(mc, p, pos);
            if (hit == null) return false;

            int prev = p.getInventory().getSelectedSlot();
            if (slot >= 0 && slot != prev) {
                p.getInventory().setSelectedSlot(slot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            if (rotate.value) {
                float[] a = calcAngle(p.getEyePos(), hit.getPos());
                // NCP Raytrace Bypass
                a[0] += (float)(Math.random() * 0.1 - 0.05);
                a[1] += (float)(Math.random() * 0.1 - 0.05);

                Rot.request(a[0], a[1], 50);
                Rot.flush(mc);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        p.getX(), p.getY(), p.getZ(), a[0], a[1], p.isOnGround(), p.horizontalCollision
                ));
            }

            mc.interactionManager.interactBlock(p, hand, hit);
            p.swingHand(hand);

            if (slot >= 0 && silentSwap.value && prev != slot) {
                p.getInventory().setSelectedSlot(prev);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prev));
            }
            return true;
        } catch (Throwable t) { return false; }
    }

    private BlockHitResult getStrictInteract(MinecraftClient mc, ClientPlayerEntity p, BlockPos bp) {
        if (mc.world == null) return null;
        float bestDistance = Float.MAX_VALUE;
        Direction bestDirection = null;
        float upPoint = (float) bp.toCenterPos().getY();

        if (p.getEyePos().getY() > upPoint) {
            bestDirection = Direction.UP;
        } else {
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue;
                Vec3d directionVec = new Vec3d(bp.getX() + 0.5 + dir.getVector().getX() * 0.5, bp.getY() + 0.99, bp.getZ() + 0.5 + dir.getVector().getZ() * 0.5);
                if (!mc.world.getBlockState(bp.offset(dir)).isReplaceable()) continue;
                float distance = (float) p.getEyePos().squaredDistanceTo(directionVec);
                if (bestDistance > distance) { bestDirection = dir; bestDistance = distance; }
            }
        }
        if (bestDirection == null) return null;

        Vec3d hitVec = getVisibleDirectionPoint(mc, p, bestDirection, bp);
        if (hitVec == null) return null;
        return new BlockHitResult(hitVec, bestDirection, bp.down(), false);
    }

    private Vec3d getVisibleDirectionPoint(MinecraftClient mc, ClientPlayerEntity p, Direction dir, BlockPos bp) {
        if (mc.world == null) return null;
        Vec3d center = Vec3d.ofCenter(bp).add(0, 0.5, 0);
        Vec3d offset = new Vec3d(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
        Vec3d hit = center.add(offset);
        BlockHitResult result = mc.world.raycast(new RaycastContext(p.getEyePos(), hit, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return result.getType() == HitResult.Type.MISS ? hit : null;
    }

    // ===================== RENDER (GÖRSEL) =====================
    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (!enabled || mc.player == null || mc.world == null) return;

        // Yerlestirme alani (Target Pos) - Yesil/Mavi Kutu
        if (targetPos != null) {
            Box box = new Box(targetPos);
            int[] proj = projectBox(mc, box, delta);
            if (proj != null) {
                int x1 = Math.min(proj[0], proj[2]), x2 = Math.max(proj[0], proj[2]);
                int y1 = Math.min(proj[1], proj[3]), y2 = Math.max(proj[1], proj[3]);
                int fillColor = withAlpha(0xFF4FC3FF, 60);
                int outlineColor = 0xFF4FC3FF;
                g.fill(x1, y1, x2, y2, fillColor);
                outline(g, x1, y1, x2, y2, outlineColor);
            }
        }

        // Patlatilacak Kristal (Target Crystal) - Kirmizi Kutu
        if (targetCrystal != null && !targetCrystal.isRemoved()) {
            Box box = targetCrystal.getBoundingBox();
            int[] proj = projectBox(mc, box, delta);
            if (proj != null) {
                int x1 = Math.min(proj[0], proj[2]), x2 = Math.max(proj[0], proj[2]);
                int y1 = Math.min(proj[1], proj[3]), y2 = Math.max(proj[1], proj[3]);
                int fillColor = withAlpha(0xFFFF5C7A, 60);
                int outlineColor = 0xFFFF5C7A;
                g.fill(x1, y1, x2, y2, fillColor);
                outline(g, x1, y1, x2, y2, outlineColor);
            }
        }
    }

    // ===================== YARDIMCILAR =====================
    private boolean shouldPause(ClientPlayerEntity p) {
        return p.getHealth() + p.getAbsorptionAmount() < 3.0;
    }

    private boolean hasCrystal(ClientPlayerEntity p) {
        return p.getOffHandStack().isOf(Items.END_CRYSTAL) || findHot(p, s -> s.isOf(Items.END_CRYSTAL)) >= 0;
    }

    private int findHot(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    private boolean pullToHotbar(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred) {
        if (p.playerScreenHandler == null || mc.interactionManager == null) return false;
        int invSlot = -1;
        for (int i = 9; i < 36; i++) if (pred.test(p.getInventory().getStack(i))) { invSlot = i; break; }
        if (invSlot < 0) return false;
        int empty = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isEmpty()) { empty = i; break; }
        if (empty < 0) empty = 8;
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, invSlot, empty, SlotActionType.SWAP, p);
        return true;
    }

    private void changeId(PlayerInteractEntityC2SPacket packet, int id) {
        try {
            Field field = PlayerInteractEntityC2SPacket.class.getDeclaredField("entityId");
            field.setAccessible(true);
            field.setInt(packet, id);
        } catch (Exception ignored) {
            try {
                Field field = PlayerInteractEntityC2SPacket.class.getDeclaredField("field_12870");
                field.setAccessible(true);
                field.setInt(packet, id);
            } catch (Exception ignored2) {}
        }
    }

    private float[] calcAngle(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
                (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
                (float) -Math.toDegrees(Math.atan2(dy, dist))
        };
    }
}