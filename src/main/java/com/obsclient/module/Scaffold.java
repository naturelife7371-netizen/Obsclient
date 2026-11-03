package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class Scaffold extends Module {
    public static String status = "Off";
    public static Scaffold INSTANCE;

    // ===================== AYARLAR =====================
    final NumSetting expand    = new NumSetting("Expand", "Uzanma", 2, 0, 5, 1);
    final NumSetting perTick   = new NumSetting("PerTick", "Tick Basi", 2, 1, 5, 1);
    final BoolSetting tower    = new BoolSetting("Tower", "Tower", true);
    final BoolSetting safeWalk = new BoolSetting("SafeWalk", "Guvenli Yuru", true);
    final BoolSetting silentRot = new BoolSetting("SilentRotate", "Sessiz Donus", true);

    private static final double PLACE_REACH = 4.5;
    private static final Direction[] DIRS = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };

    public Scaffold() {
        super("Scaffold", "Scaffold", "Auto place + tower.", "Oto blok + tower.", Category.PLAYER);
        settings.addAll(Arrays.asList(expand, perTick, tower, safeWalk, silentRot));
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? status : "Off"; }
    @Override public void onEnable() { status = "Active"; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.currentScreen != null || p.isGliding()) return;

        // Sadece yere konulabilecek blokları filtrele
        Predicate<ItemStack> pred = s -> s.getItem() instanceof BlockItem bi
                && !(bi.getBlock() instanceof BlockWithEntity)
                && !(bi.getBlock() instanceof FallingBlock);

        int slot = findHot(p, pred);
        if (slot < 0) { status = "§cBlok yok"; return; }
        status = "Active";

        Vec3d v = p.getVelocity();
        double hx = v.x, hz = v.z;

        // Eğer duruyorsak baktığımız yöne doğru köprü kur
        if (hx * hx + hz * hz < 1.0E-4) {
            float yaw = p.getYaw() * 0.017453292f;
            hx = -Math.sin(yaw) * 0.1;
            hz = Math.cos(yaw) * 0.1;
        }
        double len = Math.sqrt(hx * hx + hz * hz);
        double dx = len > 0 ? hx / len : 0, dz = len > 0 ? hz / len : 0;

        int prevSlot = p.getInventory().getSelectedSlot();
        int placed = 0;

        // 1) İLERİYE VE AYAĞININ ALTINA DOĞRU BLOK KOYMA
        for (int i = 0; i <= expand.i() && placed < perTick.i(); i++) {
            // DÜZELTME: Blok pozisyonunu daha doğru hesaplıyoruz
            BlockPos pos = BlockPos.ofFloored(p.getX() + dx * i, p.getY() - 1.0, p.getZ() + dz * i);

            // Ayağının altı her zaman önceliklidir
            if (i == 0) pos = p.getBlockPos().down();

            if (!mc.world.getBlockState(pos).isReplaceable()) continue;

            if (placeBlock(mc, p, pos, slot)) {
                placed++;
            }
        }

        // 2) SAFE WALK (Ayağının tam altı boşsa kapat)
        if (safeWalk.value && placed < perTick.i()) {
            BlockPos under = p.getBlockPos().down();
            if (mc.world.getBlockState(under).isReplaceable()) {
                if (placeBlock(mc, p, under, slot)) placed++;
            }
        }

        // 3) TOWER (Kule Modu)
        if (tower.value && mc.options.jumpKey.isPressed() && placed > 0
                && !(mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed())
                && p.getVelocity().y < 0.2) {
            Vec3d cv = p.getVelocity();
            p.setVelocity(cv.x, 0.42, cv.z);
        }
    }

    private boolean placeBlock(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos, int slot) {
        Vec3d eye = p.getEyePos();

        for (Direction dir : DIRS) {
            BlockPos support = pos.offset(dir);
            BlockState st = mc.world.getBlockState(support);

            if (st.isAir() || !st.isSolidBlock(mc.world, support)) continue;

            Direction face = dir.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(support).add(
                    face.getVector().getX() * 0.5,
                    face.getVector().getY() * 0.5,
                    face.getVector().getZ() * 0.5);

            // Gözümüz destek bloğunun baktığımız yüzünde mi?
            Vec3d toEye = eye.subtract(hitVec);
            if (toEye.x * face.getVector().getX() + toEye.y * face.getVector().getY()
                    + toEye.z * face.getVector().getZ() <= 0) continue;

            if (eye.distanceTo(hitVec) > PLACE_REACH) continue;

            // --- KRİTİK DÜZELTME: SLOT PAKETİ ---
            int prevSlot = p.getInventory().getSelectedSlot();
            if (prevSlot != slot) {
                p.getInventory().setSelectedSlot(slot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            // --- KRİTİK DÜZELTME: ANLIK ROTASYON PAKETİ ---
            if (silentRot.value) {
                float[] angle = calcAngle(eye, hitVec);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                        angle[0], angle[1], p.isOnGround(), p.horizontalCollision));
            }

            // --- BLOK KOYMA PAKETİ ---
            int seq = p.currentScreenHandler.getRevision();
            BlockHitResult hr = new BlockHitResult(hitVec, face, support, false);
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hr, seq));

            // --- KOL SALLA VE ESKİ SLOTA DÖN ---
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            p.swingHand(Hand.MAIN_HAND);

            if (prevSlot != slot) {
                p.getInventory().setSelectedSlot(prevSlot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            }

            return true;
        }
        return false;
    }

    private int findHot(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }
}