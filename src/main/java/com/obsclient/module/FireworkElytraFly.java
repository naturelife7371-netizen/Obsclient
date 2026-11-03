package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.Arrays;
import java.util.List;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class FireworkElytraFly extends Module {
    public static String status = "Off";
    public static FireworkElytraFly INSTANCE;

    // ===================== AYARLAR =====================
    final NumSetting fallSpeed   = new NumSetting("FallSpeed", "Asili Kalma Hizi", 0.02, 0.0, 0.5, 0.01);
    final NumSetting fireworkGap = new NumSetting("FireworkGap", "Fisek Gecikmesi (ms)", 250, 50, 1000, 10);
    final ModeSetting mode       = new ModeSetting("Mode", "Mod", 0,
            new String[]{"Normal", "Grim"}, new String[]{"Normal", "Grim"});

    final BoolSetting autoEquip  = new BoolSetting("AutoEquip", "Oto Elytra Giydir", true);
    final BoolSetting autoGlide  = new BoolSetting("AutoGlide", "Oto Glide", true);
    final BoolSetting noSprint   = new BoolSetting("NoSprint", "Sprint Kapat", true);
    final BoolSetting control    = new BoolSetting("Control", "Ucus Kontrolu", true);

    // ===================== STATE =====================
    private int fwCd = 0;
    private int glideCd = 0;
    private long lastFireworkTime = 0;

    public FireworkElytraFly() {
        super("FireworkElytraFly", "FireworkElytraFly",
                "LeavesHack Style Firework Elytra Fly.",
                "Yaratici mod gibi ucus (Grim Bypass).", Category.MOVEMENT);
        settings.addAll(Arrays.asList(fallSpeed, fireworkGap, mode, autoEquip, autoGlide, noSprint, control));
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? status : "Off"; }

    @Override
    public void onEnable() {
        status = "Active";
        fwCd = 0; glideCd = 0;
        lastFireworkTime = 0;
    }

    @Override
    public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (fwCd > 0) fwCd--;
        if (glideCd > 0) glideCd--;

        // 1. ELYTRA KONTROLÜ
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearingElytra = chest.isOf(Items.ELYTRA) && chest.getDamage() < chest.getMaxDamage() - 1;

        if (!wearingElytra && autoEquip.value) {
            int elytraSlot = findItemInv(p, Items.ELYTRA);
            if (elytraSlot != -1) {
                int syncId = p.playerScreenHandler.syncId;
                int invSlotId = elytraSlot < 9 ? 36 + elytraSlot : elytraSlot;
                mc.interactionManager.clickSlot(syncId, invSlotId, 6, SlotActionType.SWAP, p);
                status = "Elytra Giydiriliyor";
                return;
            }
        }

        if (!wearingElytra) {
            status = "Elytra yok!";
            return;
        }

        // 2. GLIDE BAŞLATMA
        if (!p.isGliding()) {
            if (autoGlide.value && glideCd == 0) {
                p.jump();
                p.startGliding();
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                glideCd = 5;
                status = "Glide Basladi";
            }
            return;
        }

        // 3. SPRINT KAPATMA (Grim Bypass)
        if (noSprint.value && p.isSprinting()) {
            p.setSprinting(false);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }

        // 4. HAREKET VE ROTASYON KONTROLÜ
        if (control.value) {
            handleMovement(p);
        }

        // 5. FİŞEK ATIŞI (Ghost Swap)
        boolean rocketActive = false;
        List<FireworkRocketEntity> rockets = mc.world.getEntitiesByClass(
                FireworkRocketEntity.class, p.getBoundingBox().expand(10.0), e -> e.getOwner() == p);
        if (!rockets.isEmpty()) rocketActive = true;

        long now = System.currentTimeMillis();
        if (fwCd == 0 && !rocketActive && now - lastFireworkTime > fireworkGap.value) {
            if (fireFireworkFromInv(p)) {
                lastFireworkTime = now;
                fwCd = 2;
            }
        }

        status = "Ucus | " + (rocketActive ? "Fisek" : "Glide");
    }

    private void handleMovement(ClientPlayerEntity p) {
        boolean jump = mc.options.jumpKey.isPressed();
        boolean sneak = mc.options.sneakKey.isPressed();
        boolean w = mc.options.forwardKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();

        float targetYaw = p.getYaw();
        float targetPitch = 0f;

        // Yaw Hesaplama
        if (w && !s) {
            if (a && !d) targetYaw -= 45f;
            else if (d && !a) targetYaw += 45f;
        } else if (s && !w) {
            targetYaw += 180f;
            if (a && !d) targetYaw += 45f;
            else if (d && !a) targetYaw -= 45f;
        } else if (a && !d) targetYaw -= 90f;
        else if (d && !a) targetYaw += 90f;

        // Pitch Hesaplama
        if (jump && sneak) targetPitch = -3f; // İkisine basarsan düz git
        else if (jump) {
            targetPitch = (w || s || a || d) ? -45f : -90f; // İleri + Zıpla = Hafif yukarı
        } else if (sneak) {
            targetPitch = (w || s || a || d) ? 45f : 90f; // İleri + Eğil = Hafif aşağı
        } else if (w || s || a || d) {
            targetPitch = -1.9f; // Düz uçuş için hafif burun aşağı
        }

        // Asılı Kalma (Hover) - Sadece hiçbir tuşa basılmadığında hafifçe aşağı süzül
        if (!w && !s && !a && !d && !jump && !sneak) {
            targetPitch = 0f;
            // Havada asılı kalmak yerine çok hafif aşağı süzülmesini sağla
            p.setVelocity(p.getVelocity().x * 0.98, -fallSpeed.value, p.getVelocity().z * 0.98);
        }

        // Sunucuya sessizce rotasyonu gönder
        Rot.request(targetYaw, targetPitch, 10);
    }

    private boolean fireFireworkFromInv(ClientPlayerEntity p) {
        if (mc.interactionManager == null || mc.getNetworkHandler() == null) return false;

        // Offhand
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(p, Hand.OFF_HAND);
            p.swingHand(Hand.OFF_HAND);
            return true;
        }

        // Hotbar
        int hotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) { hotbarSlot = i; break; }
        }

        // Envanter (Ghost Swap)
        if (hotbarSlot < 0) {
            int invSlot = findItemInv(p, Items.FIREWORK_ROCKET);
            if (invSlot < 0) return false;

            int syncId = p.playerScreenHandler.syncId;
            int invSlotId = invSlot < 9 ? 36 + invSlot : invSlot;
            int emptyHotbar = p.getInventory().getSelectedSlot(); // Seçili slotun üstüne yaz

            // Envanterden seçili slota çek
            mc.interactionManager.clickSlot(syncId, invSlotId, emptyHotbar, SlotActionType.SWAP, p);
            hotbarSlot = emptyHotbar;
        }

        int prev = p.getInventory().getSelectedSlot();
        if (hotbarSlot != prev) {
            p.getInventory().setSelectedSlot(hotbarSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        }

        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);

        if (hotbarSlot != prev) {
            p.getInventory().setSelectedSlot(prev);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prev));
        }

        return true;
    }

    private int findItemInv(ClientPlayerEntity p, net.minecraft.item.Item item) {
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }
}