package com.obsclient.module;

import com.obsclient.ObsClient;
import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

public class PlayerESP extends Module {
    public static String status = "Off";

    // ============ AYARLAR ============
    final NumSetting maxDist = new NumSetting("MaxDist", "Max Mesafe", 300, 20, 500, 10);
    final NumSetting fadeDist = new NumSetting("FadeDist", "Solma Mesafesi", 20, 5, 100, 5);

    final ModeSetting renderMode = new ModeSetting("Mode", "Mod", 0,
            new String[]{"2D Box", "3D Wireframe", "Skeleton"},
            new String[]{"2D Kutu", "3D Tel Kafes", "İskelet"});

    final ModeSetting colorMode = new ModeSetting("ColorMode", "Renk Modu", 0,
            new String[]{"Custom", "Health", "Distance"},
            new String[]{"Özel", "Sağlık", "Mesafe"});

    final ModeSetting customColor = new ModeSetting("Color", "Özel Renk", 0,
            new String[]{"Purple", "Red", "Blue", "Green", "White", "Cyan", "Yellow", "Rainbow"},
            new String[]{"Mor", "Kırmızı", "Mavi", "Yeşil", "Beyaz", "Camgöbeği", "Sarı", "Gökkuşağı"});

    final BoolSetting fill      = new BoolSetting("Fill", "İçini Doldur", true);
    final NumSetting fillAlpha  = new NumSetting("FillAlpha", "Dolgu Şeffaflığı", 0.2, 0.05, 1.0, 0.05);

    final BoolSetting hpBar     = new BoolSetting("HPBar", "Can Barı", true);
    final BoolSetting nameShow  = new BoolSetting("ShowName", "İsim Göster", true);
    final BoolSetting itemShow  = new BoolSetting("ShowItems", "Eşyaları Göster", true);
    final BoolSetting targetHit = new BoolSetting("TargetHit", "Nişan Alınanı Vurgula", true);

