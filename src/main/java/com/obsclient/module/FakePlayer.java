package com.obsclient.module;

import com.mojang.authlib.GameProfile;
import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

@SuppressWarnings({"unused"})
public class FakePlayer extends Module {
    public static String status = "Off";
    public static FakePlayer INSTANCE;

    // ==================== SETTINGS ====================
    final NumSetting health = new NumSetting("Health", "Can", 20, 1, 20, 1);
    final BoolSetting copyInventory = new BoolSetting("CopyInventory", "Envanter Kopyala", true);
    final BoolSetting copyRotation  = new BoolSetting("CopyRotation", "Aciyi Kopyala", true);
    final BoolSetting animated      = new BoolSetting("Animated", "Animasyonlu", true);
    final BoolSetting keepOnDisable = new BoolSetting("KeepOnDisable", "Kapatinca Kal", false);
    final BoolSetting spawnAhead    = new BoolSetting("SpawnAhead", "One Dogru", true);
    final NumSetting aheadDist      = new NumSetting("AheadDistance", "One Mesafe", 3.0, 1.0, 10.0, 0.5);

    // ==================== INTERNAL ====================
    private OtherClientPlayerEntity fake = null;
    private int animTick = 0;
    private boolean sneakingPhase = false;
    private final Random rng = new Random();

    public FakePlayer() {
        super("FakePlayer", "FakePlayer", "Spawns a client-side fake player.", "Sahte oyuncu olusturur.", Category.MISC);
        settings.addAll(Arrays.asList(
                health, copyInventory, copyRotation, animated, keepOnDisable,
                spawnAhead, aheadDist
        ));
        INSTANCE = this;
    }

    @Override
    public String status() {
        if (!enabled) return "Off";
        return fake != null ? "Spawned" : "Ready";
    }

    @Override
    public void onEnable() {
        spawn();
    }

    @Override
    public void onDisable() {
        if (!keepOnDisable.value) despawn();
        status = "Off";
    }

    private void spawn() {
        if (mc.world == null || mc.player == null) {
            status = "No world";
            return;
        }
        despawn();

        ClientPlayerEntity p = mc.player;

        double sx = p.getX();
        double sy = p.getY();
        double sz = p.getZ();

        // Öne doğru spawnlama
        if (spawnAhead.value) {
            double yawRad = Math.toRadians(p.getYaw());
            sx -= Math.sin(yawRad) * aheadDist.value;
            sz += Math.cos(yawRad) * aheadDist.value;
        }

        try {
            // DÜZELTME: getName() hatası giderildi. Doğrudan Entity ismini alıyoruz.
            GameProfile profile = new GameProfile(UUID.randomUUID(), p.getName().getString());

            fake = new OtherClientPlayerEntity(mc.world, profile);
            fake.setPosition(sx, sy, sz);
            fake.setYaw(copyRotation.value ? p.getYaw() : 0f);
            fake.setPitch(copyRotation.value ? p.getPitch() : 0f);
            fake.setNoGravity(true);
            fake.setVelocity(0, 0, 0);
            fake.setHealth((float) health.value);

            // Sunucudan gelen varlıklarla çakışmaması için negatif ID veriyoruz.
            fake.setId(-10000 - rng.nextInt(10000));

            if (copyInventory.value) copyLoadout(p, fake);

            mc.world.addEntity(fake);
            status = "Spawned";
        } catch (Throwable t) {
            status = "Add error";
            fake = null;
        }
    }

    private void despawn() {
        if (fake != null) {
            try { fake.discard(); } catch (Throwable ignored) {}
            fake = null;
        }
    }

    private void copyLoadout(PlayerEntity from, PlayerEntity to) {
        to.equipStack(EquipmentSlot.HEAD,  from.getEquippedStack(EquipmentSlot.HEAD).copy());
        to.equipStack(EquipmentSlot.CHEST, from.getEquippedStack(EquipmentSlot.CHEST).copy());
        to.equipStack(EquipmentSlot.LEGS,  from.getEquippedStack(EquipmentSlot.LEGS).copy());
        to.equipStack(EquipmentSlot.FEET,  from.getEquippedStack(EquipmentSlot.FEET).copy());
        to.setStackInHand(Hand.MAIN_HAND, from.getMainHandStack().copy());
        to.setStackInHand(Hand.OFF_HAND,  from.getOffHandStack().copy());
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (fake == null) {
            if (enabled) spawn();
            return;
        }

        if (!fake.isAlive() || fake.isRemoved()) {
            fake = null;
            if (enabled) spawn();
            return;
        }

        fake.setVelocity(0, 0, 0);
        fake.setNoGravity(true);
        fake.fallDistance = 0f;

        if (Math.abs(fake.getHealth() - (float) health.value) > 0.01f) {
            fake.setHealth((float) health.value);
        }

        if (copyRotation.value && mc.player != null) {
            fake.setYaw(mc.player.getYaw());
            fake.setPitch(mc.player.getPitch());
            fake.setHeadYaw(mc.player.getYaw());
            fake.setBodyYaw(mc.player.getYaw());
        }

        if (animated.value) {
            animTick++;
            if (animTick >= 40) {
                animTick = 0;
                sneakingPhase = !sneakingPhase;
            }
            fake.setSneaking(sneakingPhase && animTick < 20);
        }

        status = "Spawned";
    }

    public boolean isSpawned() { return fake != null; }
    public OtherClientPlayerEntity getFake() { return fake; }

    public void respawn() {
        despawn();
        spawn();
    }
}