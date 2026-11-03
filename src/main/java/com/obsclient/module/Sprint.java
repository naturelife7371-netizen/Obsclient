package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Sprint - otomatik sprint.
 */
public class Sprint extends Module {
    public static String status = "Off";

    public Sprint() {
        super("Sprint", "Sprint", "Auto sprint.", "Oto sprint.", Category.MOVEMENT);
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (p.forwardSpeed > 0 && !p.isSneaking() && !p.isUsingItem() && !p.horizontalCollision) {
            p.setSprinting(true);
        }
    }
}