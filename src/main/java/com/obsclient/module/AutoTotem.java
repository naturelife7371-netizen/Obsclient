package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;

import static com.obsclient.ObsClient.findInv;

public class AutoTotem extends Module {
    public static String status = "Off";

    final NumSetting hpLimit = new NumSetting("HPLimit", "Can Esigi", 20, 1, 20, 1);
    final NumSetting delay   = new NumSetting("Delay", "Gecikme", 0, 0, 5, 1);

    private int cd = 0;

    public AutoTotem() {
        super("AutoTotem", "AutoTotem", "Auto offhand totem.", "Oto offhand totem.", Category.COMBAT);
        settings.addAll(Arrays.asList(hpLimit, delay));
    }

    @Override public String status() {
        if (!enabled) return "Off";
        return hpLimit.i() + " HP";
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cd > 0) { cd--; return; }
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) { status = "Holding"; return; }
        if (p.getHealth() + p.getAbsorptionAmount() > hpLimit.value) { status = "Safe"; return; }

        int idx = findInv(p, s -> s.isOf(Items.TOTEM_OF_UNDYING));
        if (idx < 0) { status = "No totem"; return; }

        int slotId = idx < 9 ? 36 + idx : idx;
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slotId, 40, SlotActionType.SWAP, p);
        status = "Swapped";
        cd = delay.i();
    }
}