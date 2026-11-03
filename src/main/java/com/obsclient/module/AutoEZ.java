package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

import static com.obsclient.ObsClient.isFriend;

@SuppressWarnings("unused")
public class AutoEZ extends Module {
    public static String status = "Off";
    public static AutoEZ INSTANCE;

    final BoolSetting onPop = new BoolSetting("OnPop", "Totemde Yaz", true);
    final BoolSetting onDeath = new BoolSetting("OnDeath", "Olunce Yaz", true);
    final NumSetting delay = new NumSetting("Delay", "Gecikme (ms)", 1000, 0, 5000, 100);
    final NumSetting range = new NumSetting("Range", "Menzil", 20, 5, 64, 1);

    // ObsClient içindeki *ez komutu için public olmalı
    public final TextSetting popText = new TextSetting("PopMessages", "Pop Mesaji", "Ezzz %target% %count% popped");
    public final TextSetting deathText = new TextSetting("DeathMessages", "Olum Mesaji", "Ezzz %target%");

    private final Map<UUID, Integer> popCounts = new HashMap<>();
    private final Map<UUID, Long> lastPopSent = new HashMap<>();
    private final Map<UUID, Long> lastDeathSent = new HashMap<>();
    private final Map<UUID, Boolean> wasDead = new HashMap<>();

    // Totem tespiti için Absorption takibi (Mixin'siz)
    private final Map<UUID, Float> lastAbsorption = new HashMap<>();

    public AutoEZ() {
        super("AutoEZ", "AutoEZ", "Sends EZ messages.", "Oto EZ mesaji yollar.", Category.COMBAT);
        settings.addAll(Arrays.asList(onPop, onDeath, delay, range, popText, deathText));
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onEnable() {
        popCounts.clear();
        lastPopSent.clear();
        lastDeathSent.clear();
        wasDead.clear();
        lastAbsorption.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

            for (PlayerEntity target : mc.world.getPlayers()) {
                if (target == mc.player || isFriend(target)) continue;

                UUID id = target.getUuid();

                // 1. TOTEM PATLAMA TESPİTİ (Absorption artışı)
                float currAbs = target.getAbsorptionAmount();
                float prevAbs = lastAbsorption.getOrDefault(id, 0.0f);

                // Önceki absorption 2.0'ın altındaysa ve şimdi aniden 4.0 veya üzeri olduysa -> Totem patlattı!
                if (prevAbs < 2.0f && currAbs >= 4.0f) {
                    onTotemPop(target);
                }
                lastAbsorption.put(id, currAbs);

                // 2. ÖLÜM TESPİTİ
                boolean isDead = !target.isAlive() || target.getHealth() <= 0f;
                Boolean was = wasDead.get(id);

                if (was == null) {
                    wasDead.put(id, isDead);
                } else if (isDead && !was) {
                    wasDead.put(id, true);
                    if (onDeath.value && mc.player.distanceTo(target) <= range.value) {
                        sendDeathMessage(target.getName().getString(), popCounts.getOrDefault(id, 0), id);
                    }
                    popCounts.remove(id);
                    lastAbsorption.remove(id);
                    continue;
                } else if (!isDead && was) {
                    wasDead.put(id, false);
                }
            }
        } catch (Throwable ignored) {}
    }

    public void onTotemPop(PlayerEntity target) {
        if (!enabled || !onPop.value) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == mc.player || isFriend(target)) return;
        if (mc.player.distanceTo(target) > range.value) return;

        long now = System.currentTimeMillis();
        UUID id = target.getUuid();

        Long last = lastPopSent.get(id);
        if (last != null && now - last < delay.value) return;

        int count = popCounts.merge(id, 1, Integer::sum);
        sendMessage(popText.value, target.getName().getString(), count);
        lastPopSent.put(id, now);
    }

    private void sendDeathMessage(String targetName, int count, UUID id) {
        long now = System.currentTimeMillis();
        Long last = lastDeathSent.get(id);
        if (last != null && now - last < delay.value) return;

        sendMessage(deathText.value, targetName, count);
        lastDeathSent.put(id, now);
    }

    private void sendMessage(String template, String targetName, int count) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || template.isEmpty()) return;

        String msg = template.replace("%target%", targetName).replace("%count%", String.valueOf(count));

        if (msg.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(msg.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(msg);
        }
    }
}