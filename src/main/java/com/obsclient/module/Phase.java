package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
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
public class Phase extends Module {
    public static String status = "Off";
    public static Phase INSTANCE;

    // Mod sırası KORUNDU — index'ler değişmedi, config'ler bozulmaz.
    final ModeSetting mode = new ModeSetting("Mode", "Mod", 1,
            new String[]{"Vanilla", "Pearl", "Sunrise", "ForceMine", "CCClip", "Teleport"},
            new String[]{"Vanilla", "Pearl", "Sunrise", "ForceMine", "CCClip", "Teleport"});

    final BoolSetting silent         = new BoolSetting("Silent", "Sessiz", true);
    final BoolSetting waitBreak      = new BoolSetting("WaitBreak", "Kirmayi Bekle", true);
    final BoolSetting onlyOnGround   = new BoolSetting("OnlyOnGround", "Sadece Yerde", true);
    final BoolSetting autoDisable    = new BoolSetting("AutoDisable", "Otomatik Kapan", true);
    final NumSetting  afterBreak     = new NumSetting("BreakTimeout", "Kirma Gecikmesi", 4, 1, 20, 1);
    final NumSetting  afterPearl     = new NumSetting("PearlTimeout", "Pearl Gecikmesi", 20, 0, 60, 1); // Anarşi için 20 tick
    final NumSetting  pitch          = new NumSetting("Pitch", "Bakis Acisi", 85f, 70f, 90f, 1f);
    final BoolSetting strict         = new BoolSetting("Strict", "Kati (Strict)", false);

    // Pearl bypass seçenekleri
    final BoolSetting fireCharge     = new BoolSetting("FireCharge", "Fire Bypass", false);
    final BoolSetting useFlint       = new BoolSetting("UseFlint", "Flint & Steel", false);
    final BoolSetting autoRemove     = new BoolSetting("AutoRemove", "Fisi Otomatik Sil", true);

    public int clipTimer;
    public int afterPearlTime;

    public Phase() {
        super("Phase", "Phase", "Advanced Anarchy Phase Bypass.", "Gelismis Anarsi Faz (Bloklardan Gecis).", Category.MOVEMENT);
        settings.addAll(Arrays.asList(
                mode, silent, waitBreak, onlyOnGround, autoDisable,
                afterBreak, afterPearl, pitch, strict,
                fireCharge, useFlint, autoRemove
        ));
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        afterPearlTime = 0;
        clipTimer = 0;
        status = mode.modes[mode.index];

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null) {
            status = "Off";
            return;
        }

