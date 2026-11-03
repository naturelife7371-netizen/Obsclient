package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Arrays;

/**
 * FastPlace - blok koyma hizini arttirir.
 * Kristal, XP sisesi ve normal bloklar icin.
 */
public class FastPlace extends Module {
    public static String status = "Off";

    final NumSetting every   = new NumSetting("Interval", "Aralik", 1, 1, 4, 1);
    final BoolSetting crystals = new BoolSetting("Crystals", "Kristal", true);
    final BoolSetting exp      = new BoolSetting("XP", "XP", true);

    private int t = 0;

    public FastPlace() {
        super("FastPlace", "FastPlace", "Faster place.", "Hizli koy.", Category.PLAYER);
        settings.addAll(Arrays.asList(every, crystals, exp));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!mc.options.useKey.isPressed()) return;
        if (++t % every.i() != 0) return;

        ItemStack s = p.getMainHandStack();

        // XP sisesi
        if (exp.value && s.isOf(Items.EXPERIENCE_BOTTLE)) {
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.swingHand(Hand.MAIN_HAND);
            return;
        }

        boolean ok = s.getItem() instanceof BlockItem || (crystals.value && s.isOf(Items.END_CRYSTAL));
        if (!ok) return;

        if (mc.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bh);
            p.swingHand(Hand.MAIN_HAND);
        }
    }
}