package com.obsclient.mixin;

import com.obsclient.module.PopChams;
import com.obsclient.module.PopCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void obsclient$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        try {
            if (packet.getStatus() != 35) return; // 35 = Totem Pop (USE_TOTEM_OF_UNDYING)
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            Entity e = packet.getEntity(mc.world);

            if (e instanceof PlayerEntity player) {
                // Hem hayaleti (PopChams) hem de sayacı (PopCounter) tetikle
                PopChams.onPop(player);
                PopCounter.onPop(player);
            }
        } catch (Throwable ignored) {}
    }
}