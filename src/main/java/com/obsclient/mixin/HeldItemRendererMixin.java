package com.obsclient.mixin;

import com.obsclient.module.PvPAspect;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onRenderItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                              float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                              OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {

        PvPAspect aspect = PvPAspect.get();
        if (aspect == null || !aspect.isActive()) return;

        // 1.12 Animasyonu (Sallama Pozisyonu)
        if (aspect.oldAnimations.value) {
            matrices.translate(0.0f, 0.0f, -0.1f);
        }

        // Eşya Boyutu (Scale)
        if (aspect.shrinkItems.value) {
            float scale = (float) aspect.itemScale.value;
            matrices.scale(scale, scale, scale);
        }
    }
}