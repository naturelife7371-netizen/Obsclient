package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Arrays;

/**
 * NoRender - bazi render efektlerini kapatir.
 */
public class NoRender extends Module {
    public static String status = "Off";

    final BoolSetting hurtCam = new BoolSetting("HurtCam", "Hasar Kamera", true);
    final BoolSetting fireOverlay = new BoolSetting("FireOverlay", "Ates Overlay", false);
    final BoolSetting nausea = new BoolSetting("Nausea", "Bulanti", false);

    public NoRender() {
        super("NoRender", "NoRender", "No render.", "Render kapat.", Category.RENDER);
        settings.addAll(Arrays.asList(hurtCam, fireOverlay, nausea));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (hurtCam.value) p.hurtTime = 0;
    }
}