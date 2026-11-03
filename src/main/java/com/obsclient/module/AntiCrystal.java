package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.Notification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static com.obsclient.ObsClient.*;

/**
 * AntiCrystal — yakındaki tehdit kristallerini otomatik kırar.
 *
 * Mantık:
 *   1) Belirli bir menzildeki tüm EndCrystalEntity'leri topla.
 *   2) Hangi kristaller tehdit?
 *      - Çok yakınsa (closeDist) → tehdit
 *      - Patlama self damage selfLimit üstündeyse → tehdit
 *      - PreBreak menzilindeyse → tehdit
 *   3) Tehditleri en yakından uzağa sırala.
 *   4) Her birini tek tek kır (menzil içindekiler).
 *
 * ÖNEMLİ: AutoCrystal ile birlikte çalışır — kendi koyduğun kristalleri
 * de kırar! AutoCrystal açıkken kapatman önerilir.
 *
 * Ayarlar:
 *  - Range          : tarama menzili
 *  - SelfDamageLimit: bu hasardan yüksek → tehdit
 *  - CloseThreat    : bu mesafeden yakınsa → tehdit
 *  - PreBreakRange  : bu menzildeki tüm kristaller → tehdit
 *  - BreakCooldown  : kırma sonrası bekleme (tick)
 *  - BreakThreats   : false ise sadece uyarır, kırmaz
 *  - SilentRotate   : sessiz dönüş
 *  - Alert          : uyarı göster
 */
public class AntiCrystal extends Module {
    public static String status = "Off";

    final NumSetting range      = new NumSetting("Range", "Menzil", 8.0, 2.0, 12.0, 0.5);
    final NumSetting selfLimit  = new NumSetting("SelfDamageLimit", "Kendi Limit", 8, 1, 20, 0.5);
    final NumSetting closeDist  = new NumSetting("CloseThreat", "Yakın Tehdit", 2.5, 1.0, 5.0, 0.1);
    final NumSetting preBreak   = new NumSetting("PreBreakRange", "Ön Kırma", 6.0, 1.0, 10.0, 0.5);
    final NumSetting breakCd    = new NumSetting("BreakCooldown", "Kırma CD", 0, 0, 4, 1);

    final BoolSetting breakThreats = new BoolSetting("BreakThreats", "Tehdit Kır", true);
    final BoolSetting silent       = new BoolSetting("SilentRotate", "Sessiz Dönüş", true);
    final BoolSetting alert        = new BoolSetting("Alert", "Uyarı", true);

    private int cd = 0;
    private long lastAlert = 0;

    public AntiCrystal() {
        super("AntiCrystal", "AntiCrystal", "Breaks threat crystals.", "Tehdit kristallerini kırar.", Category.COMBAT);
        settings.addAll(Arrays.asList(range, selfLimit, closeDist, preBreak, breakCd,
                breakThreats, silent, alert));
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Guarding"; cd = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cd > 0) { cd--; return; }

        Vec3d eye = p.getEyePos();
        Vec3d me = epos(p);

        // 1) Menzildeki tüm kristalleri topla
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                p.getBoundingBox().expand(range.value),
                EndCrystalEntity::isAlive);
        if (crystals.isEmpty()) { status = "No threat"; return; }

        // 2) Tehdit olanları filtrele
        List<EndCrystalEntity> threats = new ArrayList<>();
        for (EndCrystalEntity c : crystals) {
            Vec3d cp = epos(c);
            double d = cp.distanceTo(me);
            double selfDmg = blast(mc, p, cp, 6f, 1.0);

            // Yakın mı? Self damage limit aşıldı mı? Pre-break menzilinde mi?
            boolean isThreat = d <= closeDist.value
                    || selfDmg >= selfLimit.value
                    || d <= preBreak.value;
            if (isThreat) threats.add(c);
        }
        if (threats.isEmpty()) { status = "Safe"; return; }

        // 3) Uyarı göster (rate-limit 1s)
        if (alert.value && System.currentTimeMillis() - lastAlert > 1000) {
            Notification.send("§cThreat! " + threats.size() + " crystals", 0xFFFF3030);
            lastAlert = System.currentTimeMillis();
        }

        // 4) Kırmayacaksak sadece bilgi
        if (!breakThreats.value) {
            status = "Alert x" + threats.size();
            return;
        }

        // 5) En yakından uzağa sırala
        threats.sort(Comparator.comparingDouble(c -> epos(c).distanceTo(me)));

        // 6) Kır
        for (EndCrystalEntity c : threats) {
            if (boxDist(eye, c.getBoundingBox()) > crystalReach()) continue;
            if (silent.value) silentLook(mc, p, epos(c).add(0, c.getHeight() / 2.0, 0));
            mc.interactionManager.attackEntity(p, c);
            p.swingHand(Hand.MAIN_HAND);
            Rot.postAttack();
            status = "Broke x" + threats.size();
            cd = breakCd.i();
            return; // bir tick'te bir kristal — anti-cheat dostu
        }

        status = "Out of reach";
    }
}