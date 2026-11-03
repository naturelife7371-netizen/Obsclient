package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;

/**
 * AutoArmor - otomatik zirh giy.
 */
public class AutoArmor extends Module {
    public static String status = "Off";

    final NumSetting delay = new NumSetting("Delay", "Gecikme", 1, 0, 10, 1);
    final BoolSetting keepElytra = new BoolSetting("KeepElytra", "Elytra Koru", true);

    private int cd = 0;

    public AutoArmor() {
        super("AutoArmor", "AutoArmor", "Auto armor.", "Oto zirh.", Category.PLAYER);
        settings.addAll(Arrays.asList(delay, keepElytra));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cd > 0) { cd--; return; }
        if (!p.currentScreenHandler.getCursorStack().isEmpty()) return;

        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        int[] armorSlotIds = { 5, 6, 7, 8 };
        int syncId = p.playerScreenHandler.syncId;

        for (int i = 0; i < 4; i++) {
            ItemStack cur = p.getEquippedStack(slots[i]);
            if (keepElytra.value && i == 1 && cur.isOf(Items.ELYTRA)) continue;

            int bestIdx = -1;
            int bestScore = armorScore(cur);
            for (int j = 0; j < 36; j++) {
                ItemStack s = p.getInventory().getStack(j);
                if (s.isEmpty() || !isArmorFor(s, slots[i])) continue;
                if (keepElytra.value && s.isOf(Items.ELYTRA)) continue;
                int sc = armorScore(s);
                if (sc > bestScore) { bestScore = sc; bestIdx = j; }
            }
            if (bestIdx < 0) continue;

            int fromId = bestIdx < 9 ? 36 + bestIdx : bestIdx;
            if (cur.isEmpty()) {
                mc.interactionManager.clickSlot(syncId, fromId, 0, SlotActionType.QUICK_MOVE, p);
            } else {
                mc.interactionManager.clickSlot(syncId, fromId, 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(syncId, armorSlotIds[i], 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(syncId, fromId, 0, SlotActionType.PICKUP, p);
            }
            status = "Equipping";
            cd = delay.i();
            return;
        }
        status = "OK";
    }

    private boolean isArmorFor(ItemStack s, EquipmentSlot sl) {
        Item it = s.getItem();
        return switch (sl) {
            case HEAD -> it == Items.LEATHER_HELMET || it == Items.CHAINMAIL_HELMET || it == Items.IRON_HELMET || it == Items.GOLDEN_HELMET || it == Items.DIAMOND_HELMET || it == Items.NETHERITE_HELMET || it == Items.TURTLE_HELMET;
            case CHEST -> it == Items.LEATHER_CHESTPLATE || it == Items.CHAINMAIL_CHESTPLATE || it == Items.IRON_CHESTPLATE || it == Items.GOLDEN_CHESTPLATE || it == Items.DIAMOND_CHESTPLATE || it == Items.NETHERITE_CHESTPLATE || it == Items.ELYTRA;
            case LEGS -> it == Items.LEATHER_LEGGINGS || it == Items.CHAINMAIL_LEGGINGS || it == Items.IRON_LEGGINGS || it == Items.GOLDEN_LEGGINGS || it == Items.DIAMOND_LEGGINGS || it == Items.NETHERITE_LEGGINGS;
            case FEET -> it == Items.LEATHER_BOOTS || it == Items.CHAINMAIL_BOOTS || it == Items.IRON_BOOTS || it == Items.GOLDEN_BOOTS || it == Items.DIAMOND_BOOTS || it == Items.NETHERITE_BOOTS;
            default -> false;
        };
    }

    private int armorScore(ItemStack s) {
        if (s.isEmpty()) return -1;
        int base = 0;
        Item it = s.getItem();
        if (it == Items.NETHERITE_HELMET) base = 3;
        else if (it == Items.NETHERITE_CHESTPLATE) base = 8;
        else if (it == Items.NETHERITE_LEGGINGS) base = 6;
        else if (it == Items.NETHERITE_BOOTS) base = 3;
        else if (it == Items.DIAMOND_HELMET) base = 3;
        else if (it == Items.DIAMOND_CHESTPLATE) base = 8;
        else if (it == Items.DIAMOND_LEGGINGS) base = 6;
        else if (it == Items.DIAMOND_BOOTS) base = 3;
        else if (it == Items.IRON_HELMET) base = 2;
        else if (it == Items.IRON_CHESTPLATE) base = 6;
        else if (it == Items.IRON_LEGGINGS) base = 5;
        else if (it == Items.IRON_BOOTS) base = 2;
        else if (it == Items.CHAINMAIL_HELMET) base = 2;
        else if (it == Items.CHAINMAIL_CHESTPLATE) base = 5;
        else if (it == Items.CHAINMAIL_LEGGINGS) base = 4;
        else if (it == Items.CHAINMAIL_BOOTS) base = 1;
        else if (it == Items.GOLDEN_HELMET) base = 2;
        else if (it == Items.GOLDEN_CHESTPLATE) base = 5;
        else if (it == Items.GOLDEN_LEGGINGS) base = 3;
        else if (it == Items.GOLDEN_BOOTS) base = 1;
        else if (it == Items.LEATHER_HELMET) base = 1;
        else if (it == Items.LEATHER_CHESTPLATE) base = 3;
        else if (it == Items.LEATHER_LEGGINGS) base = 2;
        else if (it == Items.LEATHER_BOOTS) base = 1;
        else if (it == Items.TURTLE_HELMET) base = 2;
        return base * 1000 + (s.getMaxDamage() - s.getDamage());
    }
}