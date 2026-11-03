package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class AutoMace extends Module {
    public static String status = "Kapali";
    public static AutoMace INSTANCE;

    // ===================== AYARLAR =====================
    public final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzili", 20.0, 5.0, 40.0, 0.5);
    public final NumSetting climbHeight = new NumSetting("ClimbHeight", "Tirmanma Yuksekligi", 3.0, 3.0, 20.0, 0.5); // 3 blok
    public final NumSetting hitRange    = new NumSetting("HitRange", "Vurus Menzili", 3.5, 2.0, 6.0, 0.1);
    public final NumSetting minFall     = new NumSetting("MinFall", "Min Dusme (Kritik)", 1.0, 0.0, 4.0, 0.1);

    public final BoolSetting silentHit  = new BoolSetting("SilentHit", "Gizli Vurus", true);
    public final BoolSetting autoElytra = new BoolSetting("AutoElytra", "Oto Elytra Giydir", true);
    public final BoolSetting autoRocket = new BoolSetting("AutoRocket", "Oto Fisek", true);
    public final BoolSetting diveRocket = new BoolSetting("DiveRocket", "Kafaya Fisek", true);
    public final BoolSetting unEquipElytra = new BoolSetting("UnEquipElytra", "Vurusta Elytra Cikar", true);
    public final BoolSetting smartWeapon = new BoolSetting("SmartWeapon", "Akilli Silah (Density/Breach)", true);

    // ===================== STATE =====================
    private int oldSlot = -1;
    private int rocketCd = 0;
    private int stuckTicks = 0;
    private int recoverTicks = 0;
    private int hitCooldown = 0;
    private boolean diving = false;
    private boolean recovering = false;
    private boolean bedrockTarget = false;

    public AutoMace() {
        super("AutoMace", "AutoMace",
                "Aggressive Elytra Mace Aura (Anti-Stuck).",
                "Agresif Mace Vurusu (Havada Takilmaz).",
                Category.COMBAT);
        settings.addAll(Arrays.asList(
                targetRange, climbHeight, hitRange, minFall, silentHit,
                autoElytra, autoRocket, diveRocket, unEquipElytra, smartWeapon
        ));
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        status = "Hazir";
        oldSlot = -1; rocketCd = 0; stuckTicks = 0;
        recoverTicks = 0; hitCooldown = 0;
        diving = false; recovering = false; bedrockTarget = false;
    }

    @Override
    public void onDisable() {
        restoreSlot(mc);
        status = "Kapali";
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        if (rocketCd > 0) rocketCd--;
        if (hitCooldown > 0) hitCooldown--;

        // --- TOPARLANMA ---
        if (recovering) {
            recoverTicks++;
            if (p.isOnGround() || p.isTouchingWater() || recoverTicks > 60) {
                recovering = false; recoverTicks = 0; diving = false; status = "Hazir";
                return;
            }
            Rot.request(p.getYaw(), -35f, 10);
            status = "Toparlaniyor...";
            return;
        }

        PlayerEntity target = findTarget(mc, p);
        if (target == null) {
            status = "Hedef yok";
            diving = false;
            restoreSlot(mc);
            return;
        }

        // --- ELYTRA KONTROLU ---
        if (autoElytra.value && !p.isGliding()) {
            ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
            if (!chest.isOf(Items.ELYTRA)) {
                if (equipFromInventory(mc, p, s -> s.isOf(Items.ELYTRA), 6)) {
                    status = "Elytra giyiliyor...";
                    return;
                }
            }
        }

        // --- MACE KONTROLU (Elinde yoksa zorla al) ---
        int maceSlot = findHot(p, s -> s.isOf(Items.MACE));
        if (maceSlot == -1) {
            if (pullToHotbar(mc, p, s -> s.isOf(Items.MACE))) {
                status = "Mace cekiliyor...";
                return;
            }
            status = "§cMace yok!";
            return;
        }

        // --- MESAFE HESABI ---
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        double dist = p.getEyePos().distanceTo(targetCenter);
        double heightAbove = p.getY() - target.getY();

        // Hedef bedrock veya obsidyen üstünde mi?
        bedrockTarget = isOnHardBlock(target);

        // --- HAVADA TAKILMA KORUMASI ---
        if (!p.isOnGround() && p.isGliding() && p.getVelocity().lengthSquared() < 0.01) {
            stuckTicks++;
            if (stuckTicks > 20) {
                status = "§cTakildi! Kurtariliyor...";
                p.setVelocity(0, -0.6, 0);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY() - 0.1, p.getZ(), false, false));
                if (useRocket(mc, p, p.getYaw(), 90f)) rocketCd = 10;
                stuckTicks = 0;
                diving = false;
                return;
            }
        } else {
            stuckTicks = 0;
        }

        // --- ZIPLAMA VE ELYTRA ACILISI ---
        if (p.isOnGround()) {
            diving = false;
            p.jump();
            status = "Zipliyor...";
            return;
        }

        if (!p.isGliding() && autoElytra.value) {
            diving = false;
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            status = "Elytra Aciliyor...";
            return;
        }

        // --- DALIS MANTIGI (3 blok irtifa) ---
        double requiredHeight = bedrockTarget ? climbHeight.value + 1.0 : climbHeight.value;
        if (!diving && heightAbove >= requiredHeight) {
            diving = true;
        }

        // =================================================================
        // VURUS KONTROLU
        // =================================================================
        if (diving && hitCooldown == 0 && dist <= hitRange.value && p.fallDistance >= minFall.value) {
            if (performHit(mc, p, target, maceSlot, bedrockTarget)) {
                hitCooldown = 6;
                diving = false;
                recovering = true;
                recoverTicks = 0;
                status = "§4SMASH!";
                return;
            }
        }

        if (diving) {
            float[] angle = calcAngle(p.getEyePos(), targetCenter);
            p.setYaw(angle[0]);
            p.setPitch(angle[1]);
            Rot.request(angle[0], angle[1], 10);

            if (diveRocket.value && rocketCd == 0 && p.getVelocity().y > -0.5) {
                if (useRocket(mc, p, angle[0], angle[1])) {
                    rocketCd = 10;
                    status = "Kafasina Fisek!";
                    return;
                }
            }

            if (heightAbove < -1.5) {
                recovering = true;
                recoverTicks = 0;
                status = "Karavana! Toparlaniyor...";
                return;
            }

            status = "Dalista... (" + String.format("%.1f", p.fallDistance) + ")";
        } else {
            float[] angle = calcAngle(p.getEyePos(), new Vec3d(target.getX(), target.getY() + 15, target.getZ()));
            p.setYaw(angle[0]);
            p.setPitch(-45f);
            Rot.request(angle[0], -45f, 10);

            if (autoRocket.value && rocketCd == 0) {
                if (useRocket(mc, p, angle[0], -45f)) {
                    rocketCd = 15;
                    status = "Yukseliyor...";
                    return;
                }
            }
            status = "Yukseliyor... (" + String.format("%.1f", heightAbove) + ")";
        }
    }

    // =====================================================================
    // VURUS METODU (Grim/Matrix Bypass + Elytra Fix)
    // =====================================================================
    private boolean performHit(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity target, int maceSlot, boolean hardBlock) {
        try {
            int prevSlot = p.getInventory().getSelectedSlot();
            ItemStack prevChest = p.getEquippedStack(EquipmentSlot.CHEST);

            // 1. Mace'e gec (Density/Breach akilli secim)
            if (smartWeapon.value) {
                int densitySlot = findHot(p, s -> s.isOf(Items.MACE) && hasEnchant(s, "density"));
                if (densitySlot != -1) maceSlot = densitySlot;
            }

            if (prevSlot != maceSlot) {
                p.getInventory().setSelectedSlot(maceSlot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
            }

            // 2. Vurustan hemen once elytra cikar (Java Smash Fix)
            if (unEquipElytra.value && p.isGliding()) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, p);
            }

            // 3. Rotasyonu ayarla ve flush et
            Vec3d aim = target.getBoundingBox().getCenter();
            float[] angle = calcAngle(p.getEyePos(), aim);
            Rot.request(angle[0], angle[1], 50);
            Rot.flush(mc);

            // 4. PAKET SIRASI: Full Move -> Attack -> Swing
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    p.getX(), p.getY(), p.getZ(), angle[0], angle[1], p.isOnGround(), p.horizontalCollision
            ));

            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, p.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            p.swingHand(Hand.MAIN_HAND);

            // 5. Eski slota geri don
            if (silentHit.value && prevSlot != maceSlot) {
                p.getInventory().setSelectedSlot(prevSlot);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            }

            // 6. Elytra'i geri giy
            if (unEquipElytra.value && prevChest.isOf(Items.ELYTRA)) {
                equipFromInventory(mc, p, s -> s.isOf(Items.ELYTRA), 6);
            }

            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // =====================================================================
    // YARDIMCI METOTLAR
    // =====================================================================
    private boolean isOnHardBlock(PlayerEntity target) {
        BlockPos below = BlockPos.ofFloored(target.getX(), target.getY() - 0.1, target.getZ());
        var block = mc.world.getBlockState(below).getBlock();
        return block == net.minecraft.block.Blocks.BEDROCK ||
                block == net.minecraft.block.Blocks.OBSIDIAN ||
                block == net.minecraft.block.Blocks.CRYING_OBSIDIAN ||
                block == net.minecraft.block.Blocks.NETHERITE_BLOCK;
    }

    private boolean hasEnchant(ItemStack stack, String name) {
        if (stack.isEmpty()) return false;
        for (var enchant : stack.getEnchantments().getEnchantments()) {
            if (enchant.getIdAsString().toLowerCase().contains(name)) return true;
        }
        return false;
    }

    private void restoreSlot(MinecraftClient mc) {
        if (oldSlot != -1 && mc.player != null && mc.getNetworkHandler() != null) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
            oldSlot = -1;
        }
    }

    private PlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity p) {
        PlayerEntity best = null;
        double bestDist = targetRange.value;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double d = p.distanceTo(o);
            if (d < bestDist) {
                bestDist = d;
                best = o;
            }
        }
        return best;
    }

    private int findHot(PlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    private boolean pullToHotbar(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred) {
        if (p.playerScreenHandler == null || mc.interactionManager == null) return false;
        int invSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (pred.test(p.getInventory().getStack(i))) { invSlot = i; break; }
        }
        if (invSlot < 0) return false;

        int emptyHotbar = -1;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isEmpty()) { emptyHotbar = i; break; }
        }
        if (emptyHotbar < 0) emptyHotbar = 8;

        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, invSlot, emptyHotbar, SlotActionType.SWAP, p);
        return true;
    }

    private boolean equipFromInventory(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred, int targetSlot) {
        if (p.playerScreenHandler == null || mc.interactionManager == null) return false;
        int invSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (pred.test(p.getInventory().getStack(i))) { invSlot = i; break; }
        }
        if (invSlot == -1) return false;
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, invSlot, targetSlot, SlotActionType.SWAP, p);
        return true;
    }

    private boolean useRocket(MinecraftClient mc, ClientPlayerEntity p, float yaw, float pitch) {
        int slot = findHot(p, s -> s.isOf(Items.FIREWORK_ROCKET));
        if (slot < 0) {
            if (pullToHotbar(mc, p, s -> s.isOf(Items.FIREWORK_ROCKET))) return false;
            slot = findHot(p, s -> s.isOf(Items.FIREWORK_ROCKET));
            if (slot < 0) return false;
        }

        p.setYaw(yaw);
        p.setPitch(pitch);

        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, p.currentScreenHandler.getRevision(), yaw, pitch));
        p.getInventory().setSelectedSlot(prev);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prev));
        return true;
    }
}