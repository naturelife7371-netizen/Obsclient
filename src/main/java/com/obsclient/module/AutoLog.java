package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

/**
 * ==========================================================
 *  AutoLog — "SafeExit" Tehdit Algılayıcı
 * ==========================================================
 *  • Can düşükse çıkış yapar.
 *  • Yakında ölümcül kristal varsa çıkış yapar.
 *  • Yakında düşman varsa ve canınız azsa çıkış yapar.
 */
public class AutoLog extends Module {
    public static String status = "Off";

    final NumSetting hpThreshold = new NumSetting("HP", "Min Can", 6.0, 1.0, 20.0, 0.5);
    final NumSetting enemyRange = new NumSetting("EnemyRange", "Dusman Menzili", 8.0, 2.0, 20.0, 0.5);
    final NumSetting crystalRange = new NumSetting("CrystalRange", "Kristal Menzil", 6.0, 2.0, 15.0, 0.5);

    final BoolSetting onCrystal = new BoolSetting("OnCrystal", "Kristalde Cik", true);
    final BoolSetting onEnemy = new BoolSetting("OnEnemy", "Dusmanda Cik", true);
    final BoolSetting sendMessage = new BoolSetting("SendMessage", "Mesaj Gonder", true);

    private int delay = 0;

    public AutoLog() {
        super("AutoLog", "AutoLog", "Auto disconnect on threat.", "Tehditte oto cikis.", Category.PLAYER);
        settings.addAll(Arrays.asList(hpThreshold, enemyRange, crystalRange, onCrystal, onEnemy, sendMessage));
    }

    @Override public String status() { return enabled ? status : "Off"; }
    @Override public void onEnable() { status = "Watching"; delay = 0; }
    @Override public void onDisable() { status = "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        try {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null || mc.getNetworkHandler() == null) return;
            if (mc.currentScreen != null) return;

            if (delay > 0) { delay--; return; }

            double hp = p.getHealth() + p.getAbsorptionAmount();

            // 1. Can Kontrolü
            if (hp <= hpThreshold.value) {
                triggerLogout(mc, "Can azaldi! (" + (int)hp + " HP)");
                return;
            }

            // 2. Kristal Kontrolü
            if (onCrystal.value) {
                // ⚡ FIX: p.getPos() yerine epos(p) kullanıldı
                Vec3d myPos = epos(p);

                for (var e : mc.world.getEntities()) {
                    if (!(e instanceof EndCrystalEntity c)) continue;
                    if (!c.isAlive()) continue;

                    double d = myPos.distanceTo(epos(c));
                    if (d <= crystalRange.value) {
                        // Kristalin patlama hasarını hesapla
                        double dmg = blast(mc, p, epos(c), 6f, 1.0);
                        if (dmg >= hp) {
                            triggerLogout(mc, "Olumcul kristal! (" + (int)dmg + " Hasar)");
                            return;
                        }
                    }
                }
            }

            // 3. Düşman Kontrolü
            if (onEnemy.value && hp <= 12) {
                for (PlayerEntity o : mc.world.getPlayers()) {
                    if (o == p || !o.isAlive() || isFriend(o)) continue;
                    if (p.distanceTo(o) <= enemyRange.value) {
                        triggerLogout(mc, "Yakin dusman! (" + o.getName().getString() + ")");
                        return;
                    }
                }
            }

            status = "Safe (" + (int)hp + " HP)";
        } catch (Throwable ignored) {}
    }

    private void triggerLogout(MinecraftClient mc, String reason) {
        status = "§aDisconnected!";

        if (sendMessage.value && mc.getNetworkHandler() != null) {
            // Çıkış yapmadan önce chat'e mesaj gönder (İsteğe bağlı)
            mc.getNetworkHandler().sendChatMessage("AutoLog: " + reason);
        }

        Notification.send("§cAutoLog: " + reason, 0xFFFF5C5C);

        // Bağlantıyı güvenli bir şekilde kes
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("AutoLog: " + reason));
        }

        // Modülü kapat ki oyuna tekrar girince spam yapmasın
        toggle();
    }
}