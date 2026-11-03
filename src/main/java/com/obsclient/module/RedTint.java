package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Arrays;

import static com.obsclient.ObsClient.withAlpha;

public class RedTint extends Module {
    public static String status = "Off";
    public static RedTint INSTANCE;

    final NumSetting strength = new NumSetting("Strength", "Yogunluk", 70, 10, 160, 5);
    final BoolSetting pulse    = new BoolSetting("Pulse", "Nabiz", false);

    public RedTint() {
        super("RedTint", "RedTint", "Red tint.", "Kirmizi ton.", Category.RENDER);
        settings.addAll(Arrays.asList(strength, pulse));
        INSTANCE = this;
    }

    public static boolean on() { return INSTANCE != null && INSTANCE.enabled; }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        // Render-only modül
    }

    public void render(DrawContext g, MinecraftClient mc) {
        int W = mc.getWindow().getScaledWidth();
        int H = mc.getWindow().getScaledHeight();
        double a = strength.value;
        if (pulse.value) a *= 0.75 + 0.25 * Math.sin(System.currentTimeMillis() / 350.0);
        g.fill(0, 0, W, H, withAlpha(0xFF1010, (int)a));
    }
}