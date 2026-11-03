package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

public class AutoAnchor extends Module {
    public static String status = "Off";
    public static AutoAnchor INSTANCE;
    public BlockPos currentPos = null;

    final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzili", 14, 4, 25, 1);
    final NumSetting placeRange = new NumSetting("PlaceRange", "Menzil", 5.0, 2.0, 6.0, 0.1);
    final NumSetting minDmg = new NumSetting("MinDamage", "Min Hasar", 2, 1, 36, 0.5);
    final NumSetting maxSelf = new NumSetting("MaxSelfDamage", "Max Kendi", 14, 1, 36, 0.5);
    final NumSetting faceHp = new NumSetting("FacePlaceHP", "FacePlace HP", 8, 1, 36, 1);
    final NumSetting delay = new NumSetting("Delay", "Gecikme", 0, 0, 10, 1);
    final NumSetting enchFactor = new NumSetting("EnchantFactor", "Büyü Fakt.", 0.8, 0.3, 1.0, 0.05);
    final BoolSetting instant = new BoolSetting("InstantCombo", "Hızlı Kombo", true);
    final BoolSetting existing = new BoolSetting("ExistingAnchor", "Mevcut Anchor", true);
    final BoolSetting useInv = new BoolSetting("FromInventory", "Envanterden", true);
    final BoolSetting silent = new BoolSetting("SilentRotate", "Sessiz Dönüş", true);
    final BoolSetting antiSuicide = new BoolSetting("AntiSuicide", "Anti-İntihar", true);
    final BoolSetting facePlace = new BoolSetting("FacePlace", "FacePlace", true);

    private int cd = 0;

    public AutoAnchor() {
        super("AutoAnchor", "AutoAnchor", "Anchor combo.", "Anchor kombo.", Category.COMBAT);
        settings.addAll(Arrays.asList(targetRange, placeRange, minDmg, maxSelf, faceHp, delay,
                enchFactor, instant, existing, useInv, silent, antiSuicide, facePlace));
        INSTANCE = this;
    }

    public boolean isActive() { return enabled && currentPos != null; }
    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Searching"; cd = 0; currentPos = null; }
    @Override public void onDisable() { status = "Off"; currentPos = null; }

    private int slotFor(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred) {
        return useInv.value ? toHotbar(mc, p, pred) : findHot(p, pred);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cd > 0) { cd--; return; }
        if (mc.world.getRegistryKey() == World.NETHER) { status = "Nether off"; return; }
        if (p.isGliding()) { status = "Gliding"; return; }

        PlayerEntity t = null; double bestD = targetRange.value;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double d = p.distanceTo(o);
            if (d < bestD) { bestD = d; t = o; }
        }
        if (t == null) { status = "No target"; currentPos = null; return; }

        double tHp = t.getHealth() + t.getAbsorptionAmount(), sHp = p.getHealth() + p.getAbsorptionAmount();
        double need = (facePlace.value && tHp <= faceHp.value) ? 1.0 : minDmg.value;
        Vec3d eye = p.getEyePos(), tPos = epos(t);
        int r = (int)Math.ceil(placeRange.value);
        BlockPos base = BlockPos.ofFloored(eye);
        BlockPos bestA = null; int bestCh = 0; double bestAS = -1e9;
        BlockPos bestP = null; Direction bestPD = null; double bestPS = -1e9;

        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            BlockPos bp = base.add(dx, dy, dz);
            var bs = mc.world.getBlockState(bp);
            boolean isA = bs.isOf(Blocks.RESPAWN_ANCHOR);
            if (isA) { if (!existing.value) continue; }
            else if (!bs.isAir()) continue;

            Vec3d mid = new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
            if (eye.distanceTo(mid) > placeRange.value || mid.distanceTo(tPos) > 10.0) continue;
            Vec3d c = isA ? new Vec3d(bp.getX() + 0.5, bp.getY() + 1.01, bp.getZ() + 0.5) : mid;

            double td = blast(mc, t, c, 5f, enchFactor.value);
            if (td < need) continue;
            double sd = blast(mc, p, c, 5f, 1.0);
            boolean lethal = td >= tHp;
            if (antiSuicide.value && sd >= sHp - 1.0) continue;
            if (!lethal && sd > maxSelf.value) continue;

            double score = td - sd * 0.5 + (lethal ? 100 : 0);
            if (isA) {
                if (score > bestAS) {
                    bestAS = score; bestA = bp;
                    bestCh = bs.get(Properties.CHARGES, 0);
                }
            } else {
                if (score <= bestPS) continue;
                Direction sup = support(mc, bp);
                if (sup == null) continue;
                bestP = bp; bestPD = sup; bestPS = score;
            }
        }

        int prev = p.getInventory().getSelectedSlot();
        boolean did = false;
        if (bestA != null) { currentPos = bestA; did = detonate(mc, p, bestA, bestCh); }
        else if (bestP != null) {
            currentPos = bestP;
            int slot = slotFor(mc, p, s -> s.isOf(Items.RESPAWN_ANCHOR));
            if (slot < 0) status = "No anchor";
            else {
                BlockPos n = bestP.offset(bestPD);
                Direction face = bestPD.getOpposite();
                Vec3d hv = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                if (silent.value) silentLook(mc, p, Vec3d.ofCenter(bestP));
                p.getInventory().setSelectedSlot(slot);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hv, face, n, false));
                p.swingHand(Hand.MAIN_HAND);
                status = "Anchor placed"; did = true;
                if (instant.value) detonate(mc, p, bestP, 0);
            }
        } else { status = "No position"; currentPos = null; }
        p.getInventory().setSelectedSlot(prev);
        if (did) cd = delay.i();
    }

    private boolean detonate(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos, int charges) {
        BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false);
        if (silent.value) silentLook(mc, p, Vec3d.ofCenter(pos));
        if (charges <= 0) {
            int g = slotFor(mc, p, s -> s.isOf(Items.GLOWSTONE));
            if (g < 0) { status = "No glowstone"; return false; }
            p.getInventory().setSelectedSlot(g);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
            p.swingHand(Hand.MAIN_HAND);
        }
        int other = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isEmpty()) { other = i; break; }
        if (other < 0) for (int i = 0; i < 9; i++) if (!p.getInventory().getStack(i).isOf(Items.GLOWSTONE)) { other = i; break; }
        if (other >= 0) p.getInventory().setSelectedSlot(other);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
        status = "Detonated";
        return true;
    }

    private Direction support(MinecraftClient mc, BlockPos bp) {
        for (Direction d : Direction.values()) {
            var s = mc.world.getBlockState(bp.offset(d));
            if (!s.isAir() && s.getFluidState().isEmpty()) return d;
        }
        return null;
    }
}