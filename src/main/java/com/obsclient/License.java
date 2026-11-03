package com.obsclient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * ObsClient basit lisans sistemi.
 * - keys.txt içindeki geçerli key listesini yükler (jar'a gömülü)
 * - Kullanıcının girdiği key listeye bakılır
 * - Doğruysa cache'lenir → sonraki açılışta otomatik geçer
 * - Yanlışsa hata verir
 *
 * Satın alma: obsclient.rf.gd
 */
public final class License {

    /** Satın alma sayfası. */
    public static final String PURCHASE_URL = "https://obsclient.rf.gd";

    /** Cache geçerlilik süresi: 30 gün. */
    private static final long CACHE_MS = 30L * 24 * 60 * 60 * 1000;

    public static final String VERSION = "11.0.0";
    private static final String CACHE_NAME = ".obsclient.dat";

    public static boolean authed = false;
    public static String license = "";
    public static String status = "Not authenticated";

    /** keys.txt'den yüklenen hash seti (düz metin değil, hash tutulur). */
    private static final Set<String> VALID_HASHES = new HashSet<>();
    private static boolean keysLoaded = false;

    // ============================================================
    // KEY LİSTESİ
    // ============================================================

    /** keys.txt'yi yükle. */
    public static synchronized void loadKeys() {
        if (keysLoaded) return;
        keysLoaded = true;
        try {
            InputStream is = License.class.getResourceAsStream("/keys.txt");
            if (is == null) {
                System.err.println("[ObsClient] keys.txt bulunamadı!");
                return;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    // Hash'le ve set'e ekle
                    VALID_HASHES.add(sha256(line));
                }
            }
            System.out.println("[ObsClient] " + VALID_HASHES.size() + " lisans anahtarı yüklendi.");
        } catch (Exception e) {
            System.err.println("[ObsClient] keys.txt yüklenemedi: " + e.getMessage());
        }
    }

    public static int totalKeys() { return VALID_HASHES.size(); }

    // ============================================================
    // DOĞRULAMA
    // ============================================================

    /** Kullanıcının girdiği key geçerli mi? */
    public static boolean isValidKey(String key) {
        if (key == null || key.isBlank()) return false;
        loadKeys();
        String h = sha256(key.trim());
        return VALID_HASHES.contains(h);
    }

    /** Ana giriş noktası. Kullanıcı key'i girince çağrılır. */
    public static boolean authenticate(String key) {
        if (key == null || key.isBlank()) {
            status = "Empty key";
            return false;
        }
        key = key.trim();

        if (!isValidKey(key)) {
            status = "Invalid license key";
            return false;
        }

        authed = true;
        license = key;
        status = "Active";
        saveCache(key);
        return true;
    }

    public static boolean check() { return authed; }

    // ============================================================
    // CACHE
    // ============================================================

    private static Path cachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CACHE_NAME);
    }

    private static void saveCache(String key) {
        try {
            Properties pr = new Properties();
            pr.setProperty("key", key);
            pr.setProperty("time", String.valueOf(System.currentTimeMillis()));
            try (OutputStream os = Files.newOutputStream(cachePath())) {
                pr.store(os, "ObsClient license");
            }
        } catch (Exception ignored) {}
    }

    private static boolean loadCache() {
        try {
            Path path = cachePath();
            if (!Files.exists(path)) return false;
            Properties pr = new Properties();
            try (InputStream is = Files.newInputStream(path)) { pr.load(is); }
            long time = Long.parseLong(pr.getProperty("time", "0"));
            if (System.currentTimeMillis() - time > CACHE_MS) return false;
            String key = pr.getProperty("key", "");
            if (key.isEmpty()) return false;
            return isValidKey(key);
        } catch (Exception e) {
            return false;
        }
    }

    /** Açılışta cache'den yükle. */
    public static void tryLoadCache() {
        loadKeys();
        if (loadCache()) {
            authed = true;
            status = "Cached";
            try {
                Properties pr = new Properties();
                try (InputStream is = Files.newInputStream(cachePath())) { pr.load(is); }
                license = pr.getProperty("key", "");
            } catch (Exception ignored) {}
        }
    }

    /** Lisansı sil (logout). */
    public static void reset() {
        authed = false;
        license = "";
        status = "Not authenticated";
        try { Files.deleteIfExists(cachePath()); } catch (Exception ignored) {}
    }

    // ============================================================
    // SATIN ALMA YÖNLENDİRMESİ
    // ============================================================

    public static void openPurchasePage() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            Runtime rt = Runtime.getRuntime();
            if (os.contains("win")) {
                rt.exec("rundll32 url.dll,FileProtocolHandler " + PURCHASE_URL);
            } else if (os.contains("mac")) {
                rt.exec("open " + PURCHASE_URL);
            } else {
                rt.exec("xdg-open " + PURCHASE_URL);
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // YARDIMCI
    // ============================================================

    private static String sha256(String in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(in.hashCode());
        }
    }
}