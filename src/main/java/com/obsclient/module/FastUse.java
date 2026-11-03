package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.Arrays;

import static com.obsclient.ObsClient.isFood;

/**
 * FastUse - esya kullanim hizini arttirir.
 * Yemek, iksir ve XP sisesi icin ekstra interactItem paketi yollar.
 */
public class FastUse extends Module {
    public static String status = "Off";

    final NumSetting delay = new NumSetting("Delay", "Gecikme", 0, 0, 4, 1);

    private int cd = 0;

    public FastUse() {
        super("FastUse", "FastUse", "Fast item use.", "Hizli kullan.", Category.PLAYER);
        settings.addAll(Arrays.asList(delay));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (!mc.options.useKey.isPressed()) return;
        if (!p.isUsingItem()) return;
        if (cd > 0) { cd--; return; }

        ItemStack s = p.getMainHandStack();
        if (isFood(s) || s.isOf(Items.POTION) || s.isOf(Items.EXPERIENCE_BOTTLE)) {
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            status = "Using";
            cd = delay.i();
        }
    }
}