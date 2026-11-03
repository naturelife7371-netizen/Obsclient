package com.obsclient.module;

import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Notification;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.obsclient.ObsClient.MODULES;
import static com.obsclient.ObsClient.lerpColor;
import static com.obsclient.ObsClient.withAlpha;

@SuppressWarnings("unused")
public class HUD extends Module {
    public static String status = "Off";

    private static final String[] COLORS = { "Rainbow", "Metal", "Fire", "Ocean", "Purple", "Gold", "Category", "Static", "Black", "Dark", "White", "Astro", "Sunset" };
    private static final String[] CASES  = { "Normal", "UPPER", "lower" };
    private static final String[] STYLES = { "Normal", "Bold", "Italic", "Bold+Italic", "Underline", "Strikethrough" };

    private static final int[] METAL  = { 0xFFF5F7FA, 0xFFB4BBC4, 0xFF7A828C, 0xFFB4BBC4 };
    private static final int[] FIRE   = { 0xFFFFE259, 0xFFFF8A00, 0xFFFF2D2D, 0xFFFF8A00 };
    private static final int[] OCEAN  = { 0xFF4FC3FF, 0xFF5CFFB0, 0xFF3A7BFF, 0xFF5CFFB0 };
    private static final int[] PURPLE = { 0xFFB65CFF, 0xFF4FC3FF, 0xFFFF5CD6, 0xFF4FC3FF };
    private static final int[] GOLD   = { 0xFFFFF1A8, 0xFFFFC233, 0xFFB8860B, 0xFFFFC233 };
    private static final int[] ASTRO  = { 0xFF8A2BE2, 0xFF00FFFF, 0xFF8A2BE2 };
    private static final int[] SUNSET = { 0xFFFF7E5F, 0xFFFEB47B, 0xFFFF7E5F };

    private static final int GRAY = 0xFF9A9AB0;

    // ---- genel ----
    final BoolSetting simplified    = new BoolSetting("Simplified", "Sadelestirilmis", false);
    final BoolSetting watermark     = new BoolSetting("Watermark", "Filigran", true);
    final BoolSetting arraylist     = new BoolSetting("ArrayList", "Liste", true);
    final BoolSetting coords        = new BoolSetting("Coords", "Koordinat", true);
    final BoolSetting notifications = new BoolSetting("Notifications", "Bildirimler", true);
    final BoolSetting fps           = new BoolSetting("FPS", "FPS", true);
    final BoolSetting ping          = new BoolSetting("Ping", "Ping", true);
    final BoolSetting totems        = new BoolSetting("Totems", "Totem Sayaci", true); // YENI: TOTEM SAYACI
    final BoolSetting bottomText    = new BoolSetting("Bottom Text", "Alt Yazi", true);
    final BoolSetting animations    = new BoolSetting("Animations", "Animasyon", true);

    // ---- YENI ANIMASYON VE EFEKTLER ----
    final BoolSetting glowEffect    = new BoolSetting("GlowEffect", "Parlama Efekti", true);
    final BoolSetting pulseWatermark= new BoolSetting("PulseWatermark", "Filigran Nabzi", true);
    final BoolSetting listGradient  = new BoolSetting("ListGradientBg", "Liste Gradyan Arkaplan", true);
    final BoolSetting listOutline   = new BoolSetting("ListOutline", "Liste Cercevesi", true);

    // ---- yazilar ----
    final TextSetting wmText     = new TextSetting("Watermark Text", "Filigran Yazisi", "ObsClient");
    final TextSetting bottomName = new TextSetting("Bottom Text Value", "Alt Yazi Metni", "ObsClient");

    // ---- renkler ----
    final ModeSetting wmColor     = new ModeSetting("Watermark Color", 0, COLORS);
    final ModeSetting listColor   = new ModeSetting("List Color", 0, COLORS);
    final ModeSetting bottomColor = new ModeSetting("Bottom Color", 1, COLORS);
    final NumSetting hue          = new NumSetting("Static Hue", "Sabit Renk", 280, 0, 360, 1);
    final NumSetting sat          = new NumSetting("Static Saturation", "Doygunluk", 70, 0, 100, 1);
    final NumSetting speed        = new NumSetting("Color Speed", "Renk Hizi", 1.0, 0.1, 5.0, 0.1);
    final BoolSetting perLetter   = new BoolSetting("Per Letter", "Harf Harf (Akan Renk)", true);

    // ---- yazi stili ----
    final ModeSetting textStyle = new ModeSetting("Text Style", 0, STYLES);
    final ModeSetting textCase  = new ModeSetting("Text Case", 0, CASES);
    final BoolSetting shadow    = new BoolSetting("Shadow", "Golge", true);

