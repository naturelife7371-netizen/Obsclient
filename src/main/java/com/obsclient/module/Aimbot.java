package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

/**
 * Aimbot — FOV içindeki en yakın hedefe yumuşak rotasyon.
 *
 * KillAura'dan farkı:
 *  - VURUŞ YAPMAZ — sadece bakış açını hedefe doğru kaydırır
 *  - Yumuşak (smooth) interpolasyon
 *  - "OnlyAttacking" ayarı: sadece sol tuşa basılıyken çalışsın
 *  - FOV filtresi: sadece görüş açın içindeki hedefler
 *
 * Kullanım senaryoları:
 *  - KillAura ile birlikte: KillAura vurur, Aimbot nişan alır (daha doğal)
 *  - Sniper okçulukta
 *  - Silah değiştirirken hazır olmak için
 *
 * Anti-cheat notu:
 *  - Grim'in GCD (Greatest Common Divisor) rotasyon kontrolü için
 *    speed ≤ 30 önerilir. Çok hızlı aim flag yer.
 */
public class Aimbot extends Module {
    public static String status = "Off";

    final NumSetting range    = new NumSetting("Range", "Menzil", 8.0, 2.0, 20.0, 0.5);
    final NumSetting fov      = new NumSetting("FOV", "FOV", 180, 10, 360, 5);
    final NumSetting speed    = new NumSetting("Speed", "Hız", 25, 3, 30, 1);
    final NumSetting smooth   = new NumSetting("Smooth", "Yumuşak", 0.7, 0.1, 1.0, 0.05);

    final BoolSetting onlyAtk   = new BoolSetting("OnlyAttacking", "Sadece Vururken", false);
    final BoolSetting wall      = new BoolSetting("WallCheck", "Duvar", false);
    final BoolSetting usePred   = new BoolSetting("Predict", "Predict", true);
    final BoolSetting vertical  = new BoolSetting("Vertical", "Dikey", true);

    public Aimbot() {
        super("Aimbot", "Aimbot", "Smooth aim assist.", "Yumuşak nişan yardımı.", Category.COMBAT);
        settings.addAll(Arrays.asList(range, fov, speed, smooth, onlyAtk, wall, usePred, vertical));
    }

    @Override public String status() {
        if (!enabled) return "Off";
        return String.format("FOV %d | Speed %.0f", fov.i(), speed.value);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) return;

        // Only attacking ise, sol tuşa basılı değilse çalışma
        if (onlyAtk.value && !mc.options.attackKey.isPressed()) {
            status = "Idle";
            return;
        }

        Vec3d eye = p.getEyePos();

        // ========== En iyi hedefi bul ==========
        PlayerEntity best = null;
        Vec3d bestAim = null;
        double bestAngle = fov.value / 2.0;

        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;

            // Hedefin tahmini veya mevcut pozisyonu
            Vec3d base = (usePred.value && predOn()) ? predicted(o, predTicks()) : epos(o);
            Vec3d aim = base.add(0, o.getHeight() * 0.85, 0);   // göğüs hizası

            // Menzil kontrolü
            if (eye.distanceTo(aim) > range.value) continue;

            // Duvar kontrolü
            if (wall.value) {
                BlockHitResult r = mc.world.raycast(new RaycastContext(eye, aim,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
                if (r.getType() != HitResult.Type.MISS) continue;
            }

            // Açı hesabı — mevcut bakışına en yakın hedef
            double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
            float ty = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float tp = (float)-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            double angle = Math.hypot(MathHelper.wrapDegrees(ty - p.getYaw()), tp - p.getPitch());

            if (angle < bestAngle) {
                bestAngle = angle;
                best = o;
                bestAim = aim;
            }
        }

        if (best == null) { status = "No target"; return; }
        status = "Target: " + best.getName().getString();

        // ========== Rotasyon ==========
        double dx = bestAim.x - eye.x, dy = bestAim.y - eye.y, dz = bestAim.z - eye.z;
        float targetYaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float)-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));

        float maxStep = speed.f();
        float sm = smooth.f();

        // Yumuşak geçiş: hedef açı ile mevcut açı arasındaki fark × smooth, max speed kadar
        float stepYaw = MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - p.getYaw()) * sm, -maxStep, maxStep);
        float stepPitch = MathHelper.clamp((targetPitch - p.getPitch()) * sm, -maxStep, maxStep);

        p.setYaw(gcd(p.getYaw() + stepYaw));
        if (vertical.value) {
            p.setPitch(gcd(MathHelper.clamp(p.getPitch() + stepPitch, -90f, 90f)));
        }
    }
}