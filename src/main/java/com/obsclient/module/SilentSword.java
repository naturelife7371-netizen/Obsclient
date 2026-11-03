package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class SilentSword extends Module {
    public static String status = "Off";
    public static SilentSword INSTANCE;

    final BoolSetting autoWeapon = new BoolSetting("AutoWeapon", "Oto Silah", true);
    final BoolSetting packetCrit = new BoolSetting("PacketCrit", "Kritik Vurus", false);
    final BoolSetting autoClick = new BoolSetting("AutoClick", "Otomatik Vurus", false);

    private int swapDelay = 0;

    public SilentSword() {
        super("SilentSword", "SilentSword", "Invisible sword hit on click.", "Manuel tiklayinca gorunmez kilicla vur.", Category.COMBAT);
        settings.addAll(Arrays.asList(autoWeapon, packetCrit, autoClick));
        INSTANCE = this;
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Ready"; swapDelay = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        if (swapDelay > 0) { swapDelay--; return; }

        LivingEntity target = findTarget(mc, p);
        if (target == null) {
            status = "Hedef yok";
            return;
        }

        boolean wantHit = autoClick.value || mc.options.attackKey.isPressed();
        if (!wantHit) return;

        // Vanilla vurus hizi kontrolu (Grim bypass icin kritik)
        if (p.getAttackCooldownProgress(0.5f) < 0.92f) {
            status = "Cooldown";
            return;
        }

        // Menzil kontrolu (Goz ile merkez arasi)
        if (p.getEyePos().distanceTo(target.getBoundingBox().getCenter()) > 3.0) {
            status = "Menzil disi";
            return;
        }

        ItemStack mainHand = p.getMainHandStack();
        if (mainHand.isIn(ItemTags.SWORDS) || mainHand.isIn(ItemTags.AXES)) {
            status = "Vanilla Hit";
            return;
        }

        int swordSlot = findWeaponSlot(mc, p);
        if (swordSlot == -1) {
            status = "Kilic yok";
            return;
        }

        int oldSlot = p.getInventory().getSelectedSlot();

        // 1. Kiliça gec
        if (oldSlot != swordSlot) {
            p.getInventory().setSelectedSlot(swordSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(swordSlot));
        }

        // 2. Vur
        performHit(mc, p, target);

        // 3. Eski esyaya don
        if (oldSlot != swordSlot) {
            p.getInventory().setSelectedSlot(oldSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }

        swapDelay = 2; // Paketlerin oturmasi icin 2 tick bekle
        status = "§cSilent Hit!";
    }

    private LivingEntity findTarget(MinecraftClient mc, ClientPlayerEntity p) {
        Entity aimed = mc.targetedEntity;
        if (aimed instanceof LivingEntity living && living.isAlive() && !isFriend(living)) {
            return living;
        }

        LivingEntity best = null;
        double bestDist = 4.0;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double d = p.getEyePos().distanceTo(o.getBoundingBox().getCenter());
            if (d < bestDist) {
                bestDist = d;
                best = o;
            }
        }
        return best;
    }

    private void performHit(MinecraftClient mc, ClientPlayerEntity p, LivingEntity target) {
        Vec3d aim = target.getBoundingBox().getCenter();
        float[] angle = calcAngle(p.getEyePos(), aim);

        // 1. Sunucuya rotasyonu zorla (Grim icin sart)
        Rot.request(angle[0], angle[1], 50);
        Rot.flush(mc);

        // 2. Kritik vurus istege bagli
        if (packetCrit.value && p.isOnGround() && !p.isTouchingWater()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY() + 0.0625, p.getZ(), false, p.horizontalCollision));
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, p.isSneaking()));
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(), p.getY(), p.getZ(), true, p.horizontalCollision));
        } else {
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, p.isSneaking()));
        }

        // 3. Kol sallama
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        p.swingHand(Hand.MAIN_HAND);
    }

    private int findWeaponSlot(MinecraftClient mc, ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isIn(ItemTags.SWORDS)) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isIn(ItemTags.AXES)) return i;
        }
        if (autoWeapon.value) {
            for (int i = 9; i < 36; i++) {
                if (p.getInventory().getStack(i).isIn(ItemTags.SWORDS)) {
                    int empty = -1;
                    for (int j = 0; j < 9; j++) {
                        if (p.getInventory().getStack(j).isEmpty()) { empty = j; break; }
                    }
                    if (empty < 0) empty = 8;
                    mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, empty, net.minecraft.screen.slot.SlotActionType.SWAP, p);
                    return empty;
                }
            }
        }
        return -1;
    }
}