package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * NoSlow - esya kullanirken yavaslama iptal.
 */
public class NoSlow extends Module {
    public static String status = "Off";

    final BoolSetting item = new BoolSetting("ItemUse", "Esya", true);
    final NumSetting boost = new NumSetting("Boost", "Boost", 1.5, 1.0, 3.0, 0.1);

    public NoSlow() {
        super("NoSlow", "NoSlow", "No slowdown.", "Yavaslama yok.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(item, boost));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (item.value && p.isUsingItem()) {
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x * boost.value, v.y, v.z * boost.value);
        }
    }
}