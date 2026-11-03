package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ObsGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class NameTag extends Module {
    public static String status = "Off";

    final NumSetting maxDist = new NumSetting("MaxDist", "Max Mesafe", 200, 20, 400, 5);
    final BoolSetting items  = new BoolSetting("Items", "Esyalar", true);
    final BoolSetting armor  = new BoolSetting("Armor", "Zirh", true);

    public NameTag() {
        super("NameTag", "NameTag", "Player card.", "Oyuncu karti.", Category.RENDER);
        settings.addAll(Arrays.asList(maxDist, items, armor));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        // Render-only modül
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (mc.player == null || mc.world == null) return;
        var tr = mc.textRenderer;
        boolean mono = ObsGui.MONO;

        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == mc.player || !o.isAlive() || o.isSpectator()) continue;
            double dist = mc.player.distanceTo(o);
            if (dist > maxDist.value) continue;

            Vec3d lp = o.getLerpedPos(delta);
            int[] s = project(mc, lp.add(0, o.getHeight() + 0.6, 0), delta);
            if (s == null) continue;

            boolean fr = isFriend(o);
            double hp = o.getHealth() + o.getAbsorptionAmount();
            String name = (fr ? "§b[F] §f" : "§f") + o.getName().getString();
            String hpS = String.format("%.1f", hp);
            String distS = String.format("%.0fm", dist);

            // Eşyaları topla
            ArrayList<ItemStack> list = new ArrayList<>();
            if (items.value) list.add(o.getMainHandStack());
            if (armor.value) {
                list.add(o.getEquippedStack(EquipmentSlot.HEAD));
                list.add(o.getEquippedStack(EquipmentSlot.CHEST));
                list.add(o.getEquippedStack(EquipmentSlot.LEGS));
                list.add(o.getEquippedStack(EquipmentSlot.FEET));
            }
            if (items.value) list.add(o.getOffHandStack());

            // Boyut hesaplamaları
            int iw = list.size() * 18;
            int nameW = tr.getWidth(o.getName().getString()) + (fr ? 14 : 0);
            int topRowW = nameW + tr.getWidth(distS) + 20;
            int bw = Math.max(Math.max(topRowW, iw), 100) + 12;
            int rowsH = list.isEmpty() ? 0 : 22;
            int bh = 16 + 8 + 6 + rowsH + 6; // Name + Divider + HealthBar + Items + Padding

            int cx = s[0];
            int bx = cx - bw / 2;
            int by = s[1] - bh;

            // ⚡ ARKA PLAN TAMAMEN KALDIRILDI ⚡

            // 1. İsim ve Mesafe (Gölgeli, okunabilir)
            int nameColor = 0xFFFFFFFF;
            g.drawText(tr, name, bx + 6, by + 5, nameColor, true);
            g.drawText(tr, distS, bx + bw - 6 - tr.getWidth(distS), by + 5, 0xFFAAAAAA, true);

            // 2. Can Barı (İnce ve Şık)
            int hy = by + 19;
            int barW = bw - 12 - tr.getWidth(hpS) - 6;
            int hpc = mono ? 0xFFFFFFFF : hpColor(hp);

            // Can Barı Arka Planı (Sadece barın arkasında çok hafif bir karartma)
            g.fill(bx + 6, hy, bx + 6 + barW, hy + 3, 0x80000000);

            // Can Barı Dolgusu
            int fw = (int)(barW * Math.min(1.0, hp / 20.0));
            if (fw > 0) {
                g.fill(bx + 6, hy, bx + 6 + fw, hy + 3, hpc);
            }

            // Can Değeri Yazısı
            g.drawText(tr, hpS, bx + bw - 6 - tr.getWidth(hpS), hy - 2, hpc, true);

            // 3. Eşyalar (Arka plansız, direkt havada)
            int cy = hy + 10;
            if (!list.isEmpty()) {
                int ix = cx - iw / 2;
                for (ItemStack st : list) {
                    if (!st.isEmpty()) {
                        // Eşya çizimi ve sayısı
                        g.drawItem(st, ix + 1, cy);
                        g.drawStackOverlay(tr, st, ix + 1, cy);
                    }
                    ix += 18;
                }
            }
        }
    }
}