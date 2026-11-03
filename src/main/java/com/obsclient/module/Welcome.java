package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/**
 * ==========================================================
 *  Welcome — "Phantom" Modern HUD
 * ==========================================================
 *  • Gelişmiş Karşılama Metni (Ping, FPS, Gradyan)
 *  • Detaylı Envanter Görüntüsü (Slot arka planları, Gölgeler)
 *  • Gelişmiş Zırh ve Dayanıklılık Barları (Dinamik Renkler)
 *  • ObsGui açıkken sol tık ile fareyle taşınabilir.
 */
public class Welcome extends Module {
    public static String status = "Off";
    public static Welcome INSTANCE;

    // ⚡ Konum ve Metin Ayarları
    final TextSetting welcomeText = new TextSetting("Text", "Yazi", "Welcome");

    final NumSetting welcomeX = new NumSetting("WelcomeX", "Karsilama X", 10, -1000, 2000, 1);
    final NumSetting welcomeY = new NumSetting("WelcomeY", "Karsilama Y", 10, -1000, 2000, 1);

    final NumSetting invX = new NumSetting("InvX", "Envanter X", 10, -1000, 2000, 1);
    final NumSetting invY = new NumSetting("InvY", "Envanter Y", 40, -1000, 2000, 1);

    final NumSetting armorX = new NumSetting("ArmorX", "Zirh X", 10, -1000, 2000, 1);
    final NumSetting armorY = new NumSetting("ArmorY", "Zirh Y", 130, -1000, 2000, 1);

    final BoolSetting showInventory = new BoolSetting("ShowInventory", "Envanteri Goster", true);
    final BoolSetting showArmor = new BoolSetting("ShowArmor", "Zirhi Goster", true);

    // Sürükleme durumları
    private boolean draggingWelcome = false;
    private boolean draggingInv = false;
    private boolean draggingArmor = false;
    private double dragX, dragY;

    public Welcome() {
        super("Welcome", "Welcome", "Modern Draggable HUD.", "Modern Suruklenebilir HUD.", Category.RENDER);
        settings.addAll(Arrays.asList(
                welcomeText, welcomeX, welcomeY, invX, invY, armorX, armorY, showInventory, showArmor
        ));
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? "On" : "Off"; }
    @Override public void onEnable() { status = "On"; }
    @Override public void onDisable() { status = "Off"; }
    @Override public void onTick(MinecraftClient mc) {}

