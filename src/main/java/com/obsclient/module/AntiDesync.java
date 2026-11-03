package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

import java.util.Arrays;

/**
 * AntiDesync - slot ve pozisyon paketlerini periyodik olarak yollar.
 * Sunucu ile istemci arasindaki desync sorununu onler.
 */
public class AntiDesync extends Module {
    public static String status = "Off";

    final NumSetting slotEvery = new NumSetting("SlotSync", "Slot Sync", 8, 2, 40, 1);
    final NumSetting posEvery  = new NumSetting("PosSync", "Pos Sync", 15, 5, 100, 1);

    private int tick = 0;

    public AntiDesync() {
        super("Anti-Desync", "Anti-Desync", "Sync slot/pos.", "Slot/pos sync.", Category.PLAYER);
        settings.addAll(Arrays.asList(slotEvery, posEvery));
    }

    @Override public String status() { return enabled ? "Syncing" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.getNetworkHandler() == null) return;
        tick++;

        if (tick % slotEvery.i() == 0) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(p.getInventory().getSelectedSlot()));
        }
        if (tick % posEvery.i() == 0 && !p.isGliding()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    p.getX(), p.getY(), p.getZ(), p.isOnGround(), p.horizontalCollision));
        }
    }
}