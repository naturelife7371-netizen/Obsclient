package com.obsclient.mixin;

import com.obsclient.module.Visual;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeInterpolator;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnvironmentAttributeInterpolator.class)
public class SkyColorMixin {

    @Inject(
            method = "get(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void obsclient$onGet(EnvironmentAttribute<?> attribute, float tickProgress,
                                 CallbackInfoReturnable<Object> cir) {
        if (attribute == EnvironmentAttributes.SKY_COLOR_VISUAL
                && Visual.isActive() && Visual.INSTANCE.customSky.value) {
            cir.setReturnValue(0xFF000000 | (Visual.getSkyColor() & 0xFFFFFF));
        }
    }
}