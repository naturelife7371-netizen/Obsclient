package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;

public class Visual extends Module {
    public static String status = "Off";
    public static Visual INSTANCE;

    public final BoolSetting customSky = new BoolSetting("CustomSky", "Gokyuzu Rengi", true);
    public final NumSetting skyColor = new NumSetting("SkyColor", "Gokyuzu Rengi (Hex)", 0x87CEEB, 0x000000, 0xFFFFFF, 1);

    public final BoolSetting customFog = new BoolSetting("CustomFog", "Sis Rengi", true);
    public final NumSetting fogColor = new NumSetting("FogColor", "Sis Rengi (Hex)", 0xC0D8FF, 0x000000, 0xFFFFFF, 1);

    public Visual() {
        super("Visual", "Visual", "Change sky and fog colors.", "Gokyuzu ve sis renklerini degistir.", Category.RENDER);
        settings.add(customSky);
        settings.add(skyColor);
        settings.add(customFog);
        settings.add(fogColor);
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }
    @Override public void onEnable() { status = "Active"; }
    @Override public void onDisable() { status = "Off"; }
    @Override public void onTick(MinecraftClient mc) { /* Mixin'ler hallediyor */ }

    public static boolean isActive() { return INSTANCE != null && INSTANCE.enabled; }
    public static int getSkyColor() { return INSTANCE != null ? (int) INSTANCE.skyColor.value : 0x87CEEB; }
    public static int getFogColor() { return INSTANCE != null ? (int) INSTANCE.fogColor.value : 0xC0D8FF; }
}