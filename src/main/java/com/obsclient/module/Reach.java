package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

import static com.obsclient.ObsClient.epos;

public class Reach extends Module {
    public static String status = "Off";

    // Limitleri sunucu sınırlarının çok ötesine taşıdık
    final NumSetting entityReach = new NumSetting("EntityReach", "Entity Reach", 3.5, 3.0, 8.0, 0.1);
    final NumSetting blockReach  = new NumSetting("BlockReach", "Blok Reach", 5.0, 4.5, 10.0, 0.1);

    // YENİ: Paket hilesi ve Silah kontrolü
    final BoolSetting packetSpoof = new BoolSetting("PacketSpoof", "Paket Hilesi (Bypass)", true);
    final BoolSetting weaponOnly  = new BoolSetting("WeaponOnly", "Sadece Silahla", false);

    public Reach() {
        super("Reach", "Reach", "Increase reach (Packet Bypass).", "Menzil artir (Paket Bypass).", Category.COMBAT);
        settings.addAll(Arrays.asList(entityReach, blockReach, packetSpoof, weaponOnly));
    }

    @Override public String status() {
        return enabled ? String.format("%.1f / %.1f", entityReach.value, blockReach.value) : "Off";
    }

    private void apply(ClientPlayerEntity p, double e, double b) {
        EntityAttributeInstance ea = p.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (ea != null) ea.setBaseValue(e);
        EntityAttributeInstance ba = p.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        if (ba != null) ba.setBaseValue(b);
    }

    @Override
    public void onDisable() {
        ClientPlayerEntity p = mc.player;
        if (p != null) apply(p, 3.0, 4.5);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // Menzil attribute'larını sürekli uygula
        apply(mc.player, entityReach.value, blockReach.value);

        if (weaponOnly.value) {
            if (!mc.player.getMainHandStack().isIn(ItemTags.SWORDS) && !mc.player.getMainHandStack().isIn(ItemTags.AXES)) {
                return;
            }
        }

        // --- GÜÇLÜ BYPASS: PAKET SEVİYESİNDE IŞINLANMA (SILENT REACH) ---
        if (packetSpoof.value && mc.options.attackKey.isPressed()) {
            Entity target = mc.targetedEntity;
            if (target != null) {
                double dist = mc.player.distanceTo(target);

                // Eğer hedef normal menzil dışındaysa ama bizim ayarımızın içindeyse
                if (dist > 3.0 && dist <= entityReach.value) {
                    // Hedefe doğru giden vektörü hesapla
                    Vec3d targetVec = new Vec3d(target.getX(), target.getY(), target.getZ());
                    Vec3d myVec = epos(mc.player); // getPos() hatasını çözmek için epos kullanıldı

                    Vec3d dir = targetVec.subtract(myVec).normalize();

                    // Kendimizi sunucuya hedefin 2.9 bloğunda gibi göster
                    Vec3d spoofPos = targetVec.subtract(dir.multiply(2.9));

                    // 1. Sunucuya ışınlanma paketini gönder (Kamera oynamaz, görünmez)
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            spoofPos.x, spoofPos.y, spoofPos.z, mc.player.isOnGround(), mc.player.horizontalCollision
                    ));

                    // 2. Vuruş paketini gönder
                    mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, false));
                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                    // 3. Anında eski yerimize geri ışınlan
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            myVec.x, myVec.y, myVec.z, mc.player.isOnGround(), mc.player.horizontalCollision
                    ));
                }
            }
        }
    }
}