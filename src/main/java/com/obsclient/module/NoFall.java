package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.Arrays;

/**
 * NoFall - dusme hasarini engeller.
 */
public class NoFall extends Module {
    public static String status = "Off";

    final ModeSetting mode = new ModeSetting("Mode", "Mod", 0,
            new String[]{"Packet", "Position"}, new String[]{"Packet", "Position"});

    private boolean sent = false;

    public NoFall() {
        super("NoFall", "NoFall", "No fall damage.", "Dusme hasari yok.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(mode));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.getNetworkHandler() == null) return;

        if (p.isOnGround()) { sent = false; return; }
        if (sent || p.fallDistance < 3.0) return;

        if (mode.value().equals("Packet")) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    p.getX(), p.getY(), p.getZ(), true, false));
        }
        sent = true;
    }
}