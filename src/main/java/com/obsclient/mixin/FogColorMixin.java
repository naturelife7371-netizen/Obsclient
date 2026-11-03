package com.obsclient.mixin;

import com.obsclient.module.Visual;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogColorMixin {

    @Inject(
            method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void obsclient$onGetFogColor(Camera camera, float tickProgress, ClientWorld world,
                                         int viewDistance, float skyDarkness,
                                         CallbackInfoReturnable<Vector4f> cir) {
        if (Visual.isActive() && Visual.INSTANCE.customFog.value) {
            int color = Visual.getFogColor();
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            cir.setReturnValue(new Vector4f(r, g, b, 1.0f));
        }
    }
}