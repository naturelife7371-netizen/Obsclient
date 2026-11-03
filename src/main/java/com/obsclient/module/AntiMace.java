package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.Notification;
import com.obsclient.ObsClient.Rot;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class AntiMace extends Module {
    public static String status = "Off";

    final NumSetting range      = new NumSetting("DetectRange", "Tespit", 12, 4, 25, 1);
    final NumSetting heightDiff = new NumSetting("HeightDiff", "Yuks. Fark", 2.0, 1.5, 10, 0.5);
    final NumSetting fallSpeed  = new NumSetting("FallSpeed", "Dusus Hizi", -0.1, -1.0, 0.1, 0.05);

    // YENI EKLENEN AYARLAR
    final BoolSetting placeObsidian = new BoolSetting("PlaceObsidian", "Obsidyen Koy", true);
    final BoolSetting fullTrap      = new BoolSetting("FullTrap", "Tam Tuzak (Etrafi Kapat)", false);

    final BoolSetting autoShield = new BoolSetting("AutoShield", "Oto Kalkan", true);
    final BoolSetting dodge      = new BoolSetting("Dodge", "Yandan Kac", true);
    final BoolSetting alert      = new BoolSetting("Alert", "Uyari", true);

    private boolean shielding = false;
    private long lastAlert = 0;
    private int shieldCd = 0;

    public AntiMace() {
        super("AntiMace", "AntiMace", "Shield + dodge + trap vs mace.", "Kalkan + kacis + tuzak (mace).", Category.COMBAT);
        settings.addAll(Arrays.asList(range, heightDiff, fallSpeed, placeObsidian, fullTrap, autoShield, dodge, alert));
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Watching"; }
    @Override public void onDisable() { stopShield(mc); status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) return;

        if (shieldCd > 0) shieldCd--;

        // 1) Tehdit tespiti
        PlayerEntity threat = null;
        double closest = Double.MAX_VALUE;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
            double d = p.distanceTo(o);
            if (d > range.value || d >= closest) continue;
            if (o.getY() - p.getY() < heightDiff.value) continue;
            if (!o.getMainHandStack().isOf(Items.MACE)) continue;

            // Düşüş hızı kontrolü
            if (o.fallDistance <= 0.0 && o.getVelocity().y > fallSpeed.value) continue;

            threat = o;
            closest = d;
        }

        // 2) Tehdit yoksa kalkanı indir ve dur
        if (threat == null) {
            if (shielding) stopShield(mc);
            status = "Safe";
            return;
        }

        status = "Mace: " + threat.getName().getString();

        // 3) Uyari
        if (alert.value && System.currentTimeMillis() - lastAlert > 1000) {
            Notification.send("§cMace Geliyor!", 0xFFFF3030);
            lastAlert = System.currentTimeMillis();
        }

        // 4) OBSIDYEN KOYMA (En güvenli yöntem)
        if (placeObsidian.value && findObsidianSlot(p) != -1) {
            if (buildDefense(mc, p)) {
                return; // Başarıyla blok koyduysa diğerlerini atla
            }
        }

        // 5) AutoShield (Kalkan Kaldırma) - Obsidyen yoksa veya işe yaramazsa
        if (autoShield.value && shieldCd == 0) {
            if (p.getOffHandStack().isOf(Items.SHIELD)) {
                if (!p.isUsingItem()) {
                    mc.interactionManager.interactItem(p, Hand.OFF_HAND);
                    p.swingHand(Hand.OFF_HAND);
                }
                shielding = true;
            } else {
                int slot = findHot(p, s -> s.isOf(Items.SHIELD));
                if (slot >= 0 && p.getInventory().getSelectedSlot() != slot) {
                    p.getInventory().setSelectedSlot(slot);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                    shieldCd = 2;
                } else if (slot >= 0) {
                    if (!p.isUsingItem()) {
                        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                        p.swingHand(Hand.MAIN_HAND);
                    }
                    shielding = true;
                }
            }
        }

        // 6) Dodge — Yandan kaçış (Son çare)
        if (dodge.value && p.isOnGround()) {
            p.jump();
        }
    }

    // ===================== BLOK KOYMA (DEFENSE) =====================
    private boolean buildDefense(MinecraftClient mc, ClientPlayerEntity p) {
        BlockPos feet = p.getBlockPos();

        // Üstümüze gelen Mace'e karşı tavan ör (kafanın 2 blok üstü)
        BlockPos[] targets;
        if (fullTrap.value) {
            // Etrafı ve üstü tamamen kapat
            targets = new BlockPos[]{
                    feet.up(2),               // Tepe
                    feet.add(1, 1, 0), feet.add(-1, 1, 0), // Kafa yanları
                    feet.add(0, 1, 1), feet.add(0, 1, -1)
            };
        } else {
            // Sadece tepeyi kapat (Mace yukarıdan geldiği için)
            targets = new BlockPos[]{ feet.up(2) };
        }

        for (BlockPos pos : targets) {
            if (!mc.world.getBlockState(pos).isReplaceable()) continue; // Zaten doluysa geç
            if (placeBlock(mc, p, pos)) return true; // Bir tane bile koysa yeter
        }
        return false;
    }

    private boolean placeBlock(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos) {
        int slot = findObsidianSlot(p);
        if (slot == -1) return false;

        // Destek bloğu bul
        BlockPos support = null;
        Direction face = null;
        Vec3d hitVec = null;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState s = mc.world.getBlockState(n);
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            support = n;
            face = d.getOpposite();
            hitVec = Vec3d.ofCenter(n).add(
                    face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            break;
        }
        if (support == null || p.getEyePos().distanceTo(hitVec) > 4.5) return false;

        // Slot değiştir
        int prevSlot = p.getInventory().getSelectedSlot();
        if (prevSlot != slot) {
            p.getInventory().setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        // Sessiz rotasyon paketini hemen gönder
        float[] angle = calcAngle(p.getEyePos(), hitVec);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(angle[0], angle[1], p.isOnGround(), p.horizontalCollision));
        Rot.request(angle[0], angle[1], 10);

        // Blok yerleştir (Sequence ID ile)
        int seq = p.currentScreenHandler.getRevision();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(hitVec, face, support, false), seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        p.swingHand(Hand.MAIN_HAND);

        // Eski slota dön
        if (prevSlot != slot) {
            p.getInventory().setSelectedSlot(prevSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        }
        return true;
    }

    private int findObsidianSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.OBSIDIAN)) return i;
        }
        return -1;
    }

    private void stopShield(MinecraftClient mc) {
        if (mc.player != null && mc.interactionManager != null) {
            if (mc.player.isUsingItem()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
            shielding = false;
        }
    }

    private int findHot(PlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }
}