package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;

/**
 * ChestStealer - hizli loot.
 */
public class ChestStealer extends Module {
    public static String status = "Off";

    final NumSetting delay = new NumSetting("Delay", "Gecikme", 1, 0, 10, 1);
    private int cd = 0;

    public ChestStealer() {
        super("ChestStealer", "ChestStealer", "Quick loot.", "Hizli loot.", Category.PLAYER);
        settings.addAll(Arrays.asList(delay));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?>)) { status = "No chest"; return; }
        var handler = p.currentScreenHandler;
        if (handler == p.playerScreenHandler) return;
        if (cd > 0) { cd--; return; }

        // Chest slotlari (son 36 player envanteri)
        for (int i = 0; i < handler.slots.size() - 36; i++) {
            ItemStack s = handler.slots.get(i).getStack();
            if (s.isEmpty()) continue;
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, p);
            status = "Looting";
            cd = delay.i();
            return;
        }
        status = "Empty";
    }
}