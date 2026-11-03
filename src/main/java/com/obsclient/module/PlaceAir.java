package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class PlaceAir extends Module {
    public static String status = "Off";
    public static PlaceAir INSTANCE;

    // ===================== AYARLAR =====================
    final NumSetting range = new NumSetting("Range", "Menzil", 4.5, 1.0, 6.0, 0.1);
    final NumSetting delay = new NumSetting("Delay", "Gecikme (tick)", 2, 0, 10, 1);
    final BoolSetting rotate = new BoolSetting("Rotate", "Sessiz Donus", true);
    final BoolSetting swing = new BoolSetting("Swing", "Kol Salla", true);
    final BoolSetting onlyAir = new BoolSetting("OnlyAir", "Sadece Bosluga", true);
    final BoolSetting render = new BoolSetting("Render", "Koyulani Goster", true);

    // ===================== STATE =====================
    private int cd = 0;
    private boolean rightClickHeld = false;
    private BlockPos lastPlacedPos = null;
    private long lastPlacedTime = 0;

    public PlaceAir() {
        super("PlaceAir", "PlaceAir", "Manually place blocks in air.", "Havaya blok koy.", Category.WORLD);
        settings.addAll(Arrays.asList(range, delay, rotate, swing, onlyAir, render));
        INSTANCE = this;
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Ready"; cd = 0; rightClickHeld = false; lastPlacedPos = null; }
    @Override public void onDisable() { status = "Off"; lastPlacedPos = null; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) {
            rightClickHeld = false;
            return;
        }

        if (cd > 0) { cd--; return; }

        boolean isRightClick = mc.options.useKey.isPressed();

        if (isRightClick && !rightClickHeld) {
            rightClickHeld = true;

            int slot = findBlockSlot(p);
            if (slot < 0) { status = "Blok yok"; return; }

            HitResult hit = mc.crosshairTarget;

            // Eğer katı bir bloğa bakıyorsak oyunun kendi mekaniğine bırak
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) return;

            Vec3d eye = p.getEyePos();
            Vec3d look = p.getRotationVec(1.0f);
            Vec3d end = eye.add(look.multiply(range.value));

            // Gözümüzün baktığı yöndeki en uzak havayı bul
            BlockHitResult ray = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
            BlockPos targetPos;
            if (ray.getType() == HitResult.Type.MISS) {
                targetPos = BlockPos.ofFloored(end);
            } else {
                targetPos = ray.getBlockPos().offset(ray.getSide());
            }

            BlockState state = mc.world.getBlockState(targetPos);
            boolean isReplaceable = state.isReplaceable() || state.isAir() || !state.getFluidState().isEmpty();

            if (!isReplaceable) { status = "Hedef dolu"; return; }
            if (onlyAir.value && !state.isAir()) { status = "Sadece havaya"; return; }
            if (eye.distanceTo(Vec3d.ofCenter(targetPos)) > range.value) { status = "Menzil disi"; return; }

            // Sunucuya yem atacagimiz destek blogunu bul (Komsu blok)
            BlockPos neighbor = null;
            Direction side = null;
            for (Direction d : Direction.values()) {
                BlockPos n = targetPos.offset(d);
                BlockState s = mc.world.getBlockState(n);
                if (!s.isAir() && !s.isLiquid() && s.isSolidBlock(mc.world, n)) {
                    neighbor = n;
                    side = d.getOpposite();
                    break;
                }
            }

            // Eger etrafta destek blogu yoksa (True AirPlace) sunucuyu kandirmaya calis
            Vec3d hitVec;
            if (neighbor != null) {
                hitVec = Vec3d.ofCenter(neighbor).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            } else {
                neighbor = targetPos.down();
                side = Direction.UP;
                hitVec = Vec3d.ofCenter(targetPos).add(0, -0.5, 0);
            }

            int prevSlot = p.getInventory().getSelectedSlot();
            if (prevSlot != slot) {
                p.getInventory().setSelectedSlot(slot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            // Rotasyon paketini hemen gonder (Sunucu bloğa baktiğimizi anlasin)
            if (rotate.value) {
                float[] angle = calcAngle(p.getEyePos(), hitVec);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(angle[0], angle[1], p.isOnGround(), p.horizontalCollision));
                Rot.request(angle[0], angle[1], 10);
            }

            // Blok koyma paketini gonder
            int seq = p.currentScreenHandler.getRevision();
            BlockHitResult fakeHit = new BlockHitResult(hitVec, side, neighbor, false);
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, fakeHit, seq));

            if (swing.value) {
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                p.swingHand(Hand.MAIN_HAND);
            }

            if (prevSlot != slot) {
                p.getInventory().setSelectedSlot(prevSlot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            }

            lastPlacedPos = targetPos;
            lastPlacedTime = System.currentTimeMillis();
            status = "Placed";
            cd = (int) delay.value;
        } else if (!isRightClick) {
            rightClickHeld = false;
        }
    }

    private int findBlockSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = p.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    // ===================== RENDER =====================
    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (!enabled || !render.value || mc.player == null || lastPlacedPos == null) return;

        // 1 saniye (1000ms) boyunca koyulan yeri goster
        if (System.currentTimeMillis() - lastPlacedTime > 1000) {
            lastPlacedPos = null;
            return;
        }

        Box box = new Box(lastPlacedPos);
        int[] proj = projectBox(mc, box, delta);
        if (proj != null) {
            g.fill(proj[0], proj[1], proj[2], proj[3], 0x4000FF00); // Yarı saydam yeşil
            outline(g, proj[0], proj[1], proj[2], proj[3], 0xFF00FF00); // Yeşil çerçeve
        }
    }
}