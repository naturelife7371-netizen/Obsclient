package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

public class TotKiller extends Module {
    public static String status = "Off";

    final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzil", 8.0, 3.0, 15.0, 0.5);
    final NumSetting placeRange  = new NumSetting("PlaceRange", "Koyma Menzil", 4.5, 3.0, 5.5, 0.1);
    final NumSetting breakRange  = new NumSetting("BreakRange", "Kirma Menzil", 4.5, 3.0, 5.5, 0.1);
    final NumSetting placeDelay  = new NumSetting("PlaceDelay", "Koyma Gecik.", 1, 0, 5, 1);
    final NumSetting breakDelay  = new NumSetting("BreakDelay", "Kirma Gecik.", 1, 0, 5, 1);
    final NumSetting holdTick    = new NumSetting("PostAttackHold", "Vurus Bekle", 3, 0, 10, 1);

    final BoolSetting autoTrap   = new BoolSetting("AutoTrap", "Oto Tuzak", true);
    final BoolSetting autoMace   = new BoolSetting("AutoMace", "Oto Mace", true);
    final BoolSetting silent     = new BoolSetting("SilentRotate", "Sessiz Donus", true);
    final BoolSetting antiSuicide= new BoolSetting("AntiSuicide", "Intihar Engel", true);

    private PlayerEntity target = null;
    private int placeCd = 0, breakCd = 0, holdCd = 0;
    private float sentYaw = 0f, sentPitch = 0f;
    private boolean rotInit = false;

    public TotKiller() {
        super("TotKiller", "TotKiller", "Drains all totems and kills.", "Tum totemleri bitirip oldurur.", Category.COMBAT);
        settings.addAll(Arrays.asList(targetRange, placeRange, breakRange, placeDelay, breakDelay, holdTick, autoTrap, autoMace, silent, antiSuicide));
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        status = "Searching";
        target = null;
        placeCd = 0; breakCd = 0; holdCd = 0;
        rotInit = false;
    }

    @Override
    public void onDisable() {
        status = "Off";
        target = null;
    }

    private void sendLook(MinecraftClient mc, ClientPlayerEntity p, Vec3d aim) {
        try {
            if (mc.getNetworkHandler() == null) return;
            float[] angle = calcAngle(p.getEyePos(), aim);

            if (!rotInit) {
                sentYaw = p.getYaw(); sentPitch = p.getPitch();
                rotInit = true;
            }

            float dy = MathHelper.wrapDegrees(angle[0] - sentYaw);
            float dp = angle[1] - sentPitch;
            float maxStep = 25f;
            if (Math.abs(dy) > maxStep) dy = Math.signum(dy) * maxStep;
            if (Math.abs(dp) > maxStep) dp = Math.signum(dp) * maxStep;

            float newYaw = MathHelper.wrapDegrees(sentYaw + dy);
            float newPitch = MathHelper.clamp(sentPitch + dp, -90f, 90f);

            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    newYaw, newPitch, p.isOnGround(), p.horizontalCollision));

