package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ObsGui;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.obsclient.ObsClient.*;

public class HoleESP extends Module {
    public static String status = "Off";

    final NumSetting range    = new NumSetting("Range", "Menzil", 24, 4, 40, 1);
    final BoolSetting bedrock = new BoolSetting("Bedrock", "Bedrock", true);
    final BoolSetting obsidian = new BoolSetting("Obsidian", "Obsidyen", true);
    final BoolSetting fill    = new BoolSetting("Fill", "Dolgu", true);

    private volatile List<Object[]> cache = new ArrayList<>();
    private int tick = 0;

    public HoleESP() {
        super("HoleESP", "HoleESP", "Safe holes.", "Guvenli delikler.", Category.RENDER);
        settings.addAll(Arrays.asList(range, bedrock, obsidian, fill));
    }

    @Override public String status() { return enabled ? cache.size() + " holes" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        if (tick++ % 8 != 0) return;

        List<Object[]> out = new ArrayList<>();
        int r = range.i();
        BlockPos base = p.getBlockPos();

        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);

                    if (!mc.world.getBlockState(pos).isAir()) continue;
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    if (!mc.world.getBlockState(pos.up(2)).isAir()) continue;

                    boolean bd = false, ob = false, ok = true;
                    for (Direction d : Direction.Type.HORIZONTAL) {
                        var s = mc.world.getBlockState(pos.offset(d));
                        if (!s.isSolidBlock(mc.world, pos.offset(d))) { ok = false; break; }
                        if (s.isOf(Blocks.BEDROCK)) bd = true;
                        else if (s.isOf(Blocks.OBSIDIAN)) ob = true;
                    }
                    if (!ok) continue;

                    var fl = mc.world.getBlockState(pos.down());
                    if (!fl.isSolidBlock(mc.world, pos.down())) continue;
                    if (fl.isOf(Blocks.BEDROCK)) bd = true;
                    else if (fl.isOf(Blocks.OBSIDIAN)) ob = true;

                    int col = 0;
                    if (bd && bedrock.value) col = 0xFF5CFFB0;
                    else if (ob && obsidian.value) col = 0xFF4FC3FF;
                    if (col == 0) continue;

                    out.add(new Object[]{ pos, col });
                    if (out.size() > 500) break outer;
                }
            }
        }
        cache = out;
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        for (Object[] o : cache) {
            BlockPos p = (BlockPos)o[0];
            int col = (int)o[1];
            if (ObsGui.MONO) col = ((int)o[1]) == 0xFF5CFFB0 ? 0xFFFFFFFF : 0xFFA0A0A0;

            Box b = new Box(p.getX() + 0.05, p.getY() + 0.01, p.getZ() + 0.05,
                    p.getX() + 0.95, p.getY() + 0.05, p.getZ() + 0.95);
            int[] r = projectBox(mc, b, delta);
            if (r == null) continue;
            if (fill.value) g.fill(r[0], r[1], r[2], r[3], withAlpha(col, 60));
            outline(g, r[0], r[1], r[2], r[3], col);
        }
    }
}