    public PlayerESP() {
        super("PlayerESP", "PlayerESP", "Wallhack ve gelişmiş oyuncu gösterimi.", "Wallhack ve gelişmiş oyuncu gösterimi.", Category.RENDER);
        settings.addAll(Arrays.asList(
                maxDist, fadeDist, renderMode, colorMode, customColor,
                fill, fillAlpha, hpBar, nameShow, itemShow, targetHit
        ));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }
    @Override public void onTick(MinecraftClient mc) { /* Render modülü */ }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (mc.player == null || mc.world == null) return;
        var tr = mc.textRenderer;

        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == mc.player || !o.isAlive() || o.isSpectator()) continue;

            double dist = mc.player.distanceTo(o);
            if (dist > maxDist.value) continue;

            // İnterpolasyonlu pozisyon (Kasmayı önler)
            Vec3d lp = o.getLerpedPos(delta);
            Box b = o.getBoundingBox().offset(lp.subtract(ObsClient.epos(o)));

            // ============ RENK HESABI ============
            int baseCol = getColor(o, dist);
            double fade = 1.0;
            if (dist > fadeDist.value) {
                fade = 1.0 - ((dist - fadeDist.value) / (maxDist.value - fadeDist.value));
                if (fade < 0) fade = 0;
            }
            int finalCol = ObsClient.withAlpha(baseCol, (int) (255 * fade));

            // Nişan alınan oyuncu mu?
            boolean isTarget = targetHit.value && mc.targetedEntity == o;
            if (isTarget) finalCol = 0xFFFF5555; // Hedefi kırmızı yap

            // ============ RENDER ============
            if (renderMode.index == 0) { // 2D Box
                int[] r = ObsClient.projectBox(mc, b, delta);
                if (r != null) {
                    if (fill.value) {
                        int fillColor = ObsClient.withAlpha(finalCol, (int) (255 * fillAlpha.value * fade));
                        g.fill(r[0], r[1], r[2], r[3], fillColor);
                    }
                    ObsClient.outline(g, r[0], r[1], r[2], r[3], finalCol);
                    drawInfo(g, mc, tr, r, o, dist, finalCol, fade);
                }
            }
            else if (renderMode.index == 1) { // 3D Wireframe
                draw3DWireframe(g, mc, b, delta, finalCol);
                if (fill.value) {
                    int[] r = ObsClient.projectBox(mc, b, delta);
                    if (r != null) {
                        int fillColor = ObsClient.withAlpha(finalCol, (int) (255 * fillAlpha.value * fade));
                        g.fill(r[0], r[1], r[2], r[3], fillColor);
                    }
                }
                int[] r = ObsClient.projectBox(mc, b, delta);
                if (r != null) drawInfo(g, mc, tr, r, o, dist, finalCol, fade);
            }
            else if (renderMode.index == 2) { // Skeleton (İskelet)
                drawSkeleton(g, mc, o, b, delta, finalCol);
                int[] r = ObsClient.projectBox(mc, b, delta);
                if (r != null) drawInfo(g, mc, tr, r, o, dist, finalCol, fade);
            }
        }
    }

    // ============ YARDIMCI METOTLAR ============

    private void drawInfo(DrawContext g, MinecraftClient mc, net.minecraft.client.font.TextRenderer tr, int[] r, PlayerEntity o, double dist, int col, double fade) {
        // Can Barı
        if (hpBar.value) {
            double hp = o.getHealth() + o.getAbsorptionAmount();
            int bh = r[3] - r[1];
            int fh = (int)(bh * Math.min(1.0, hp / 20.0));
            g.fill(r[0] - 4, r[1] - 1, r[0] - 1, r[3] + 1, 0xC0000000);
            g.fill(r[0] - 3, r[3] - fh, r[0] - 2, r[3], ObsClient.hpColor(hp));
        }

        // İsim ve Mesafe
        if (nameShow.value) {
            String nm = o.getName().getString() + String.format(" §7[%.1fm]", dist);
            int nw = tr.getWidth(nm);
            g.fill((r[0] + r[2]) / 2 - nw / 2 - 2, r[1] - 12, (r[0] + r[2]) / 2 + nw / 2 + 2, r[1] - 1, 0xB0000000);
            g.drawText(tr, nm, (r[0] + r[2]) / 2 - nw / 2, r[1] - 10, col, true);
        }

        // Eşyalar (El ve Offhand)
        if (itemShow.value) {
            ItemStack main = o.getMainHandStack();
            if (!main.isEmpty()) {
                g.drawItem(main, r[0] - 20, r[1]);
                g.drawStackOverlay(tr, main, r[0] - 20, r[1]);
            }
            ItemStack off = o.getOffHandStack();
            if (!off.isEmpty()) {
                g.drawItem(off, r[2] + 4, r[1]);
                g.drawStackOverlay(tr, off, r[2] + 4, r[1]);
            }
        }
    }

    private void draw3DWireframe(DrawContext g, MinecraftClient mc, Box box, float delta, int color) {
        Vec3d[] c = {
                new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ), new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ), new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ), new Vec3d(box.minX, box.maxY, box.maxZ),
        };
        int[][] s = new int[8][];
        for (int i = 0; i < 8; i++) {
            s[i] = ObsClient.project(mc, c[i], delta);
            if (s[i] == null) return;
        }
        // Alt
        ObsClient.line(g, s[0][0], s[0][1], s[1][0], s[1][1], color);
        ObsClient.line(g, s[1][0], s[1][1], s[2][0], s[2][1], color);
        ObsClient.line(g, s[2][0], s[2][1], s[3][0], s[3][1], color);
        ObsClient.line(g, s[3][0], s[3][1], s[0][0], s[0][1], color);
        // Üst
        ObsClient.line(g, s[4][0], s[4][1], s[5][0], s[5][1], color);
        ObsClient.line(g, s[5][0], s[5][1], s[6][0], s[6][1], color);
        ObsClient.line(g, s[6][0], s[6][1], s[7][0], s[7][1], color);
        ObsClient.line(g, s[7][0], s[7][1], s[4][0], s[4][1], color);
        // Dikey
        ObsClient.line(g, s[0][0], s[0][1], s[4][0], s[4][1], color);
        ObsClient.line(g, s[1][0], s[1][1], s[5][0], s[5][1], color);
        ObsClient.line(g, s[2][0], s[2][1], s[6][0], s[6][1], color);
        ObsClient.line(g, s[3][0], s[3][1], s[7][0], s[7][1], color);
    }

    private void drawSkeleton(DrawContext g, MinecraftClient mc, PlayerEntity o, Box b, float delta, int col) {
        Vec3d lp = o.getLerpedPos(delta);
        double h = o.getHeight();

        Vec3d head = lp.add(0, h - 0.2, 0);
        Vec3d neck = lp.add(0, h - 0.5, 0);
        Vec3d pelvis = lp.add(0, h * 0.4, 0);
        Vec3d legL = lp.add(0.15, 0, 0);
        Vec3d legR = lp.add(-0.15, 0, 0);
        Vec3d armL = lp.add(0.35, h * 0.75, 0);
        Vec3d armR = lp.add(-0.35, h * 0.75, 0);

        int[] hP = ObsClient.project(mc, head, delta);
        int[] nP = ObsClient.project(mc, neck, delta);
        int[] pP = ObsClient.project(mc, pelvis, delta);
        int[] lLP = ObsClient.project(mc, legL, delta);
        int[] lRP = ObsClient.project(mc, legR, delta);
        int[] aLP = ObsClient.project(mc, armL, delta);
        int[] aRP = ObsClient.project(mc, armR, delta);

        if (hP != null && nP != null) ObsClient.line(g, hP[0], hP[1], nP[0], nP[1], col);
        if (nP != null && pP != null) ObsClient.line(g, nP[0], nP[1], pP[0], pP[1], col);
        if (pP != null && lLP != null) ObsClient.line(g, pP[0], pP[1], lLP[0], lLP[1], col);
        if (pP != null && lRP != null) ObsClient.line(g, pP[0], pP[1], lRP[0], lRP[1], col);
        if (nP != null && aLP != null) ObsClient.line(g, nP[0], nP[1], aLP[0], aLP[1], col);
        if (nP != null && aRP != null) ObsClient.line(g, nP[0], nP[1], aRP[0], aRP[1], col);

        // Eklem noktaları
        if (hP != null) g.fill(hP[0]-1, hP[1]-1, hP[0]+1, hP[1]+1, 0xFFFFFFFF);
        if (pP != null) g.fill(pP[0]-1, pP[1]-1, pP[0]+1, pP[1]+1, 0xFFFFFFFF);
    }

    private int getColor(PlayerEntity o, double dist) {
        if (colorMode.index == 1) { // Health
            float hp = o.getHealth() + o.getAbsorptionAmount();
            return ObsClient.lerpColor(0xFFFF3333, 0xFF33FF33, hp / 20.0);
        }
        else if (colorMode.index == 2) { // Distance
            return ObsClient.lerpColor(0xFF33FF33, 0xFFFF3333, Math.min(1.0, dist / maxDist.value));
        }
        else { // Custom
            switch (customColor.index) {
                case 1: return 0xFFFF3333; // Red
                case 2: return 0xFF3366FF; // Blue
                case 3: return 0xFF33FF66; // Green
                case 4: return 0xFFFFFFFF; // White
                case 5: return 0xFF00FFFF; // Cyan
                case 6: return 0xFFFFFF33; // Yellow
                case 7: return 0xFF000000 | MathHelper.hsvToRgb((System.currentTimeMillis() % 3000L) / 3000f, 0.85f, 1f); // Rainbow
                default: return 0xFFB65CFF; // Purple
            }
        }
    }
}