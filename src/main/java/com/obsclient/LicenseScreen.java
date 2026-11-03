package com.obsclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Lisans giriş ekranı.
 * Ctrl basınca açılır (key yoksa zorunlu, varsa opsiyonel).
 */
public class LicenseScreen extends Screen {

    private String input = "";
    private String statusText = "Enter your license key";
    private boolean busy = false;
    private long copyFeedbackTime = 0;

    public LicenseScreen() { super(Text.literal("ObsClient License")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(KeyInput kInput) {
        int kc = kInput.key();
        if (busy) return true;

        if (kc == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (kc == GLFW.GLFW_KEY_ENTER) { submit(); return true; }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            // Lisans yoksa kapatma
            if (!License.check()) {
                statusText = "License required!";
                return true;
            }
            MinecraftClient.getInstance().setScreen(null);
            return true;
        }
        // Ctrl+V yapıştır
        if (kc == GLFW.GLFW_KEY_V) {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            boolean ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            if (ctrl) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null && !clip.isBlank()) { input = clip.trim(); return true; }
            }
        }
        String ch = GLFW.glfwGetKeyName(kc, 0);
        if (ch != null && ch.length() == 1) { input += ch; return true; }
        return super.keyPressed(kInput);
    }

    private void submit() {
        if (busy || input.isBlank()) return;
        busy = true;
        statusText = "Verifying...";

        final String key = input;
        new Thread(() -> {
            boolean ok = License.authenticate(key);
            statusText = License.status;
            busy = false;
            if (ok) {
                MinecraftClient.getInstance().execute(
                        () -> MinecraftClient.getInstance().setScreen(null));
            }
        }, "ObsClient-Auth").start();
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        var tr = this.textRenderer;

        // Arka plan
        g.fill(0, 0, this.width, this.height, 0xFF08080C);

        // Dekoratif şeritler
        g.fill(0, this.height / 2 - 160, this.width, this.height / 2 - 158, 0xFF3FD07A);
        g.fill(0, this.height / 2 + 160, this.width, this.height / 2 + 162, 0xFF3FD07A);

        // Başlık
        String title = "ObsClient";
        g.drawText(tr, title, this.width / 2 - tr.getWidth(title) / 2,
                this.height / 2 - 120, 0xFFFFFFFF, true);

        String sub = "Enter your license key";
        g.drawText(tr, sub, this.width / 2 - tr.getWidth(sub) / 2,
                this.height / 2 - 100, 0xFF9A9A9A, true);

        // Input kutusu
        int inputW = 320, inputH = 24;
        int inputX = this.width / 2 - inputW / 2;
        int inputY = this.height / 2 - 40;

        g.fill(inputX - 1, inputY - 1, inputX + inputW + 1, inputY + inputH + 1,
                busy ? 0xFF6A6A6A : (License.check() ? 0xFF3FD07A : 0xFF3FD07A));
        g.fill(inputX, inputY, inputX + inputW, inputY + inputH, 0xFF15151C);

        String displayInput = input.length() > 46 ? input.substring(0, 46) + "..." : input;
        String caret = busy ? "" : (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : " ");
        g.drawText(tr, displayInput + caret, inputX + 8, inputY + 8,
                input.isEmpty() ? 0xFF5A5A5A : 0xFFFFFFFF, false);

        // ACTIVATE butonu
        int btnW = 140, btnH = 24;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = this.height / 2 + 10;
        boolean btnHov = !busy && inside(mx, my, btnX, btnY, btnW, btnH);
        int btnBg = btnHov ? 0xFF3FD07A : 0xFF1E5C3A;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
        String btnLabel = busy ? "VERIFYING..." : "ACTIVATE";
        g.drawText(tr, btnLabel, btnX + btnW / 2 - tr.getWidth(btnLabel) / 2,
                btnY + 8, 0xFFFFFFFF, true);

        // Status
        int statusColor = License.check() ? 0xFF3FD07A : 0xFFFF5C5C;
        g.drawText(tr, statusText, this.width / 2 - tr.getWidth(statusText) / 2,
                btnY + btnH + 14, statusColor, false);

        // BUY butonu
        int buyW = 300, buyH = 24;
        int buyX = this.width / 2 - buyW / 2;
        int buyY = this.height / 2 + 70;
        boolean buyHov = inside(mx, my, buyX, buyY, buyW, buyH);
        int buyBg = buyHov ? 0xFF3FD07A : 0xFF1E5C3A;
        g.fill(buyX, buyY, buyX + buyW, buyY + buyH, buyBg);
        g.fill(buyX, buyY, buyX + buyW, buyY + 2, 0xFF5CFFB0);
        String buyLabel = "Don't have a key? Buy at " + License.PURCHASE_URL;
        g.drawText(tr, buyLabel, buyX + buyW / 2 - tr.getWidth(buyLabel) / 2,
                buyY + 8, 0xFFFFFFFF, true);

        // Alt bilgi
        String foot = "ObsClient v" + ObsClient.VERSION + "   |   Ctrl+V to paste   |   ESC to close";
        g.drawText(tr, foot, this.width / 2 - tr.getWidth(foot) / 2,
                this.height - 20, 0xFF4A4A5A, false);

        // Toplam key sayısı (debug)
        String info = License.totalKeys() + " keys loaded";
        g.drawText(tr, info, 6, this.height - 12, 0xFF303040, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int mb = click.button();
        if (mb != 0) return super.mouseClicked(click, doubled);

        int btnW = 140, btnH = 24;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = this.height / 2 + 10;
        if (!busy && inside((int) mx, (int) my, btnX, btnY, btnW, btnH)) {
            submit();
            return true;
        }

        int buyW = 300, buyH = 24;
        int buyX = this.width / 2 - buyW / 2;
        int buyY = this.height / 2 + 70;
        if (inside((int) mx, (int) my, buyX, buyY, buyW, buyH)) {
            License.openPurchasePage();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}