package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class PvPAspect extends Module {
    public static String status = "Off";
    public static PvPAspect INSTANCE;

    private static final int MAX_VANILLA_FOV = 110;
    private static final double AURA_RANGE_SQ = 64.0 * 64.0;

    // ===================== KAMERA & FOV =====================
    public final BoolSetting maxFov = new BoolSetting("MaxFOV", "Maksimum FOV", true);
    public final NumSetting fovValue = new NumSetting("FovValue", "FOV Degeri", 110.0, 30.0, 110.0, 1.0);
    public final BoolSetting noHurtCam = new BoolSetting("NoHurtCam", "Hasar Sarsintisini Kapat", true);

    // ===================== VIEWMODEL (EL POZISYONU) =====================
    public final BoolSetting oldAnimations = new BoolSetting("OldAnimations", "1.12 Animasyonlari", true);
    public final BoolSetting shrinkItems = new BoolSetting("ShrinkItems", "Esyalari Kucult", true);
    public final NumSetting itemScale = new NumSetting("ItemScale", "Esya Boyutu", 0.8, 0.5, 1.0, 0.05);
    public final NumSetting itemX = new NumSetting("ItemX", "El X", 0.0, -1.0, 1.0, 0.05);
    public final NumSetting itemY = new NumSetting("ItemY", "El Y", 0.0, -1.0, 1.0, 0.05);
    public final NumSetting itemZ = new NumSetting("ItemZ", "El Z", 0.0, -1.0, 1.0, 0.05);

    // ===================== EL AURASI (3D GLOW) =====================
    public final BoolSetting viewmodelAura = new BoolSetting("ViewModelAura", "El Aura (Glow)", true);
    public final ModeSetting viewmodelColorMode = new ModeSetting("VmColorMode", "El Renk Efekti", 1,
            new String[]{"Static", "Rainbow", "BlackWhite", "Metallic"},
            new String[]{"Sabit Renk", "Gokkusagi", "Siyah-Beyaz", "Metalik"});

    // ===================== CROSSHAIR (NISANGAH) =====================
    public final BoolSetting customCrosshair = new BoolSetting("CustomCrosshair", "Ozel Nisangah", true);
    public final ModeSetting crosshairShape = new ModeSetting("CrossShape", "Sekil", 1,
            new String[]{"Dot", "Cross", "Circle"}, new String[]{"Nokta", "Arti", "Cember"});
    public final ModeSetting crosshairColor = new ModeSetting("CrossColor", "Nisangah Rengi", 1,
            new String[]{"White", "Red", "Green", "Blue", "Rainbow", "Custom"},
            new String[]{"Beyaz", "Kirmizi", "Yesil", "Mavi", "Gokkusagi", "Ozel"});
    public final NumSetting crossSize = new NumSetting("CrossSize", "Nisangah Boyutu", 4.0, 1.0, 10.0, 1.0);

    // ===================== EFEKTLER & RENDER =====================
    public final BoolSetting damageTint = new BoolSetting("DamageTint", "Hasar Alma Efekti", true);
    public final ModeSetting hurtTintColor = new ModeSetting("HurtTintColor", "Hasar Rengi", 0,
            new String[]{"Red", "Blue", "Purple", "Green", "Rainbow"},
            new String[]{"Kirmizi", "Mavi", "Mor", "Yesil", "Gokkusagi"});

    // ===================== AURALAR (ESP) =====================
    public final BoolSetting itemAura = new BoolSetting("ItemAura", "Yer Esya Auralari", true);
    public final ModeSetting itemAuraColor = new ModeSetting("ItemAuraColor", "Esya Aura Rengi", 1,
            new String[]{"Pink", "Rainbow", "Gold"}, new String[]{"Pembe", "Gokkusagi", "Altin"});

    public final BoolSetting crystalAura = new BoolSetting("CrystalAura", "Kristal Auralari", true);
    public final BoolSetting playerAura = new BoolSetting("PlayerAura", "Oyuncu Auralari", false);
    public final BoolSetting glowFill = new BoolSetting("GlowFill", "Aura Icini Doldur", true);

    private int oldFov = -1;

    public PvPAspect() {
        super("PvPAspect", "PvPAspect", "Ultimate PvP Visuals & Viewmodel.", "Zirve PvP Gorunumu.", Category.RENDER);
        settings.addAll(Arrays.asList(
                maxFov, fovValue, noHurtCam,
                oldAnimations, shrinkItems, itemScale, itemX, itemY, itemZ,
                viewmodelAura, viewmodelColorMode,
                customCrosshair, crosshairShape, crosshairColor, crossSize,
                damageTint, hurtTintColor,
                itemAura, itemAuraColor, crystalAura, playerAura, glowFill
        ));
        INSTANCE = this;
    }

    public static PvPAspect get() { return INSTANCE; }
    public boolean isActive() { return enabled; }

    @Override public String status() { return status; }

    private int targetFov() {
        return Math.max(30, Math.min(MAX_VANILLA_FOV, (int) fovValue.value));
    }

    @Override
    public void onEnable() {
        status = "Active";
        if (mc.options != null) {
            oldFov = mc.options.getFov().getValue();
            if (maxFov.value) mc.options.getFov().setValue(targetFov());
        }
    }

    @Override
    public void onDisable() {
        status = "Off";
        if (mc.options != null && oldFov != -1) {
            mc.options.getFov().setValue(oldFov);
            oldFov = -1;
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.options == null) return;
        if (maxFov.value) {
            int target = targetFov();
            int current = client.options.getFov().getValue();
            if (current != target) {
                client.options.getFov().setValue(target);
            }
        }
    }

    public void render(DrawContext g, MinecraftClient client, float delta) {
        if (!enabled || client.player == null || client.world == null) return;
        int W = client.getWindow().getScaledWidth();
        int H = client.getWindow().getScaledHeight();

        // 1. HASAR ALMA EFEKTI (Hurt Tint)
        if (damageTint.value && client.player.hurtTime > 0) {
            int color = switch (hurtTintColor.index) {
                case 1 -> 0x40FF0000; // Mavi
                case 2 -> 0x408000FF; // Mor
                case 3 -> 0x4000FF00; // Yesil
                case 4 -> withAlpha(getCurrentAuraColor(), 64); // Gokkusagi
                default -> 0x40FF0000; // Kirmizi
            };
            g.fill(0, 0, W, H, color);
        }

        // 2. OZEL CROSSHAIR (Nisangah)
        if (customCrosshair.value && !client.options.hudHidden) {
            int cx = W / 2;
            int cy = H / 2;
            int cColor = 0xFFFFFFFF; // Beyaz

            switch (crosshairColor.index) {
                case 1 -> cColor = 0xFFFF0000; // Kirmizi
                case 2 -> cColor = 0xFF00FF00; // Yesil
                case 3 -> cColor = 0xFF0000FF; // Mavi
                case 4 -> cColor = getCurrentAuraColor(); // Gokkusagi
            }

            int size = (int) crossSize.value;
            if (crosshairShape.index == 0) { // Dot (Nokta)
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, cColor);
            } else if (crosshairShape.index == 1) { // Cross (Arti)
                g.fill(cx - size, cy, cx + size, cy + 1, cColor);
                g.fill(cx, cy - size, cx + 1, cy + size, cColor);
            } else if (crosshairShape.index == 2) { // Circle (Cember)
                // Basit bir cember cizimi (4 noktadan)
                g.fill(cx - size, cy - 1, cx - size + 2, cy + 1, cColor);
                g.fill(cx + size - 2, cy - 1, cx + size, cy + 1, cColor);
                g.fill(cx - 1, cy - size, cx + 1, cy - size + 2, cColor);
                g.fill(cx - 1, cy + size - 2, cx + 1, cy + size, cColor);
            }
        }

        // 3. AURALAR (ESP)
        if (!itemAura.value && !crystalAura.value && !playerAura.value) return;

        for (Entity e : client.world.getEntities()) {
            if (e.isRemoved() || e.squaredDistanceTo(client.player) > AURA_RANGE_SQ) continue;

            Box box = null;
            int mainColor = 0;

            if (itemAura.value && e instanceof ItemEntity) {
                box = e.getBoundingBox().expand(0.2);
                mainColor = itemAuraColor.index == 1 ? getCurrentAuraColor() : (itemAuraColor.index == 2 ? 0xFFFFD700 : 0xFFFF00FF);
            } else if (crystalAura.value && e instanceof EndCrystalEntity) {
                box = e.getBoundingBox().expand(0.2);
                mainColor = 0xFFFF5C7A;
            } else if (playerAura.value && e instanceof PlayerEntity && e != client.player) {
                box = e.getBoundingBox().expand(0.1);
                mainColor = 0xFF4FC3FF;
            }

            if (box == null) continue;

            int[] proj = projectBox(client, box, delta);
            if (proj == null) continue;

            int x1 = Math.min(proj[0], proj[2]), x2 = Math.max(proj[0], proj[2]);
            int y1 = Math.min(proj[1], proj[3]), y2 = Math.max(proj[1], proj[3]);

            outline(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, withAlpha(mainColor, 100));
            outline(g, x1, y1, x2, y2, mainColor);
            if (glowFill.value) g.fill(x1, y1, x2, y2, withAlpha(mainColor, 40));
        }
    }

    // Renk Hesaplayici (Auralar, Crosshair ve HurtTint icin)
    public int getCurrentAuraColor() {
        long t = System.currentTimeMillis();
        return switch (viewmodelColorMode.index) {
            case 1 -> 0xFF000000 | MathHelper.hsvToRgb((t % 3000L) / 3000f, 0.8f, 1f); // Rainbow
            case 2 -> {
                int bw = (int)((Math.sin(t / 150.0) * 0.5 + 0.5) * 255);
                yield 0xFF000000 | (bw << 16) | (bw << 8) | bw; // BlackWhite
            }
            case 3 -> {
                int met = 100 + (int)((Math.sin(t / 100.0) * 0.5 + 0.5) * 155);
                yield 0xFF000000 | (met << 16) | (met << 8) | met; // Metallic
            }
            default -> 0xFF4FC3FF; // Static (Mavi)
        };
    }
}