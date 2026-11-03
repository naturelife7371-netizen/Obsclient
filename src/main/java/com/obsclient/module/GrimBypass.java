package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Notification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Random;

/**
 * ==========================================================
 *  GrimBypass — Kick-Safe Anticheat Bypass + Hareket Koruması
 * ==========================================================
 *  • Modül (GUI'den aç/kapat)
 *  • Toggle Key ile anında aç/kapat (varsayılan: RIGHT_SHIFT)
 *  • Hareket ederken paket göndermez (client çakışması önlenir)
 *  • Sadece rotation request varsa paket gönderir
 *  • Silent rotation — diğer modüller için
 *  • Kick-safe paket rate limiting
 */
public class GrimBypass extends Module {
    public static String status = "Off";
    public static GrimBypass INSTANCE;

    // ==================== SETTINGS ====================
    final ModeSetting mode = new ModeSetting("Mode", "Mod", 1,
            new String[]{"Vanilla", "Grim", "Matrix", "Intave", "Verus", "Vulcan", "NCP"},
            new String[]{"Vanilla", "Grim", "Matrix", "Intave", "Verus", "Vulcan", "NCP"});

    final BoolSetting enableSilentRot = new BoolSetting("SilentRotation", "Sessiz Donus", true);
    final BoolSetting freezeOnMove = new BoolSetting("FreezeOnMove", "Hareket Ederken Dur", true);
    final BoolSetting showNotification = new BoolSetting("Notify", "Bildirim", true);

    final NumSetting maxPacketsPerSecond = new NumSetting("MaxPackets/Sec", "Max Paket/Sn", 20, 5, 40, 1);
    final NumSetting maxYawStep = new NumSetting("MaxYawStep", "Max Yaw Adim", 22, 5, 45, 1);
    final NumSetting maxPitchStep = new NumSetting("MaxPitchStep", "Max Pitch Adim", 18, 5, 45, 1);
    final NumSetting postAttackHold = new NumSetting("PostAttackHold", "Vurus Sonrasi Bekle", 4, 0, 10, 1);

    // ==================== INTERNAL ====================
    private static final Random RNG = new Random();
    private long lastPacketTime = 0;
    private int packetsThisSecond = 0;
    private long secondStart = 0;
    private int tickCounter = 0;

    // Rotation state
    private float lastSentYaw = 0, lastSentPitch = 0;
    private float clientYaw = 0, clientPitch = 0;
    private boolean rotInitialized = false;

    // Request queue (diğer modüllerden)
    private static float requestedYaw = 0, requestedPitch = 0;
    private static boolean requestPending = false;
    private static int requestHold = 0;

    // Toggle key durumu
    private boolean toggleKeyWas = false;

    // GCD
    private double rollingGCD = 0.0;
    private int gcdSamples = 0;

    public GrimBypass() {
        super("GrimBypass", "GrimBypass",
                "Kick-safe anticheat bypass engine.",
                "Kick-safe anticheat bypass motoru.", Category.MISC);
        settings.addAll(Arrays.asList(
                mode,
                enableSilentRot, freezeOnMove, showNotification,
                maxPacketsPerSecond, maxYawStep, maxPitchStep, postAttackHold
        ));
        INSTANCE = this;
    }

    @Override public String status() {
        if (!enabled) return "Off";
        return MODE() + " | " + packetsThisSecond + " pps";
    }

    private String MODE() {
        return mode.value();
    }

    @Override
    public void onEnable() {
        status = "Active";
        packetsThisSecond = 0;
        secondStart = System.currentTimeMillis();
        lastPacketTime = 0;
        tickCounter = 0;
        requestPending = false;
        requestHold = 0;
        rotInitialized = false;
        if (showNotification.value) {
            Notification.send("§aGrimBypass aktif — " + MODE(), 0xFF3FD07A);
        }
    }

