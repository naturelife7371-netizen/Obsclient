package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class NoPhase extends Module {
    public static String status = "Off";
    public static NoPhase INSTANCE;

    // ===================== AYARLAR =====================
    final ModeSetting blockMode = new ModeSetting("Block", "Blok", 0,
            new String[]{"ItemFrame", "Scaffolding", "Cobweb", "Snow", "Button"},
            new String[]{"Esya Cercevesi", "Iskele", "Orumcek Agi", "Kar", "Buton"});

    final NumSetting range = new NumSetting("Range", "Menzil", 5.0, 2.0, 8.0, 0.5);
    final NumSetting delay = new NumSetting("Delay", "Gecikme (Tick)", 2, 0, 10, 1);
    final BoolSetting rotate = new BoolSetting("Rotate", "Sessiz Donus", true);
    final BoolSetting onlyMoving = new BoolSetting("OnlyMoving", "Sadece Hareket Edene", true);
    final BoolSetting autoPull = new BoolSetting("AutoPull", "Envanterden Cek", true);
    final BoolSetting multiPlace = new BoolSetting("MultiPlace", "Coklu Yerlestir", false);
    final NumSetting blocksPerTick = new NumSetting("BlocksPerTick", "Tick Basi Blok", 1, 1, 3, 1);

    // ===================== STATE =====================
    private int timer = 0;

    public NoPhase() {
        super("NoPhase", "NoPhase",
                "Blocks enemies from phasing using ItemFrames/Scaffolding.",
                "Rakiplerin phase girmesini engeller.",
                Category.COMBAT);
        settings.addAll(Arrays.asList(
                blockMode, range, delay, rotate, onlyMoving, autoPull, multiPlace, blocksPerTick
        ));
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        status = "Hazir";
        timer = 0;
    }

    @Override
    public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        if (timer > 0) { timer--; return; }

        Item item = getSelectedItem();
        if (item == null) return;

        // Envanter kontrolu
        if (findHot(p, s -> s.isOf(item)) < 0 && findInv(p, s -> s.isOf(item)) < 0) {
            status = "§c" + getItemName() + " yok!";
            return;
        }

        List<PlayerEntity> enemies = findEnemies(mc, p);
        if (enemies.isEmpty()) {
            status = "Hedef yok";
            return;
        }

        int placed = 0;
        int max = multiPlace.value ? (int) blocksPerTick.value : 1;

        for (PlayerEntity enemy : enemies) {
            if (placed >= max) break;
            if (onlyMoving.value && enemy.getVelocity().lengthSquared() < 0.01) continue;
            if (tryBlockEnemy(mc, p, enemy)) placed++;
        }

        if (placed > 0) {
            timer = (int) delay.value;
            status = "§aEngellendi! x" + placed;
        } else {
            status = "Bekleniyor";
        }
    }

    // ===================== ENGELLEME =====================
    private boolean tryBlockEnemy(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity enemy) {
        Item item = getSelectedItem();
        if (item == null) return false;

        int slot = getOrPullSlot(mc, p, item);
        if (slot < 0) return false;

        if (item == Items.ITEM_FRAME) {
            return tryPlaceItemFrameNearEnemy(mc, p, enemy, slot);
        } else {
            return tryPlaceBlockNearEnemy(mc, p, enemy, slot);
        }
    }

    /** Hedefin yakinindaki kati bloklarin yuzune esya cercevesi yapistirir. */
    private boolean tryPlaceItemFrameNearEnemy(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity enemy, int slot) {
        if (mc.world == null) return false;
        BlockPos enemyBlock = enemy.getBlockPos();
        // FIX: getPos() yerine koordinatlardan Vec3d olustur
        Vec3d enemyPos = new Vec3d(enemy.getX(), enemy.getY(), enemy.getZ());

        // Yakin cevredeki kati bloklari topla
        List<BlockPos> solids = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = enemyBlock.add(dx, dy, dz);
                    BlockState s = mc.world.getBlockState(pos);
                    if (!s.isAir() && s.getFluidState().isEmpty()) solids.add(pos);
                }
            }
        }

        // Hedefe en yakin bloklardan basla
        solids.sort(Comparator.comparingDouble(b -> Vec3d.ofCenter(b).squaredDistanceTo(enemyPos)));

        for (BlockPos solid : solids) {
            for (Direction face : Direction.values()) {
                Vec3d faceCenter = Vec3d.ofCenter(solid).add(
                        face.getOffsetX() * 0.5,
                        face.getOffsetY() * 0.5,
                        face.getOffsetZ() * 0.5);

                // Yuz hedefe yakin mi ve bizim menzilimizde mi?
                if (faceCenter.squaredDistanceTo(enemyPos) > 4.0) continue;
                if (p.getEyePos().distanceTo(faceCenter) > 4.5) continue;

                if (doPlaceBlock(mc, p, solid, face, faceCenter, slot)) return true;
            }
        }
        return false;
    }

    /** Hedefin etrafindaki hava bloklarina normal blok yerlestirir. */
    private boolean tryPlaceBlockNearEnemy(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity enemy, int slot) {
        if (mc.world == null) return false;
        BlockPos enemyBlock = enemy.getBlockPos();
        // FIX: getPos() yerine koordinatlardan Vec3d olustur
        Vec3d enemyPos = new Vec3d(enemy.getX(), enemy.getY(), enemy.getZ());

        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = enemyBlock.add(dx, dy, dz);
                    if (mc.world.getBlockState(pos).isReplaceable()) candidates.add(pos);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(b -> Vec3d.ofCenter(b).squaredDistanceTo(enemyPos)));

        for (BlockPos pos : candidates) {
            if (p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 4.5 * 4.5) continue;
            if (placeBlock(mc, p, pos, slot)) return true;
        }
        return false;
    }

    // ===================== YERLESTIRME =====================
    private boolean placeBlock(MinecraftClient mc, ClientPlayerEntity p, BlockPos placePos, int slot) {
        if (mc.world == null) return false;
        for (Direction face : Direction.values()) {
            BlockPos support = placePos.offset(face);
            BlockState state = mc.world.getBlockState(support);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;

            Direction placeFace = face.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(support).add(
                    placeFace.getOffsetX() * 0.5,
                    placeFace.getOffsetY() * 0.5,
                    placeFace.getOffsetZ() * 0.5);

            if (p.getEyePos().distanceTo(hitVec) > 4.5) continue;

            return doPlaceBlock(mc, p, support, placeFace, hitVec, slot);
        }
        return false;
    }

    /** Grim uyumlu paket sirasi: slot -> rotasyon -> interact -> swing -> slot geri. */
    private boolean doPlaceBlock(MinecraftClient mc, ClientPlayerEntity p, BlockPos support, Direction face, Vec3d hitVec, int slot) {
        try {
            if (mc.getNetworkHandler() == null) return false;

            int prev = p.getInventory().getSelectedSlot();

            if (prev != slot) {
                p.getInventory().setSelectedSlot(slot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            // Rotasyon paketini interact'ten ONCE gonder
            if (rotate.value) {
                float[] angle = calcAngle(p.getEyePos(), hitVec);
                Rot.request(angle[0], angle[1], 50);
                Rot.flush(mc);
            }

            BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.MAIN_HAND, hit, p.currentScreenHandler.getRevision()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            p.swingHand(Hand.MAIN_HAND);

            if (prev != slot) {
                p.getInventory().setSelectedSlot(prev);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prev));
            }

            if (rotate.value) Rot.postAttack();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ===================== YARDIMCILAR =====================
    private int getOrPullSlot(MinecraftClient mc, ClientPlayerEntity p, Item item) {
        int slot = findHot(p, s -> s.isOf(item));
        if (slot >= 0) return slot;
        if (!autoPull.value) return -1;
        if (findInv(p, s -> s.isOf(item)) < 0) return -1;
        toHotbar(mc, p, s -> s.isOf(item));
        return findHot(p, s -> s.isOf(item));
    }

    private List<PlayerEntity> findEnemies(MinecraftClient mc, ClientPlayerEntity p) {
        List<PlayerEntity> out = new ArrayList<>();
        if (mc.world == null) return out;
        double rangeSq = range.value * range.value;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            if (p.squaredDistanceTo(o) > rangeSq) continue;
            out.add(o);
        }
        out.sort(Comparator.comparingDouble(p::squaredDistanceTo));
        return out;
    }

    private Item getSelectedItem() {
        return switch (blockMode.index) {
            case 0 -> Items.ITEM_FRAME;
            case 1 -> Items.SCAFFOLDING;
            case 2 -> Items.COBWEB;
            case 3 -> Items.SNOW;
            case 4 -> Items.STONE_BUTTON;
            default -> null;
        };
    }

    private String getItemName() {
        return switch (blockMode.index) {
            case 0 -> "Item Frame";
            case 1 -> "Scaffolding";
            case 2 -> "Cobweb";
            case 3 -> "Snow";
            case 4 -> "Button";
            default -> "?";
        };
    }
}