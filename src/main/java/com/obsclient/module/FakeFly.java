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
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class FakeFly extends Module {
    public static String status = "Off";
    public static FakeFly INSTANCE;

    // ===================== AYARLAR =====================
    final NumSetting health = new NumSetting("Health", "Tetik Can", 10.0, 1.0, 20.0, 0.5);
    final NumSetting threatRange = new NumSetting("ThreatRange", "Tehdit Menzili", 6.0, 1.0, 15.0, 0.5);
    final NumSetting fireworkDelay = new NumSetting("FireworkDelay", "Fisek Gecikmesi (Tick)", 3.0, 1.0, 20.0, 1.0);
    final BoolSetting autoDisable = new BoolSetting("AutoDisable", "Guvenli Olunca Kapan", true);
    final BoolSetting rotate = new BoolSetting("Rotate", "Sessiz Donus", true);

    // ===================== STATE =====================
    private int fwDelay = 0;
    private boolean flying = false;

    public FakeFly() {
        super("FakeFly", "FakeFly", "Secretly flies to a safe zone when in danger.", "Tehlikedeyken gizlice guvenli alana ucar.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(health, threatRange, fireworkDelay, autoDisable, rotate));
        INSTANCE = this;
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Ready"; flying = false; fwDelay = 0; }
    @Override public void onDisable() { status = "Off"; flying = false; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (fwDelay > 0) fwDelay--;

        // 1. Tehdit ve Can Kontrolü
        boolean danger = false;
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (hp <= health.value) danger = true;

        if (!danger && threatRange.value > 0) {
            for (PlayerEntity target : mc.world.getPlayers()) {
                if (target == p || !target.isAlive() || isFriend(target)) continue;
                if (p.distanceTo(target) <= threatRange.value) {
                    danger = true; break;
                }
            }
        }

        if (danger) {
            flying = true;
        } else if (autoDisable.value && flying) {
            // Güvenliysek modülü kapat, uçuşu bırak (süzülerek inersin)
            flying = false;
            toggle();
            return;
        }

        if (!flying) {
            status = "Waiting";
            return;
        }

        // 2. Elytra Kontrolü (Envanterin neresinde olursa olsun giydir)
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) {
            int elytraSlot = -1;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = p.getInventory().getStack(i);
                if (stack.isOf(Items.ELYTRA) && stack.getDamage() < stack.getMaxDamage() - 1) {
                    elytraSlot = i; break;
                }
            }
            if (elytraSlot != -1) {
                int syncId = p.playerScreenHandler.syncId;
                int invSlotId = elytraSlot < 9 ? 36 + elytraSlot : elytraSlot;
                // 6 = Göğüs slotu ID'si
                mc.interactionManager.clickSlot(syncId, invSlotId, 6, SlotActionType.SWAP, p);
                status = "Equipping Elytra";
                return; // Bir sonraki tick devam et
            } else {
                status = "§cNo Elytra!";
                return;
            }
        }

        // 3. Glide Başlatma
        if (!p.isGliding()) {
            p.jump(); // Yerden havalanmak için zıpla
            p.startGliding();
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            status = "Starting Glide";
            return;
        }

        // 4. Yukarı Bakma (Sessiz Rotasyon - Kameran dönmez, karakterin havaya bakar)
        if (rotate.value) {
            Rot.request(p.getYaw(), -45f, 10);
        } else {
            p.setPitch(-45f);
        }

        // 5. Fişek Atışı
        if (fwDelay <= 0) {
            // Offhand kontrolü
            if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
                mc.interactionManager.interactItem(p, Hand.OFF_HAND);
                p.swingHand(Hand.OFF_HAND);
                fwDelay = (int) fireworkDelay.value;
                status = "Flying (Offhand)";
                return;
            }

            // Hotbar kontrolü
            int hotbarSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (p.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) { hotbarSlot = i; break; }
            }

            // Hotbar'da yoksa envanterden çek
            if (hotbarSlot == -1) {
                int invSlot = findInv(p, s -> s.isOf(Items.FIREWORK_ROCKET));
                if (invSlot != -1) {
                    int syncId = p.playerScreenHandler.syncId;
                    int invSlotId = invSlot < 9 ? 36 + invSlot : invSlot;
                    int emptyHotbar = -1;
                    for (int i = 0; i < 9; i++) {
                        if (p.getInventory().getStack(i).isEmpty()) { emptyHotbar = i; break; }
                    }
                    if (emptyHotbar < 0) emptyHotbar = p.getInventory().getSelectedSlot();
                    mc.interactionManager.clickSlot(syncId, invSlotId, emptyHotbar, SlotActionType.SWAP, p);
                    hotbarSlot = emptyHotbar;
                }
            }

            // Fişeği kullan
            if (hotbarSlot != -1) {
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
                fwDelay = (int) fireworkDelay.value;
                status = "Flying (Mainhand)";
            } else {
                status = "§cNo Firework!";
            }
        } else {
            status = "Flying...";
        }
    }
}