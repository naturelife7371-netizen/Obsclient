package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ObsGui;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class StorageESP extends Module {
    public static String status = "Off";

    final NumSetting range = new NumSetting("Range", "Menzil", 96, 16, 200, 8);
    final BoolSetting fill = new BoolSetting("Fill", "Dolgu", true);

    private static class SE {
        BlockPos pos;
        int color;
        SE(BlockPos p, int c) { pos = p; color = c; }
    }

    private volatile List<SE> cache = new ArrayList<>();
    private int tick = 0;

    public StorageESP() {
        super("StorageESP", "StorageESP", "Show storages.", "Depolar.", Category.RENDER);
        settings.addAll(Arrays.asList(range, fill));
    }

    @Override public String status() { return enabled ? cache.size() + " found" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (tick++ % 10 != 0) return;

        List<SE> out = new ArrayList<>();
        int cr = range.i() / 16 + 1;

        // ⚡ HATA ÇÖZÜMÜ BURADA: Chunk koordinatları bit kaydırma ile bulunuyor
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;

        Vec3d pp = epos(mc.player);

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                WorldChunk ch = mc.world.getChunk(cx, cz);
                if (ch == null) continue;

                // ConcurrentModificationException önlemi
                for (BlockEntity be : new ArrayList<>(ch.getBlockEntities().values())) {
                    int col = 0;
                    if (be instanceof EnderChestBlockEntity)        col = 0xFFB65CFF;
                    else if (be instanceof ShulkerBoxBlockEntity)   col = 0xFFFF7ADE;
                    else if (be instanceof ChestBlockEntity)        col = 0xFFFFA23A;
                    else if (be instanceof BarrelBlockEntity)       col = 0xFFC98B4B;
                    else if (be instanceof TrappedChestBlockEntity) col = 0xFFFF7A3A;
                    if (col == 0) continue;

                    if (ObsGui.MONO) col = 0xFFE0E0E0;

                    BlockPos bp = be.getPos();
                    if (Vec3d.ofCenter(bp).distanceTo(pp) > range.value) continue;
                    out.add(new SE(bp, col));
                }
            }
        }
        cache = out;
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        for (SE s : cache) {
            Box b = new Box(s.pos.getX(), s.pos.getY(), s.pos.getZ(),
                    s.pos.getX() + 1, s.pos.getY() + 1, s.pos.getZ() + 1);
            int[] r = projectBox(mc, b, delta);
            if (r == null) continue;
            if (fill.value) g.fill(r[0], r[1], r[2], r[3], (s.color & 0x00FFFFFF) | 0x30000000);
            outline(g, r[0], r[1], r[2], r[3], s.color);
        }
    }
}