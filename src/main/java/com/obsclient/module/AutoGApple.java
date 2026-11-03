package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;

@SuppressWarnings("unused")
public class AutoGApple extends Module {
    public static String status = "Off";
    public static AutoGApple INSTANCE;

    // ===================== AYARLAR =====================
    final NumSetting health = new NumSetting("Health", "Min Saglik", 15.0, 1.0, 20.0, 0.5);
    final ModeSetting mode = new ModeSetting("Mode", "Elma Tipi", 0,
            new String[]{"Both", "Enchanted", "Golden"},
            new String[]{"Ikisi de", "Buyulu Elma", "Normal Elma"});

    // ===================== STATE =====================
    private int oldSlot = -1;
    private boolean isEating = false;
    private Hand eatingHand = Hand.MAIN_HAND;
    private int eatTicks = 0;

    public AutoGApple() {
        super("AutoGApple", "AutoGApple", "Auto eats gapple when low health.", "Can azalinca altin elma yer.", Category.COMBAT);
        settings.addAll(Arrays.asList(health, mode));
        INSTANCE = this;
    }

    @Override public String status() { return status; }
    @Override public void onEnable() { status = "Ready"; isEating = false; oldSlot = -1; eatTicks = 0; }
    @Override public void onDisable() { stopEating(mc); status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        float hp = p.getHealth() + p.getAbsorptionAmount();

        // 1. EĞER ZATEN YİYORSAK
        if (isEating) {
            eatTicks++;
            // Can dolduysa, elma bittiyse veya yeme süresi (32 tick) geçtiyse durdur
            if (hp > health.value + 1.0f || p.getHealth() <= 0 || eatTicks > 40) {
                stopEating(mc);
                return;
            }

            // Elimizdeki elma bitti mi?
            ItemStack activeStack = eatingHand == Hand.MAIN_HAND ? p.getMainHandStack() : p.getOffHandStack();
            if (!isGapple(activeStack)) {
                stopEating(mc);
                return;
            }

            // Yeme işlemi devam etmiyorsa yeniden başlat (sunucu reddetmiş olabilir)
            if (!p.isUsingItem()) {
                startUseItem(mc, p);
            }

            status = "Eating (" + eatTicks + "t)";
            return;
        }

        // 2. CAN DOLUYSA HİÇBİR ŞEY YAPMA
        if (hp > health.value) {
            status = "Healthy";
            return;
        }

        // 3. CAN AZSA ELMAYI BUL VE YEMEYE BAŞLA
        int slot = findGapple(p);
        if (slot != -1) {
            startEating(mc, p, slot);
        } else {
            status = "No Gapple";
        }
    }

    private void startEating(MinecraftClient mc, ClientPlayerEntity p, int slot) {
        oldSlot = p.getInventory().getSelectedSlot();
        eatTicks = 0;

        // Slot belirleme: -2 = offhand, -3 = zaten mainhand'de
        if (slot >= 0) {
            p.getInventory().setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            eatingHand = Hand.MAIN_HAND;
        } else if (slot == -2) {
            eatingHand = Hand.OFF_HAND;
        } else {
            eatingHand = Hand.MAIN_HAND;
        }

        isEating = true;
        status = "Eating";
        startUseItem(mc, p);
    }

    private void startUseItem(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.getNetworkHandler() == null || mc.interactionManager == null) return;

        // 1.21'de yeme paketini göndermenin en güvenilir yolu interactItem kullanmaktır
        mc.interactionManager.interactItem(p, eatingHand);

        // Yedek olarak doğrudan paket gönder (bazı sunucularda interactionManager yetersiz kalabilir)
        float yaw = p.getYaw();
        float pitch = p.getPitch();
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(eatingHand, p.currentScreenHandler.getRevision(), yaw, pitch));
    }

    private void stopEating(MinecraftClient mc) {
        if (mc.player != null && mc.interactionManager != null) {
            // Yeme işlemini durdur
            if (mc.player.isUsingItem()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }

            // Eski eşyaya (kılıç/totem) geri dön
            if (oldSlot != -1) {
                mc.player.getInventory().setSelectedSlot(oldSlot);
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
                }
                oldSlot = -1;
            }
        }

        isEating = false;
        eatTicks = 0;
        status = "Ready";
    }

    private int findGapple(ClientPlayerEntity p) {
        boolean eatNormal = mode.index == 0 || mode.index == 2;
        boolean eatEnchanted = mode.index == 0 || mode.index == 1;

        // Offhand kontrolü
        if (isGapple(p.getOffHandStack(), eatNormal, eatEnchanted)) return -2;
        // Mainhand kontrolü
        if (isGapple(p.getMainHandStack(), eatNormal, eatEnchanted)) return -3;

        // Hotbar (0-8) kontrolü
        for (int i = 0; i < 9; i++) {
            if (isGapple(p.getInventory().getStack(i), eatNormal, eatEnchanted)) return i;
        }
        return -1;
    }

    private boolean isGapple(ItemStack stack) {
        return isGapple(stack, true, true);
    }

    private boolean isGapple(ItemStack stack, boolean normal, boolean enchanted) {
        if (stack.isEmpty()) return false;
        if (normal && stack.isOf(Items.GOLDEN_APPLE)) return true;
        if (enchanted && stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return true;
        return false;
    }
}