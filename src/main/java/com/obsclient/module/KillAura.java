package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.ModeSetting;
import com.obsclient.ObsClient.Rot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Arrays;
import java.util.function.Predicate;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class KillAura extends Module {
    public static String status = "Off";
    public static KillAura INSTANCE;

    // ===================== GENEL AYARLAR =====================
    final NumSetting range = new NumSetting("Range", "Menzil", 3.2, 1.0, 6.0, 0.1);
    final NumSetting cooldown = new NumSetting("Cooldown", "Vurus Gecikmesi", 0.85, 0.0, 1.0, 0.05);
    final BoolSetting swordOnly = new BoolSetting("SwordOnly", "Sadece Kilic", false);
    final BoolSetting eatingPause = new BoolSetting("EatingPause", "Yemek Yerken Dur", true);

    // ===================== HEDEF SEÇİMİ =====================
    final ModeSetting targetMode = new ModeSetting("TargetMode", "Hedefleme", 0,
            new String[]{"Distance", "Health"},
            new String[]{"Mesafe", "Can"});
    final BoolSetting players = new BoolSetting("Players", "Oyuncular", true);
    final BoolSetting mobs = new BoolSetting("Mobs", "Yaratiklar", false);
    final BoolSetting passive = new BoolSetting("Passive", "Pasifler", true);
    final BoolSetting neutral = new BoolSetting("Neutral", "Notrler", true);
    final BoolSetting hostile = new BoolSetting("Hostile", "Dusmanlar", true);
    final BoolSetting antiBot = new BoolSetting("AntiBot", "Anti-Bot", true);
    final BoolSetting wallCheck = new BoolSetting("WallCheck", "Duvar Kontrolu", false);

    // ===================== VURUS =====================
    final BoolSetting rotate = new BoolSetting("Rotate", "Sessiz Donus", true);
    final BoolSetting crits = new BoolSetting("Crits", "Full Kritik", true); // Varsayılan olarak açık
    final BoolSetting autoWeapon = new BoolSetting("AutoWeapon", "Oto Silah", true);

    // ===================== STATE =====================
    private LivingEntity target = null;
    private boolean justSwapped = false;

    public KillAura() {
        super("KillAura", "KillAura", "TrollHack Ported KillAura.", "Gelismis KillAura.", Category.COMBAT);
        settings.addAll(Arrays.asList(
                range, cooldown, swordOnly, eatingPause,
                targetMode, players, mobs, passive, neutral, hostile, antiBot, wallCheck,
                rotate, crits, autoWeapon
        ));
        INSTANCE = this;
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Searching"; target = null; justSwapped = false; }
    @Override public void onDisable() { status = "Off"; target = null; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;
        if (eatingPause.value && p.isUsingItem()) return;

        target = findTarget(mc);
        if (target == null) {
            status = "No Target";
            justSwapped = false;
            return;
        }

        if (autoWeapon.value) {
            if (justSwapped) {
                justSwapped = false;
                return;
            }

            ItemStack cur = p.getMainHandStack();
            if (!cur.isIn(ItemTags.SWORDS) && !cur.isIn(ItemTags.AXES)) {
                int s = findHot(p, st -> st.isIn(ItemTags.SWORDS));
                if (s < 0) s = findHot(p, st -> st.isIn(ItemTags.AXES));
                if (s >= 0) {
                    p.getInventory().setSelectedSlot(s);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(s));
                    justSwapped = true;
                    status = "Swapping...";
                    return;
                }
            }
        }

        if (swordOnly.value && !p.getMainHandStack().isIn(ItemTags.SWORDS)) return;

        if (rotate.value) {
            Vec3d aim = target.getEyePos();
            float[] angle = calcAngle(p.getEyePos(), aim);
            // GrimAC Perfect Angle Bypass
            angle[0] += (float)(Math.random() * 0.05 - 0.025);
            angle[1] += (float)(Math.random() * 0.05 - 0.025);
            Rot.request(angle[0], angle[1], 10);
        }

        float progress = p.getAttackCooldownProgress(0.5f);
        if (progress < cooldown.value) {
            status = "Cooldown " + String.format("%.2f", progress);
            return;
        }

        attack(mc, target);
        status = "Hit: " + target.getName().getString();
    }

    private void attack(MinecraftClient mc, LivingEntity target) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return;

        // 1. Rotasyon paketini vuruştan ÖNCE gönder (Grim şartı)
        Rot.flush(mc);

        boolean sprint = p.isSprinting();

        // 2. Kritik vuruş için Sprint'i kapat (Sprint varsa kritik vurmaz)
        if (sprint) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            p.setSprinting(false);
        }

        // 3. Full Kritik Paketleri (Grim/NCP Bypass)
        if (crits.value) {
            // Zıplama efekti ver (Y + 0.0625)
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    p.getX(), p.getY() + 0.0625, p.getZ(), false, p.horizontalCollision));

            // Düşme efekti ver (Y + 0.00001) - Kritik için FallDistance > 0 olmalı
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    p.getX(), p.getY() + 0.00001, p.getZ(), false, p.horizontalCollision));
        }

        // 4. Vuruşu Yap
        mc.interactionManager.attackEntity(p, target);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        p.swingHand(Hand.MAIN_HAND);

        // 5. Kritik Paketlerini Sıfırla (Yere geri dön)
        if (crits.value) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    p.getX(), p.getY(), p.getZ(), true, p.horizontalCollision));
        }

        // 6. Sprint'i geri aç
        if (sprint) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
            p.setSprinting(true);
        }
    }

    private LivingEntity findTarget(MinecraftClient mc) {
        LivingEntity best = null;
        double bestValue = Double.MAX_VALUE;

        if (mc.world == null || mc.player == null) return null;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive() || isFriend(living)) continue;

            if (mc.player.distanceTo(living) > range.value) continue;

            if (wallCheck.value) {
                BlockHitResult r = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), living.getEyePos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (r.getType() != HitResult.Type.MISS) continue;
            }

            if (living instanceof PlayerEntity) {
                if (!players.value) continue;
                if (antiBot.value && living.getName().getString().isEmpty()) continue;
            } else if (living instanceof MobEntity) {
                if (!mobs.value) continue;
                if (living instanceof HostileEntity) { if (!hostile.value) continue; }
                else if (living instanceof PassiveEntity) { if (!passive.value) continue; }
                else { if (!neutral.value) continue; }
            } else {
                continue;
            }

            double value = targetMode.index == 0
                    ? mc.player.squaredDistanceTo(living)
                    : living.getHealth() + living.getAbsorptionAmount();

            if (value < bestValue) {
                bestValue = value;
                best = living;
            }
        }
        return best;
    }

    private int findHot(PlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    private float[] calcAngle(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
                (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
                (float) -Math.toDegrees(Math.atan2(dy, dist))
        };
    }
}