package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.Arrays;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

/**
 * AutoPot - can azalinca otomatik iksir atar.
 */
public class AutoPot extends Module {
    public static String status = "Off";

    final NumSetting hpThreshold = new NumSetting("HPThreshold", "Can Esigi", 15, 1, 36, 1);
    final BoolSetting heal = new BoolSetting("Heal", "Sifa", true);
    final BoolSetting speed = new BoolSetting("Speed", "Hiz", true);
    final BoolSetting fire = new BoolSetting("FireRes", "Ates", true);

    private int cd = 0;

    public AutoPot() {
        super("AutoPot", "AutoPot", "Auto potion.", "Oto iksir.", Category.COMBAT);
        settings.addAll(Arrays.asList(hpThreshold, heal, speed, fire));
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { cd = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cd > 0) { cd--; return; }

        double hp = p.getHealth() + p.getAbsorptionAmount();
        Predicate<ItemStack> pred = null;

        if (heal.value && hp < hpThreshold.value) {
            pred = s -> s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION);
        } else if (speed.value && p.getStatusEffect(StatusEffects.SPEED) == null) {
            pred = s -> s.isOf(Items.SPLASH_POTION);
        } else if (fire.value && p.getStatusEffect(StatusEffects.FIRE_RESISTANCE) == null && p.isOnFire()) {
            pred = s -> s.isOf(Items.SPLASH_POTION);
        }

        if (pred == null) { status = "OK"; return; }

        int slot = findHot(p, pred);
        if (slot < 0) { status = "No potion"; return; }

        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(prev);
        status = "Threw";
        cd = 10;
    }
}