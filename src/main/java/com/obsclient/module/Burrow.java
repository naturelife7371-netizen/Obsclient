package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

public class Burrow extends Module {
    public static String status = "Off";

    final BoolSetting rotate = new BoolSetting("Rotate", "Sunucuya Bak", true);
    private int cd = 0;

    public Burrow() {
        super("Burrow", "Burrow", "Burrow into block.", "Bloga gomul.", Category.COMBAT);
        settings.addAll(Arrays.asList(rotate));
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Ready"; cd = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.currentScreen != null) return;

        if (cd > 0) {
            cd--;
            return;
        }

        BlockPos pos = p.getBlockPos();

        // Zaten bir bloğun içindeysek işlem yapma
        if (!mc.world.getBlockState(pos).isReplaceable()) {
            status = "Burrowed";
            return;
        }

        // Hotbar'da blok ara (Obsidyen, Ağlayan Obsidyen, Ender Chest, Örs)
        int slot = findHot(p, s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN) ||
                s.isOf(Items.ENDER_CHEST) || s.isOf(Items.ANVIL));

        if (slot < 0) {
            status = "No block";
            return;
        }

        int prevSlot = p.getInventory().getSelectedSlot();

        // 1. Elindeki eşyayı bloğa çevir ve sunucuya bildir
        p.getInventory().setSelectedSlot(slot);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        // 2. Bloğun konumunu ve vurulacak yüzü ayarla (ayağımızın altındaki bloğun üstü)
        Vec3d hitVec = Vec3d.ofCenter(pos.down()).add(0, 0.5, 0);

        // 3. Eğer "Sunucuya Bak" açıksa sunucuya aşağı baktığımızı gizlice bildir
        if (rotate.value) {
            float[] angle = calcAngle(p.getEyePos(), hitVec);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(angle[0], angle[1], p.isOnGround(), p.horizontalCollision));
        }

        // 4. BLOK KOYMA PAKETİNİ DOĞRUDAN SUNUCUYA GÖNDER
        // (İstemci çarpışmasını atlar, böylece oyun bloğu içimize koymamızı engelleyemez)
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos.down(), false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, p.currentScreenHandler.getRevision()));

        // 5. El sallama animasyonu
        p.swingHand(Hand.MAIN_HAND);

        // 6. Eski slotuna geri dön (Kılıcını eline al)
        p.getInventory().setSelectedSlot(prevSlot);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));

        status = "Burrowed";
        cd = 5; // Paket spam'ini önlemek için 5 tick bekle
    }
}