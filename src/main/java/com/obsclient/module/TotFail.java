package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

public class TotFail extends Module {
    public static String status = "Off";
    public static TotFail INSTANCE;

    // Ayarlar
    final NumSetting range = new NumSetting("Range", "Hedef Menzili", 6.0, 1.0, 12.0, 0.5);
    final NumSetting packetsPerTick = new NumSetting("Packets", "Paket/Tick", 2, 1, 5, 1);
    final BoolSetting onlyWhenTarget = new BoolSetting("OnlyTarget", "Sadece Hedef Varsa", true);
    final BoolSetting hideTotem = new BoolSetting("HideTotem", "Totemi Gizle (Gorsel)", true);

    public TotFail() {
        super("TotFail", "TotFail", "Totem desync packet spammer.", "Totem fail paket spammer.", Category.COMBAT);
        settings.addAll(Arrays.asList(range, packetsPerTick, onlyWhenTarget, hideTotem));
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? "Spamming" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

            boolean hasTarget = false;

            // 1. GÖRSEL KISIM: Totemi senin ekranında gizle
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player && !p.isDead() && !isFriend(p) && mc.player.distanceTo(p) <= range.value) {
                    hasTarget = true;

                    if (hideTotem.value) {
                        // Eğer rakibin offhand'inde totem varsa, senin ekranında onu anında boşalt
                        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                            p.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                        }
                        // Eğer ana elinde tutuyorsa onu da gizle
                        if (p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                            p.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        }
                    }
                }
            }

            if (onlyWhenTarget.value && !hasTarget) return;

            // 2. DESYNC KISMI: Sunucuya paket bombardımanı yap
            int count = (int) packetsPerTick.value;
            for (int i = 0; i < count; i++) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN, Direction.DOWN
                ));
            }
        } catch (Throwable ignored) {}
    }
}