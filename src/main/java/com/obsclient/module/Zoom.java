package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;

/**
 * Zoom - Shift tusuna basinca yakinlastirma.
 */
public class Zoom extends Module {
    public static String status = "Off";

    final NumSetting factor = new NumSetting("Factor", "Faktor", 5.0, 1.5, 15.0, 0.5);

    private int savedFov = -1;
    private boolean wasZoom = false;

    public Zoom() {
        super("Zoom", "Zoom", "Zoom.", "Zoom.", Category.RENDER);
        settings.addAll(Arrays.asList(factor));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onDisable() {
        if (savedFov >= 0) {
            mc.options.getFov().setValue(savedFov);
            savedFov = -1;
        }
        wasZoom = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        boolean zooming = mc.options.sneakKey.isPressed() && mc.currentScreen == null;

        if (zooming && !wasZoom) {
            savedFov = mc.options.getFov().getValue();
            mc.options.getFov().setValue((int)Math.max(1, savedFov / factor.value));
        } else if (!zooming && wasZoom && savedFov >= 0) {
            mc.options.getFov().setValue(savedFov);
            savedFov = -1;
        }
        wasZoom = zooming;
    }
}