    @Override
    public void onDisable() {
        status = "Off";
        requestPending = false;
        requestHold = 0;
        if (showNotification.value) {
            Notification.send("§cGrimBypass kapali", 0xFFFF5C5C);
        }
    }

    // ==========================================================
    // PUBLIC API — diğer modüller için
    // ==========================================================
    public static void requestRotation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        requestedYaw = yaw;
        requestedPitch = MathHelper.clamp(pitch, -90f, 90f);
        requestPending = true;
    }

    public static void postAttackHold() {
        if (INSTANCE != null) {
            requestHold = INSTANCE.postAttackHold.i();
        }
    }

    // ==========================================================
    // ANA TICK
    // ==========================================================
    @Override
    public void onTick(MinecraftClient mc) {
        try {
            if (mc == null || mc.player == null || mc.world == null) return;
            if (mc.getNetworkHandler() == null) return;

            ClientPlayerEntity p = mc.player;
            if (p.isRemoved() || !p.isAlive()) return;
            if (mc.currentScreen != null) return;

            // ==================================================
            // TOGGLE KEY KONTROLÜ — Right Shift ile aç/kapat
            // ==================================================
            long handle = mc.getWindow().getHandle();
            boolean toggleKeyDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (toggleKeyDown && !toggleKeyWas) {
                toggle();
                toggleKeyWas = toggleKeyDown;
                return;
            }
            toggleKeyWas = toggleKeyDown;

            // ==================================================
            // TIMER
            // ==================================================
            tickCounter++;
            if (secondStart == 0) secondStart = System.currentTimeMillis();
            if (System.currentTimeMillis() - secondStart >= 1000) {
                packetsThisSecond = 0;
                secondStart = System.currentTimeMillis();
            }

            // ==================================================
            // ⚡ HAREKET KONTROLÜ — hareket ederken paket gönderme
            // ==================================================
            boolean moving = isMoving(mc, p);
            if (freezeOnMove.value && moving && !requestPending) {
                // Hareket ediyorsa ve rotation request yoksa → paket gönderme
                // Vanilla client zaten hareket paketlerini gönderiyor
                status = "Moving (paused)";
                return;
            }

            // ==================================================
            // CLIENT ROTATION KAYDET
            // ==================================================
            clientYaw = p.getYaw();
            clientPitch = p.getPitch();

            if (!rotInitialized) {
                lastSentYaw = clientYaw;
                lastSentPitch = clientPitch;
                rotInitialized = true;
            }

            // ==================================================
            // GCD HESABI (rolling average)
            // ==================================================
            double sens = 0.5;
            if (mc.options != null) sens = mc.options.getMouseSensitivity().getValue();
            double f = sens * 0.6 + 0.2;
            double rawGcd = f * f * f * 1.2;
            if (gcdSamples == 0) rollingGCD = rawGcd;
            else rollingGCD = rollingGCD * 0.966 + rawGcd * 0.034;
            gcdSamples++;
            if (gcdSamples > 60) gcdSamples = 60;

            // ==================================================
            // HEDEF ROTATION
            // ==================================================
            float targetYaw = clientYaw;
            float targetPitch = clientPitch;
            boolean didRequest = false;

            if (requestPending && requestHold <= 0 && enableSilentRot.value) {
                targetYaw = requestedYaw;
                targetPitch = requestedPitch;
                didRequest = true;
                requestPending = false;
            }
            if (requestHold > 0) requestHold--;

            // ==================================================
            // KICK-SAFE STEP
            // ==================================================
            float dy = MathHelper.wrapDegrees(targetYaw - lastSentYaw);
            float dp = targetPitch - lastSentPitch;

            // Mod bazlı step limitleri
            float maxY, maxP;
            switch (MODE()) {
                case "Grim" -> { maxY = (float) maxYawStep.value; maxP = (float) maxPitchStep.value; }
                case "Matrix" -> { maxY = 28f; maxP = 22f; }
                case "Intave" -> { maxY = 26f; maxP = 18f; }
                case "Verus" -> { maxY = 28f; maxP = 20f; }
                case "Vulcan" -> { maxY = 24f; maxP = 20f; }
                case "NCP" -> { maxY = 35f; maxP = 28f; }
                default -> { maxY = 180f; maxP = 90f; }
            }
            maxY = Math.min(maxY, 24f);  // Hard cap — kick-safe
            maxP = Math.min(maxP, 18f);

            if (Math.abs(dy) > maxY) dy = Math.signum(dy) * maxY;
            if (Math.abs(dp) > maxP) dp = Math.signum(dp) * maxP;

            // Grim jitter (küçük)
            if ("Grim".equals(MODE())) {
                dy += (RNG.nextFloat() - 0.5f) * 0.05f;
                dp += (RNG.nextFloat() - 0.5f) * 0.03f;
            }

            float newYaw = MathHelper.wrapDegrees(lastSentYaw + dy);
            float newPitch = MathHelper.clamp(lastSentPitch + dp, -90f, 90f);

            if (!Float.isFinite(newYaw) || !Float.isFinite(newPitch)) return;

            // GCD uygula
            if (rollingGCD > 1e-6) {
                newYaw = (float) (Math.round(newYaw / rollingGCD) * rollingGCD);
                newPitch = (float) (Math.round(newPitch / rollingGCD) * rollingGCD);
            }

            // ==================================================
            // PAKET GÖNDERİM
            // ==================================================
            boolean rotChanged = Math.abs(dy) > 0.001f || Math.abs(dp) > 0.001f;
            boolean shouldSend = didRequest || rotChanged;

            // Kick-safe rate limit
            if (packetsThisSecond >= maxPacketsPerSecond.i()) {
                if (!didRequest) {
                    p.setYaw(clientYaw);
                    p.setPitch(clientPitch);
                }
                status = "Rate limited";
                return;
            }

            if (shouldSend) {
                long minInterval = 1000L / Math.max(1, maxPacketsPerSecond.i());
                long now = System.currentTimeMillis();
                if (now - lastPacketTime >= minInterval) {
                    lastPacketTime = now;
                    packetsThisSecond++;

                    float sendYaw = MathHelper.wrapDegrees(newYaw);
                    float sendPitch = MathHelper.clamp(newPitch, -90f, 90f);

                    if (Float.isFinite(sendYaw) && Float.isFinite(sendPitch)) {
                        try {
                            // ⚡ SADECE LOOK paketi — pozisyon paketi GÖNDERMİYORUZ
                            // (pozisyon paketi hareket çakışmasına sebep oluyordu)
                            mc.getNetworkHandler().sendPacket(
                                    new PlayerMoveC2SPacket.LookAndOnGround(
                                            sendYaw, sendPitch,
                                            p.isOnGround(), p.horizontalCollision));
                            lastSentYaw = sendYaw;
                            lastSentPitch = sendPitch;
                        } catch (Throwable ignored) {}
                    }
                }
            }

            // Silent rotation restore
            if (!didRequest) {
                p.setYaw(clientYaw);
                p.setPitch(clientPitch);
            }

            status = MODE() + " | " + packetsThisSecond + " pps";
        } catch (Throwable ignored) {}
    }

    // ==========================================================
    // HAREKET KONTROLÜ
    // ==========================================================
    private boolean isMoving(MinecraftClient mc, ClientPlayerEntity p) {
        try {
            // Tuş kontrolü
            if (mc.options.forwardKey.isPressed()) return true;
            if (mc.options.backKey.isPressed()) return true;
            if (mc.options.leftKey.isPressed()) return true;
            if (mc.options.rightKey.isPressed()) return true;
            if (mc.options.jumpKey.isPressed()) return true;
            if (mc.options.sneakKey.isPressed()) return true;

            // Velocity kontrolü
            Vec3d vel = p.getVelocity();
            double hLen = Math.hypot(vel.x, vel.z);
            return hLen > 0.02;
        } catch (Throwable ignored) { return false; }
    }
}