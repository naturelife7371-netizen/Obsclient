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
import net.minecraft.entity.Entity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class PistonCrystal extends Module {
    public static String status = "Off";
    public static PistonCrystal INSTANCE;

    // ===================== AYARLAR =====================
    final NumSetting delay = new NumSetting("Delay", "Gecikme (Tick)", 1, 0, 10, 1);
    final ModeSetting pattern = new ModeSetting("Pattern", "Desen", 3,
            new String[]{"Small", "Cross", "Liner", "All"},
            new String[]{"Kucuk", "Carpi", "Cizgi", "Hepsi"});

    final BoolSetting supportPlace = new BoolSetting("SupportPlace", "Destek Blogu Koy", false);
    final BoolSetting fireBypass = new BoolSetting("FireBypass", "Ates Bypass", false);
    final BoolSetting trapTarget = new BoolSetting("Trap", "Hedefi Tuzakla", false);
    final BoolSetting rotate = new BoolSetting("Rotate", "Sessiz Donus", true);

    final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzili", 10.0, 0.0, 12.0, 0.5);
    final NumSetting placeRange = new NumSetting("PlaceRange", "Koyma Menzili", 4.5, 1.0, 7.0, 0.1);

    // ===================== STATE =====================
    private PlayerEntity target;
    private BlockPos targetPos, pistonPos, crystalPos, redStonePos, firePos, pistonHeadPos;
    private boolean builtTrap, isFire;

    private int timer = 0;
    private Stage stage = Stage.Searching;

    public enum Stage { Searching, Trap, Piston, Fire, Crystal, RedStone, Break }

    public PistonCrystal() {
        super("PistonCrystal", "PistonCrystal", "ThunderHack Ported Piston Aura.", "Piston tavan kombosu (Gelismis).", Category.COMBAT);
        settings.addAll(Arrays.asList(
                delay, pattern, supportPlace, fireBypass, trapTarget, rotate, targetRange, placeRange
        ));
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { status = "Off"; }

    private void reset() {
        builtTrap = false;
        target = null;
        stage = Stage.Searching;
        pistonPos = null; targetPos = null; firePos = null; pistonHeadPos = null;
        crystalPos = null; redStonePos = null;
        timer = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        if (timer > 0) { timer--; return; }

        // Hedef kaçtıysa planı sıfırla
        if (stage != Stage.Searching && target != null && targetPos != null) {
            if (target.getBlockPos().getManhattanDistance(targetPos) > 1) {
                status = "Hedef Kacti!";
                reset();
                return;
            }
        }

        if (stage == Stage.Break) {
            if (isFire) { reset(); return; }
            breakCrystals(mc);
            return;
        }

        // Envanter kontrolü
        if (trapTarget.value && findInv(mc.player, s -> s.isOf(Items.OBSIDIAN)) < 0 && findHot(mc.player, s -> s.isOf(Items.OBSIDIAN)) < 0) { status = "No Obsidian!"; return; }
        if (findInv(mc.player, s -> s.isOf(Items.REDSTONE_BLOCK) || s.isOf(Items.REDSTONE_TORCH)) < 0 && findHot(mc.player, s -> s.isOf(Items.REDSTONE_BLOCK) || s.isOf(Items.REDSTONE_TORCH)) < 0) { status = "No Redstone!"; return; }
        if (findInv(mc.player, s -> s.isOf(Items.END_CRYSTAL)) < 0 && findHot(mc.player, s -> s.isOf(Items.END_CRYSTAL)) < 0 && mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) { status = "No Crystals!"; return; }
        if (findInv(mc.player, s -> s.isOf(Items.PISTON) || s.isOf(Items.STICKY_PISTON)) < 0 && findHot(mc.player, s -> s.isOf(Items.PISTON) || s.isOf(Items.STICKY_PISTON)) < 0) { status = "No Pistons!"; return; }

        handlePistonAura(mc);
    }

    private void handlePistonAura(MinecraftClient mc) {
        switch (stage) {
            case Searching -> { findPos(mc); stage = Stage.Trap; }
            case Trap -> buildTrap(mc);
            case Piston -> placePiston(mc);
            case Fire -> placeFire(mc);
            case Crystal -> placeCrystal(mc);
            case RedStone -> placeRedStone(mc);
            case Break -> { if (isFire) stage = Stage.Searching; }
        }
    }

    // ===================== EVRE 1: POZISYON BULMA =====================
    private void findPos(MinecraftClient mc) {
        List<Structure> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || isFriend(p) || mc.player.distanceTo(p) > targetRange.value) continue;

            for (int i = 0; i <= 2; i++) {
                if (pattern.index == 0 || pattern.index == 3) { // Small & All
                    list.add(new Structure(mc, p, new BlockPos(1, i, 0), new BlockPos(1, i, -1), new BlockPos(0, i, -1), new BlockPos[]{new BlockPos(1, i, -2)}, new BlockPos[]{new BlockPos(0, 0, 1), new BlockPos(1, i, 1), new BlockPos(0, 1 + i, 1)}));
                    list.add(new Structure(mc, p, new BlockPos(1, i, 0), new BlockPos(1, i, 1), new BlockPos(0, i, 1), new BlockPos[]{new BlockPos(1, i, 2)}, new BlockPos[]{new BlockPos(0, i, -1), new BlockPos(1, i, -1), new BlockPos(0, 1 + i, -1)}));
                }
                if (pattern.index == 1 || pattern.index == 3) { // Cross & All
                    list.add(new Structure(mc, p, new BlockPos(1, i, 0), new BlockPos(2, i, -1), new BlockPos(1, i, -1), new BlockPos[]{new BlockPos(3, i, -1), new BlockPos(2, i, -2)}, new BlockPos[]{new BlockPos(0, i, -1), new BlockPos(1, i, 1), new BlockPos(0, 1 + i, 1)}));
                }
                if (pattern.index == 2 || pattern.index == 3) { // Liner & All
                    list.add(new Structure(mc, p, new BlockPos(1, i, 0), new BlockPos(2, i, 0), new BlockPos(1, i, 0), new BlockPos[]{new BlockPos(3, i, 0)}, new BlockPos[]{new BlockPos(1, i, 1), new BlockPos(1, i, -1), new BlockPos(0, i, 1)}));
                }
            }
        }

        Structure best = null;
        double bestDist = Double.MAX_VALUE;
        for (Structure s : list) {
            boolean valid = fireBypass.value ? s.isFirePa() : s.isNormalPa();
            if (valid && s.getMaxRange() < bestDist) { bestDist = s.getMaxRange(); best = s; }
        }

        if (best != null) {
            target = best.target; targetPos = best.targetPos;
            pistonPos = best.pistonPos; crystalPos = best.crystalPos;
            redStonePos = best.redStonePos; pistonHeadPos = best.pistonHeadPos;
            firePos = best.firePos; isFire = fireBypass.value && firePos != null;
            status = "Target: " + target.getName().getString();
        } else {
            status = "No Space!"; reset();
        }
    }

    // ===================== EVRELER =====================
    private void buildTrap(MinecraftClient mc) {
        if (!trapTarget.value) { stage = Stage.Piston; return; }
        if (mc.world.getBlockState(targetPos.add(0, 2, 0)).isOf(Blocks.OBSIDIAN) || pistonPos.getY() >= targetPos.add(0, 2, 0).getY()) { stage = Stage.Piston; return; }

        if (!builtTrap) {
            BlockPos offset = new BlockPos(crystalPos.getX() - targetPos.getX(), 0, crystalPos.getZ() - targetPos.getZ());
            BlockPos trapBase = targetPos.add(-offset.getX(), 0, -offset.getZ());
            BlockPos[] trapPos = {targetPos.add(0, 2, 0), trapBase.add(0, 2, 0), trapBase.add(0, 1, 0)};

            for (BlockPos bp : trapPos) {
                if (placeBlock(mc, bp, s -> s.isOf(Items.OBSIDIAN))) {
                    if (bp.equals(targetPos.add(0, 2, 0))) { builtTrap = true; stage = Stage.Piston; }
                    timer = delay.i();
                    return;
                }
            }
        }
    }

    private void placePiston(MinecraftClient mc) {
        if (pistonPos == null || pistonHeadPos == null) { stage = Stage.Searching; return; }
        if (mc.world.getBlockState(pistonPos).getBlock() instanceof net.minecraft.block.PistonBlock) { stage = isFire ? Stage.Fire : Stage.Crystal; return; }

        if (mc.world.getBlockState(pistonPos.down()).isReplaceable() && supportPlace.value) {
            placeBlock(mc, pistonPos.down(), s -> s.isOf(Items.OBSIDIAN)); return;
        }

        float[] angle = calcAngle(mc.player.getEyePos(), Vec3d.ofCenter(pistonHeadPos));
        if (placeBlock(mc, pistonPos, s -> s.isOf(Items.PISTON) || s.isOf(Items.STICKY_PISTON), angle)) {
            stage = isFire ? Stage.Fire : Stage.Crystal;
            timer = delay.i();
        }
    }

    private void placeFire(MinecraftClient mc) {
        if (firePos == null) { stage = Stage.Searching; return; }
        if (mc.world.getBlockState(firePos).getBlock() instanceof net.minecraft.block.FireBlock) { stage = Stage.Crystal; return; }
        if (mc.world.getBlockState(firePos.down()).isReplaceable() && supportPlace.value) { placeBlock(mc, firePos.down(), s -> s.isOf(Items.OBSIDIAN)); return; }

        if (placeBlock(mc, firePos, s -> s.isOf(Items.FLINT_AND_STEEL))) { stage = Stage.Crystal; timer = delay.i(); }
    }

    private void placeCrystal(MinecraftClient mc) {
        if (crystalPos == null) { stage = Stage.Searching; return; }
        if (mc.world.getBlockState(crystalPos).isReplaceable() && supportPlace.value) { placeBlock(mc, crystalPos, s -> s.isOf(Items.OBSIDIAN)); return; }

        if (placeBlock(mc, crystalPos, s -> s.isOf(Items.END_CRYSTAL))) { stage = Stage.RedStone; timer = delay.i(); }
    }

    private void placeRedStone(MinecraftClient mc) {
        if (redStonePos == null) { stage = Stage.Searching; return; }
        if (mc.world.getBlockState(redStonePos).getBlock() instanceof net.minecraft.block.RedstoneBlock) { stage = Stage.Break; return; }
        if (mc.world.getBlockState(redStonePos.down()).isReplaceable() && supportPlace.value) { placeBlock(mc, redStonePos.down(), s -> s.isOf(Items.OBSIDIAN)); return; }

        if (placeBlock(mc, redStonePos, s -> s.isOf(Items.REDSTONE_BLOCK) || s.isOf(Items.REDSTONE_TORCH))) { stage = Stage.Break; timer = delay.i(); }
    }

    private void breakCrystals(MinecraftClient mc) {
        if (target == null) return;
        for (Entity ent : mc.world.getEntities()) {
            if (!(ent instanceof EndCrystalEntity crystal) || target.squaredDistanceTo(ent) > 16 || crystal.age < 2) continue;

            if (rotate.value) {
                float[] angle = calcAngle(mc.player.getEyePos(), new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ()));
                Rot.request(angle[0], angle[1], 10);
                Rot.postAttack();
            }

            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            mc.player.swingHand(Hand.MAIN_HAND);
            status = "Detonated";
            timer = delay.i();
        }
    }

    // ===================== YARDIMCI METOTLAR =====================
    private boolean placeBlock(MinecraftClient mc, BlockPos pos, Predicate<ItemStack> pred) {
        return placeBlock(mc, pos, pred, null);
    }

    private boolean placeBlock(MinecraftClient mc, BlockPos pos, Predicate<ItemStack> pred, float[] customAngle) {
        if (mc.player == null || mc.getNetworkHandler() == null) return false;

        int slot = findHot(mc.player, pred);
        if (slot < 0) {
            if (findInv(mc.player, pred) >= 0) {
                toHotbar(mc, mc.player, pred);
                status = "Pulling items...";
            }
            return false;
        }

        BlockPos neighbor = null;
        Direction side = null;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState s = mc.world.getBlockState(n);
            if (s.isAir() || !s.getFluidState().isEmpty() || !s.isSolidBlock(mc.world, n)) continue;
            neighbor = n;
            side = d.getOpposite();
            break;
        }
        if (side == null) return false;

        Vec3d hitVec = Vec3d.ofCenter(neighbor).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        if (mc.player.getEyePos().distanceTo(hitVec) > 4.5) return false;

        float[] angle = customAngle != null ? customAngle : calcAngle(mc.player.getEyePos(), hitVec);

        if (rotate.value) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(angle[0], angle[1], mc.player.isOnGround(), mc.player.horizontalCollision));
            Rot.request(angle[0], angle[1], 10);
        }

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        if (prevSlot != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        int seq = mc.player.currentScreenHandler.getRevision();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(hitVec, side, neighbor, false), seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        mc.player.swingHand(Hand.MAIN_HAND);

        if (prevSlot != slot) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        }

        return true;
    }

    private int findHot(PlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    // ===================== STRUCTURE SINIFI =====================
    public class Structure {
        public BlockPos pistonPos, crystalPos, targetPos, redStonePos, firePos, pistonHeadPos;
        public PlayerEntity target;

        public Structure(MinecraftClient mc, PlayerEntity target, BlockPos crystalOff, BlockPos pistonOff, BlockPos pistonHeadOff, BlockPos[] redstoneOff, BlockPos[] fireOff) {
            this.target = target;
            this.targetPos = target.getBlockPos();

            BlockPos pPos = targetPos.add(pistonOff.getX(), pistonOff.getY() + 1, pistonOff.getZ());
            this.pistonPos = canPlace(mc, pPos) ? pPos : null;

            BlockPos cPos = targetPos.add(crystalOff.getX(), crystalOff.getY(), crystalOff.getZ());
            this.crystalPos = mc.world.getBlockState(cPos).isReplaceable() ? cPos : null;

            BlockPos phPos = targetPos.add(pistonHeadOff.getX(), pistonHeadOff.getY() + 1, pistonHeadOff.getZ());
            this.pistonHeadPos = mc.world.getBlockState(phPos).isAir() ? phPos : null;

            if (this.pistonHeadPos != null && !mc.world.getNonSpectatingEntities(PlayerEntity.class, new Box(this.pistonHeadPos)).isEmpty()) this.pistonHeadPos = null;
            if (this.crystalPos != null && !mc.world.getOtherEntities(null, new Box(this.crystalPos)).isEmpty()) this.crystalPos = null;

            this.redStonePos = getFirstPlaceable(mc, redstoneOff);
            this.firePos = fireOff != null ? getFirstPlaceable(mc, fireOff) : null;
        }

        private BlockPos getFirstPlaceable(MinecraftClient mc, BlockPos[] offsets) {
            for (BlockPos off : offsets) {
                BlockPos pos = targetPos.add(off.getX(), off.getY() + 1, off.getZ());
                if (canPlace(mc, pos)) return pos;
            }
            return null;
        }

        private boolean canPlace(MinecraftClient mc, BlockPos pos) {
            if (!mc.world.getBlockState(pos).isReplaceable()) return false;
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.offset(dir);
                BlockState s = mc.world.getBlockState(n);
                if (!s.isAir() && s.isSolidBlock(mc.world, n)) return true;
            }
            return false;
        }

        public boolean isNormalPa() { return pistonPos != null && crystalPos != null && redStonePos != null && pistonHeadPos != null; }
        public boolean isFirePa() { return isNormalPa() && firePos != null; }

        public double getMaxRange() {
            if (pistonPos == null || crystalPos == null || redStonePos == null) return 999;
            double piston = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pistonPos));
            double crystal = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(crystalPos));
            double redstone = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(redStonePos));
            return Math.max(Math.max(piston, crystal), redstone);
        }
    }

    // ===================== RENDER =====================
    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (!enabled || mc.player == null || mc.world == null) return;
        if (pistonPos != null) drawBox(g, mc, pistonPos, 0x40FFAA00, delta);
        if (crystalPos != null) drawBox(g, mc, crystalPos, 0x40FF55FF, delta);
        if (redStonePos != null) drawBox(g, mc, redStonePos, 0x40FF0000, delta);
    }

    private void drawBox(DrawContext g, MinecraftClient mc, BlockPos pos, int color, float delta) {
        int[] proj = projectBox(mc, new Box(pos), delta);
        if (proj != null) {
            g.fill(proj[0], proj[1], proj[2], proj[3], color);
            outline(g, proj[0], proj[1], proj[2], proj[3], color | 0xFF000000);
        }
    }
}