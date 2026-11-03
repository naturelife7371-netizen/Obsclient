package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.Arrays;

import static com.obsclient.ObsClient.Notification;

/**
 * ==========================================================
 *  AntiVoid — "Anchor" Void Saver
 * ==========================================================
 *  • Oyuncu boşluğa düşerken otomatik olarak yukarı ışınlar.
 *  • GrimAC'nin "Invalid Move" (Imkansiz Hareket) flagini yememesi için
 *    sunucuya anında güncel pozisyon paketi gönderir.
 */
public class AntiVoid extends Module {
    public static String status = "Off";

    final NumSetting threshold = new NumSetting("Threshold", "Dusme Siniri", -55.0, -100.0, 0.0, 1.0);
    final NumSetting teleportY = new NumSetting("TeleportY", "Isinlanma Y", 0.0, 0.0, 256.0, 1.0);
    final BoolSetting onlyFalling = new BoolSetting("OnlyFalling", "Sadece Dusarken", true);

    private int cooldown = 0;

    public AntiVoid() {
        super("AntiVoid", "AntiVoid", "Saves you from falling into the void.", "Void'e dusmekten kurtarir.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(threshold, teleportY, onlyFalling));
    }

    @Override public String status() { return enabled ? status : "Off"; }
    @Override public void onEnable() { status = "Watching"; cooldown = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null || mc.getNetworkHandler() == null) return;
            if (p.isSpectator() || p.getAbilities().creativeMode) return;

            if (cooldown > 0) { cooldown--; return; }

            // Belirlenen Y sınırının altına düştü mü?
            if (p.getY() < threshold.value) {
                // Sadece düşerken mi kontrol edilsin?
                if (onlyFalling.value && p.getVelocity().y >= 0) return;

                // Güvenli Y seviyesini belirle
                double safeY = Math.max(teleportY.value, threshold.value + 10);

                // Konumu değiştir
                p.setPosition(p.getX(), safeY, p.getZ());
                p.setVelocity(0, 0, 0);
                p.fallDistance = 0;

                // Sunucuya anında pozisyon paketi gönder (Teleport bypass)
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        p.getX(), safeY, p.getZ(), false, false));

                status = "§aKurtarildi!";
                Notification.send("§aAntiVoid: Boşluktan kurtarıldın!", 0xFF3FD07A);
                cooldown = 40; // 2 saniye bekle
            } else {
                status = String.format("%.0f Y", p.getY());
            }
        } catch (Throwable ignored) {}
    }
}