package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

/**
 * AutoPearl — hedefe doğru ender incisi fırlatır.
 *
 * Balistik simülasyon:
 *  - End incisi 1.5 blok/tick başlangıç hızıyla fırlar.
 *  - Her tick hız ×0.99, dikey −0.03 düşer.
 *  - Bu simülasyonla hedefe uygun pitch açısı hesaplanır.
 *
 * Kritik ayarlar:
 *  - MinDist      : hedef bu kadar yakınsa atma (kendine zarar verirsin)
 *  - MaxDist      : bu kadar uzaksa atma
 *  - Lead         : hedefin gelecek pozisyonunu tahmin etme katsayısı
 *  - ArcUp        : pitch açısını yukarı kaydırma (daha yüksek yay)
 *  - OnlyRunning  : sadece hedef hareket ederken fırlat
 *
 * Anti-cheat notu:
 *  - SilentRotate açık olması önerilir (client-side rotasyon)
 *  - Cooldown'u düşük tutmak flag riskini artırır (varsayılan 15 tick iyi)
 */
public class AutoPearl extends Module {
    public static String status = "Off";

    final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzili", 24, 5, 50, 1);
    final NumSetting minDist     = new NumSetting("MinDist", "Min Mes.", 4.0, 2.0, 10.0, 0.5);
    final NumSetting maxDist     = new NumSetting("MaxDist", "Max Mes.", 36.0, 10.0, 60.0, 1);
    final NumSetting lead        = new NumSetting("Lead", "Tahmin", 1.0, 0, 3.0, 0.1);
    final NumSetting cooldown    = new NumSetting("Cooldown", "Gecikme", 15, 3, 60, 1);
    final NumSetting arcUp       = new NumSetting("ArcUp", "Yay", 1.15, 0.5, 2.0, 0.05);

    final BoolSetting onlyRunning = new BoolSetting("OnlyRunning", "Sadece Kaçarken", false);
    final BoolSetting silent      = new BoolSetting("SilentRotate", "Sessiz Dönüş", true);

    private int cd = 0;

    public AutoPearl() {
        super("AutoPearl", "AutoPearl", "Auto ender pearl.", "Oto end incisi.", Category.COMBAT);
        settings.addAll(Arrays.asList(targetRange, minDist, maxDist, lead, cooldown, arcUp, onlyRunning, silent));
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Searching"; cd = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cd > 0) { cd--; return; }

        // 1) Hedef seç — en yakın
        PlayerEntity t = null;
        double bd = targetRange.value;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double d = p.distanceTo(o);
            if (d < bd) { bd = d; t = o; }
        }
        if (t == null) { status = "No target"; return; }

        // 2) Mesafe filtreleri
        if (bd < minDist.value || bd > maxDist.value) {
            status = bd < minDist.value ? "Close" : "Far";
            return;
        }

        // 3) Sadece hareketliyse fırlat
        if (onlyRunning.value) {
            Vec3d v = VEL.getOrDefault(t.getId(), Vec3d.ZERO);
            if (v.horizontalLength() < 0.15) { status = "Still"; return; }
        }

        // 4) Envanterde inci var mı?
        int slot = findHot(p, s -> s.isOf(Items.ENDER_PEARL));
        boolean off = p.getOffHandStack().isOf(Items.ENDER_PEARL);
        if (slot < 0 && !off) { status = "No pearl"; return; }

        // 5) Balistik hesapla
        Vec3d aim = computePearlAim(p, t);
        if (aim == null) { status = "No angle"; return; }

        // 6) Fırlat
        Hand hand = off ? Hand.OFF_HAND : Hand.MAIN_HAND;
        int prev = p.getInventory().getSelectedSlot();
        if (!off) p.getInventory().setSelectedSlot(slot);

        float[] a = calcAngle(p.getEyePos(), aim);
        if (silent.value) silentLook(mc, p, aim);
        else { p.setYaw(a[0]); p.setPitch(a[1]); }

        mc.interactionManager.interactItem(p, hand);
        p.swingHand(hand);

        if (!off) p.getInventory().setSelectedSlot(prev);

        cd = cooldown.i();
        status = String.format("Pearl %.1fm", bd);
    }

    /**
     * End incisi balistik simülasyonu.
     *
     * Yaklaşım:
     *  1) Başlangıç pitch açısını hedeften direkt hesapla.
     *  2) Bu açıyla bir simülasyon çalıştır.
     *  3) Hedefe olan mesafeyi minimize eden pitch'i bul (60 iterasyona kadar).
     *  4) Bulunan pitch'e arcUp faktörünü uygula.
     */
    private Vec3d computePearlAim(ClientPlayerEntity p, PlayerEntity t) {
        double spd = Math.max(0.5, p.getVelocity().length() + 0.3);
        double dist = p.distanceTo(t);
        double travelTicks = dist / (spd * 20.0);
        if (lead.value > 0) travelTicks *= lead.value;

        Vec3d tp = predicted(t, travelTicks);
        Vec3d from = p.getEyePos();
        double dx = tp.x - from.x, dz = tp.z - from.z, dy = tp.y - from.y;
        double horiz = Math.hypot(dx, dz);
        if (horiz < 0.01) return null;

        // Başlangıç tahmini: direkt atış
        double bestPitch = -Math.toDegrees(Math.atan2(dy, horiz));

        // İteratif iyileştirme
        for (int iter = 0; iter < 30; iter++) {
            double pitchRad = Math.toRadians(bestPitch);
            double speed = 1.5;
            double vX = (dx / horiz) * Math.cos(pitchRad) * speed;
            double vZ = (dz / horiz) * Math.cos(pitchRad) * speed;
            double vY = Math.sin(pitchRad) * speed;

            double simX = 0, simY = 0, simZ = 0;
            double err = Double.MAX_VALUE;

            // Balistik simülasyon (200 tick)
            for (int tick = 0; tick < 200; tick++) {
                simX += vX;
                simY += vY;
                simZ += vZ;
                vX *= 0.99;
                vY = vY * 0.99 - 0.03;
                vZ *= 0.99;

                double ex = simX - dx;
                double ey = simY - dy;
                double ez = simZ - dz;
                double d2 = ex * ex + ey * ey + ez * ez;
                if (d2 < err) err = d2;
                if (simY < dy - 6 && vY < 0) break; // yere düştü
            }

            if (err < 0.3) break;
            bestPitch += 1.0;
            if (bestPitch > 80) break;
        }

        bestPitch *= arcUp.value;
        bestPitch = MathHelper.clamp(bestPitch, -80, 80);

        // Aim vektörü: yatay yön + pitch yüksekliği
        double yawRad = Math.atan2(dz, dx);
        return new Vec3d(
                from.x + Math.cos(yawRad),
                from.y + Math.tan(Math.toRadians(bestPitch)),
                from.z + Math.sin(yawRad)
        );
    }
}