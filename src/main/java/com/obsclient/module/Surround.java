package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class Surround extends Module {
    public static String status = "Off";
    public static Surround INSTANCE;

    final NumSetting delay = new NumSetting("Delay", "Gecikme", 0, 0, 5, 1); // Anarşi için 0 (Her tick)
    final ModeSetting center = new ModeSetting("Center", "Merkezle", 2, new String[]{"Never", "OnEnable", "Incomplete", "Always"}, new String[]{"Asla", "Acilista", "Eksikse", "Her Zaman"});
    final BoolSetting onlyOnGround = new BoolSetting("OnlyOnGround", "Sadece Yerde", true);
    final BoolSetting rotate = new BoolSetting("Rotate", "Sessiz Donus", true);
    final BoolSetting doubleHeight = new BoolSetting("DoubleHeight", "Cift Kat", false);
    final BoolSetting strict = new BoolSetting("Strict", "Kati (Grim)", true);

    private int timer = 0;

    public Surround() {
        super("Surround", "Surround", "Anarchy Safe Surround.", "Etrafini obsidyenle cevir.", Category.COMBAT);
        settings.addAll(Arrays.asList(delay, center, onlyOnGround, rotate, doubleHeight, strict));
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        if (center.index == 1 || center.index == 3) centerPlayer(mc);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.currentScreen != null) return;

        if (timer > 0) { timer--; return; }
        if (onlyOnGround.value && !p.isOnGround()) return;

        Predicate<ItemStack> pred = s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN) || s.isOf(Items.ENDER_CHEST);
        if (findInv(p, pred) < 0) { status = "§cBlok Yok"; return; }

        if (center.index == 2 || center.index == 3) centerPlayer(mc);

        BlockPos playerPos = p.getBlockPos();
        List<BlockPos> todo = new ArrayList<>();

        // Kendi etrafındaki boşlukları bul
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos pos = playerPos.offset(dir);
            if (isReplaceable(mc, pos)) todo.add(pos);
        }

        if (doubleHeight.value) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos pos = playerPos.offset(dir).up();
                if (isReplaceable(mc, pos)) todo.add(pos);
            }
        }

        // En yakındaki bloğu ilk sıraya al
        todo.sort(Comparator.comparingDouble(b -> p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(b))));

        // Tüm adayları sırayla dene (Eğer biri menzil dışıysa diğerine geçer)
        for (BlockPos pos : todo) {
            if (placeBlockSafe(mc, pos, pred)) {
                timer = (int) delay.value;
                status = "§aKorunuyor";
                break; // Bir tane koyduysa tick'i bitir
            }
        }
    }

    private void centerPlayer(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || !p.isOnGround()) return;
        double cx = Math.floor(p.getX()) + 0.5;
        double cz = Math.floor(p.getZ()) + 0.5;
        if (Math.abs(p.getX() - cx) > 0.1 || Math.abs(p.getZ() - cz) > 0.1) {
            p.setPosition(cx, p.getY(), cz);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(cx, p.getY(), cz, true, false));
        }
    }

    private boolean placeBlockSafe(MinecraftClient mc, BlockPos pos, Predicate<ItemStack> pred) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.getNetworkHandler() == null) return false;

        int slot = findHot(p, pred);
        if (slot < 0) {
            if (!toHotbar(mc, p, pred)) return false;
            slot = findHot(p, pred);
            if (slot < 0) return false;
        }

        BlockPos support = null;
        Direction face = null;
        Vec3d hitVec = null;

        // Etrafındaki tüm blokları tara, en uygun destek bloğunu bul
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState s = mc.world.getBlockState(n);

            // Eğer destek bloğu hava değilse ve katıysa
            if (!s.isAir() && !s.getFluidState().isEmpty() == false && s.isSolidBlock(mc.world, n)) {
                Direction opp = d.getOpposite();

                // Strict modu: Sadece görebildiğimiz yüzeylere mi bakıyoruz?
                if (strict.value) {
                    Vec3d testHit = Vec3d.ofCenter(n).add(opp.getOffsetX() * 0.5, opp.getOffsetY() * 0.5, opp.getOffsetZ() * 0.5);
                    var ray = mc.world.raycast(new RaycastContext(p.getEyePos(), testHit, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
                    if (ray.getType() != net.minecraft.util.hit.HitResult.Type.MISS) continue; // Görüş açısı kapalıysa geç
                }

                support = n;
                face = opp;
                hitVec = Vec3d.ofCenter(n).add(opp.getOffsetX() * 0.5, opp.getOffsetY() * 0.5, opp.getOffsetZ() * 0.5);
                break;
            }
        }

        if (support == null || hitVec == null) return false;
        if (p.getEyePos().distanceTo(hitVec) > 4.5) return false;

        int prevSlot = p.getInventory().getSelectedSlot();
        if (prevSlot != slot) {
            p.getInventory().setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        if (rotate.value) {
            float[] angle = calcAngle(p.getEyePos(), hitVec);
            // GrimAC Perfect Angle Bypass
            angle[0] += (float)(Math.random() * 0.05 - 0.025);
            angle[1] += (float)(Math.random() * 0.05 - 0.025);

            Rot.request(angle[0], angle[1], 50);
            Rot.flush(mc);

            // Paket sırası kritik (Rotation -> Full Move -> Interact)
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    p.getX(), p.getY(), p.getZ(), angle[0], angle[1], p.isOnGround(), p.horizontalCollision
            ));
        }

        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(hitVec, face, support, false), p.currentScreenHandler.getRevision()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        p.swingHand(Hand.MAIN_HAND);

        if (prevSlot != slot) {
            p.getInventory().setSelectedSlot(prevSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        }
        return true;
    }

    private boolean isReplaceable(MinecraftClient mc, BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).isReplaceable();
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (!enabled || mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        List<BlockPos> renderPos = new ArrayList<>();

        for (Direction dir : Direction.Type.HORIZONTAL) {
            renderPos.add(playerPos.offset(dir));
            if (doubleHeight.value) renderPos.add(playerPos.offset(dir).up());
        }

        for (BlockPos pos : renderPos) {
            boolean isSafe = !mc.world.getBlockState(pos).isReplaceable();
            int color = isSafe ? 0x4000FF00 : 0x40FF0000;
            int lineColor = isSafe ? 0xFF00FF00 : 0xFFFF0000;

            Box box = new Box(pos);
            int[] proj = projectBox(mc, box, delta);
            if (proj != null) {
                int x1 = Math.min(proj[0], proj[2]), x2 = Math.max(proj[0], proj[2]);
                int y1 = Math.min(proj[1], proj[3]), y2 = Math.max(proj[1], proj[3]);
                g.fill(x1, y1, x2, y2, color);
                outline(g, x1, y1, x2, y2, lineColor);
            }
        }
    }

    // ==== YARDIMCILAR ====
    private int findHot(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    private int findInv(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 9; i < 36; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    private boolean toHotbar(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred) {
        if (p.playerScreenHandler == null || mc.interactionManager == null) return false;
        int invSlot = findInv(p, pred);
        if (invSlot < 0) return false;
        int empty = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isEmpty()) { empty = i; break; }
        if (empty < 0) empty = 8;
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, invSlot, empty, net.minecraft.screen.slot.SlotActionType.SWAP, p);
        return true;
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