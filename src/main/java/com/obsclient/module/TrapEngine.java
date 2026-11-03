package com.obsclient.module;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

/**
 * TrapEngine — blok bazlı trap modülleri için ortak motor.
 * AutoTrap, SelfTrap ve AutoCrystal tarafından çağrılır.
 *
 * Temel akış:
 *   1) plan() ile yerleştirilecek pozisyonların listesi oluşturulur.
 *   2) run() her tick'te bu listeden sırayla blok yerleştirir.
 *   3) Result ile kaç blok yerleştirildiği, kaçının uzak/engelli olduğu raporlanır.
 */
public class TrapEngine {

    /** Bir yerleştirme turunun sonuç raporu. */
    public static class Result {
        public int placed;         // başarıyla yerleştirilen blok sayısı
        public int remaining;      // toplam eksik blok sayısı (başlangıçta)
        public int far;            // menzil dışı olduğu için atlananlar
        public int blocked;        // entity (oyuncu/kristal) engellediği için atlananlar
        public int noSupport;      // uygun destek bloğu bulunamadığı için atlananlar
        public int cleared;        // temizlenen kristal sayısı
        public boolean noBlocks;   // envanterde uygun blok yok
    }

    /**
     * Bir entity'nin ayak pozisyonunu döner.
     * Y değerine 0.05 offset eklenir çünkü oyuncular genelde bir bloğun üst kenarında durur.
     */
    public static BlockPos feetOf(Entity e) {
        return BlockPos.ofFloored(e.getX(), e.getY() + 0.05, e.getZ());
    }

    /**
     * Trap planını oluşturur.
     *  @param feet     hedef entity'nin ayak pozisyonu
     *  @param floor    zemine blok koyulsun mu? (düşmesin diye)
     *  @param feetRing ayak seviyesindeki 4 yan yön kapatılsın mı?
     *  @param headRing kafa seviyesindeki 4 yan yön kapatılsın mı?
     *  @param cap      üstü kapatılsın mı? (kafa üstü blok)
     */
    public static List<BlockPos> plan(BlockPos feet, boolean floor, boolean feetRing, boolean headRing, boolean cap) {
        List<BlockPos> out = new ArrayList<>();
        if (floor) out.add(feet.down());
        if (feetRing) for (Direction d : Direction.Type.HORIZONTAL) out.add(feet.offset(d));
        if (headRing) for (Direction d : Direction.Type.HORIZONTAL) out.add(feet.up().offset(d));
        if (cap) out.add(feet.up(2));
        return out;
    }

    /**
     * Blok tipini seçer.
     *  0 = sadece Obsidian / Crying Obsidian
     *  1 = Obsidian / Crying Obsidian / Ender Chest
     *  2 = Herhangi bir blok (BlockWithEntity ve FallingBlock hariç)
     */
    public static Predicate<ItemStack> blockPred(int idx) {
        if (idx == 0) return s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN);
        if (idx == 1) return s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN) || s.isOf(Items.ENDER_CHEST);
        return s -> s.getItem() instanceof BlockItem bi
                && !(bi.getBlock() instanceof BlockWithEntity && bi.getBlock() != Blocks.ENDER_CHEST)
                && !(bi.getBlock() instanceof FallingBlock);
    }

    /**
     * Belirli bir menzil içindeki en uygun düşmanı seçer.
     * lowestHp true ise canı en düşük hedef, false ise en yakın hedef seçilir.
     */
    public static PlayerEntity pickEnemy(MinecraftClient mc, ClientPlayerEntity p, double range, boolean lowestHp) {
        if (mc == null || mc.world == null || p == null) return null;
        PlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double d = p.distanceTo(o);
            if (d > range) continue;
            // lowestHp modunda: can × 100 + mesafe  → can öncelikli
            // normal modda: sadece mesafe
            double score = lowestHp
                    ? (o.getHealth() + o.getAbsorptionAmount()) * 100.0 + d
                    : d;
            if (score < bestScore) { bestScore = score; best = o; }
        }
        return best;
    }

    /**
     * Ana yerleştirme metodu. Her tick çağrılır.
     *
     * @param mc             MinecraftClient
     * @param p              oyuncu
     * @param plan           plan()'dan gelen pozisyon listesi
     * @param perTick        bir tick'te en fazla kaç blok yerleştirilecek
     * @param range          maksimum yerleştirme menzili (eye → block)
     * @param silent         true ise sessiz rotasyon kullanılır
     * @param clearCrystals  hedef pozisyondaki kristalleri kır
     * @param pred           blok tipi filtresi
     */
    public static Result run(MinecraftClient mc, ClientPlayerEntity p, List<BlockPos> plan, int perTick,
                             double range, boolean silent, boolean clearCrystals, Predicate<ItemStack> pred) {
        Result r = new Result();
        if (mc.world == null || mc.interactionManager == null || p == null || plan == null) return r;

        // 1) Hangi pozisyonlar hâlâ boş?
        List<BlockPos> todo = new ArrayList<>();
        for (BlockPos b : plan) {
            if (mc.world.getBlockState(b).isReplaceable()) todo.add(b);
        }
        r.remaining = todo.size();
        if (todo.isEmpty()) return r;

        // 2) Envanterde uygun blok var mı?
        if (findInv(p, pred) < 0) { r.noBlocks = true; return r; }

        // 3) Öncelik sıralaması: önce aşağıdaki katmanlar (y düşük), sonra göz mesafesi yakın olanlar
        final Vec3d eye = p.getEyePos();
        todo.sort(Comparator
                .comparingInt((BlockPos b) -> b.getY())
                .thenComparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b))));

        int prev = p.getInventory().getSelectedSlot();
        for (BlockPos b : todo) {
            if (r.placed >= perTick) break;

            // Menzil kontrolü
            if (eye.distanceTo(Vec3d.ofCenter(b)) > range) { r.far++; continue; }

            // Pozisyonu engelleyen entity var mı?
            boolean blockedByEntity = false;
            for (Entity e : mc.world.getOtherEntities(null, new Box(b))) {
                if (e instanceof ItemEntity || e instanceof ExperienceOrbEntity) continue;

                // Kristal temizleme: menzilde ve clearCrystals açıksa kır
                if (e instanceof EndCrystalEntity c && clearCrystals && boxDist(eye, c.getBoundingBox()) <= crystalReach()) {
                    mc.interactionManager.attackEntity(p, c);
                    p.swingHand(Hand.MAIN_HAND);
                    r.cleared++;
                }
                blockedByEntity = true;
            }
            if (blockedByEntity) { r.blocked++; continue; }

            // Blok yerleştir
            if (placeSupported(mc, p, b, pred, silent)) r.placed++;
            else r.noSupport++;
        }
        p.getInventory().setSelectedSlot(prev);
        return r;
    }

    /** Status string'i için kısa açıklama. */
    public static String describe(Result r) {
        if (r.noBlocks) return "No blocks";
        if (r.remaining == 0) return "Done";
        StringBuilder sb = new StringBuilder("Left " + (r.remaining - r.placed));
        if (r.far > 0) sb.append(" | far ").append(r.far);
        if (r.blocked > 0) sb.append(" | blocked ").append(r.blocked);
        if (r.noSupport > 0) sb.append(" | noSup ").append(r.noSupport);
        return sb.toString();
    }
}