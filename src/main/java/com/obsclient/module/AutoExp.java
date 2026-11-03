package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.Arrays;

import static com.obsclient.ObsClient.dur;
import static com.obsclient.ObsClient.findHot;
import static com.obsclient.ObsClient.findInv;

public class AutoExp extends Module {
    public static String status = "Off";

    final NumSetting minDurability = new NumSetting("MinDurability", "Min Dayaniklilik", 40, 1, 90, 1);
    final NumSetting delay         = new NumSetting("Delay", "Gecikme", 1, 0, 10, 1);
    final NumSetting perTick       = new NumSetting("Bottles/Tick", "Sise/Tick", 2, 1, 5, 1);
    final BoolSetting onlyMending  = new BoolSetting("OnlyMending", "Sadece Mending", true);
    final BoolSetting allArmor     = new BoolSetting("CheckAllArmor", "Tum Zirh", true);
    final BoolSetting offhandFirst = new BoolSetting("OffhandFirst", "Offhand Oncelik", false);

    private int cd = 0;
    private int swapCd = 0;

    public AutoExp() {
        super("AutoExp", "AutoExp", "Mending repair.", "Mending tamir.", Category.PLAYER);
        settings.addAll(Arrays.asList(minDurability, delay, perTick, onlyMending, allArmor, offhandFirst));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }
    @Override public void onEnable() { cd = 0; swapCd = 0; }

    private boolean needsRepair(ClientPlayerEntity p) {
        EquipmentSlot[] slots = allArmor.value
                ? new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
                : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        for (EquipmentSlot sl : slots) {
            ItemStack st = p.getEquippedStack(sl);
            if (st.isEmpty() || st.getMaxDamage() <= 0) continue;

            if (onlyMending.value) {
                boolean hasMending = false;
                // Mending kontrolü (Daha güvenli yöntem)
                if (st.hasEnchantments()) {
                    var enchants = st.getEnchantments().getEnchantments();
                    for (var ench : enchants) {
                        if (ench.getIdAsString().contains("mending")) {
                            hasMending = true;
                            break;
                        }
                    }
                }
                if (!hasMending) continue;
            }
            int d = dur(st);
            if (d >= 0 && d < minDurability.value) return true;
        }
        return false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;

        if (swapCd > 0) { swapCd--; return; }
        if (cd > 0) { cd--; return; }

        if (!needsRepair(p)) { status = "Armor OK"; return; }

        boolean offXP = p.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE);
        int hotbarSlot = findHot(p, s -> s.isOf(Items.EXPERIENCE_BOTTLE));

        // 1. ADIM: XP Envanterdeyse Hotbar'a Çek
        if (!offXP && hotbarSlot < 0) {
            int invSlot = findInv(p, s -> s.isOf(Items.EXPERIENCE_BOTTLE));
            if (invSlot >= 9 && invSlot < 36) {
                int emptyHotbar = -1;
                for (int i = 0; i < 9; i++) {
                    if (p.getInventory().getStack(i).isEmpty()) {
                        emptyHotbar = i;
                        break;
                    }
                }

                if (emptyHotbar >= 0) {
                    mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, invSlot, emptyHotbar, SlotActionType.SWAP, p);
                    swapCd = 3; // Sunucunun slot değişimini onaylaması için bekle
                    status = "Swapping XP";
                    return;
                }
            }
        }

        // 2. ADIM: XP'yi Fırlat (Paket Seviyesinde)
        int slot = findHot(p, s -> s.isOf(Items.EXPERIENCE_BOTTLE));
        if (!offXP && slot < 0) { status = "No XP"; return; }

        Hand hand;
        int prev = p.getInventory().getSelectedSlot();

        if (offhandFirst.value && offXP) {
            hand = Hand.OFF_HAND;
        } else if (slot >= 0) {
            p.getInventory().setSelectedSlot(slot);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            hand = Hand.MAIN_HAND;
        } else {
            hand = Hand.OFF_HAND;
        }

        int n = perTick.i();
        for (int i = 0; i < n; i++) {
            try {
                // Direkt paket gönderimi (İstemci bekleme süresini atlar)
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, 0, p.getYaw(), p.getPitch()));
                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
                }
                p.swingHand(hand);
            } catch (Throwable ignored) {}
        }

        // Eski slota geri dön
        if (hand == Hand.MAIN_HAND) {
            p.getInventory().setSelectedSlot(prev);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prev));
            }
        }

        cd = delay.i();
        status = "Repair x" + n;
    }
}