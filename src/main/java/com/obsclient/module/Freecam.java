package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;

@SuppressWarnings("unused")
public class Freecam extends Module {
    public static String status = "Off";
    public static Freecam INSTANCE;

    // ==================== SETTINGS ====================
    final NumSetting speed         = new NumSetting("Speed", "Yatay Hiz", 1.0, 0.1, 10.0, 0.1);
    final NumSetting verticalSpeed = new NumSetting("VertSpeed", "Dikey Hiz", 1.0, 0.1, 10.0, 0.1);
    final BoolSetting invertPitch  = new BoolSetting("InvertPitch", "Ters Pitch", false);
    final BoolSetting smoothCam    = new BoolSetting("Smooth", "Yumusak", true);
    final NumSetting smoothFactor  = new NumSetting("SmoothFactor", "Yumusaklik", 0.5, 0.1, 1.0, 0.05);

    // ==================== INTERNAL (CameraMixin bu değerleri okur) ====================
    public double posX, posY, posZ;
    public double prevX, prevY, prevZ;
    public float camYaw, camPitch;

    private double savedX, savedY, savedZ;
    private float savedYaw, savedPitch;
    private boolean savedInvisible = false;

    private double targetX, targetY, targetZ;

    public Freecam() {
        super("Freecam", "Freecam", "True Mixin Camera.", "Gercek Mixin Kamera.", Category.RENDER);
        settings.addAll(Arrays.asList(speed, verticalSpeed, invertPitch, smoothCam, smoothFactor));
        INSTANCE = this;
    }

    @Override public String status() {
        if (!enabled) return "Off";
        return String.format("%.0f %.0f %.0f", posX, posY, posZ);
    }

    @Override
    public void onEnable() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            toggle();
            return;
        }

        // 1. Oyuncunun mevcut durumunu kaydet
        savedX = p.getX();
        savedY = p.getY();
        savedZ = p.getZ();
        savedYaw = p.getYaw();
        savedPitch = p.getPitch();
        savedInvisible = p.isInvisible();

        // 2. Kamera başlangıç konumu (Göz hizası)
        posX = savedX;
        posY = savedY + p.getEyeHeight(p.getPose());
        posZ = savedZ;
        prevX = posX; prevY = posY; prevZ = posZ;

        camYaw = savedYaw;
        camPitch = savedPitch;

        targetX = posX;
        targetY = posY;
        targetZ = posZ;

        // 3. Oyuncuyu dondur ve görünmez yap (Sunucu seni görmesin)
        p.setVelocity(0, 0, 0);
        p.fallDistance = 0;
        p.setInvisible(true);
        p.noClip = true; // Hayalet modu

        status = "Flying";
    }

    @Override
    public void onDisable() {
        ClientPlayerEntity p = mc.player;
        if (p != null) {
            p.setYaw(savedYaw);
            p.setPitch(savedPitch);
            p.setInvisible(savedInvisible);
            p.noClip = false;
            p.setPosition(savedX, savedY, savedZ);
        }
        status = "Off";
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;

        // 1. Oyuncuyu her tick'te olduğu yerde tut (Sunucuya hareket paketi gitmesin)
        p.setVelocity(0, 0, 0);
        p.fallDistance = 0;
        p.setPosition(savedX, savedY, savedZ);

        // 2. Kamera Hareketi
        if (mc.currentScreen == null) {
            // Mouse ile dönüşü kameraya aktar
            camYaw = p.getYaw();
            camPitch = p.getPitch();
            if (invertPitch.value) camPitch = -camPitch;

            // Tuş kontrolleri
            double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
            double strafe  = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
            double up      = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

            double sp = speed.value;
            double vsp = verticalSpeed.value;

            if (mc.options.sprintKey.isPressed()) { sp *= 2.5; vsp *= 2.5; }

            // Vektör hesaplamaları
            float yawRad = (float) Math.toRadians(camYaw);
            float pitchRad = (float) Math.toRadians(camPitch);

            double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double lookY = -Math.sin(pitchRad);
            double lookZ =  Math.cos(yawRad) * Math.cos(pitchRad);

            double rightX = Math.cos(yawRad);
            double rightZ = Math.sin(yawRad);

            double moveX = lookX * forward * sp + rightX * strafe * sp;
            double moveY = lookY * forward * sp + up * vsp;
            double moveZ = lookZ * forward * sp + rightZ * strafe * sp;

            targetX += moveX;
            targetY += moveY;
            targetZ += moveZ;
        }

        // 3. Eski pozisyonu kaydet (Mixin için)
        prevX = posX;
        prevY = posY;
        prevZ = posZ;

        // 4. Yumuşak Geçiş (Smooth)
        if (smoothCam.value) {
            double f = smoothFactor.value;
            posX += (targetX - posX) * f;
            posY += (targetY - posY) * f;
            posZ += (targetZ - posZ) * f;
        } else {
            posX = targetX;
            posY = targetY;
            posZ = targetZ;
        }

        status = String.format("%.0f %.0f %.0f", posX, posY, posZ);
    }

    public boolean isActive() {
        return enabled && mc.player != null;
    }
}