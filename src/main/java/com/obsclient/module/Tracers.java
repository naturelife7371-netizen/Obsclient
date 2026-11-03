package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ObsGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

public class Tracers extends Module {
    public static String status = "Off";

    final NumSetting maxDist = new NumSetting("MaxDist", "Max Mesafe", 300, 20, 500, 10);
    final BoolSetting friends = new BoolSetting("Friends", "Arkadaslar", true);

    public Tracers() {
        super("Tracers", "Tracers", "Lines to players.", "Oyuncu cizgileri.", Category.RENDER);
        settings.addAll(Arrays.asList(maxDist, friends));
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        // Render-only modül
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (mc.player == null || mc.world == null) return;
        int W = mc.getWindow().getScaledWidth();
        int H = mc.getWindow().getScaledHeight();

        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == mc.player || !o.isAlive() || o.isSpectator()) continue;
            if (isFriend(o) && !friends.value) continue;
            double dist = mc.player.distanceTo(o);
            if (dist > maxDist.value) continue;

            Vec3d lp = o.getLerpedPos(delta);
            int[] s = project(mc, lp.add(0, o.getHeight() / 2.0, 0), delta);
            if (s == null) continue;

            int col = ObsGui.MONO
                    ? (isFriend(o) ? 0xFFFFFFFF : 0xFFC8C8C8)
                    : (isFriend(o) ? 0xFF5CFF8A : 0xFFB65CFF);
            line(g, W / 2, H - 1, s[0], s[1], withAlpha(col, 200));
        }
    }
}