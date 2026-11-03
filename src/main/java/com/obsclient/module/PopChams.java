package com.obsclient.module;

import com.obsclient.ObsClient;
import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Category;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class PopChams extends Module {
    public static String status = "Off";
    public static PopChams INSTANCE;

    // ============ TIMING ============
    final NumSetting duration = new NumSetting("Duration", "Süre", 2.0, 0.5, 10.0, 0.5);

    // ============ RENK (13 seçenek) ============
    final ModeSetting color = new ModeSetting(
            "Color", "Renk", 0,
            new String[]{ "Red", "White", "Black", "Mint", "Purple", "Blue", "Orange",
                    "Pink", "Yellow", "Cyan", "Lime", "Gold", "Rainbow" },
            new String[]{ "Kırmızı", "Beyaz", "Siyah", "Mint", "Mor", "Mavi", "Turuncu",
                    "Pembe", "Sarı", "Camgöbeği", "Açık Yeşil", "Altın", "Gökkuşağı" }
    );

    // ============ RENDER ============
    final BoolSetting fill          = new BoolSetting("Fill", "İç Doldur", true);
    final NumSetting fillAlpha      = new NumSetting("FillAlpha", "Dolgu Şeffaflığı", 0.25, 0.05, 1.0, 0.05);
    final BoolSetting names         = new BoolSetting("Name", "İsim", true);
    final BoolSetting nameBg        = new BoolSetting("NameBg", "İsim Arka Planı", true);
    final BoolSetting self          = new BoolSetting("Self", "Kendin", false);
    final BoolSetting showTimer     = new BoolSetting("Timer", "Sayaç", false);

    final BoolSetting deathLightning = new BoolSetting("DeathLightning", "Olunce Simsek Caksin", true);

    // ============ ANİMASYON ============
    final NumSetting yModifier      = new NumSetting("YModifier", "Y Kayması", 0.75, -4.0, 4.0, 0.05);
    final NumSetting scaleModifier  = new NumSetting("ScaleMod", "Ölçek Değişimi", -0.25, -4.0, 4.0, 0.05);
    final BoolSetting fadeOut       = new BoolSetting("FadeOut", "Solarak Kaybol", true);

    // ============ STATE ============
    private static final List<Ghost> ghosts = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Long>  lastPop      = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> lastHealth   = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> lastAbsorp   = new ConcurrentHashMap<>();
    private static final AtomicInteger detected = new AtomicInteger(0);

    private boolean wasDead = false;
    private int lightningTimer = 0;

    public PopChams() {
        super("PopChams", "PopChams",
                "Totem pop ghost.", "Totem patlama hayaleti.",
                Category.RENDER);
        settings.addAll(Arrays.asList(
                duration, color,
                fill, fillAlpha, names, nameBg, self, showTimer, deathLightning,
                yModifier, scaleModifier, fadeOut
        ));
        INSTANCE = this;
    }

    @Override
    public String status() {
        return enabled ? (detected.get() > 0 ? "Pops " + detected.get() : "Active") : "Off";
    }

    @Override
    public void onEnable() {
        status = "Active";
        detected.set(0);
        ghosts.clear();
        lastPop.clear();
        lastHealth.clear();
        lastAbsorp.clear();
        wasDead = false;
        lightningTimer = 0;
    }

    @Override
    public void onDisable() {
        status = "Off";
        ghosts.clear();
        lastPop.clear();
        lastHealth.clear();
        lastAbsorp.clear();
        wasDead = false;
        lightningTimer = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;

        // -------- YENİ: Ölünce Sessiz Şimşek Efekti --------
        if (deathLightning.value) {
            boolean isDead = mc.player.getHealth() <= 0 || mc.player.isDead();

            if (isDead && !wasDead) {
                wasDead = true;
                lightningTimer = 20; // 1 saniye boyunca çiz
            } else if (!isDead && wasDead) {
                wasDead = false;
            }
        }

        if (lightningTimer > 0) lightningTimer--;

        // -------- DÜZELTİLMİŞ TOTEM TESPİTİ --------
        long now = System.currentTimeMillis();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == null) continue;
            UUID id = p.getUuid();
            float hp  = p.getHealth();
            float abs = p.getAbsorptionAmount();

            Float prevHp  = lastHealth.get(id);
            Float prevAbs = lastAbsorp.get(id);

            if (prevHp != null && prevAbs != null) {
                // Totem patlaması için: Emici can (Absorption) fırlamalı ve Hasar alıyor olmalı (hurtTime > 0)
                // Veya canı 0'ın altına düşüp 1'e fırlamalı
                boolean totemPop = (abs > prevAbs + 2.0f && p.hurtTime > 0) || (prevHp > 0 && hp <= 0);

                if (totemPop) {
                    if (p != mc.player || self.value) addGhost(p);
                }
            }
            lastHealth.put(id, hp);
            lastAbsorp.put(id, abs);
        }

        // -------- Eski ghost'ları temizle --------
        long life = (long) (duration.value * 1000.0);
        ghosts.removeIf(g -> (now - g.time) > life);
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (mc.player == null || mc.world == null) return;

        int W = mc.getWindow().getScaledWidth();
        int H = mc.getWindow().getScaledHeight();

        // Sessiz Şimşek Efekti
        if (lightningTimer > 0) {
            // Ekran beyazlaması
            float alpha = (lightningTimer / 20f) * 150f;
            g.fill(0, 0, W, H, ObsClient.withAlpha(0xFFFFFF, (int) alpha));

            // DÜZELTME: getPos() yerine getX/Y/Z kullanıldı
            Vec3d pPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3d skyPos = pPos.add(0, 15, 0);

            int[] pScreen = ObsClient.project(mc, pPos, delta);
            int[] sScreen = ObsClient.project(mc, skyPos, delta);

            if (pScreen != null && sScreen != null) {
                Random r = new Random();
                int midX = (pScreen[0] + sScreen[0]) / 2 + r.nextInt(20) - 10;
                int midY = (pScreen[1] + sScreen[1]) / 2;

                // Zigzag çizgiler
                ObsClient.line(g, sScreen[0], sScreen[1], midX, midY, 0xFFFFFFFF);
                ObsClient.line(g, midX, midY, pScreen[0], pScreen[1], 0xFFFFFFFF);
                ObsClient.line(g, midX + 10, midY, pScreen[0], pScreen[1], 0xFFFFFFFF); // Ekstra dal
            }
        }

        if (ghosts.isEmpty()) return;

        long now  = System.currentTimeMillis();
        double life = duration.value * 1000.0;
        int baseColor = currentColor();

        for (Ghost gh : ghosts) {
            long elapsed = now - gh.time;
            double ageSec = elapsed / 1000.0;
            double age    = elapsed / life;
            if (age >= 1.0) continue;

            double fade = fadeOut.value ? (1.0 - age) : 1.0;
            double yShift = yModifier.value * ageSec;
            double currentScale = Math.max(0.1, 1.0 + scaleModifier.value * ageSec);

            Box baseBox   = gh.box.offset(0, yShift, 0);

            int lineCol = ObsClient.withAlpha(baseColor, (int) (255 * fade));
            int fillCol = ObsClient.withAlpha(baseColor, (int) (255 * fade * fillAlpha.value));

            // İSKELET ÇİZİMİ (Kafa, Gövde, Kollar, Bacaklar)
            drawSkeleton(g, mc, baseBox, currentScale, delta, lineCol, fillCol);

            // İsim Çizimi
            int[] headScreen = ObsClient.project(mc, baseBox.getCenter().add(0, 0.5 * currentScale, 0), delta);
            if (headScreen == null) continue;

            int cx = headScreen[0];
            int cy = headScreen[1] - 30; // Kafanın biraz üstü

            if (names.value) {
                String label = gh.name + " POP";
                int w = mc.textRenderer.getWidth(label);
                if (nameBg.value) {
                    g.fill(cx - w / 2 - 2, cy - 1, cx + w / 2 + 2, cy + 9, 0xB0000000);
                }
                g.drawText(mc.textRenderer, label, cx - w / 2, cy, ObsClient.withAlpha(0xFFFFFF, (int) (255 * fade)), true);
            }

            // Sayaç Çizimi
            if (showTimer.value) {
                String timer = String.format(Locale.ROOT, "%.1fs", (life - elapsed) / 1000.0);
                int tw = mc.textRenderer.getWidth(timer);
                int ty = cy + 10;
                if (nameBg.value) {
                    g.fill(cx - tw / 2 - 1, ty - 1, cx + tw / 2 + 1, ty + 8, 0x80000000);
                }
                g.drawText(mc.textRenderer, timer, cx - tw / 2, ty, ObsClient.withAlpha(baseColor, (int) (200 * fade)), true);
            }
        }
    }

    // Oyuncu İskeleti Çizimi
    private void drawSkeleton(DrawContext g, MinecraftClient mc, Box box, double scale, float delta, int color, int fillColor) {
        double cx = box.getCenter().x;
        double cz = box.getCenter().z;
        double y = box.minY;

        // Ölçekleme (Küçülüp büyüme efekti için)
        double w = 0.25 * scale;  // Genişlik
        double d = 0.125 * scale; // Derinlik
        double h = scale;         // Yükseklik çarpanı

        // Kafa (1.4 - 1.8)
        Box head = new Box(cx-w, y+1.4*h, cz-w, cx+w, y+1.8*h, cz+w);
        drawBoxPart(g, mc, head, delta, color, fillColor);

        // Gövde (0.75 - 1.4)
        Box body = new Box(cx-w, y+0.75*h, cz-d, cx+w, y+1.4*h, cz+d);
        drawBoxPart(g, mc, body, delta, color, fillColor);

        // Sağ Kol (0.75 - 1.4)
        Box rArm = new Box(cx-(w*2), y+0.75*h, cz-d, cx-w, y+1.4*h, cz+d);
        drawBoxPart(g, mc, rArm, delta, color, fillColor);

        // Sol Kol (0.75 - 1.4)
        Box lArm = new Box(cx+w, y+0.75*h, cz-d, cx+(w*2), y+1.4*h, cz+d);
        drawBoxPart(g, mc, lArm, delta, color, fillColor);

        // Sağ Bacak (0 - 0.75)
        Box rLeg = new Box(cx-w, y, cz-d, cx, y+0.75*h, cz+d);
        drawBoxPart(g, mc, rLeg, delta, color, fillColor);

        // Sol Bacak (0 - 0.75)
        Box lLeg = new Box(cx, y, cz-d, cx+w, y+0.75*h, cz+d);
        drawBoxPart(g, mc, lLeg, delta, color, fillColor);
    }

    private void drawBoxPart(DrawContext g, MinecraftClient mc, Box box, float delta, int color, int fillColor) {
        int[] r = ObsClient.projectBox(mc, box, delta);
        if (r != null) {
            if (fillColor != 0) {
                g.fill(r[0], r[1], r[2], r[3], fillColor);
            }
            draw3DWireframe(g, mc, box, delta, color);
        }
    }

    private static void draw3DWireframe(DrawContext g, MinecraftClient mc, Box box, float delta, int color) {
        Vec3d[] corners = {
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
        };
        int[][] s = new int[8][];
        for (int i = 0; i < 8; i++) {
            s[i] = ObsClient.project(mc, corners[i], delta);
            if (s[i] == null) return;
        }
        // Alt yüz
        ObsClient.line(g, s[0][0], s[0][1], s[1][0], s[1][1], color);
        ObsClient.line(g, s[1][0], s[1][1], s[2][0], s[2][1], color);
        ObsClient.line(g, s[2][0], s[2][1], s[3][0], s[3][1], color);
        ObsClient.line(g, s[3][0], s[3][1], s[0][0], s[0][1], color);
        // Üst yüz
        ObsClient.line(g, s[4][0], s[4][1], s[5][0], s[5][1], color);
        ObsClient.line(g, s[5][0], s[5][1], s[6][0], s[6][1], color);
        ObsClient.line(g, s[6][0], s[6][1], s[7][0], s[7][1], color);
        ObsClient.line(g, s[7][0], s[7][1], s[4][0], s[4][1], color);
        // Dikey kenarlar
        ObsClient.line(g, s[0][0], s[0][1], s[4][0], s[4][1], color);
        ObsClient.line(g, s[1][0], s[1][1], s[5][0], s[5][1], color);
        ObsClient.line(g, s[2][0], s[2][1], s[6][0], s[6][1], color);
        ObsClient.line(g, s[3][0], s[3][1], s[7][0], s[7][1], color);
    }

    private int currentColor() {
        switch (color.value()) {
            case "Red":     return 0xFFFF3344;
            case "White":   return 0xFFFFFFFF;
            case "Black":   return 0xFF222222;
            case "Mint":    return 0xFF7FD6A5;
            case "Purple":  return 0xFFB65CFF;
            case "Blue":    return 0xFF4FA3FF;
            case "Orange":  return 0xFFFFA23F;
            case "Pink":    return 0xFFFF6FC8;
            case "Yellow":  return 0xFFFFFF44;
            case "Cyan":    return 0xFF44FFFF;
            case "Lime":    return 0xFF44FF44;
            case "Gold":    return 0xFFFFD700;
            case "Rainbow":
                float hue = (System.currentTimeMillis() % 3600L) / 3600f;
                return 0xFF000000 | MathHelper.hsvToRgb(hue, 0.85f, 1f);
            default: return 0xFFFF3344;
        }
    }

    public static void onPop(PlayerEntity p) {
        if (INSTANCE == null || !INSTANCE.enabled || p == null) return;
        if (p == MinecraftClient.getInstance().player && !INSTANCE.self.value) return;
        addGhost(p);
    }

    private static void addGhost(PlayerEntity p) {
        try {
            long now = System.currentTimeMillis();
            Long lp = lastPop.get(p.getUuid());
            if (lp != null && now - lp < 800) return;
            lastPop.put(p.getUuid(), now);
            detected.incrementAndGet();
            ghosts.add(new Ghost(p.getBoundingBox(), p.getName().getString(), now));
        } catch (Throwable ignored) {}
    }

    private static class Ghost {
        final Box box;
        final String name;
        final long time;
        Ghost(Box box, String name, long time) {
            this.box = box;
            this.name = name;
            this.time = time;
        }
    }
}