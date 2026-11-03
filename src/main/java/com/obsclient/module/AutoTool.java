package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * AutoTool - en iyi aleti otomatik sec.
 */
public class AutoTool extends Module {
    public static String status = "Off";

    public AutoTool() {
        super("AutoTool", "AutoTool", "Best tool.", "En iyi alet.", Category.PLAYER);
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.options.attackKey.isPressed()) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return;

        var bs = mc.world.getBlockState(bh.getBlockPos());
        float bestSpeed = 1.0f;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            float sp = s.getMiningSpeedMultiplier(bs);
            if (sp > bestSpeed) { bestSpeed = sp; bestSlot = i; }
        }
        if (bestSlot >= 0 && p.getInventory().getSelectedSlot() != bestSlot) {
            p.getInventory().setSelectedSlot(bestSlot);
        }
    }
}