package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

/**
 * FastBreak - blok kirma hizini arttirir.
 * Silent tool: alet sessizce secilir, blogu kirar, geri alir.
 * Reflection ile blockBreakingCooldown sifirlanir.
 */
public class FastBreak extends Module {
    public static String status = "Off";

    final NumSetting repeat = new NumSetting("Repeat", "Tekrar", 20, 1, 100, 1);
    final BoolSetting silentTool = new BoolSetting("SilentTool", "Sessiz Alet", true);
    final BoolSetting keepAfterBreak = new BoolSetting("KeepAfterBreak", "Kirinca Tut", false);

    private BlockPos currentBreak = null;
    private int originalSlot = -1;

    // Reflection cache
    private static java.lang.reflect.Field cooldownField = null;
    private static boolean cooldownTried = false;

    public FastBreak() {
        super("FastBreak", "FastBreak", "Silent tool fast break.", "Sessiz alet hizli kir.", Category.PLAYER);
        settings.addAll(Arrays.asList(repeat, silentTool, keepAfterBreak));
    }

    @Override public String status() {
        return enabled ? (silentTool.value ? "Silent" : "Normal") : "Off";
    }

    @Override
    public void onEnable() {
        currentBreak = null;
        originalSlot = -1;
        status = "Ready";
    }

    @Override
    public void onDisable() {
        if (mc.player != null) restore(mc.player);
        status = "Off";
    }

    private void restore(ClientPlayerEntity p) {
        if (originalSlot >= 0 && p.getInventory().getSelectedSlot() != originalSlot) {
            p.getInventory().setSelectedSlot(originalSlot);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            }
        }
        currentBreak = null;
        originalSlot = -1;
    }

    private int findBestTool(ClientPlayerEntity p, net.minecraft.block.BlockState bs) {
        float bestSpeed = p.getMainHandStack().getMiningSpeedMultiplier(bs);
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            boolean isTool = s.isIn(ItemTags.PICKAXES) || s.isIn(ItemTags.SHOVELS)
                    || s.isIn(ItemTags.AXES) || s.isIn(ItemTags.HOES) || s.isOf(Items.SHEARS);
            if (!isTool) continue;
            float sp = s.getMiningSpeedMultiplier(bs);
            if (sp > bestSpeed) { bestSpeed = sp; bestSlot = i; }
        }
        return bestSlot;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            if (p != null) restore(p);
            return;
        }
        if (!mc.options.attackKey.isPressed()) { restore(p); status = "Idle"; return; }
        if (!(mc.crosshairTarget instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) {
            restore(p); status = "No block"; return;
        }

        BlockPos pos = bh.getBlockPos();
        var bs = mc.world.getBlockState(pos);
        if (bs.isAir() || bs.isOf(Blocks.BEDROCK)) { restore(p); status = "Air"; return; }

        if (currentBreak != null && !currentBreak.equals(pos)) restore(p);
        if (currentBreak == null) {
            currentBreak = pos;
            originalSlot = p.getInventory().getSelectedSlot();
        }

        // Silent tool sec
        if (silentTool.value) {
            int best = findBestTool(p, bs);
            if (best >= 0 && best != p.getInventory().getSelectedSlot()) {
                p.getInventory().setSelectedSlot(best);
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(best));
                }
            }
        }

        tryResetCooldown();

        int loops = Math.max(1, repeat.i());
        for (int i = 0; i < loops; i++) {
            try {
                if (mc.interactionManager.isBreakingBlock()) {
                    mc.interactionManager.updateBlockBreakingProgress(pos, bh.getSide());
                } else {
                    mc.interactionManager.attackBlock(pos, bh.getSide());
                }
            } catch (Throwable ignored) {}
        }
        p.swingHand(Hand.MAIN_HAND);

        if (mc.world.getBlockState(pos).isAir()) {
            if (!keepAfterBreak.value) restore(p);
            else { currentBreak = null; originalSlot = p.getInventory().getSelectedSlot(); }
            status = "Broke";
        } else {
            status = "Mining";
        }
    }

    private static void tryResetCooldown() {
        try {
            MinecraftClient m = MinecraftClient.getInstance();
            if (m.interactionManager == null) return;
            if (!cooldownTried) {
                cooldownTried = true;
                for (java.lang.reflect.Field f : m.interactionManager.getClass().getDeclaredFields()) {
                    if (f.getType() != int.class) continue;
                    String n = f.getName().toLowerCase();
                    if (n.equals("blockbreakingcooldown") || n.contains("cooldown")) {
                        f.setAccessible(true);
                        cooldownField = f;
                        break;
                    }
                }
            }
            if (cooldownField != null) {
                cooldownField.setInt(m.interactionManager, 0);
            }
        } catch (Throwable ignored) {}
    }
}