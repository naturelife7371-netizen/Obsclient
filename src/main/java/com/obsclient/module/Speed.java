package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import static com.obsclient.ObsClient.isMoving;

public class Speed extends Module {
    public static String status = "Off";
    final ModeSetting mode = new ModeSetting("Mode", "Mod", 0, new String[]{"Grim", "Vanilla"}, new String[]{"Grim", "Vanilla"});
    final NumSetting multiplier = new NumSetting("Multiplier", "Carpan", 1.15, 1.0, 1.3, 0.01); // 1.3'ten fazlası ban sebebi

    public Speed() {
        super("Speed", "Speed", "Grim-Safe Move speed.", "Grim-Guvenli Hiz.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(mode, multiplier));
    }

    @Override public String status() { return enabled ? mode.valueLabel() : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null || mc.currentScreen != null) return;
            if (!isMoving(p)) return;

            if (p.isOnGround() && !p.isSneaking() && p.forwardSpeed > 0) {
                Vec3d v = p.getVelocity();
                p.setVelocity(v.x * multiplier.value, v.y, v.z * multiplier.value);
            }
        } catch (Throwable ignored) {}
    }
}