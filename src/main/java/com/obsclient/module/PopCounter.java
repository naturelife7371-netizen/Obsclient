package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

import static com.obsclient.ObsClient.isFriend;

public class PopCounter extends Module {
    public static String status = "Off";
    public static PopCounter INSTANCE;

    // ============ AYARLAR ============
    final BoolSetting chatMessage = new BoolSetting("ChatMessage", "Sohbete Yaz", true);
    final BoolSetting sound       = new BoolSetting("Sound", "Ses Cikar", true);

    // "Sadece bizimkiler" mantığı için filtreler
    final BoolSetting onlyTarget  = new BoolSetting("OnlyTarget", "Sadece Nisan Aldigim", false);
    final NumSetting maxDist      = new NumSetting("MaxDist", "Max Mesafe", 15, 3, 100, 1);

    // Veri Tutucular
    public static class PopData {
        public String name;
        public int count;
        public long lastPopTime;
        public PopData(String name) { this.name = name; this.count = 0; this.lastPopTime = 0; }
    }

    private static final Map<UUID, PopData> pops = new HashMap<>();

    public PopCounter() {
        super("PopCounter", "PopCounter", "Counts enemy totem pops.", "Rakip totem sayacini gosterir.", Category.RENDER);
        settings.addAll(Arrays.asList(chatMessage, sound, onlyTarget, maxDist));
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onEnable() {
        pops.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        // Mixin kullandığımız için tick içinde tarama yapmaya gerek kalmadı.
        // Bu sayede lag olmaz, Gapple yiyenler totem patlattı sanılmaz.
        if (mc.world == null || mc.player == null) {
            pops.clear();
        }
    }

    /** Mixin tarafından totem patladığında çağrılır. */
    public static void onPop(PlayerEntity target) {
        if (INSTANCE == null || !INSTANCE.enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null || target == mc.player) return;

        // --- FİLTRELER ---
        // 1. Arkadaşları sayma
        if (isFriend(target)) return;

        // 2. Mesafe filtresi (Uzaktaki rastgele adamları saymasın)
        if (mc.player.distanceTo(target) > INSTANCE.maxDist.value) return;

        // 3. Sadece nişan aldığın kişiyi say
        if (INSTANCE.onlyTarget.value && mc.targetedEntity != target) return;

        // --- SAYACI GÜNCELLE ---
        UUID id = target.getUuid();
        PopData data = pops.computeIfAbsent(id, k -> new PopData(target.getName().getString()));
        data.count++;
        data.lastPopTime = System.currentTimeMillis();

        // --- SOHBET MESAJI (Şık Format) ---
        if (INSTANCE.chatMessage.value) {
            // Örnek Çıktı: [PopCounter] Steve patlatti! (Toplam: 3)
            String msg = "§8[§dPop§8] §f" + data.name + " §7totem patlatti! §8(§d" + data.count + "§8)";
            mc.inGameHud.getChatHud().addMessage(Text.literal(msg));
        }

        // --- SES EFEKTİ ---
        if (INSTANCE.sound.value) {
            mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }
}