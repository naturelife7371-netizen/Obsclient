package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * ElytraFly - Elytra ucusu (vanilla/packet).
 */
public class ElytraFly extends Module {
    public static String status = "Off";

    final ModeSetting mode = new ModeSetting("Mode", "Mod", 0,
            new String[]{"Vanilla", "Packet"}, new String[]{"Vanilla", "Packet"});
    final NumSetting hSpeed = new NumSetting("HSpeed", "Yatay", 2.5, 0.5, 8.0, 0.1);
    final NumSetting vSpeed = new NumSetting("VSpeed", "Dikey", 1.5, 0.2, 5.0, 0.1);
    final BoolSetting autoStart = new BoolSetting("AutoStart", "Oto Baslat", true);
    final BoolSetting noFall    = new BoolSetting("NoFall", "Dusme Yok", true);
    final BoolSetting onlyElytra = new BoolSetting("OnlyElytra", "Elytra Sart", true);
    final BoolSetting boostUp   = new BoolSetting("JumpUp", "Zipla Yuksel", true);

    public ElytraFly() {
        super("ElytraFly", "ElytraFly", "Elytra flight.", "Elytra ucusu.", Category.MOVEMENT);
        settings.addAll(Arrays.asList(mode, hSpeed, vSpeed, autoStart, noFall, onlyElytra, boostUp));
    }

    @Override public String status() { return enabled ? mode.valueLabel() : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) return;
        if (onlyElytra.value && !p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            status = "No elytra";
            return;
        }
        if (!p.isGliding()) {
            if (autoStart.value && !p.isOnGround() && p.getVelocity().y < -0.05) {
                p.startGliding();
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
                status = "Started";
            } else {
                status = "Ready";
            }
            return;
        }

        double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double up = (boostUp.value && mc.options.jumpKey.isPressed()) ? 1 : 0;
        double down = mc.options.sneakKey.isPressed() ? 1 : 0;
        float yaw = (float)Math.toRadians(p.getYaw());

        double dx = (-Math.sin(yaw) * f + Math.cos(yaw) * s) * hSpeed.value;
        double dz = (Math.cos(yaw) * f + Math.sin(yaw) * s) * hSpeed.value;
        double dy = (up * vSpeed.value) - (down * vSpeed.value);

        if (mode.value().equals("Vanilla")) {
            Vec3d v = p.getVelocity();
            p.setVelocity(dx + v.x * 0.15, dy - 0.02 + v.y * 0.15, dz + v.z * 0.15);
        } else {
            p.setVelocity(dx, dy, dz);
        }
        if (noFall.value) p.fallDistance = 0;
        status = String.format("%.1f", Math.hypot(dx, dz));
    }
}