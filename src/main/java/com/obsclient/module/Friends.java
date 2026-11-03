package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.BoolSetting;
import com.obsclient.ObsClient.Notification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

import static com.obsclient.ObsClient.FRIENDS;
import static com.obsclient.ObsClient.saveConfig;

/**
 * Friends - orta tik ile arkadas ekle/cikar.
 * Arkadaslar AutoCrystal, KillAura gibi modullerde hedef alinmaz.
 */
public class Friends extends Module {
    public static String status = "Off";

    final BoolSetting middle = new BoolSetting("MiddleClick", "Orta Tik", true);
    final BoolSetting clear  = new BoolSetting("Clear", "Temizle", false);

    private boolean wasMid = false;

    public Friends() {
        super("Friends", "Friends", "Middle click friends.", "Orta tik arkadas.", Category.PLAYER);
        settings.addAll(Arrays.asList(middle, clear));
        enabled = true;
    }

    @Override public String status() { return FRIENDS.size() + " friends"; }

    @Override
    public void onTick(MinecraftClient mc) {
        // Clear butonuna basildiysa listeyi temizle
        if (clear.value) {
            FRIENDS.clear();
            clear.value = false;
            saveConfig();
        }

        if (!middle.value || mc.player == null) return;

        long h = mc.getWindow().getHandle();
        boolean d = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (d && !wasMid && mc.currentScreen == null
                && mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof PlayerEntity pe) {
            String n = pe.getName().getString();
            if (FRIENDS.remove(n.toLowerCase())) {
                Notification.send("§c" + n + " removed", 0xFFFF5C5C);
            } else {
                FRIENDS.add(n.toLowerCase());
                Notification.send("§a" + n + " added", 0xFF3FD07A);
            }
            saveConfig();
        }
        wasMid = d;
    }
}