        int idx = mode.index;
        if (idx == 1) {
            doPearl(p);
        } else if (idx == 4 || idx == 5) {
            doTeleport(p);
        }
    }

    @Override
    public void onDisable() {
        status = "Off";
    }

    // ===================================================================
    //  PEARL — Anarşi Odaklı (GrimAC Rotasyon Bypass'ı)
    // ===================================================================
    private void doPearl(ClientPlayerEntity p) {
        if (mc.world == null || mc.getNetworkHandler() == null) return;

        if (!mc.world.getBlockState(p.getBlockPos()).isReplaceable()) {
            if (autoDisable.value) toggle();
            status = "Off";
            return;
        }

        if (p.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
            if (autoDisable.value) toggle();
            status = "Off";
            return;
        }

        int slot = findPearlSlot(p);
        if (slot == -1) {
            if (autoDisable.value) toggle();
            status = "Off";
            return;
        }

        int prevSlot = p.getInventory().getSelectedSlot();
        float prevYaw = p.getYaw();
        float prevPitch = p.getPitch();

        // Hedef blok merkezi
        Vec3d blockCenter = new Vec3d(
                Math.floor(p.getX()) + 0.5,
                p.getY() + 0.5,
                Math.floor(p.getZ()) + 0.5
        );
        float[] rot = calcRotations(p.getEyePos(), blockCenter);
        // GrimAC "Perfect Angle" bypass: rastgele ondalık ekle
        float yaw = Math.round(rot[0]) + 180f + (float)(Math.random() * 0.05 - 0.025);
        float finalPitch = (float) pitch.value + (float)(Math.random() * 0.05 - 0.025);

        boolean didFire = false;

        // --- Fire Charge bypass ---
        BlockPos downPos = p.getBlockPos().down();
        if (fireCharge.value
                && mc.world.getBlockState(p.getBlockPos()).isAir()
                && !mc.world.getBlockState(downPos).isReplaceable()) {

            Item chargeItem = useFlint.value ? Items.FLINT_AND_STEEL : Items.FIRE_CHARGE;
            int chargeSlot = findHotbar(chargeItem);

            if (chargeSlot != -1) {
                // Aşağı bak ve ateşi yak
                sendRotation(p, yaw, 90f);
                p.getInventory().setSelectedSlot(chargeSlot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(chargeSlot));

                mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(downPos, 1), Direction.UP, downPos, false),
                        p.currentScreenHandler.getRevision()
                ));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                p.getInventory().setSelectedSlot(prevSlot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                didFire = true;
            }
        }

        // --- Pearl fırlatma paketleri (Sıra kritik) ---
        // 1. Rotasyonu gönder (Full paket ile)
        sendRotation(p, yaw, finalPitch);

        // 2. Pearl'e geç
        p.getInventory().setSelectedSlot(slot);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        // 3. Pearl'i fırlat
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
                Hand.MAIN_HAND,
                p.currentScreenHandler.getRevision(),
                yaw, finalPitch
        ));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        // 4. Eski slota geri dön
        p.getInventory().setSelectedSlot(prevSlot);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));

        // --- Ateşi sil ---
        if (didFire && autoRemove.value) {
            BlockPos firePos = p.getBlockPos();
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, firePos, Direction.UP));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, firePos, Direction.UP));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        // --- Rotasyonu geri yükle ---
        sendRotation(p, prevYaw, prevPitch);

        afterPearlTime = (int) afterPearl.value;
        if (autoDisable.value) toggle();
    }

    // ===================================================================
    //  TELEPORT / CCLIP — Paket sıralaması düzeltildi
    // ===================================================================
    private void doTeleport(ClientPlayerEntity p) {
        if (mc.world == null || mc.getNetworkHandler() == null) return;

        if (!p.isOnGround()) {
            if (autoDisable.value) toggle();
            status = "Off";
            return;
        }

        double[] diagOffset = getForward(p.getYaw(), 0.44);
        float yawMod = Math.abs(p.getYaw() % 90f);
        boolean diagonal = yawMod > 35f && yawMod < 55f;

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));

        if (diagonal) {
            double[] dir = getForward(p.getYaw(), 0.51);
            int height = getHeight(mc, diagOffset[0], diagOffset[1]);

            // İlk hareket paketi (Yukarı)
            p.setPosition(p.getX() + dir[0], p.getY() + height, p.getZ() + dir[1]);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), true, p.horizontalCollision));

            height = mc.world.isAir(BlockPos.ofFloored(p.getX() + diagOffset[0], p.getY() - 2, p.getZ() + diagOffset[1])) ? 2 : 1;

            // İkinci hareket paketi (Aşağı)
            p.setPosition(p.getX() + dir[0], p.getY() - height, p.getZ() + dir[1]);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), true, p.horizontalCollision));
        } else {
            double[] dir = getForward(p.getYaw(), 0.57);
            int height = getHeight(mc, diagOffset[0], diagOffset[1]);

            p.setPosition(p.getX() + dir[0], p.getY() + height, p.getZ() + dir[1]);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), true, p.horizontalCollision));

            p.setPosition(p.getX() + dir[0], p.getY(), p.getZ() + dir[1]);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), true, p.horizontalCollision));

            height = mc.world.isAir(BlockPos.ofFloored(p.getX() + diagOffset[0], p.getY() - 2, p.getZ() + diagOffset[1])) ? 2 : 1;

            p.setPosition(p.getX() + dir[0], p.getY() - height, p.getZ() + dir[1]);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), true, p.horizontalCollision));
        }

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        if (autoDisable.value) toggle();
    }

    // ===================================================================
    //  onTick — Vanilla / Sunrise / ForceMine
    // ===================================================================
    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        if (clipTimer > 0) clipTimer--;

        if (afterPearlTime > 0) {
            afterPearlTime--;
            p.noClip = true;
        }

        int idx = mode.index;
        boolean noclipMode = (idx == 0 || idx == 2 || idx == 3);

        if (noclipMode) {
            p.noClip = canNoClip();
        } else if (afterPearlTime <= 0) {
            p.noClip = false;
        }

        // Sunrise
        if (idx == 2
                && (p.horizontalCollision || playerInsideBlock(p))
                && !p.isSubmergedInWater() && !p.isInLava()
                && clipTimer <= 0) {

            double[] dir = getForward(p.getYaw(), 0.5);
            BlockPos blockToBreak = null;

            if (mc.options.jumpKey.isPressed()) {
                blockToBreak = BlockPos.ofFloored(p.getX() + dir[0], p.getY() + 2, p.getZ() + dir[1]);
            } else if (mc.options.sneakKey.isPressed()) {
                blockToBreak = BlockPos.ofFloored(p.getX() + dir[0], p.getY() - 1, p.getZ() + dir[1]);
            } else if (isMoving(p)) {
                blockToBreak = BlockPos.ofFloored(p.getX() + dir[0], p.getY(), p.getZ() + dir[1]);
            }

            if (blockToBreak != null && mc.interactionManager != null) {
                int bestTool = findBestTool(mc, blockToBreak);
                if (bestTool != -1) {
                    int prevItem = p.getInventory().getSelectedSlot();
                    p.getInventory().setSelectedSlot(bestTool);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bestTool));

                    mc.interactionManager.updateBlockBreakingProgress(blockToBreak, p.getHorizontalFacing());
                    p.swingHand(Hand.MAIN_HAND);

                    if (silent.value) {
                        p.getInventory().setSelectedSlot(prevItem);
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevItem));
                    }
                }
            }
        }

        // ForceMine
        if (idx == 3
                && (p.horizontalCollision || playerInsideBlock(p))
                && !p.isSubmergedInWater() && !p.isInLava()) {

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos bp = BlockPos.ofFloored(p.getX(), p.getY(), p.getZ()).add(x, y, z);
                        if (p.getBoundingBox().intersects(new Box(bp)) && !mc.world.isAir(bp)) {
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, bp, Direction.UP));
                        }
                    }
                }
            }
        }
    }

    // ===================================================================
    //  Helpers
    // ===================================================================
    private void sendRotation(ClientPlayerEntity p, float yaw, float pitch) {
        p.setYaw(yaw);
        p.setPitch(pitch);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                p.getX(), p.getY(), p.getZ(), yaw, pitch, p.isOnGround(), p.horizontalCollision
        ));
    }

    private boolean canNoClip() {
        if (mode.index == 0) return true;
        return !waitBreak.value || clipTimer != 0;
    }

    private boolean playerInsideBlock(ClientPlayerEntity p) {
        return mc.world != null && !mc.world.isAir(BlockPos.ofFloored(p.getX(), p.getY(), p.getZ()));
    }

    private int findPearlSlot(ClientPlayerEntity p) {
        if (p.getMainHandStack().getItem() == Items.ENDER_PEARL) return p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) return i;
        }
        return -1;
    }

    private int findHotbar(Item item) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return -1;
        if (p.getMainHandStack().getItem() == item) return p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private double[] getForward(float yaw, double dist) {
        double rad = Math.toRadians(yaw);
        return new double[]{-Math.sin(rad) * dist, Math.cos(rad) * dist};
    }

    private int getHeight(MinecraftClient mc, double offsetX, double offsetZ) {
        if (mc.player == null || mc.world == null) return 1;
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(offsetX, 0, offsetZ);
        HitResult result = mc.world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return result.getType().equals(HitResult.Type.MISS) ? 1 : 2;
    }

    private int findBestTool(MinecraftClient mc, BlockPos pos) {
        if (mc.player == null || mc.world == null) return -1;
        int best = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        return best;
    }

    private boolean isMoving(ClientPlayerEntity p) {
        return Math.abs(p.getVelocity().x) > 0.01 || Math.abs(p.getVelocity().z) > 0.01
                || mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    private float[] calcRotations(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitchOut = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitchOut};
    }
}