    public void render(DrawContext g, MinecraftClient mc, float delta) {
        if (!enabled || mc.player == null) return;

        ClientPlayerEntity p = mc.player;
        var tr = mc.textRenderer;

        boolean editMode = mc.currentScreen instanceof com.obsclient.ObsClient.ObsGui;
        double mouseX = mc.mouse.getX() * (double) mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
        double mouseY = mc.mouse.getY() * (double) mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
        boolean mouseDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // ==========================================
        // 1. WELCOME METNİ (Modern Gradyan & Gölge)
        // ==========================================
        String text = welcomeText.value + " " + p.getName().getString() + " :)";
        int textW = tr.getWidth(text);
        int wWidth = textW + 16;
        int wHeight = 20;

        int wx = (int) welcomeX.value;
        int wy = (int) welcomeY.value;

        // Sürükleme Mantığı
        if (editMode) {
            if (mouseDown) {
                if (!draggingWelcome && !draggingInv && !draggingArmor) {
                    if (mouseX >= wx && mouseX <= wx + wWidth && mouseY >= wy && mouseY <= wy + wHeight) {
                        draggingWelcome = true;
                        dragX = mouseX - wx;
                        dragY = mouseY - wy;
                    }
                }
            } else draggingWelcome = false;
        } else draggingWelcome = false;

        if (draggingWelcome) {
            welcomeX.value = mouseX - dragX;
            welcomeY.value = mouseY - dragY;
            wx = (int) welcomeX.value;
            wy = (int) welcomeY.value;
        }

        // Çizim: Gölge -> Arka Plan (Gradyan) -> Sol Çizgi -> Metin
        g.fill(wx - 2, wy - 2, wx + wWidth + 2, wy + wHeight + 2, 0x40000000); // Gölge
        g.fillGradient(wx, wy, wx + wWidth, wy + wHeight, 0xFF1E1E25, 0xFF131318); // Arka plan
        g.fill(wx, wy, wx + 3, wy + wHeight, 0xFFFF5C7A); // Sol vurgu çizgisi
        g.drawText(tr, text, wx + 8, wy + 6, 0xFFFFFFFF, true);

        // ==========================================
        // 2. ENVANTER (Detaylı 3x9, Slot Çerçeveleri)
        // ==========================================
        if (showInventory.value) {
            int ix = (int) invX.value;
            int iy = (int) invY.value;
            int iWidth = 9 * 18 + 8;
            int iHeight = 3 * 18 + 24; // Başlık için ekstra boşluk

            if (editMode) {
                if (mouseDown) {
                    if (!draggingWelcome && !draggingInv && !draggingArmor) {
                        if (mouseX >= ix && mouseX <= ix + iWidth && mouseY >= iy && mouseY <= iy + iHeight) {
                            draggingInv = true;
                            dragX = mouseX - ix;
                            dragY = mouseY - iy;
                        }
                    }
                } else draggingInv = false;
            } else draggingInv = false;

            if (draggingInv) {
                invX.value = mouseX - dragX;
                invY.value = mouseY - dragY;
                ix = (int) invX.value;
                iy = (int) invY.value;
            }

            // Çizim
            g.fill(ix - 2, iy - 2, ix + iWidth + 2, iy + iHeight + 2, 0x40000000); // Gölge
            g.fillGradient(ix, iy, ix + iWidth, iy + iHeight, 0xFF1E1E25, 0xFF131318); // Arka Plan
            g.fill(ix, iy, ix + iWidth, iy + 2, 0xFFFF5C7A); // Üst vurgu

            g.drawText(tr, "Inventory", ix + 5, iy + 6, 0xFFB0B0B0, true);
            g.drawText(tr, "3x9", ix + iWidth - 20, iy + 6, 0xFFFF5C7A, false);

            int startX = ix + 4;
            int startY = iy + 18;

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    ItemStack stack = p.getInventory().getStack(slotIndex);

                    int sx = startX + col * 18;
                    int sy = startY + row * 18;

                    // Slot arka planı ve hafif çerçevesi
                    g.fill(sx, sy, sx + 18, sy + 18, 0x80000000);
                    drawOutline(g, sx, sy, 18, 18, 0xFF333333);

                    if (!stack.isEmpty()) {
                        g.drawItem(stack, sx + 1, sy + 1);
                        g.drawStackOverlay(tr, stack, sx + 1, sy + 1);
                    }
                }
            }
        }

        // ==========================================
        // 3. ZIRH & DAYANIKLILIK (Dinamik Barlar ve Renkler)
        // ==========================================
        if (showArmor.value) {
            int ax = (int) armorX.value;
            int ay = (int) armorY.value;
            int aWidth = 4 * 22 + 4;
            int aHeight = 24 + 14; // İkonlar + Barlar için

            if (editMode) {
                if (mouseDown) {
                    if (!draggingWelcome && !draggingInv && !draggingArmor) {
                        if (mouseX >= ax && mouseX <= ax + aWidth && mouseY >= ay && mouseY <= ay + aHeight) {
                            draggingArmor = true;
                            dragX = mouseX - ax;
                            dragY = mouseY - ay;
                        }
                    }
                } else draggingArmor = false;
            } else draggingArmor = false;

            if (draggingArmor) {
                armorX.value = mouseX - dragX;
                armorY.value = mouseY - dragY;
                ax = (int) armorX.value;
                ay = (int) armorY.value;
            }

            // Çizim
            g.fill(ax - 2, ay - 2, ax + aWidth + 2, ay + aHeight + 2, 0x40000000); // Gölge
            g.fillGradient(ax, ay, ax + aWidth, ay + aHeight, 0xFF1E1E25, 0xFF131318); // Arka Plan
            g.fill(ax, ay, ax + aWidth, ay + 2, 0xFFFF5C7A); // Üst vurgu

            EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

            for (int i = 0; i < 4; i++) {
                ItemStack stack = p.getEquippedStack(slots[i]);
                int sx = ax + 3 + i * 22;
                int sy = ay + 4;

                // Zırh Slotu Arka Planı
                g.fill(sx, sy, sx + 18, sy + 18, 0x80000000);
                drawOutline(g, sx, sy, 18, 18, 0xFF444444);

                if (!stack.isEmpty()) {
                    g.drawItem(stack, sx + 1, sy + 1);

                    if (stack.isDamageable()) {
                        int max = stack.getMaxDamage();
                        int dmg = stack.getDamage();
                        int pct = (int) (((double) (max - dmg) / max) * 100);

                        // Dinamik Renk (Yeşil -> Sarı -> Kırmızı)
                        int color = pct > 66 ? 0xFF55FF55 : (pct > 33 ? 0xFFFFFF55 : 0xFFFF5555);

                        // 1. İnce Dayanıklılık Barı (Item'in hemen altında)
                        int barY = sy + 20;
                        g.fill(sx, barY, sx + 18, barY + 2, 0x80000000); // Bar arka planı
                        g.fill(sx, barY, sx + (int) (18 * (pct / 100.0)), barY + 2, color); // Bar dolgusu

                        // 2. Yüzdelik Metin (Ortalanmış)
                        String pctText = pct + "%";
                        int tw = tr.getWidth(pctText);
                        g.drawText(tr, pctText, sx + 9 - tw / 2, sy + 23, color, true);
                    }
                }
            }
        }
    }

    // Yardımcı Metot: Çerçeve Çizimi
    private void drawOutline(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); // Üst
        g.fill(x, y + h - 1, x + w, y + h, color); // Alt
        g.fill(x, y, x + 1, y + h, color); // Sol
        g.fill(x + w - 1, y, x + w, y + h, color); // Sağ
    }
}