    // ---- liste ve arka plan ----
    final BoolSetting showStatus = new BoolSetting("Module Status", "Modul Durumu", true);
    final BoolSetting wmBox     = new BoolSetting("Watermark Box", "Filigran Kutu", true);
    final BoolSetting waveAnim  = new BoolSetting("Wave Anim", "Dalga Animasyonu", false);

    // ---- SADELESTIRILMIS MOD ----
    final NumSetting simpScale  = new NumSetting("SimpScale", "Sade Yazı Boyutu", 1.0, 0.5, 2.0, 0.1);
    final NumSetting simpWmX    = new NumSetting("SimpWmX", "Sade Filigran X", 4.0, -1000, 2000, 1);
    final NumSetting simpWmY    = new NumSetting("SimpWmY", "Sade Filigran Y", 4.0, -1000, 2000, 1);
    final NumSetting simpListX  = new NumSetting("SimpListX", "Sade Liste X", 400.0, -1000, 2000, 1);
    final NumSetting simpListY  = new NumSetting("SimpListY", "Sade Liste Y", 4.0, -1000, 2000, 1);
    final NumSetting simpInfoX  = new NumSetting("SimpInfoX", "Sade Info X", 400.0, -1000, 2000, 1);
    final NumSetting simpInfoY  = new NumSetting("SimpInfoY", "Sade Info Y", 200.0, -1000, 2000, 1);
    final NumSetting simpCoordX = new NumSetting("SimpCoordX", "Sade Koord X", 4.0, -1000, 2000, 1);
    final NumSetting simpCoordY = new NumSetting("SimpCoordY", "Sade Koord Y", 200.0, -1000, 2000, 1);

    private final Map<Module, Float> anim = new HashMap<>();
    private long lastNs = 0;

    private String dragging = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    public HUD() {
        super("HUD", "HUD", "Advanced HUD.", "Gelismis HUD.", Category.RENDER);
        settings.addAll(Arrays.asList(
                simplified,
                watermark, arraylist, coords, notifications, fps, ping, totems, bottomText, animations,
                glowEffect, pulseWatermark, listGradient, listOutline,
                wmText, bottomName,
                wmColor, listColor, bottomColor, hue, sat, speed, perLetter,
                textStyle, textCase, shadow,
                showStatus, wmBox, waveAnim,
                simpScale, simpWmX, simpWmY, simpListX, simpListY, simpInfoX, simpInfoY, simpCoordX, simpCoordY));
        enabled = true;
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }
    @Override public void onTick(MinecraftClient mc) {}

    /* ==================== renk ==================== */
    private static int hsb(double h, double s, double b) {
        return java.awt.Color.HSBtoRGB((float) h, (float) s, (float) b) | 0xFF000000;
    }

    private static int palette(int[] p, double phase) {
        double ph = ((phase % 1.0) + 1.0) % 1.0 * p.length;
        int i = (int) Math.floor(ph);
        double f = ph - i;
        return lerpColor(p[i % p.length], p[(i + 1) % p.length], f);
    }

    private int color(int mode, double phase, Category cat) {
        switch (mode) {
            case 0: return hsb(((phase % 1.0) + 1.0) % 1.0, 0.65, 1.0);
            case 1: return palette(METAL, phase);
            case 2: return palette(FIRE, phase);
            case 3: return palette(OCEAN, phase);
            case 4: return palette(PURPLE, phase);
            case 5: return palette(GOLD, phase);
            case 6: return cat != null ? cat.color : 0xFFFFFFFF;
            case 7: return hsb(hue.value / 360.0, sat.value / 100.0, 1.0);
            case 8: return 0xFF111111;
            case 9: return 0xFF2A2A2A;
            case 10: return 0xFFFFFFFF;
            case 11: return palette(ASTRO, phase);
            case 12: return palette(SUNSET, phase);
            default: return hsb(hue.value / 360.0, sat.value / 100.0, 1.0);
        }
    }

    private double timePhase() {
        return System.currentTimeMillis() / 1000.0 * speed.value * 0.25;
    }

    /* ==================== yazi ==================== */
    private String fmt(String s) {
        return switch (textCase.index) {
            case 1 -> s.toUpperCase();
            case 2 -> s.toLowerCase();
            default -> s;
        };
    }

    private Style style() {
        return switch (textStyle.index) {
            case 1 -> Style.EMPTY.withBold(true);
            case 2 -> Style.EMPTY.withItalic(true);
            case 3 -> Style.EMPTY.withBold(true).withItalic(true);
            case 4 -> Style.EMPTY.withUnderline(true);
            case 5 -> Style.EMPTY.withStrikethrough(true);
            default -> Style.EMPTY;
        };
    }

