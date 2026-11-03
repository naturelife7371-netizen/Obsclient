package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.Arrays;

/**
 * FullBright - tam aydinlik.
 * NightVision: istemci tarafinda gece görüşü efekti verir (gerçek fullbright).
 * Gamma: sadece oyun ayarindaki gamma'yi kullanir (en fazla 1.0, sinirli).
 */
public class FullBright extends Module {
    public static String status = "Off";

    final ModeSetting mode = new ModeSetting("Mode", 0, "NightVision", "Gamma");
    final NumSetting level = new NumSetting("Level", "Parlaklik", 1.0, 0.3, 1.0, 0.05);

    private double saved = -1;
    private boolean addedNv = false;

    public FullBright() {
        super("FullBright", "FullBright", "Bright.", "Aydinlik.", Category.RENDER);
        settings.addAll(Arrays.asList(mode, level));
    }

    @Override public String status() { return enabled ? mode.value() : "Off"; }

    @Override
    public void onEnable() {
        try { saved = mc.options.getGamma().getValue(); } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        try {
            removeNv(mc.player);
            if (saved >= 0) mc.options.getGamma().setValue(saved);
        } catch (Throwable ignored) {}
        saved = -1;
    }

    private void removeNv(ClientPlayerEntity p) {
        if (p != null && addedNv) {
            try { p.removeStatusEffect(StatusEffects.NIGHT_VISION); } catch (Throwable ignored) {}
        }
        addedNv = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options == null) return;

        if (mode.index == 0) {
            // NightVision modu
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || (addedNv && cur.getDuration() < 600)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false));
                addedNv = true;
            }
        } else {
            // Gamma modu
            removeNv(p);
            if (saved < 0) saved = mc.options.getGamma().getValue();
            if (Math.abs(mc.options.getGamma().getValue() - level.value) > 0.01) {
                mc.options.getGamma().setValue(level.value);
            }
        }
    }
}