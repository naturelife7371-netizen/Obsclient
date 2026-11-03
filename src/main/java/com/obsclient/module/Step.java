package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * Step - otomatik tirmanma (yuksek blok).
 */
public class Step extends Module {
    public static String status = "Off";

    final NumSetting height = new NumSetting("Height", "Yukseklik", 1.5, 0.5, 4.0, 0.1);

    public Step() {
        super("Step", "Step", "Auto climb.", "Oto tirman.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(height));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        if (!p.horizontalCollision || !p.isOnGround()) return;

        Vec3d v = p.getVelocity();
        if (v.horizontalLength() < 0.05) return;

        // Yerde tam blok hizasinda mi?
        if (p.getY() - Math.floor(p.getY()) < 0.01) {
            p.setPosition(p.getX(), p.getY() + height.value, p.getZ());
            p.setVelocity(v.x, 0, v.z);
        }
    }
}