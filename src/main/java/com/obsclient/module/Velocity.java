package com.obsclient.module;

import com.obsclient.ObsClient.Module;
import com.obsclient.ObsClient.Category;
import com.obsclient.ObsClient.NumSetting;
import com.obsclient.ObsClient.BypassMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

import static com.obsclient.ObsClient.*;

@SuppressWarnings("unused")
public class Velocity extends Module {
    public static String status = "Off";
    public static Velocity INSTANCE;

    final NumSetting horizontal = new NumSetting("Horizontal%", "Yatay %", 0, 0, 100, 1);
    final NumSetting vertical   = new NumSetting("Vertical%", "Dikey %", 0, 0, 100, 1);
    final NumSetting explosion  = new NumSetting("Explosion%", "Patlama %", 0, 0, 100, 1);
    final NumSetting grimFactor = new NumSetting("GrimSmooth", "Grim Yumusatma", 0.0, 0.0, 1.0, 0.01);

    public Velocity() {
        super("Velocity", "Velocity", "Grim-Safe Knockback.", "Grim-Guvenli Savrulma.", Category.COMBAT);
        settings.addAll(Arrays.asList(horizontal, vertical, explosion, grimFactor));
        INSTANCE = this;
    }

    @Override public String status() { return enabled ? "Active" : "Off"; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.getNetworkHandler() == null) return;

        // Hasari aldigimiz sure boyunca (hurtTime > 0) hizi kes
        if (p.hurtTime > 0) {
            Vec3d v = p.getVelocity();

            // Patlama mi yoksa normal vurus mu? (Y hizi 0.4'ten buyukse patlamadir)
            boolean isExplosion = v.y > 0.4;

            double baseH = (isExplosion ? explosion.value : horizontal.value) / 100.0;
            double baseV = vertical.value / 100.0;

            double nx = v.x * baseH;
            double ny = v.y * baseV;
            double nz = v.z * baseH;

            if (BYPASS == BypassMode.GRIM) {
                // Grim'de tamamen sifirlamak yerine grimFactor kadarini birak
                // Eger grimFactor 0 ise (varsayilan) hic hareket etmez
                nx *= grimFactor.value;
                nz *= grimFactor.value;
                // Dikey hizi da kesmek istersen ny *= grimFactor.value; ekleyebilirsin.
            }

            // Hizi uygula (Sifirla)
            p.setVelocity(nx, ny, nz);

            // GrimAC'nin rubberband (geri atma) yemesini engellemek icin
            // sunucuya "Ben buradayim" konum paketi gonderiyoruz.
            if (BYPASS == BypassMode.GRIM) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        p.getX(), p.getY(), p.getZ(), p.isOnGround(), p.horizontalCollision
                ));
            }
        }
    }
}