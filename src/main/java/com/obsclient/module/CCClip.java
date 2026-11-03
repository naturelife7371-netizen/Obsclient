package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import static com.obsclient.ObsClient.*;

public class CCClip extends Module {
    public static String status = "Off";
    public static CCClip INSTANCE;

    public CCClip() {
        super("CCClip", "CCClip", "Instant corner clip bypass.", "Aninda koseden gecis.", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.getNetworkHandler() == null || mc.world == null) {
            toggle(); return;
        }

        status = "Clipping...";

        double yawRad = Math.toRadians(p.getYaw());
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);

        double[] diagOffset = new double[]{dx * 0.44, dz * 0.44};
        float yawMod = Math.abs(p.getYaw() % 90f);
        boolean diagonal = yawMod > 35f && yawMod < 55f;

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));

        if (diagonal) {
            double dist = 0.51;
            int height = getHeight(mc, diagOffset[0], diagOffset[1]);

            // 1. Paket: Yukari isinla
            p.setPosition(p.getX() + dx * dist, p.getY() + height, p.getZ() + dz * dist);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), true, false));

            // 2. Paket: Asagi isinla (icinden gec)
            height = mc.world.isAir(BlockPos.ofFloored(p.getX() + diagOffset[0], p.getY() - 2, p.getZ() + diagOffset[1])) ? 2 : 1;
            p.setPosition(p.getX() + dx * dist, p.getY() - height, p.getZ() + dz * dist);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), true, false));
        } else {
            double dist = 0.57;
            int height = getHeight(mc, diagOffset[0], diagOffset[1]);

            p.setPosition(p.getX() + dx * dist, p.getY() + height, p.getZ() + dz * dist);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), true, false));

            p.setPosition(p.getX() + dx * dist, p.getY(), p.getZ() + dz * dist);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), true, false));

            height = mc.world.isAir(BlockPos.ofFloored(p.getX() + diagOffset[0], p.getY() - 2, p.getZ() + diagOffset[1])) ? 2 : 1;
            p.setPosition(p.getX() + dx * dist, p.getY() - height, p.getZ() + dz * dist);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), true, false));
        }

        toggle(); // İşlem bitince kapan
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient mc) {}

    private int getHeight(net.minecraft.client.MinecraftClient mc, double offsetX, double offsetZ) {
        if (mc.player == null || mc.world == null) return 1;
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(offsetX, 0, offsetZ);
        HitResult result = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return result.getType().equals(HitResult.Type.MISS) ? 1 : 2;
    }
}