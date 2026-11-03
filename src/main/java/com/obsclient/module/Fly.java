package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.Arrays;

/**
 * ==========================================================
 *  Fly — "Phantom" Grim-Safe Flight (FIXED)
 * ==========================================================
 *  • Grim-Safe: Sunucuya sürekli "Yerdeyim" paketi (onGround) yollar.
 *  • Anti-Kick: Havada asılı kalma (Fly) flagini engeller.
 *  • Çökmeye sebep olan clampPacket silindi.
 */
public class Fly extends Module {
    public static String status = "Off";

    final ModeSetting mode = new ModeSetting("Mode", "Mod", 1,
            new String[]{"Grim", "Vanilla", "Glide"}, new String[]{"Grim", "Vanilla", "Glide"});

    final NumSetting speed = new NumSetting("Speed", "Hiz", 1.5, 0.1, 8.0, 0.1);
    final NumSetting verticalSpeed = new NumSetting("VertSpeed", "Dikey Hiz", 0.42, 0.1, 2.0, 0.01);

    final BoolSetting spoofGround = new BoolSetting("SpoofGround", "Yerde Gorun", true);
    final BoolSetting antiKick = new BoolSetting("AntiKick", "Kick Engelle", true);

    private int spoofTimer = 0;

    public Fly() {
        super("Fly", "Fly", "Grim-Safe flight.", "Grim-Guvenli ucus.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(mode, speed, verticalSpeed, spoofGround, antiKick));
    }

    @Override public String status() { return enabled ? "Flying (" + mode.value() + ")" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null || mc.currentScreen != null) return;

            // Yaratıcı moddaysak modüle gerek yok
            if (p.getAbilities().creativeMode || p.getAbilities().allowFlying) return;

            // ⚡ Client-side fly aktif (Düşmemek için)
            p.getAbilities().flying = true;
            p.fallDistance = 0;
            p.setVelocity(p.getVelocity().x, 0, p.getVelocity().z); // Yerçekimini sıfırla

            // Hareket hesaplaması
            double sp = speed.value;
            double vsp = verticalSpeed.value;

            double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
            double s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
            double yaw = Math.toRadians(p.getYaw());

            double dx = (-Math.sin(yaw) * f + Math.cos(yaw) * s) * sp;
            double dz = (Math.cos(yaw) * f + Math.sin(yaw) * s) * sp;
            double dy = 0;

            if (mc.options.jumpKey.isPressed()) dy = vsp;
            if (mc.options.sneakKey.isPressed()) dy = -vsp;

            // ⚡ Modlara göre özel davranış
            if (mode.value().equals("Glide")) {
                dy = -0.05; // Yavaşça süzül
                if (mc.options.jumpKey.isPressed()) dy = vsp;
            }

            p.setVelocity(dx, dy, dz);

            // ⚡ GRIM BYPASS: Sunucuya sürekli "Yerdeyim" sinyali gönder
            if (mode.value().equals("Grim")) {
                spoofTimer++;
                if (spoofGround.value) {
                    // Her 2 tick'te bir sahte pozisyon ve onGround=true paketi
                    if (spoofTimer % 2 == 0) {
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                                p.getX(), p.getY(), p.getZ(), true, false));
                    }
                }

                // Anti-Kick: 20 tick'te bir hafifçe aşağı çek (Grim'in havada asılı kalmayı fark etmemesi için)
                if (antiKick.value && spoofTimer >= 20) {
                    p.setVelocity(p.getVelocity().x, -0.1, p.getVelocity().z);
                    spoofTimer = 0;
                }
            }

            // ⚡ ServerPredictor.clampPacket(p) BURADAN SİLİNDİ.
            // Bu kod oyunun donmasına (rubberband) sebep oluyordu.

        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        ClientPlayerEntity p = mc.player;
        if (p != null && !p.getAbilities().creativeMode) {
            p.getAbilities().flying = false;
        }
    }
}