    private Text txt(String s) { return Text.literal(s).setStyle(style()); }
    private int width(TextRenderer tr, String s) { return tr.getWidth(txt(s)); }

    // Gelişmiş Çizim Metodu (Glow Destekli)
    private int drawStyled(DrawContext g, TextRenderer tr, String raw, int x, int y,
                           int mode, double phase, Category cat, boolean letters, int alpha) {
        String s = fmt(raw);
        if (alpha < 8) return width(tr, s);

        if (glowEffect.value) {
            g.drawText(tr, txt(s), x + 1, y + 1, withAlpha(color(mode, phase, cat), alpha / 3), shadow.value);
            g.drawText(tr, txt(s), x - 1, y - 1, withAlpha(color(mode, phase, cat), alpha / 3), shadow.value);
        }

        if (!letters) {
            g.drawText(tr, txt(s), x, y, withAlpha(color(mode, phase, cat), alpha), shadow.value);
            return width(tr, s);
        }

        int cx = x;
        int cy = y;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int yOffset = 0;
            if (waveAnim.value) {
                yOffset = (int) (Math.sin((System.currentTimeMillis() / 200.0) + i * 0.5) * 2);
            }
            g.drawText(tr, txt(ch), cx, cy + yOffset, withAlpha(color(mode, phase - i * 0.03, cat), alpha), shadow.value);
            cx += width(tr, ch);
        }
        return cx - x;
    }

    private String suffix(Module m) {
        if (!showStatus.value) return "";
        try {
            String s = m.status();
            if (s == null) return "";
            s = s.replaceAll("§.", "").trim();
            if (s.isEmpty() || s.equals("Active") || s.equals("Off") || s.equals("Waiting")
                    || s.equals("Idle") || s.equals("Searching")) return "";
            return fmt(s);
        } catch (Throwable t) { return ""; }
    }

    private static double ease(double a) {
        double inv = 1.0 - Math.max(0.0, Math.min(1.0, a));
        return 1.0 - inv * inv * inv;
    }

    // ==================== YENI: TOTEM SAYACI METODU ====================
    private int getTotemCount(ClientPlayerEntity p) {
        if (p == null) return 0;
        int count = 0;
        // Envanterin tamamını (Hotbar + Ana Envanter + Zırh + Offhand) tarar
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack stack = p.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /* ==================== render ==================== */
    public void render(DrawContext g, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        TextRenderer tr = mc.textRenderer;
        int W = mc.getWindow().getScaledWidth();
        int H = mc.getWindow().getScaledHeight();
        double t = timePhase();
        boolean letters = perLetter.value;

        long nowNs = System.nanoTime();
        double dt = lastNs == 0 ? 0.016 : Math.min(0.1, (nowNs - lastNs) / 1.0E9);
        lastNs = nowNs;

        // ==========================================
        // ⚡ SADELEŞTİRİLMİŞ MOD
        // ==========================================
        if (simplified.value) {
            float scale = (float) simpScale.value;

            if (mc.currentScreen instanceof com.obsclient.ObsClient.ObsGui) {
                double mx = mc.mouse.getX() * (double) W / mc.getWindow().getWidth();
                double my = mc.mouse.getY() * (double) H / mc.getWindow().getHeight();
                boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

                if (down && dragging == null) {
                    if (watermark.value && mx >= simpWmX.value && mx <= simpWmX.value + width(tr, wmText.value) * scale && my >= simpWmY.value && my <= simpWmY.value + 10 * scale) {
                        dragging = "WM"; dragOffsetX = mx - simpWmX.value; dragOffsetY = my - simpWmY.value;
                    } else if (arraylist.value && mx >= simpListX.value - 100 && mx <= simpListX.value + 100 && my >= simpListY.value - 10 && my <= simpListY.value + 200) {
                        dragging = "LIST"; dragOffsetX = mx - simpListX.value; dragOffsetY = my - simpListY.value;
                    } else if ((fps.value || ping.value || totems.value) && mx >= simpInfoX.value && mx <= simpInfoX.value + 100 && my >= simpInfoY.value && my <= simpInfoY.value + 20) {
                        dragging = "INFO"; dragOffsetX = mx - simpInfoX.value; dragOffsetY = my - simpInfoY.value;
                    } else if (coords.value && mx >= simpCoordX.value && mx <= simpCoordX.value + 150 && my >= simpCoordY.value && my <= simpCoordY.value + 20) {
                        dragging = "COORD"; dragOffsetX = mx - simpCoordX.value; dragOffsetY = my - simpCoordY.value;
                    }
                } else if (!down) {
                    dragging = null;
                }

                if (dragging != null) {
                    double nx = mx - dragOffsetX;
                    double ny = my - dragOffsetY;
                    if (dragging.equals("WM")) { simpWmX.value = nx; simpWmY.value = ny; }
                    else if (dragging.equals("LIST")) { simpListX.value = nx; simpListY.value = ny; }
                    else if (dragging.equals("INFO")) { simpInfoX.value = nx; simpInfoY.value = ny; }
                    else if (dragging.equals("COORD")) { simpCoordX.value = nx; simpCoordY.value = ny; }
                }
            }

            g.getMatrices().pushMatrix();
            g.getMatrices().scale(scale, scale);

            int sx = (int) (simpWmX.value / scale);
            int sy = (int) (simpWmY.value / scale);

            if (watermark.value) drawStyled(g, tr, wmText.value, sx, sy, wmColor.index, t, null, letters, 255);

            if (arraylist.value) {
                List<Module> on = new ArrayList<>();
                for (Module m : MODULES) if (m.enabled && m != this) on.add(m);
                on.sort((a, b) -> Integer.compare(tr.getWidth(b.label()), tr.getWidth(a.label())));
                int lx = (int) (simpListX.value / scale);
                int ly = (int) (simpListY.value / scale);
                for (Module m : on) {
                    String txt = m.label();
                    int w = tr.getWidth(txt);
                    drawStyled(g, tr, txt, lx - w, ly, listColor.index, t - ly * 0.01, m.cat, letters, 255);
                    ly += 10;
                }
            }

            if (fps.value || ping.value || totems.value) {
                int ix = (int) (simpInfoX.value / scale);
                int iy = (int) (simpInfoY.value / scale);
                String info = "";
                if (fps.value) info += mc.getCurrentFps() + " fps";
                if (ping.value && mc.getNetworkHandler() != null) {
                    PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
                    if (e != null && e.getLatency() >= 0) {
                        if (!info.isEmpty()) info += " | ";
                        info += e.getLatency() + " ms";
                    }
                }
                if (totems.value) {
                    int tCount = getTotemCount(p);
                    if (tCount > 0) {
                        if (!info.isEmpty()) info += " | ";
                        info += tCount + " Totem";
                    }
                }
                if (!info.isEmpty()) {
                    g.drawText(tr, info, ix, iy, 0xFFFFFFFF, shadow.value);
                }
            }

            if (coords.value) {
                int cx = (int) (simpCoordX.value / scale);
                int cy = (int) (simpCoordY.value / scale);
                String pos = String.format("XYZ: %.0f, %.0f, %.0f", p.getX(), p.getY(), p.getZ());
                g.drawText(tr, pos, cx, cy, 0xFFFFFFFF, shadow.value);
            }

            g.getMatrices().popMatrix();
            return;
        }

        // ==========================================
        // NORMAL MOD (Gelişmiş)
        // ==========================================
        if (watermark.value) {
            String name = fmt(wmText.value.isEmpty() ? "ObsClient" : wmText.value);
            StringBuilder info = new StringBuilder();
            if (fps.value) info.append("  |  ").append(mc.getCurrentFps()).append(" fps");
            if (ping.value && mc.getNetworkHandler() != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
                if (e != null && e.getLatency() >= 0) info.append("  |  ").append(e.getLatency()).append(" ms");
            }
            if (totems.value) {
                int tCount = getTotemCount(p);
                if (tCount > 0) info.append("  |  ").append(tCount).append(" Totem");
            }
            String inf = info.toString();
            int nameW = width(tr, name);
            int w = nameW + tr.getWidth(inf) + 12;

            float pulse = pulseWatermark.value ? (float) (1.0 + Math.sin(System.currentTimeMillis() / 200.0) * 0.05) : 1.0f;

            g.getMatrices().pushMatrix();
            g.getMatrices().translate(4, 4);
            g.getMatrices().scale(pulse, pulse);

            if (wmBox.value) {
                g.fill(-4, -4, w, 14, 0xD00A0A0F);
                for (int xx = 0; xx < w; xx += 2) {
                    g.fill(-4 + xx, -4, Math.min(-4 + w, -2 + xx), -2, color(wmColor.index, t - xx * 0.004, null));
                }
                g.fill(-4, 13, w, 14, withAlpha(0xFFFFFF, 18));
            }

            int used = drawStyled(g, tr, name, 6, 0, wmColor.index, t, null, letters, 255);
            if (!inf.isEmpty()) g.drawText(tr, inf, 6 + used, 0, GRAY, shadow.value);

            g.getMatrices().popMatrix();
        }

        if (coords.value) {
            int cy = H - 12 - (mc.currentScreen instanceof ChatScreen ? 14 : 0);
            int x = 6;
            g.drawText(tr, "XYZ", x, cy, GRAY, shadow.value);
            x += tr.getWidth("XYZ ");
            String pos = String.format("%.0f, %.0f, %.0f", p.getX(), p.getY(), p.getZ());
            g.drawText(tr, pos, x, cy, 0xFFF0F0FA, shadow.value);
            x += tr.getWidth(pos);

            String alt = "";
            if (p.getEntityWorld().getRegistryKey() == World.NETHER) {
                alt = String.format(" (OW: %.0f, %.0f, %.0f)", p.getX() * 8, p.getY(), p.getZ() * 8);
            } else if (p.getEntityWorld().getRegistryKey() == World.OVERWORLD) {
                alt = String.format(" (N: %.0f, %.0f, %.0f)", p.getX() / 8, p.getY(), p.getZ() / 8);
            }
            g.drawText(tr, alt, x, cy, GRAY, shadow.value);
            x += tr.getWidth(alt);

            String dir = p.getHorizontalFacing().asString();
            g.drawText(tr, " [" + dir + "]", x, cy, GRAY, shadow.value);
        }

        if (arraylist.value) {
            List<Module> on = new ArrayList<>();
            for (Module m : MODULES) if (m.enabled && m != this) on.add(m);
            on.sort((a, b) -> Integer.compare(
                    tr.getWidth(fmt(b.label()) + suffix(b)),
                    tr.getWidth(fmt(a.label()) + suffix(a))));

            int rightX = W - 4;
            int y = 4;

            for (Module m : on) {
                float target = 1f;
                float current = anim.getOrDefault(m, 0f);
                if (animations.value) {
                    current += (target - current) * (float) (dt * 12);
                    if (current > 0.99f) current = 1f;
                } else {
                    current = 1f;
                }
                anim.put(m, current);

                String label = fmt(m.label());
                String sfx = suffix(m);
                int wLabel = width(tr, label);
                int wSfx = width(tr, sfx);
                int totalW = wLabel + (sfx.isEmpty() ? 0 : wSfx + 4);

                int x = rightX - (int) (totalW * ease(current));
                int alpha = (int) (255 * current);

                if (listGradient.value) {
                    g.fillGradient(x - 4, y - 1, rightX + 2, y + 10, withAlpha(color(listColor.index, t - y * 0.01, m.cat), (int)(100 * current)), withAlpha(0x000000, (int)(160 * current)));
                } else {
                    g.fill(x - 4, y - 1, rightX + 2, y + 10, withAlpha(0x000000, (int)(140 * current)));
                }

                if (listOutline.value) {
                    g.fill(x - 4, y - 1, x - 3, y + 10, withAlpha(color(listColor.index, t - y * 0.01, m.cat), alpha));
                }

                drawStyled(g, tr, label, x, y, listColor.index, t - y * 0.01, m.cat, letters, alpha);

                if (!sfx.isEmpty()) {
                    g.drawText(tr, sfx, x + wLabel + 4, y, withAlpha(GRAY, alpha), shadow.value);
                }
                y += 11;
            }
            anim.keySet().removeIf(m -> !m.enabled);
        }

        if (bottomText.value) {
            String name = fmt(bottomName.value.isEmpty() ? "ObsClient" : bottomName.value);
            int w = width(tr, name);
            int x = W / 2 - w / 2;
            int y = H - 14;
            drawStyled(g, tr, name, x, y, bottomColor.index, t, null, letters, 255);
        }

        if (notifications.value && !Notification.LIST.isEmpty()) {
            int y = H - 40;
            long now = System.currentTimeMillis();
            for (Notification.Note n : Notification.LIST) {
                long age = now - n.time;
                if (age > 3000) continue;
                float life = 1.0f - (age / 3000f);
                float easeLife = (float) ease(life * 2);

                int w = tr.getWidth(n.msg);
                int xOffset = (int) ((1f - easeLife) * 50);
                int x = W - w - 10 + xOffset;
                int yDraw = y;
                int alpha = (int) (255 * Math.min(1f, life * 3));

                g.drawText(tr, n.msg, x, yDraw, withAlpha(n.color, alpha), shadow.value);
                g.fill(x, yDraw + 10, x + (int)(w * life), yDraw + 11, withAlpha(n.color, alpha));

                y -= 14;
            }
        }
    }
}