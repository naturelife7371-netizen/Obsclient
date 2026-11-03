package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;

public class Predict extends Module {
    public final NumSetting ticks = new NumSetting("Ticks", "Tick", 4, 1, 12, 1);
    public final BoolSetting show = new BoolSetting("ShowESP", "ESP'de", true);

    public Predict() {
        super("Predict", "Predict", "Predict position.", "Konum tahmini.", Category.COMBAT);
        settings.addAll(Arrays.asList(ticks, show));
    }

    @Override public void onTick(MinecraftClient mc) {}
}