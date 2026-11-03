package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Notification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;

import static com.obsclient.ObsClient.*;

/**
 * AutoTrap — hedefin etrafını bloklarla kapatır.
 *
 * Hedefin ayak pozisyonunu merkez alarak:
 *  - Full : ayak + kafa çemberi + üst kapak  (en güvenli, en çok blok)
 *  - Head : sadece kafa çemberi + üst kapak
 *  - Top  : sadece üst kapak
 *  - Feet : ayak çemberi
 *
 * Ayarlar:
 *  - Block        : hangi blok tipini kullansın
 *  - TargetRange  : hedef arama menzili
 *  - PlaceRange   : blok yerleştirme menzili
 *  - PerTick      : bir tick'te en fazla kaç blok
 *  - Delay        : her tur arasında bekleme (tick)
 *  - Floor        : zemine blok koy
 *  - SilentRotate : sessiz dönüş (sunucuya rotasyon paketi)
 *  - ClearCrystals: yoldaki kristalleri kır
 *  - LowestHP     : en düşük canlı hedefi seç
 *  - SkipAirborne : havadaki hedefi atla
 *  - StillOnly    : sadece hareketsiz hedefi trap'le
 *  - AutoOff      : trap tamamlanınca modülü otomatik kapat
 */
public class AutoTrap extends Module {
    public static String status = "Off";

    final ModeSetting shape = new ModeSetting("Shape", "Şekil", 0,
            new String[]{"Full", "Head", "Top", "Feet"},
            new String[]{"Tam", "Kafa", "Üst", "Ayak"});

    final ModeSetting block = new ModeSetting("Block", "Blok", 1,
            new String[]{"Obsidian", "Obsidian+Ender", "Any"},
            new String[]{"Obsidyen", "Obsidyen+Ender", "Herhangi"});

    final NumSetting targetRange = new NumSetting("TargetRange", "Hedef Menzili", 8, 2, 12, 1);
    final NumSetting placeRange  = new NumSetting("PlaceRange", "Koyma Menzili", 4.5, 3.0, 5.0, 0.1);
    final NumSetting perTick     = new NumSetting("PerTick", "Tick Başı", 4, 1, 10, 1);
    final NumSetting delay       = new NumSetting("Delay", "Gecikme", 0, 0, 6, 1);

    final BoolSetting floor          = new BoolSetting("Floor", "Zemin", false);
    final BoolSetting silent         = new BoolSetting("SilentRotate", "Sessiz Dönüş", true);
    final BoolSetting clearCrystals  = new BoolSetting("ClearCrystals", "Kristal Temizle", true);
    final BoolSetting lowestHp       = new BoolSetting("LowestHP", "Düşük Can", false);
    final BoolSetting skipAir        = new BoolSetting("SkipAirborne", "Havadakini Atla", true);
    final BoolSetting stillOnly      = new BoolSetting("StillOnly", "Sadece Hareketsiz", false);
    final BoolSetting autoOff        = new BoolSetting("AutoOff", "Bitince Kapan", false);

    private int cd = 0;

    public AutoTrap() {
        super("AutoTrap", "AutoTrap", "Traps target with blocks.", "Hedefin etrafını bloklarla kapatır.", Category.COMBAT);
        settings.addAll(Arrays.asList(shape, block, targetRange, placeRange, perTick, delay,
                floor, silent, clearCrystals, lowestHp, skipAir, stillOnly, autoOff));
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Searching"; cd = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cd > 0) { cd--; return; }
        if (p.isGliding()) { status = "Gliding"; return; }

        // 1) Hedef seç
        PlayerEntity t = TrapEngine.pickEnemy(mc, p, targetRange.value, lowestHp.value);
        if (t == null) { status = "No target"; return; }

        // 2) Filtreler
        if (skipAir.value && !t.isOnGround() && Math.abs(t.getVelocity().y) > 0.1) {
            status = "Target air"; return;
        }
        if (stillOnly.value && VEL.getOrDefault(t.getId(), Vec3d.ZERO).horizontalLength() > 0.08) {
            status = "Moving"; return;
        }

        // 3) Şekil parametrelerini ayarla
        String s = shape.value();
        boolean feetRing = s.equals("Full") || s.equals("Feet");
        boolean headRing = s.equals("Full") || s.equals("Head");
        boolean cap      = !s.equals("Feet");

        // 4) Plan oluştur ve çalıştır
        BlockPos feet = TrapEngine.feetOf(t);
        List<BlockPos> plan = TrapEngine.plan(feet, floor.value, feetRing, headRing, cap);
        TrapEngine.Result r = TrapEngine.run(mc, p, plan, perTick.i(), placeRange.value,
                silent.value, clearCrystals.value, TrapEngine.blockPred(block.index));

        // 5) Sonuç işle
        if (r.noBlocks) { status = "No blocks"; return; }
        if (r.remaining == 0) {
            status = "Done";
            if (autoOff.value) {
                toggle();
                Notification.send("AutoTrap done", 0xFF3FD07A);
            }
            return;
        }
        if (r.placed > 0) {
            status = "Building | " + TrapEngine.describe(r);
            cd = delay.i();
        } else {
            status = TrapEngine.describe(r);
        }
    }
}