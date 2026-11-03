package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.obsclient.ObsClient.*;

/**
 * Search - belirli bloklari arar ve isaretler.
 */
public class Search extends Module {
    public static String status = "Off";

    final NumSetting range = new NumSetting("Range", "Menzil", 48, 8, 80, 4);
    final ModeSetting block = new ModeSetting("Block", "Blok", 0,
            new String[]{"Diamond", "Debris", "Emerald", "Gold", "Iron", "Lapis", "Redstone", "Copper"},
            new String[]{"Elmas", "Debris", "Zumrut", "Altin", "Demir", "Lapis", "Redstone", "Bakir"});

    private volatile List<BlockPos> cache = new ArrayList<>();
    private int tick = 0;

    public Search() {
        super("Search", "Search", "Find blocks.", "Blok bul.", Category.RENDER);
        settings.addAll(Arrays.asList(range, block));
    }

    @Override public String status() { return enabled ? cache.size() + " blocks" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        if (tick++ % 20 != 0) return;

        List<BlockPos> out = new ArrayList<>();
        Block target = switch (block.value()) {
            case "Diamond"  -> Blocks.DIAMOND_ORE;
            case "Debris"   -> Blocks.ANCIENT_DEBRIS;
            case "Emerald"  -> Blocks.EMERALD_ORE;
            case "Gold"     -> Blocks.GOLD_ORE;
            case "Iron"     -> Blocks.IRON_ORE;
            case "Lapis"    -> Blocks.LAPIS_ORE;
            case "Redstone" -> Blocks.REDSTONE_ORE;
            case "Copper"   -> Blocks.COPPER_ORE;
            default -> Blocks.DIAMOND_ORE;
        };

        int r = range.i();
        BlockPos base = p.getBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (mc.world.getBlockState(pos).isOf(target)) {
                        out.add(pos);
                        if (out.size() > 500) { cache = out; return; }
                    }
                }
            }
        }
        cache = out;
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        for (BlockPos p : cache) {
            Box b = new Box(p.getX(), p.getY(), p.getZ(),
                    p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            int[] r = projectBox(mc, b, delta);
            if (r == null) continue;
            outline(g, r[0], r[1], r[2], r[3], ObsGui.MONO ? 0xFFFFFFFF : 0xFFFFD700);
        }
    }
}