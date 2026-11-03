package com.obsclient.mixin;

import com.obsclient.module.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At("TAIL")
    )
    private void onCameraUpdate(World area, Entity focusedEntity, boolean thirdPerson,
                                boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (Freecam.INSTANCE != null && Freecam.INSTANCE.isActive()) {
            Freecam fc = Freecam.INSTANCE;

            this.setPos(
                    MathHelper.lerp(tickProgress, fc.prevX, fc.posX),
                    MathHelper.lerp(tickProgress, fc.prevY, fc.posY),
                    MathHelper.lerp(tickProgress, fc.prevZ, fc.posZ)
            );
            this.setRotation(fc.camYaw, fc.camPitch);
        }
    }
}