            sentYaw = newYaw; sentPitch = newPitch;
        } catch (Throwable ignored) {}
    }

    private void syncSlot(MinecraftClient mc, ClientPlayerEntity p, int slot) {
        try {
            if (mc.getNetworkHandler() == null) return;
            p.getInventory().setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return;
            if (mc.currentScreen != null || p.isGliding()) return;

            if (placeCd > 0) placeCd--;
            if (breakCd > 0) breakCd--;
            if (holdCd > 0) holdCd--;

            target = findTarget(mc, p);
            if (target == null) {
                status = "No target";
                return;
            }

            status = "Fighting " + target.getName().getString();

            if (autoTrap.value && holdCd == 0) {
                if (tryTrap(mc, p, target)) return;
            }

            if (holdCd == 0) {
                if (tryBreakCrystal(mc, p, target)) return;
                if (placeCd == 0) {
                    if (tryPlaceCrystal(mc, p, target)) {
                        placeCd = (int) placeDelay.value;
                        return;
                    }
                }
            }

            if (autoMace.value && holdCd == 0) {
                tryMaceAttack(mc, p, target);
            }

        } catch (Throwable ignored) {}
    }

    private PlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity p) {
        PlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double d = p.distanceTo(o);
            if (d > targetRange.value) continue;
            double score = (o.getHealth() + o.getAbsorptionAmount()) * 10 + d;
            if (score < bestScore) { bestScore = score; best = o; }
        }
        return best;
    }

    private boolean tryTrap(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t) {
        if (findInv(p, s -> s.isOf(Items.OBSIDIAN)) < 0) return false;

        BlockPos feet = t.getBlockPos();
        List<BlockPos> toPlace = new ArrayList<>();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos fp = feet.offset(dir);
            BlockPos hp = feet.up().offset(dir);
            if (mc.world.getBlockState(fp).isReplaceable()) toPlace.add(fp);
            if (mc.world.getBlockState(hp).isReplaceable()) toPlace.add(hp);
        }

        if (toPlace.isEmpty()) return false;

        toPlace.sort(Comparator.comparingDouble(b -> p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(b))));

        int placed = 0;
        for (BlockPos bp : toPlace) {
            if (placed >= 2) break;
            if (p.getEyePos().distanceTo(Vec3d.ofCenter(bp)) > placeRange.value) continue;
            if (placeBlockPacket(mc, p, bp, s -> s.isOf(Items.OBSIDIAN))) {
                placed++;
            }
        }
        return placed > 0;
    }

    private boolean tryBreakCrystal(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t) {
        if (breakCd > 0) return false;

        EndCrystalEntity bestCrystal = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, t.getBoundingBox().expand(6.0), e -> e.isAlive())) {
            // 1.21+ uyumlu konum alma: getPos() yerine getX/Y/Z kullanılıyor
            Vec3d cPos = new Vec3d(c.getX(), c.getY(), c.getZ());
            double dist = p.getEyePos().distanceTo(cPos);
            if (dist > breakRange.value) continue;

            if (antiSuicide.value && p.getHealth() + p.getAbsorptionAmount() <= 8 && p.distanceTo(c) < 3.0) continue;

            double score = t.distanceTo(c);
            if (score > bestScore) { bestScore = score; bestCrystal = c; }
        }

        if (bestCrystal != null) {
            Vec3d cPos = new Vec3d(bestCrystal.getX(), bestCrystal.getY(), bestCrystal.getZ());
            if (silent.value) sendLook(mc, p, cPos);

            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(bestCrystal, p.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            p.swingHand(Hand.MAIN_HAND);

            holdCd = (int) holdTick.value;
            breakCd = (int) breakDelay.value;
            return true;
        }
        return false;
    }

    private boolean tryPlaceCrystal(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t) {
        if (findHot(p, s -> s.isOf(Items.END_CRYSTAL)) < 0 && findInv(p, s -> s.isOf(Items.END_CRYSTAL)) < 0) return false;

        BlockPos base = t.getBlockPos();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos bp = base.add(x, y, z);
                    var state = mc.world.getBlockState(bp);
                    if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) continue;
                    if (!mc.world.getBlockState(bp.up()).isAir()) continue;

                    // 1.21+ uyumlu konum alma
                    Vec3d targetPos = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
                    double dist = p.getEyePos().distanceTo(targetPos);
                    if (dist <= placeRange.value && dist < bestDist) {
                        bestDist = dist;
                        bestPos = bp;
                    }
                }
            }
        }

        if (bestPos != null) {
            Vec3d hitVec = Vec3d.ofCenter(bestPos).add(0, 1, 0);
            if (placeBlockPacket(mc, p, bestPos, s -> s.isOf(Items.END_CRYSTAL), hitVec, Direction.UP)) {
                holdCd = 1;
                return true;
            }
        }
        return false;
    }

    private void tryMaceAttack(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t) {
        if (p.fallDistance < 1.0) return;
        if (!p.getMainHandStack().isOf(Items.MACE)) {
            int slot = findHot(p, s -> s.isOf(Items.MACE));
            if (slot >= 0) syncSlot(mc, p, slot);
            else return;
        }

        if (p.distanceTo(t) <= 3.5 && p.fallDistance > 1.2) {
            // 1.21+ uyumlu hedef konumu
            Vec3d tPos = new Vec3d(t.getX(), t.getY() + t.getHeight() / 2.0, t.getZ());
            if (silent.value) sendLook(mc, p, tPos);
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(t, p.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            p.swingHand(Hand.MAIN_HAND);
            holdCd = (int) holdTick.value;
            status = "Mace Smash!";
        }
    }

    private boolean placeBlockPacket(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos, Predicate<ItemStack> pred) {
        return placeBlockPacket(mc, p, pos, pred, null, null);
    }

    private boolean placeBlockPacket(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos, Predicate<ItemStack> pred, Vec3d forcedHit, Direction forcedFace) {
        try {
            int slot = toHotbar(mc, p, pred);
            if (slot < 0) return false;

            BlockPos support = null;
            Direction face = null;
            Vec3d hitVec = null;

            if (forcedHit != null && forcedFace != null) {
                support = pos; face = forcedFace; hitVec = forcedHit;
            } else {
                for (Direction d : Direction.values()) {
                    BlockPos n = pos.offset(d);
                    var s = mc.world.getBlockState(n);
                    if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                    support = n;
                    face = d.getOpposite();
                    hitVec = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                    break;
                }
            }
            if (support == null) return false;

            int prev = p.getInventory().getSelectedSlot();
            syncSlot(mc, p, slot);
            if (silent.value) sendLook(mc, p, hitVec);

            BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            p.swingHand(Hand.MAIN_HAND);

            syncSlot(mc, p, prev);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}