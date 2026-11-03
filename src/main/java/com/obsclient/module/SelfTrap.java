package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Rot;
import com.obsclient.ObsClient.Notification;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class SelfTrap extends Module {
    public static String status = "Off";
    public static SelfTrap INSTANCE;

    // ===================== AYARLAR =====================
    final ModeSetting topMode = new ModeSetting("TopMode", "Ust Kapatma", 1,
            new String[]{"AntiFacePlace", "Full", "Top", "None"},
            new String[]{"Yuz Korumasi", "Tam", "Ust", "Kapali"});

    final ModeSetting bottomMode = new ModeSetting("BottomMode", "Zemin Kapatma", 1,
            new String[]{"Single", "None"},
            new String[]{"Tek Blok", "Kapali"});

    final ModeSetting blockType = new ModeSetting("BlockType", "Blok Tipi", 1,
            new String[]{"Obsidian", "Obsidian+Ender", "Any"},
            new String[]{"Obsidyen", "Obsidyen+Ender", "Herhangi"});

    final NumSetting placeRange = new NumSetting("PlaceRange", "Koyma Menzili", 4.8, 3.0, 6.0, 0.1);
    final NumSetting placeDelay = new NumSetting("PlaceDelay", "Koyma Gecikmesi (Tick)", 1.0, 0.0, 10.0, 1.0);
    final NumSetting threatRange = new NumSetting("ThreatRange", "Tehdit Menzili", 6.0, 0.0, 30.0, 1.0);

    final BoolSetting center = new BoolSetting("Center", "Merkezle (Riskli)", false);
    final BoolSetting rotate = new BoolSetting("Rotate", "Grim Rotasyonu", true);
    final BoolSetting clearCrystals = new BoolSetting("ClearCrystals", "Kristal Temizle", true);
    final BoolSetting autoOff = new BoolSetting("AutoOff", "Bitince Kapan", true);
    final BoolSetting onlyGround = new BoolSetting("OnlyGround", "Sadece Yerde", true);

    // ===================== STATE =====================
    private int delay = 0;
    private final List<BlockPos> renderPositions = new ArrayList<>();

    public SelfTrap() {
        super("SelfTrap", "SelfTrap", "Traps yourself in obsidian (Meteor Enhanced).", "Kendi etrafini obsidyenle kapatir.", Category.COMBAT);
        settings.addAll(Arrays.asList(
                topMode, bottomMode, blockType, placeRange, placeDelay, threatRange,
                center, rotate, clearCrystals, autoOff, onlyGround
        ));
        INSTANCE = this;
    }

    @Override public String status() { return status; }

    @Override
    public void onEnable() {
        status = "Ready";
        delay = 0;
        renderPositions.clear();
        if (center.value) centerPlayer(mc);
    }

    @Override
    public void onDisable() {
        status = "Off";
        renderPositions.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        if (delay > 0) { delay--; return; }
        if (mc.player.isGliding()) { status = "Gliding"; return; }
        if (onlyGround.value && !mc.player.isOnGround()) { status = "Air"; return; }

        // 1. Tehdit Kontrolü
        if (threatRange.value > 0) {
            PlayerEntity nearest = null;
            double bestD = threatRange.value;
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == mc.player || !o.isAlive() || o.isSpectator() || isFriend(o)) continue;
                double d = mc.player.distanceTo(o);
                if (d < bestD) { bestD = d; nearest = o; }
            }
            if (nearest == null) { status = "No Threat"; return; }
        }

        // 2. Merkezleme
        if (center.value && mc.player.isOnGround() && mc.player.getVelocity().lengthSquared() < 0.01) {
            centerPlayer(mc);
        }

        // 3. Pozisyonları Hesapla
        BlockPos feet = mc.player.getBlockPos();
        List<BlockPos> targets = new ArrayList<>();

        if (topMode.index == 0) { // AntiFacePlace
            targets.add(feet.add(1, 1, 0)); targets.add(feet.add(-1, 1, 0));
            targets.add(feet.add(0, 1, 1)); targets.add(feet.add(0, 1, -1));
        } else if (topMode.index == 1) { // Full
            targets.add(feet.add(0, 2, 0));
            targets.add(feet.add(1, 1, 0)); targets.add(feet.add(-1, 1, 0));
            targets.add(feet.add(0, 1, 1)); targets.add(feet.add(0, 1, -1));
        } else if (topMode.index == 2) { // Top
            targets.add(feet.add(0, 2, 0));
        }

        if (bottomMode.index == 0) { // Single
            targets.add(feet.down());
        }

        // 4. Boş Olanları Filtrele
        renderPositions.clear();
        List<BlockPos> todo = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (mc.world.getBlockState(pos).isReplaceable()) {
                todo.add(pos);
            }
        }
        renderPositions.addAll(todo);

        if (todo.isEmpty()) {
            status = "Trapped";
            if (autoOff.value) {
                toggle();
                Notification.send("SelfTrap Bitti", 0xFF3FD07A);
            }
            return;
        }

        // 5. Blok Bul
        Predicate<ItemStack> pred = getBlockPredicate();
        if (findInv(mc.player, pred) < 0) { status = "No Blocks"; return; }

        // 6. En Yakından Uzağa Sırala
        Vec3d eye = mc.player.getEyePos();
        todo.sort(Comparator.comparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b))));

        // 7. Yerleştir
        int placed = 0;
        for (BlockPos pos : todo) {
            if (placed >= 1) break;

            if (eye.distanceTo(Vec3d.ofCenter(pos)) > placeRange.value) continue;

            if (hasCrystalOrEntity(mc, pos)) {
                if (clearCrystals.value) breakCrystals(mc, pos);
                continue;
            }

            if (placeBlock(mc, pos, pred)) {
                placed++;
                delay = (int) placeDelay.value;
                status = "Placing...";
            } else {
                break; // Bir sonraki tick bekle (item çekildiyse vs)
            }
        }
    }

    private void centerPlayer(MinecraftClient mc) {
        if (mc.player == null) return;
        double cx = Math.floor(mc.player.getX()) + 0.5;
        double cz = Math.floor(mc.player.getZ()) + 0.5;
        if (Math.abs(mc.player.getX() - cx) > 0.1 || Math.abs(mc.player.getZ() - cz) > 0.1) {
            mc.player.setPosition(cx, mc.player.getY(), cz);
        }
    }

    private Predicate<ItemStack> getBlockPredicate() {
        if (blockType.index == 0) return s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN);
        if (blockType.index == 1) return s -> s.isOf(Items.OBSIDIAN) || s.isOf(Items.CRYING_OBSIDIAN) || s.isOf(Items.ENDER_CHEST);
        return s -> s.getItem() instanceof net.minecraft.item.BlockItem;
    }

    private boolean placeBlock(MinecraftClient mc, BlockPos pos, Predicate<ItemStack> pred) {
        if (mc.player == null || mc.getNetworkHandler() == null) return false;

        int slot = findHot(mc.player, pred);

        // 1. Envanterden Çekme Kontrolü
        if (slot < 0) {
            if (findInv(mc.player, pred) >= 0) {
                toHotbar(mc, mc.player, pred);
                status = "Pulling blocks...";
            }
            return false; // Slot değişimi bitene kadar bekle
        }

        // 2. Destek Bloğu Bul
        BlockPos support = null;
        Direction face = null;
        Vec3d hitVec = null;

        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState s = mc.world.getBlockState(n);
            // Sadece katı ve sıvı olmayan bloklara tıkla
            if (s.isAir() || !s.getFluidState().isEmpty() || !s.isSolidBlock(mc.world, n)) continue;
            support = n;
            face = d.getOpposite();
            hitVec = Vec3d.ofCenter(n).add(
                    face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5,
                    face.getOffsetZ() * 0.5);
            break;
        }
        if (support == null) return false;

        // 3. KENDİ HITBOX'IMIZI KONTROL ET (Ayağımızın içine blok koymayalım)
        Box placeBox = new Box(pos);
        if (mc.player.getBoundingBox().intersects(placeBox) && !pos.equals(mc.player.getBlockPos().down())) {
            return false;
        }

        int prevSlot = mc.player.getInventory().getSelectedSlot();

        // --- GRIMAC BYPASS (PAKET SIRASI) ---

        // ADIM 1: Rotasyon (Sessiz)
        if (rotate.value) {
            float[] angle = calcAngle(mc.player.getEyePos(), hitVec);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    angle[0], angle[1], mc.player.isOnGround(), mc.player.horizontalCollision));
            Rot.request(angle[0], angle[1], 10);
        }

        // ADIM 2: Slot Senkronizasyonu (Sunucu ele obsidyen alındığını görsün)
        if (prevSlot != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        // ADIM 3: Bloğu Yerleştir
        int seq = mc.player.currentScreenHandler.getRevision();
        BlockHitResult hr = new BlockHitResult(hitVec, face, support, false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hr, seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        mc.player.swingHand(Hand.MAIN_HAND);

        // ADIM 4: Eski eşyaya (Kılıç/Totem) geri dön
        if (prevSlot != slot) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        }

        if (rotate.value) Rot.postAttack();
        return true;
    }

    private boolean hasCrystalOrEntity(MinecraftClient mc, BlockPos pos) {
        Box box = new Box(pos).expand(0.1);
        for (Entity e : mc.world.getOtherEntities(null, box)) {
            if (e instanceof ItemEntity || e instanceof ExperienceOrbEntity) continue;
            return true;
        }
        return false;
    }

    private void breakCrystals(MinecraftClient mc, BlockPos pos) {
        Box box = new Box(pos);
        for (Entity e : mc.world.getOtherEntities(null, box)) {
            if (e instanceof EndCrystalEntity crystal) {
                if (rotate.value) {
                    Vec3d cPos = new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
                    float[] angle = calcAngle(mc.player.getEyePos(), cPos);
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(angle[0], angle[1], mc.player.isOnGround(), mc.player.horizontalCollision));
                }
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (!enabled || mc.player == null || mc.world == null) return;

        for (BlockPos pos : renderPositions) {
            Box box = new Box(pos);
            int[] proj = projectBox(mc, box, delta);
            if (proj != null) {
                g.fill(proj[0], proj[1], proj[2], proj[3], 0x40FF0000);
                outline(g, proj[0], proj[1], proj[2], proj[3], 0xFFFF0000);
            }
        }
    }
}