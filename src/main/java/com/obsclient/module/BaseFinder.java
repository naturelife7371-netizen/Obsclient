package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.obsclient.ObsClient.*;

public class BaseFinder extends Module {
    public static String status = "Off";

    // Ayarlar
    final NumSetting range = new NumSetting("Range", "Tarama Menzili", 64, 16, 128, 8);
    final NumSetting scanSpeed = new NumSetting("ScanSpeed", "Tarama Hizi (tick)", 20, 5, 100, 5);
    final BoolSetting tracers = new BoolSetting("Tracers", "Cizgiler", true);
    final BoolSetting showText = new BoolSetting("Text", "Yazi Goster", true);

    // Filtreler
    final BoolSetting chests = new BoolSetting("Chests", "Sandiklar", true);
    final BoolSetting shulkers = new BoolSetting("Shulkers", "Shulkerlar", true);
    final BoolSetting obsidian = new BoolSetting("Obsidian", "Obsidyen", true);
    final BoolSetting spawners = new BoolSetting("Spawners", "Spawnerlar", true);
    final BoolSetting beacons = new BoolSetting("Beacons", "Beaconlar", true);

    private static class FoundBlock {
        BlockPos pos;
        Block block;
        FoundBlock(BlockPos pos, Block block) { this.pos = pos; this.block = block; }
    }

    private final List<FoundBlock> found = new ArrayList<>();
    private int tickCounter = 0;

    public BaseFinder() {
        super("BaseFinder", "BaseFinder", "Finds bases and valuables.", "Usleri ve degerli esyalari bulur.", Category.RENDER);
        settings.addAll(Arrays.asList(range, scanSpeed, tracers, showText, chests, shulkers, obsidian, spawners, beacons));
    }

    @Override public String status() { return enabled ? found.size() + " bulundu" : "Off"; }

    @Override
    public void onEnable() {
        found.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        found.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Performans için her tick tarama yapmıyoruz
        if (++tickCounter < scanSpeed.value) return;
        tickCounter = 0;
        found.clear();

        int r = (int) range.value;
        BlockPos center = mc.player.getBlockPos();

        // 1.21 API Uyumlu Y Sınırlandırması
        int minY = Math.max(mc.world.getBottomY(), center.getY() - 40);
        int maxY = Math.min(mc.world.getBottomY() + mc.world.getHeight() - 1, center.getY() + 40);

        for (BlockPos pos : BlockPos.iterate(center.add(-r, -40, -r), center.add(r, 40, r))) {
            if (pos.getY() < minY || pos.getY() > maxY) continue;

            BlockState state = mc.world.getBlockState(pos);
            Block b = state.getBlock();

            if (isTarget(b)) {
                found.add(new FoundBlock(pos.toImmutable(), b));
            }
        }
    }

    private boolean isTarget(Block b) {
        if (chests.value && (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.ENDER_CHEST || b == Blocks.BARREL)) return true;
        if (shulkers.value && b == Blocks.SHULKER_BOX) return true;
        if (obsidian.value && (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN)) return true;
        if (spawners.value && b == Blocks.SPAWNER) return true;
        if (beacons.value && b == Blocks.BEACON) return true;
        return false;
    }

    private int getColor(Block b) {
        if (b == Blocks.ENDER_CHEST) return 0xFF9C27B0; // Mor
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return 0xFFFF9800; // Turuncu
        if (b == Blocks.SHULKER_BOX) return 0xFFE91E63; // Pembe
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) return 0xFF673AB7; // Koyu Mor
        if (b == Blocks.SPAWNER) return 0xFFF44336; // Kırmızı
        if (b == Blocks.BEACON) return 0xFF00BCD4; // Cyan
        return 0xFFFFFFFF;
    }

    // ==========================================================
    // RENDER (3D KUTULAR VE ÇİZGİLER)
    // ==========================================================
    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (mc.player == null || found.isEmpty()) return;

        int W = mc.getWindow().getScaledWidth();
        int H = mc.getWindow().getScaledHeight();

        for (FoundBlock fb : found) {
            int color = getColor(fb.block);

            // Bloğun etrafına 3D Kutu çiz
            Box box = new Box(fb.pos).expand(0.002); // Z-fighting önlemek için hafif büyüt
            int[] r = projectBox(mc, box, delta);

            if (r != null) {
                // Dolgu ve Kenarlık
                g.fill(r[0], r[1], r[2], r[3], withAlpha(color, 50));
                outline(g, r[0], r[1], r[2], r[3], color);

                // Tracer (Oyuncudan bloğa çizgi)
                if (tracers.value) {
                    line(g, W / 2, H, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2, withAlpha(color, 150));
                }

                // Yazı (İsim ve Mesafe)
                if (showText.value) {
                    // 1.21 API Uyumlu Konum Alma (epos yardımcı metodu kullanıldı)
                    double dist = epos(mc.player).distanceTo(Vec3d.ofCenter(fb.pos));
                    String text = fb.block.getName().getString() + " §7[" + String.format("%.0f", dist) + "m]";
                    int tw = mc.textRenderer.getWidth(text);
                    g.drawText(mc.textRenderer, text, (r[0] + r[2]) / 2 - tw / 2, r[1] - 10, color, true);
                }
            }
        }
    }
}