package com.obsclient;

import com.obsclient.module.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class ObsClient implements ClientModInitializer {

    public static final List<Module> MODULES = new ArrayList<>();
    public static final String VERSION = "v16-Beta3";
    private static boolean ctrlWas = false;
    public static boolean EN = true;

    public static final HashMap<String, Boolean> SAVED_ENABLED = new HashMap<>();
    private static boolean stateRestored = false;

    public static String lastSaveStatus = "Never";
    public static String lastLoadStatus = "Never";
    public static long lastSaveTime = 0;
    public static long lastLoadTime = 0;

    public enum Category {
        COMBAT("Combat", 0xFFFF5C7A), MOVEMENT("Movement", 0xFF4FC3FF),
        PLAYER("Player", 0xFF5CFFB0), WORLD("World", 0xFFFFC95C),
        RENDER("Render", 0xFFB65CFF), MISC("Misc", 0xFF9AA0A6);
        public final String label; public final int color;
        Category(String l, int c) { label = l; color = c; }
    }

    public enum BypassMode { VANILLA, VULCAN, INTAVE, MATRIX, GRIM, NCP, VERUS }

    public static final Set<String> FRIENDS = new HashSet<>();
    public static final HashMap<Integer, Vec3d> LAST = new HashMap<>();
    public static final HashMap<Integer, Vec3d> VEL = new HashMap<>();
    public static final HashMap<Integer, Deque<Vec3d>> TRAIL = new HashMap<>();
    public static Predict PREDICT;
    public static boolean PANIC = false;
    public static BypassMode BYPASS = BypassMode.GRIM;

    /* ==================== LICENSE ==================== */
    public static class License {
        public static final String PURCHASE_URL = "https://obsclient.local";
        private static boolean licensed = true;
        public static void tryLoadCache() {}
        public static boolean check() { return licensed; }
        public static void set(boolean v) { licensed = v; }
    }

    public static class LicenseScreen extends Screen {
        public LicenseScreen() { super(Text.literal("ObsClient License")); }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext g, int mx, int my, float d) {
            super.render(g, mx, my, d);
            g.fill(0, 0, this.width, this.height, 0xB0000000);
            g.drawCenteredTextWithShadow(this.textRenderer, "ObsClient " + VERSION, this.width / 2, 40, 0xFFFFFFFF);
            g.drawCenteredTextWithShadow(this.textRenderer, "License: OK", this.width / 2, 58, 0xFF3FD07A);
            g.drawCenteredTextWithShadow(this.textRenderer, "ESC to close", this.width / 2, 94, 0xFF6A6A6A);
        }
    }

    /* ==================== ROTATION ==================== */
    public static final class Rot {
        public static float reqYaw = 0f, reqPitch = 0f, sentYaw = 0f, sentPitch = 0f;
        public static boolean pending = false, active = false;
        public static int priority = 0, holdTicks = 0;
        private static final Random JITTER = new Random();
        public static void request(float y, float p, int prio) {
            if (pending && prio < priority) return;
            reqYaw = y; reqPitch = p; pending = true; priority = prio;
        }
        public static void flush(MinecraftClient mc) {
            try {
                if (!pending || mc.getNetworkHandler() == null) return;
                ClientPlayerEntity p = mc.player;
                if (p == null || holdTicks > 0) { pending = false; return; }
                float fromY = active ? sentYaw : p.getYaw(), fromP = active ? sentPitch : p.getPitch();
                float dy = MathHelper.wrapDegrees(reqYaw - fromY), dp = reqPitch - fromP;
                float maxY, maxP;
                switch (BYPASS) {
                    case GRIM -> { maxY = 25f; maxP = 20f; }
                    case VULCAN -> { maxY = 28f; maxP = 22f; }
                    case MATRIX -> { maxY = 33f; maxP = 24f; }
                    case INTAVE -> { maxY = 30f; maxP = 20f; }
                    case VERUS -> { maxY = 32f; maxP = 22f; }
                    case NCP -> { maxY = 40f; maxP = 30f; }
                    default -> { maxY = 180f; maxP = 90f; }
                }
                if (priority >= 50 && BYPASS != BypassMode.GRIM) { maxY = 180f; maxP = 90f; }
                if (Math.abs(dy) > maxY) dy = Math.signum(dy) * maxY;
                if (Math.abs(dp) > maxP) dp = Math.signum(dp) * maxP;
                if (BYPASS == BypassMode.GRIM) { dy += (JITTER.nextFloat() - 0.5f) * 0.12f; dp += (JITTER.nextFloat() - 0.5f) * 0.08f; }
                float nY = gcd(fromY + dy), nP = gcd(MathHelper.clamp(fromP + dp, -90, 90));
                if (active && Math.abs(MathHelper.wrapDegrees(nY - sentYaw)) < 0.001f && Math.abs(nP - sentPitch) < 0.001f) { pending = false; return; }
                sentYaw = nY; sentPitch = nP; active = true;
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(nY, nP, p.isOnGround(), p.horizontalCollision));
            } catch (Throwable ignored) {} finally { pending = false; }
        }
        public static void postAttack() {
            switch (BYPASS) {
                case GRIM -> holdTicks = 5;
                case VULCAN, INTAVE, VERUS -> holdTicks = 3;
                case MATRIX -> holdTicks = 2;
                case NCP -> holdTicks = 1;
                default -> holdTicks = 0;
            }
        }
        public static void tick() { if (holdTicks > 0) holdTicks--; priority = 0; }
        public static boolean closeEnough(float y, float p, float tol) {
            return active && Math.abs(MathHelper.wrapDegrees(sentYaw - y)) <= tol && Math.abs(sentPitch - p) <= tol;
        }
    }

    /* ==================== NOTIFICATION ==================== */
    public static class Notification {
        public static final List<Note> LIST = new CopyOnWriteArrayList<>();
        public static class Note { public String msg; public int color; public long time; public Note(String m, int c) { msg = m; color = c; time = System.currentTimeMillis(); } }
        public static void send(String m, int c) { LIST.add(new Note(m, c)); while (LIST.size() > 8) LIST.remove(0); }
    }

    /* ==================== HELPERS ==================== */
    public static boolean isFriend(Entity e) {
        try {
            if (!(e instanceof PlayerEntity p)) return false;
            String n = p.getName().getString().toLowerCase(Locale.ROOT);
            return FRIENDS.contains(n) || FRIENDS.contains(n.split("\\s+")[0]);
        } catch (Throwable ignored) { return false; }
    }
    public static Vec3d epos(Entity e) { return new Vec3d(e.getX(), e.getY(), e.getZ()); }
    public static float[] calcAngle(Vec3d f, Vec3d t) {
        double dx = t.x - f.x, dy = t.y - f.y, dz = t.z - f.z;
        double h = Math.hypot(dx, dz);
        return new float[]{ (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float)-Math.toDegrees(Math.atan2(dy, h)) };
    }
    public static float gcd(float v) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.options == null) return v;
            double s = mc.options.getMouseSensitivity().getValue();
            double f = s * 0.6 + 0.2, g = f * f * f * 1.2;
            return g < 1e-6 ? v : (float)(Math.round(v / g) * g);
        } catch (Throwable ignored) { return v; }
    }
    public static double boxDist(Vec3d f, Box b) {
        double dx = Math.max(Math.max(b.minX - f.x, 0), f.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - f.y, 0), f.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - f.z, 0), f.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    public static double crystalReach() { return BYPASS == BypassMode.GRIM ? 3.0 : 4.5; }
    public static boolean isMoving(ClientPlayerEntity p) { return Math.abs(p.getVelocity().x) > 0.01 || Math.abs(p.getVelocity().z) > 0.01; }

    public static int findInv(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 36; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }
    public static int findHot(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) if (pred.test(p.getInventory().getStack(i))) return i;
        return -1;
    }
    private static boolean isKeep(ItemStack s) {
        return s.isOf(Items.END_CRYSTAL) || s.isOf(Items.OBSIDIAN) || s.isOf(Items.TOTEM_OF_UNDYING) || s.isOf(Items.FIREWORK_ROCKET)
                || s.isOf(Items.RESPAWN_ANCHOR) || s.isOf(Items.GLOWSTONE) || s.isOf(Items.MACE) || s.isOf(Items.ENDER_CHEST)
                || s.isOf(Items.ANVIL) || s.isOf(Items.ENDER_PEARL) || s.isOf(Items.EXPERIENCE_BOTTLE);
    }
    public static int toHotbar(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred) {
        try {
            int h = findHot(p, pred);
            if (h >= 0) return h;
            if (mc.interactionManager == null) return -1;
            for (int i = 9; i < 36; i++) {
                if (!pred.test(p.getInventory().getStack(i))) continue;
                int t = -1;
                for (int j = 0; j < 9; j++) if (p.getInventory().getStack(j).isEmpty()) { t = j; break; }
                if (t < 0) { int s = p.getInventory().getSelectedSlot(); if (!isKeep(p.getInventory().getStack(s))) t = s; }
                if (t < 0) for (int j = 0; j < 9; j++) if (!isKeep(p.getInventory().getStack(j))) { t = j; break; }
                if (t < 0) return -1;
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, t, SlotActionType.SWAP, p);
                return t;
            }
        } catch (Throwable ignored) {}
        return -1;
    }
    public static void silentLook(MinecraftClient mc, ClientPlayerEntity p, Vec3d pos) {
        try { float[] a = calcAngle(p.getEyePos(), pos); Rot.request(a[0], a[1], 1); } catch (Throwable ignored) {}
    }
    public static boolean clickBlock(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred, BlockPos a, Direction face, Vec3d look, boolean silent) {
        try {
            if (mc.interactionManager == null || mc.world == null) return false;
            int slot = toHotbar(mc, p, pred);
            if (slot < 0) return false;
            Vec3d hv = Vec3d.ofCenter(a).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (p.getEyePos().distanceTo(hv) > 5.2) return false;
            if (silent) silentLook(mc, p, look != null ? look : hv);
            p.getInventory().setSelectedSlot(slot);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hv, face, a, false));
            p.swingHand(Hand.MAIN_HAND);
            if (silent) Rot.postAttack();
            return true;
        } catch (Throwable ignored) { return false; }
    }
    public static boolean placeSupported(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos, Predicate<ItemStack> pred, boolean silent) {
        try {
            if (mc.world == null) return false;
            for (Direction d : Direction.values()) {
                BlockPos n = pos.offset(d);
                BlockState s = mc.world.getBlockState(n);
                if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                if (clickBlock(mc, p, pred, n, d.getOpposite(), null, silent)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
    public static double blast(MinecraftClient mc, LivingEntity e, Vec3d c, float power, double ench) {
        try {
            if (mc == null || mc.world == null || e == null || c == null) return 0;
            if (e.isRemoved() || !e.isAlive()) return 0;
            if (e.getEntityWorld() != mc.world) return 0;
            double rad = power * 2.0, dist = epos(e).distanceTo(c) / rad;
            if (dist > 1.0) return 0;
            double impact = (1.0 - dist) * exposure(mc, c, e);
            double d = (impact * impact + impact) / 2.0 * 7.0 * rad + 1.0;
            Difficulty diff = mc.world.getDifficulty();
            if (diff == Difficulty.PEACEFUL) return 0;
            if (diff == Difficulty.EASY) d = Math.min(d / 2.0 + 1.0, d);
            else if (diff == Difficulty.HARD) d *= 1.5;
            double armor = e.getAttributeValue(EntityAttributes.ARMOR), tough = e.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
            double f = 2.0 + tough / 4.0;
            double red = Math.min(20.0, Math.max(armor / 5.0, armor - d / f));
            d *= (1.0 - red / 25.0);
            StatusEffectInstance res = e.getStatusEffect(StatusEffects.RESISTANCE);
            if (res != null) d *= Math.max(0.0, 1.0 - 0.2 * (res.getAmplifier() + 1));
            if (armor > 0) d *= ench;
            return Math.max(0, d);
        } catch (Throwable ignored) { return 0; }
    }
    private static final HashMap<Long, Double> EXPO_CACHE = new HashMap<>();
    private static long expoCacheTick = Long.MIN_VALUE;
    private static double exposure(MinecraftClient mc, Vec3d c, LivingEntity e) {
        try {
            if (mc == null || mc.world == null || e == null || c == null) return 0;
            if (e.isRemoved() || !e.isAlive()) return 0;
            if (e.getEntityWorld() != mc.world) return 0;
            long now = mc.world.getTime();
            if (now != expoCacheTick || EXPO_CACHE.size() > 4000) { EXPO_CACHE.clear(); expoCacheTick = now; }
            long key = ((long)e.getId() * 73856093L) ^ ((long)Math.floor(c.x * 2) * 19349663L) ^ ((long)Math.floor(c.y * 2) * 83492791L) ^ ((long)Math.floor(c.z * 2) * 2654435761L);
            Double cached = EXPO_CACHE.get(key);
            if (cached != null) return cached;
            double v = exposureRaw(mc, c, e);
            EXPO_CACHE.put(key, v);
            return v;
        } catch (Throwable ignored) { return 0; }
    }
    private static double exposureRaw(MinecraftClient mc, Vec3d c, LivingEntity e) {
        try {
            if (mc.world == null || e.isRemoved()) return 0;
            Box b = e.getBoundingBox();
            final int NX = 3, NY = 4, NZ = 3;
            int vis = 0, total = 0;
            for (int ix = 0; ix < NX; ix++) for (int iy = 0; iy < NY; iy++) for (int iz = 0; iz < NZ; iz++) {
                double x = b.minX + (b.maxX - b.minX) * (ix + 0.5) / NX;
                double y = b.minY + (b.maxY - b.minY) * (iy + 0.5) / NY;
                double z = b.minZ + (b.maxZ - b.minZ) * (iz + 0.5) / NZ;
                BlockHitResult r = mc.world.raycast(new RaycastContext(new Vec3d(x, y, z), c, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
                if (r.getType() == HitResult.Type.MISS) vis++;
                total++;
            }
            return vis / (double)total;
        } catch (Throwable ignored) { return 0; }
    }
    public static int[] project(MinecraftClient mc, Vec3d w, float delta) {
        try {
            ClientPlayerEntity p = mc.player; if (p == null) return null;
            Vec3d eye = p.getLerpedPos(delta).add(0, p.getEyeHeight(p.getPose()), 0);
            double yaw = Math.toRadians(p.getYaw()), pitch = Math.toRadians(p.getPitch());
            Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3d up = right.crossProduct(fwd);
            Vec3d v = w.subtract(eye);
            double z = v.dotProduct(fwd); if (z < 0.1) return null;
            double x = v.dotProduct(right), y = v.dotProduct(up);
            int W = mc.getWindow().getScaledWidth(), H = mc.getWindow().getScaledHeight();
            double th = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
            return new int[]{ (int)(W / 2.0 + (x / z) / (th * W / (double)H) * (W / 2.0)), (int)(H / 2.0 - (y / z) / th * (H / 2.0)) };
        } catch (Throwable ignored) { return null; }
    }
    public static int[] projectBox(MinecraftClient mc, Box b, float delta) {
        try {
            double[] xs = { b.minX, b.maxX }, ys = { b.minY, b.maxY }, zs = { b.minZ, b.maxZ };
            int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
            for (double x : xs) for (double y : ys) for (double z : zs) {
                int[] s = project(mc, new Vec3d(x, y, z), delta);
                if (s == null) return null;
                x1 = Math.min(x1, s[0]); y1 = Math.min(y1, s[1]); x2 = Math.max(x2, s[0]); y2 = Math.max(y2, s[1]);
            }
            if (x2 < -500 || y2 < -500 || x1 > 5000 || y1 > 5000) return null;
            return new int[]{ x1, y1, x2, y2 };
        } catch (Throwable ignored) { return null; }
    }
    public static void outline(DrawContext g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1, y1, x2, y1 + 1, c); g.fill(x1, y2 - 1, x2, y2, c);
        g.fill(x1, y1, x1 + 1, y2, c); g.fill(x2 - 1, y1, x2, y2, c);
    }
    public static void line(DrawContext g, int x1, int y1, int x2, int y2, int c) {
        int dx = x2 - x1, dy = y2 - y1, steps = Math.min(1500, Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) { int x = x1 + dx * i / steps, y = y1 + dy * i / steps; g.fill(x, y, x + 1, y + 1, c); }
    }
    public static int lerpColor(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int)(ar + (br - ar) * t) << 16) | ((int)(ag + (bg - ag) * t) << 8) | (int)(ab + (bb - ab) * t);
    }
    public static int withAlpha(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0x00FFFFFF); }
    public static int hpColor(double hp) { return hp > 14 ? 0xFF55FF55 : hp > 7 ? 0xFFFFFF55 : 0xFFFF5555; }
    public static int dur(ItemStack s) { return (s.isEmpty() || s.getMaxDamage() <= 0) ? -1 : 100 - s.getDamage() * 100 / s.getMaxDamage(); }
    public static int durColor(int p) { return p > 66 ? 0xFF55FF55 : p > 33 ? 0xFFFFFF55 : 0xFFFF5555; }
    public static boolean isFood(ItemStack s) { return s.contains(DataComponentTypes.FOOD); }
    public static void updateTrack(MinecraftClient mc) {
        try {
            if (mc.world == null) return;
            if (LAST.size() > 400) { LAST.clear(); VEL.clear(); TRAIL.clear(); }
            for (PlayerEntity o : mc.world.getPlayers()) {
                Vec3d cur = epos(o), prev = LAST.get(o.getId());
                VEL.put(o.getId(), prev == null ? Vec3d.ZERO : cur.subtract(prev));
                LAST.put(o.getId(), cur);
                Deque<Vec3d> t = TRAIL.computeIfAbsent(o.getId(), k -> new ArrayDeque<>());
                t.addLast(cur);
                if (t.size() > 25) t.removeFirst();
            }
        } catch (Throwable ignored) {}
    }
    public static Vec3d predicted(Entity e, double ticks) {
        try {
            if (e == null) return Vec3d.ZERO;
            Vec3d v = VEL.getOrDefault(e.getId(), Vec3d.ZERO);
            if (TRAIL.containsKey(e.getId()) && TRAIL.get(e.getId()).size() >= 3) {
                Deque<Vec3d> t = TRAIL.get(e.getId());
                Vec3d[] a = t.toArray(new Vec3d[0]);
                Vec3d v1 = a[a.length - 1].subtract(a[a.length - 2]);
                Vec3d v2 = a[a.length - 2].subtract(a[a.length - 3]);
                Vec3d acc = v1.subtract(v2).multiply(0.5);
                return epos(e).add(v.multiply(ticks)).add(acc.multiply(ticks * ticks / 2.0));
            }
            return epos(e).add(v.multiply(ticks));
        } catch (Throwable ignored) { return e == null ? Vec3d.ZERO : epos(e); }
    }
    public static boolean predOn() { return PREDICT != null && PREDICT.enabled; }
    public static double predTicks() { return PREDICT == null ? 0 : PREDICT.ticks.value; }
    private static void moduleFailed(Module m, Throwable t) {
        try {
            m.errors++;
            if (m.errors == 1) System.err.println("[ObsClient] " + m.name + ": " + t);
            if (m.errors >= 5 && m.enabled) { m.toggle(); Notification.send(m.name + " §cdisabled", 0xFFFF5C5C); m.errors = 0; }
        } catch (Throwable ignored) {}
    }

    /* ==================== INIT ==================== */
    @Override
    public void onInitializeClient() {
        License.tryLoadCache();

        PREDICT = new Predict();
        MODULES.add(PREDICT);

        // ===== COMBAT =====
        MODULES.add(new AutoCrystal());
        MODULES.add(new AutoMace());
        MODULES.add(new AutoAnchor());
        MODULES.add(new AutoTrap());
        MODULES.add(new SelfTrap());
        MODULES.add(new PistonCrystal());
        MODULES.add(new AntiCrystal());
        MODULES.add(new AntiMace());
        MODULES.add(new AutoPearl());
        MODULES.add(new SilentSword());
        MODULES.add(new KillAura());
        MODULES.add(new Aimbot());
        MODULES.add(new Surround());
        MODULES.add(new Burrow());
        MODULES.add(new AutoPot());
        MODULES.add(new Velocity());
        MODULES.add(new Reach());
        MODULES.add(new AutoTotem());
        MODULES.add(new TotKiller());
        MODULES.add(new AutoEZ());
        MODULES.add(new TotFail());
        MODULES.add(new AutoGApple());
        MODULES.add(new NoPhase());

        // ===== PLAYER =====
        MODULES.add(new AutoArmor());
        MODULES.add(new ChestStealer());
        MODULES.add(new AutoTool());
        MODULES.add(new FastUse());
        MODULES.add(new AntiDesync());
        MODULES.add(new FastPlace());
        MODULES.add(new FastBreak());
        MODULES.add(new Friends());
        MODULES.add(new AutoExp());
        MODULES.add(new Scaffold());
        MODULES.add(new AutoLog());
        MODULES.add(new AutoMine());

        // ===== MOVEMENT =====
        MODULES.add(new ElytraFly());
        MODULES.add(new FireworkElytraFly());
        MODULES.add(new Phase());
        MODULES.add(new CCClip());
        MODULES.add(new Speed());
        MODULES.add(new Fly());
        MODULES.add(new NoFall());
        MODULES.add(new Step());
        MODULES.add(new Sprint());
        MODULES.add(new NoSlow());
        MODULES.add(new AntiVoid());

        // ===== WORLD =====
        MODULES.add(new Nuker());
        MODULES.add(new BaseFinder());
        MODULES.add(new PlaceAir());

        // ===== MISC =====
        MODULES.add(new Lag());
        MODULES.add(new GrimBypass());
        MODULES.add(new FakePlayer());

        // ===== RENDER =====
        MODULES.add(new PlayerESP());
        MODULES.add(new StorageESP());
        MODULES.add(new NameTag());
        MODULES.add(new FakeFly());
        MODULES.add(new FullBright());
        MODULES.add(new Tracers());
        MODULES.add(new HoleESP());
        MODULES.add(new Search());
        MODULES.add(new NoRender());
        MODULES.add(new Zoom());
        MODULES.add(new RedTint());
        MODULES.add(new HUD());
        MODULES.add(new Freecam());
        MODULES.add(new Welcome());
        MODULES.add(new PopChams());
        MODULES.add(new PopCounter());
        MODULES.add(new Visual());
        MODULES.add(new PvPAspect());

        loadConfig();

        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            try {
                if (message == null) return true;
                String trimmed = message.trim();
                String lower = trimmed.toLowerCase(Locale.ROOT);

                if (!lower.equals("*ez") && !lower.startsWith("*ez ")) return true;

                MinecraftClient mc = MinecraftClient.getInstance();
                var chat = mc.inGameHud.getChatHud();

                String[] parts = trimmed.split("\\s+", 3);
                if (parts.length < 3) {
                    chat.addMessage(Text.literal("§c[AutoEZ] Kullanim: *ez <pop|death> <mesajiniz>"));
                    return false;
                }

                String type = parts[1].toLowerCase(Locale.ROOT);
                String text = parts[2];

                boolean found = false;
                for (Module m : MODULES) {
                    if (m instanceof AutoEZ ez) {
                        found = true;
                        if (type.equals("pop")) {
                            ez.popText.value = text;
                            chat.addMessage(Text.literal("§a[AutoEZ] Pop mesaji guncellendi: §f" + text));
                            saveConfig();
                        } else if (type.equals("death")) {
                            ez.deathText.value = text;
                            chat.addMessage(Text.literal("§a[AutoEZ] Olum mesaji guncellendi: §f" + text));
                            saveConfig();
                        } else {
                            chat.addMessage(Text.literal("§c[AutoEZ] Gecersiz tip! 'pop' veya 'death' kullanin."));
                        }
                        break;
                    }
                }
                if (!found) {
                    chat.addMessage(Text.literal("§c[AutoEZ] Modul yuklu degil."));
                }
                return false;
            } catch (Throwable t) {
                return true;
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { saveModuleStates(); saveConfig(); } catch (Throwable ignored) {}
        }, "ObsClient-ConfigSave"));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            restoreModuleStates(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            saveModuleStates();
            saveConfig();
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (mc == null || mc.player == null || mc.world == null) return;
                long handle = mc.getWindow().getHandle();
                boolean ctrlL = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

                if (!License.check()) {
                    if (ctrlL && !ctrlWas) {
                        if (mc.currentScreen == null) mc.setScreen(new LicenseScreen());
                        else if (mc.currentScreen instanceof LicenseScreen) mc.setScreen(null);
                    }
                    ctrlWas = ctrlL;
                    for (Module m : MODULES) if (m.enabled && m != PREDICT) m.enabled = false;
                    return;
                }
                updateTrack(mc);
                Rot.tick();
                if (PANIC) { for (Module m : MODULES) if (m.enabled && m != PREDICT) m.toggle(); PANIC = false; }
                if (ctrlL && !ctrlWas) {
                    if (mc.currentScreen instanceof ObsGui) mc.setScreen(null);
                    else if (mc.currentScreen == null) mc.setScreen(new ObsGui());
                }
                ctrlWas = ctrlL;
                for (Module m : MODULES) {
                    try {
                        if (m.key >= 0) {
                            boolean d = GLFW.glfwGetKey(handle, m.key) == GLFW.GLFW_PRESS;
                            if (d && !m.keyWas && mc.currentScreen == null) {
                                m.toggle();
                                String stateStr = m.enabled ? "§aON" : "§cOFF";
                                mc.inGameHud.getChatHud().addMessage(Text.literal("§bObsClient §8| §f" + m.name + " " + stateStr));
                                Notification.send(m.name + (m.enabled ? " §aOn" : " §cOff"), m.enabled ? 0xFF3FD07A : 0xFFFF5C5C);
                            }
                            m.keyWas = d;
                        }
                        if (m.enabled) { m.onTick(mc); m.errors = 0; }
                    } catch (Throwable t) { moduleFailed(m, t); }
                }
                Rot.flush(mc);
            } catch (Throwable ignored) {}
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            try {
                if (!(entity instanceof EndCrystalEntity c)) return;
                if (c.isRemoved() || !c.isAlive()) return;
                for (Module m : MODULES) {
                    if (m.enabled && m instanceof AutoCrystal ac) {
                        try { ac.onCrystalSpawn(MinecraftClient.getInstance(), c); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("obsclient", "overlay"), (g, tickCounter) -> {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null || mc.world == null || mc.options.hudHidden) return;
                float delta = tickCounter.getTickProgress(false);
                for (Module m : MODULES) {
                    if (!m.enabled) continue;
                    try {
                        if (m instanceof RedTint rt) rt.render(g, mc);
                        else if (m instanceof StorageESP se) se.render(g, mc, delta);
                        else if (m instanceof PlayerESP pe) pe.render(g, mc, delta);
                        else if (m instanceof NameTag nt) nt.render(g, mc, delta);
                        else if (m instanceof HUD h) h.render(g, mc);
                        else if (m instanceof Tracers tr) tr.render(g, mc, delta);
                        else if (m instanceof HoleESP he) he.render(g, mc, delta);
                        else if (m instanceof Search sr) sr.render(g, mc, delta);
                        else if (m instanceof AutoCrystal ac) ac.render(g, mc, delta);
                        else if (m instanceof PistonCrystal pc) pc.render(g, mc, delta);
                        else if (m instanceof SelfTrap st) st.render(g, mc, delta);
                        else if (m instanceof Surround sur) sur.render(g, mc, delta);
                        else if (m instanceof PlaceAir pa) pa.render(g, mc, delta);
                        else if (m instanceof Welcome w) w.render(g, mc, delta);
                        else if (m instanceof BaseFinder bf) bf.render(g, mc, delta);
                        else if (m instanceof PopChams pc) pc.render(g, mc, delta);
                        else if (m instanceof Nuker nk) nk.render(g, mc, delta);
                        else if (m instanceof AutoMine am) am.render(g, mc, delta);
                        else if (m instanceof PvPAspect pvp) pvp.render(g, mc, delta);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        });
    }

    /* ==================== MODULE STATE PERSISTENCE ==================== */
    private static void saveModuleStates() {
        SAVED_ENABLED.clear();
        for (Module m : MODULES) {
            if (m == PREDICT) continue;
            SAVED_ENABLED.put(m.name, m.enabled);
        }
    }

    private static void restoreModuleStates(MinecraftClient mc) {
        if (!stateRestored) stateRestored = true;
        for (Module m : MODULES) {
            if (m == PREDICT) continue;
            if (m.enabled && !Boolean.TRUE.equals(SAVED_ENABLED.get(m.name))) {
                try { m.toggle(); } catch (Throwable ignored) {}
            }
        }
        for (Module m : MODULES) {
            if (m == PREDICT) continue;
            Boolean want = SAVED_ENABLED.get(m.name);
            if (want != null && want && !m.enabled) {
                try { m.toggle(); } catch (Throwable ignored) {}
            }
        }
    }

    /* ==================== CONFIG ==================== */
    public static Path cfgPath() { return FabricLoader.getInstance().getConfigDir().resolve("obsclient.properties"); }
    private static String sk(Module m, String n) { return m.name + "." + n.replaceAll("[^A-Za-z0-9]", "_"); }

    public static boolean saveConfig() {
        try {
            Properties pr = new Properties();
            pr.setProperty("version", VERSION);
            pr.setProperty("bypass", BYPASS.name());
            pr.setProperty("theme.mono", String.valueOf(ObsGui.MONO));
            pr.setProperty("lang.en", String.valueOf(EN));
            pr.setProperty("friends", String.join(",", FRIENDS));
            pr.setProperty("save.time", String.valueOf(System.currentTimeMillis()));

            ObsGui.saveLayout(pr);

            for (Module m : MODULES) {
                pr.setProperty(m.name + ".key", String.valueOf(m.key));
                pr.setProperty(m.name + ".enabled", String.valueOf(
                        SAVED_ENABLED.containsKey(m.name) ? SAVED_ENABLED.get(m.name) : m.enabled));
                for (Setting s : m.settings) {
                    if (s instanceof BoolSetting b) pr.setProperty(sk(m, s.name), String.valueOf(b.value));
                    else if (s instanceof NumSetting n) pr.setProperty(sk(m, s.name), String.valueOf(n.value));
                    else if (s instanceof ModeSetting ms) pr.setProperty(sk(m, s.name), String.valueOf(ms.index));
                    else if (s instanceof TextSetting ts) pr.setProperty(sk(m, s.name), ts.value);
                }
            }
            Path path = cfgPath(), tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream o = Files.newOutputStream(tmp)) { pr.store(o, "ObsClient " + VERSION); }
            try { Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception e) { Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }

            lastSaveTime = System.currentTimeMillis();
            lastSaveStatus = "OK @ " + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date(lastSaveTime));
            return true;
        } catch (Exception e) {
            lastSaveStatus = "FAIL: " + e.getClass().getSimpleName();
            return false;
        }
    }

    public static boolean loadConfig() {
        try {
            Path path = cfgPath();
            if (!Files.exists(path)) {
                lastLoadStatus = "NO FILE";
                return false;
            }
            Properties pr = new Properties();
            try (InputStream in = Files.newInputStream(path)) { pr.load(in); }

            String fr = pr.getProperty("friends");
            if (fr != null && !fr.isBlank()) {
                FRIENDS.clear();
                for (String s : fr.split(",")) if (!s.isBlank()) FRIENDS.add(s.trim().toLowerCase(Locale.ROOT));
            }
            String bp = pr.getProperty("bypass");
            if (bp != null) try { BYPASS = BypassMode.valueOf(bp); } catch (Exception ignored) {}
            String th = pr.getProperty("theme.mono");
            if (th != null) ObsGui.MONO = Boolean.parseBoolean(th.trim());
            String le = pr.getProperty("lang.en");
            if (le != null) EN = Boolean.parseBoolean(le.trim());

            ObsGui.loadLayout(pr);

            for (Module m : MODULES) {
                String k = pr.getProperty(m.name + ".key");
                if (k != null) try { m.key = Integer.parseInt(k.trim()); } catch (Exception ignored) {}

                String en = pr.getProperty(m.name + ".enabled");
                if (en != null) {
                    try { SAVED_ENABLED.put(m.name, Boolean.parseBoolean(en.trim())); } catch (Exception ignored) {}
                }

                for (Setting s : m.settings) {
                    String v = pr.getProperty(sk(m, s.name));
                    if (v == null) continue;
                    try {
                        if (s instanceof BoolSetting b) b.value = Boolean.parseBoolean(v.trim());
                        else if (s instanceof NumSetting n) {
                            double d = Double.parseDouble(v.trim());
                            if (Double.isNaN(d) || Double.isInfinite(d)) continue;
                            n.value = Math.max(n.min, Math.min(n.max, d));
                        } else if (s instanceof ModeSetting ms) {
                            int i = Integer.parseInt(v.trim());
                            ms.index = Math.max(0, Math.min(ms.modes.length - 1, i));
                        } else if (s instanceof TextSetting ts) {
                            ts.value = v;
                        }
                    } catch (Exception ignored) {}
                }
            }
            lastLoadTime = System.currentTimeMillis();
            lastLoadStatus = "OK @ " + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date(lastLoadTime));
            return true;
        } catch (Exception e) {
            lastLoadStatus = "FAIL: " + e.getClass().getSimpleName();
            return false;
        }
    }

    public static void resetConfig() {
        for (Module m : MODULES) {
            if (m == PREDICT) continue;
            if (m.enabled) {
                try { m.toggle(); } catch (Throwable ignored) {}
            }
        }
        SAVED_ENABLED.clear();
        FRIENDS.clear();
        BYPASS = BypassMode.GRIM;
        lastSaveStatus = "RESET";
    }

    public static String keyName(int k) {
        if (k < 0) return "None";
        if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F25) return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
        switch (k) {
            case GLFW.GLFW_KEY_SPACE: return "Space"; case GLFW.GLFW_KEY_LEFT_SHIFT: return "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R-Shift"; case GLFW.GLFW_KEY_LEFT_CONTROL: return "L-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L-Alt"; case GLFW.GLFW_KEY_RIGHT_ALT: return "R-Alt";
            case GLFW.GLFW_KEY_TAB: return "Tab"; case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_INSERT: return "Insert"; case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End"; case GLFW.GLFW_KEY_PAGE_UP: return "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PgDn"; case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down"; case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right"; case GLFW.GLFW_KEY_DELETE: return "Delete";
            case GLFW.GLFW_KEY_BACKSPACE: return "Backspace";
        }
        String n = GLFW.glfwGetKeyName(k, 0);
        return n != null ? n.toUpperCase(Locale.ROOT) : "Key " + k;
    }

    /* ==================== SETTINGS ==================== */
    public abstract static class Setting {
        public final String name, nameTr;
        public Setting(String n) { this(n, n); }
        public Setting(String n, String tr) { name = n; nameTr = tr; }
        public String label() { return EN ? name : nameTr; }
    }
    public static class BoolSetting extends Setting {
        public boolean value;
        public BoolSetting(String n, boolean v) { super(n); value = v; }
        public BoolSetting(String n, String tr, boolean v) { super(n, tr); value = v; }
    }
    public static class NumSetting extends Setting {
        public double value; public final double min, max, step;
        public NumSetting(String n, double v, double mn, double mx, double s) { super(n); value = v; min = mn; max = mx; step = s; }
        public NumSetting(String n, String tr, double v, double mn, double mx, double s) { super(n, tr); value = v; min = mn; max = mx; step = s; }
        public float f() { return (float)value; }
        public int i() { return (int)value; }
    }
    public static class ModeSetting extends Setting {
        public final String[] modes, modesTr; public int index;
        public ModeSetting(String n, int i, String... m) { super(n); index = i; modes = m; modesTr = m; }
        public ModeSetting(String n, String tr, int i, String[] en, String[] trn) { super(n, tr); index = i; modes = en; modesTr = trn; }
        public String value() { return modes[Math.max(0, Math.min(modes.length - 1, index))]; }
        public String valueLabel() { String[] m = EN ? modes : modesTr; return m[Math.max(0, Math.min(m.length - 1, index))]; }
    }
    public static class TextSetting extends Setting {
        public String value;
        public TextSetting(String n, String tr, String v) { super(n, tr); value = v; }
        public TextSetting(String n, String v) { super(n); value = v; }
    }

    /* ==================== MODULE BASE ==================== */
    public abstract static class Module {
        protected final MinecraftClient mc = MinecraftClient.getInstance();
        public final String name, nameTr, desc, descTr; public final Category cat;
        public boolean enabled = false, open = false, keyWas = false;
        public int key = -1, errors = 0;
        public final List<Setting> settings = new ArrayList<>();
        public Module(String n, String d, Category c) { this(n, n, d, d, c); }
        public Module(String n, String ntr, String d, String dtr, Category c) { name = n; nameTr = ntr; desc = d; descTr = dtr; cat = c; }
        public String label() { return EN ? name : nameTr; }
        public String descLabel() { return EN ? desc : descTr; }
        public void toggle() {
            if (!enabled && !License.check()) {
                Notification.send("§cLicense required!", 0xFFFF5C5C);
                MinecraftClient mc0 = MinecraftClient.getInstance();
                if (mc0 != null) mc0.setScreen(new LicenseScreen());
                return;
            }
            enabled = !enabled;
            try { if (enabled) onEnable(); else onDisable(); } catch (Throwable ignored) {}
            if (!(this instanceof Predict)) SAVED_ENABLED.put(name, enabled);
        }
        public void onEnable() {}
        public void onDisable() {}
        public String status() { return enabled ? "On" : "Off"; }
        public abstract void onTick(MinecraftClient mc);
    }

    /* ==================== GUI (METEOR STYLE) ==================== */
    public static class ObsGui extends Screen {
        public static boolean MONO = true;

        public static final ModeSetting ACCENT = new ModeSetting("Accent", 0, "Mint", "Purple", "Blue", "Red", "Orange", "Pink", "Rainbow", "Category");
        public static final NumSetting OPACITY = new NumSetting("Opacity", 0.92, 0.3, 1.0, 0.02);
        public static final NumSetting DIM = new NumSetting("Background Dim", 0.40, 0.0, 0.85, 0.05);
        public static final BoolSetting ANIM = new BoolSetting("Animations", true);

        public static final NumSetting ANIM_SPEED = new NumSetting("AnimSpeed", 0.35, 0.05, 1.0, 0.05);
        public static final BoolSetting HOVER_GLOW = new BoolSetting("HoverGlow", true);
        public static final BoolSetting MOD_GRADIENT = new BoolSetting("ModGradient", true);

        public static final BoolSetting DESC = new BoolSetting("Descriptions", true);
        public static final BoolSetting LANG = new BoolSetting("English", true);
        public static final ModeSetting BYPASS_SET = new ModeSetting("Bypass", 4, "VANILLA", "VULCAN", "INTAVE", "MATRIX", "GRIM", "NCP", "VERUS");

        private static final Setting[] SAVED = { ACCENT, OPACITY, DIM, ANIM, ANIM_SPEED, HOVER_GLOW, MOD_GRADIENT, DESC };
        private static final Setting[] SHOWN = { ACCENT, OPACITY, DIM, ANIM, ANIM_SPEED, HOVER_GLOW, MOD_GRADIENT, DESC, LANG, BYPASS_SET };

        public static class Panel {
            final String id; final Category cat;
            float x = -1, y = -1, coll = 1f, scroll = 0;
            boolean collapsed = false;
            int h = 18, bodyVis = 0, bodyFull = 0, contentH = 0;
            Panel(String id, Category cat) { this.id = id; this.cat = cat; }
        }
        private static final List<Panel> ORDER = new ArrayList<>();
        private static final List<Panel> ALL = new ArrayList<>();
        static {
            for (Category c : Category.values()) ALL.add(new Panel(c.name(), c));
            ALL.add(new Panel("GUI", null));
            ORDER.addAll(ALL);
        }

        private static final int W = 120, HEADER = 18, ROW = 16, BIND_H = 14;
        private static final HashMap<Module, Float> OA = new HashMap<>();

        private String search = "";
        private boolean searchActive = false;
        private TextSetting typingSetting = null; // YENI: Yazı ayarlarını düzenlemek için
        private Module listening = null, hoveredMod = null;
        private NumSetting dragging = null;
        private Panel dragPanel = null;
        private float dragDX, dragDY;
        private boolean lWas = false, rWas = false;
        private final boolean[] prevKeys = new boolean[GLFW.GLFW_KEY_LAST + 1];

        public ObsGui() {
            super(Text.literal("ObsClient"));
            LANG.value = EN;
            BYPASS_SET.index = BYPASS.ordinal();
        }

        @Override public boolean shouldPause() { return false; }
        @Override public void removed() { saveModuleStates(); saveConfig(); typingSetting = null; }

        private static String gk(Setting s) { return "gui.s." + s.name.replaceAll("[^A-Za-z0-9]", "_"); }

        public static void saveLayout(Properties pr) {
            try {
                for (Panel p : ALL) {
                    pr.setProperty("gui." + p.id + ".x", String.valueOf(p.x));
                    pr.setProperty("gui." + p.id + ".y", String.valueOf(p.y));
                    pr.setProperty("gui." + p.id + ".collapsed", String.valueOf(p.collapsed));
                    pr.setProperty("gui." + p.id + ".scroll", String.valueOf(p.scroll));
                }
                for (Setting s : SAVED) {
                    if (s instanceof BoolSetting b) pr.setProperty(gk(s), String.valueOf(b.value));
                    else if (s instanceof NumSetting n) pr.setProperty(gk(s), String.valueOf(n.value));
                    else if (s instanceof ModeSetting ms) pr.setProperty(gk(s), String.valueOf(ms.index));
                }
            } catch (Throwable ignored) {}
        }

        public static void loadLayout(Properties pr) {
            try {
                for (Panel p : ALL) {
                    try {
                        String sx = pr.getProperty("gui." + p.id + ".x");
                        String sy = pr.getProperty("gui." + p.id + ".y");
                        if (sx != null && sy != null) {
                            float fx = Float.parseFloat(sx.trim()), fy = Float.parseFloat(sy.trim());
                            if (Float.isFinite(fx) && Float.isFinite(fy) && fx >= 0 && fy >= 0) {
                                p.x = fx; p.y = fy;
                            }
                        }
                        String c = pr.getProperty("gui." + p.id + ".collapsed");
                        if (c != null) { p.collapsed = Boolean.parseBoolean(c.trim()); p.coll = p.collapsed ? 0f : 1f; }

                        String sc = pr.getProperty("gui." + p.id + ".scroll");
                        if (sc != null) {
                            try {
                                float f = Float.parseFloat(sc.trim());
                                if (Float.isFinite(f) && f >= 0) p.scroll = f;
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
                for (Setting s : SAVED) {
                    String v = pr.getProperty(gk(s));
                    if (v == null) continue;
                    try {
                        if (s instanceof BoolSetting b) b.value = Boolean.parseBoolean(v.trim());
                        else if (s instanceof NumSetting n) {
                            double d = Double.parseDouble(v.trim());
                            if (!Double.isNaN(d) && !Double.isInfinite(d)) n.value = Math.max(n.min, Math.min(n.max, d));
                        } else if (s instanceof ModeSetting ms) {
                            ms.index = Math.max(0, Math.min(ms.modes.length - 1, Integer.parseInt(v.trim())));
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        private static void resetLayout() {
            for (Panel p : ALL) { p.x = -1; p.y = -1; p.collapsed = false; p.coll = 1f; p.scroll = 0; }
            ORDER.clear(); ORDER.addAll(ALL);
        }

        private int accent(Panel p, int off) {
            switch (ACCENT.index) {
                case 0: return 0xFF7FD6A5;
                case 1: return 0xFFB65CFF;
                case 2: return 0xFF4FA3FF;
                case 3: return 0xFFFF5C7A;
                case 4: return 0xFFFFA23F;
                case 5: return 0xFFFF6FC8;
                case 6: return 0xFF000000 | MathHelper.hsvToRgb(((System.currentTimeMillis() / 20 + off * 25L) % 360) / 360f, 0.65f, 1f);
                default: return (p != null && p.cat != null) ? p.cat.color : 0xFFB65CFF;
            }
        }

        private List<Module> mods(Category c) {
            List<Module> out = new ArrayList<>();
            String q = search.toLowerCase(Locale.ROOT);
            for (Module m : MODULES)
                if (m.cat == c && (q.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(q) || m.nameTr.toLowerCase(Locale.ROOT).contains(q))) out.add(m);
            return out;
        }

        private static int settingH(Setting s) { return s instanceof NumSetting ? 18 : 14; }

        private static int settingsFullH(Module m) {
            int h = BIND_H + 3;
            for (Setting s : m.settings) h += settingH(s);
            return h;
        }

        private static int guiContentH() {
            int h = 4;
            for (Setting s : SHOWN) h += settingH(s);
            return h + 2 + ROW * 2 + 2;
        }

        private void ensurePos(Panel p, int idx) {
            if (p.x < 0 || p.y < 0) {
                int n = Math.max(1, (this.width - 10) / (W + 6));
                int perRow = Math.min(n, ALL.size());
                int total = perRow * (W + 6) - 6;
                int sx = Math.max(4, (this.width - total) / 2);
                p.x = sx + (idx % n) * (W + 6);
                p.y = 34 + (idx / n) * 190;
            }
            p.x = Math.max(0, Math.min(this.width - W, p.x));
            p.y = Math.max(0, Math.min(this.height - HEADER, p.y));
        }

        private void updateLayout(Panel p, int maxBody) {
            float target = p.collapsed ? 0f : 1f;
            float speed = (float) ANIM_SPEED.value;

            if (!ANIM.value) p.coll = target;
            else { p.coll += (target - p.coll) * speed; if (Math.abs(target - p.coll) < 0.01f) p.coll = target; }

            int content;
            if (p.cat == null) content = guiContentH();
            else {
                content = 0;
                for (Module m : mods(p.cat)) {
                    float t = m.open ? 1f : 0f, cur = OA.getOrDefault(m, 0f);
                    if (!ANIM.value) cur = t;
                    else { cur += (t - cur) * speed; if (Math.abs(t - cur) < 0.01f) cur = t; }
                    OA.put(m, cur);
                    content += ROW + (int) (settingsFullH(m) * cur);
                }
            }
            p.contentH = content;
            p.bodyFull = Math.min(content, maxBody);
            p.bodyVis = (int) (p.bodyFull * p.coll);
            p.h = HEADER + p.bodyVis;
            float maxScroll = Math.max(0, content - maxBody);
            p.scroll = Math.max(0, Math.min(maxScroll, p.scroll));
        }

        private Panel panelAt(double mx, double my) {
            for (int i = ORDER.size() - 1; i >= 0; i--) {
                Panel p = ORDER.get(i);
                if (p.x < 0) continue;
                if (mx >= p.x && mx < p.x + W && my >= p.y && my < p.y + p.h) return p;
            }
            return null;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double ha, double va) {
            Panel p = panelAt(mx, my);
            if (p != null && my >= p.y + HEADER) {
                p.scroll = Math.max(0, p.scroll - (float) (va * 14));
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(KeyInput input) {
            int kc = input.key();
            // YENI: Yazı Ayarları (TextSetting) için klavye dinleme
            if (typingSetting != null) {
                if (kc == GLFW.GLFW_KEY_ESCAPE || kc == GLFW.GLFW_KEY_ENTER) {
                    typingSetting = null;
                    saveConfig();
                    return true;
                }
                if (kc == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!typingSetting.value.isEmpty()) {
                        typingSetting.value = typingSetting.value.substring(0, typingSetting.value.length() - 1);
                    }
                    return true;
                }
                if (kc == GLFW.GLFW_KEY_SPACE) {
                    typingSetting.value += " ";
                    return true;
                }
                String ch = GLFW.glfwGetKeyName(kc, 0);
                if (ch != null && !ch.isEmpty()) {
                    typingSetting.value += ch;
                    return true;
                }
                return true;
            }

            if (listening != null && kc == GLFW.GLFW_KEY_ESCAPE) { listening = null; return true; }
            if (searchActive) {
                if (kc == GLFW.GLFW_KEY_ESCAPE || kc == GLFW.GLFW_KEY_ENTER) { searchActive = false; return true; }
                if (kc == GLFW.GLFW_KEY_BACKSPACE) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); return true; }
                if (kc == GLFW.GLFW_KEY_SPACE) { search += " "; return true; }
                String ch = GLFW.glfwGetKeyName(kc, 0);
                if (ch != null && !ch.isEmpty()) { search += ch; return true; }
            }
            return super.keyPressed(input);
        }

        @Override
        public void render(DrawContext g, int mx, int my, float delta) {
            super.render(g, mx, my, delta);
            TextRenderer tr = this.textRenderer;
            g.fill(0, 0, this.width, this.height, ((int) (DIM.value * 255)) << 24);

            EN = LANG.value;
            if (BYPASS_SET.index != BYPASS.ordinal())
                BYPASS = BypassMode.values()[Math.max(0, Math.min(BypassMode.values().length - 1, BYPASS_SET.index))];

            long handle = this.client.getWindow().getHandle();
            boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean right = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            boolean lClick = left && !lWas, rClick = right && !rWas;
            lWas = left; rWas = right;
            if (!left) {
                dragging = null;
                if (dragPanel != null) { dragPanel = null; saveConfig(); }
            }
            hoveredMod = null;

            for (int kk = GLFW.GLFW_KEY_SPACE; kk <= GLFW.GLFW_KEY_LAST; kk++) {
                boolean d = GLFW.glfwGetKey(handle, kk) == GLFW.GLFW_PRESS;
                if (listening != null && d && !prevKeys[kk]) {
                    if (kk == GLFW.GLFW_KEY_ESCAPE) listening = null;
                    else if (kk == GLFW.GLFW_KEY_BACKSPACE || kk == GLFW.GLFW_KEY_DELETE) { listening.key = -1; listening = null; saveConfig(); }
                    else { listening.key = kk; listening.keyWas = true; listening = null; saveConfig(); }
                }
                prevKeys[kk] = d;
            }

            int maxBody = Math.max(100, Math.min(300, this.height - 90));
            for (int i = 0; i < ALL.size(); i++) { ensurePos(ALL.get(i), i); updateLayout(ALL.get(i), maxBody); }

            int sw = 180, sx = this.width / 2 - sw / 2, sy = 8;
            boolean onSearch = mx >= sx && mx <= sx + sw && my >= sy && my <= sy + 16;
            if (lClick) searchActive = onSearch;

            Panel hov = onSearch ? null : panelAt(mx, my);
            if ((lClick || rClick) && hov != null && ORDER.get(ORDER.size() - 1) != hov) { ORDER.remove(hov); ORDER.add(hov); }

            if (hov != null && dragPanel == null) {
                boolean onHeader = my >= hov.y && my < hov.y + HEADER;
                if (onHeader && rClick) hov.collapsed = !hov.collapsed;
                else if (onHeader && lClick) {
                    if (mx >= hov.x + W - 14) hov.collapsed = !hov.collapsed;
                    else { dragPanel = hov; dragDX = mx - hov.x; dragDY = my - hov.y; }
                }
            }
            if (dragPanel != null && left) {
                dragPanel.x = Math.max(0, Math.min(this.width - W, mx - dragDX));
                dragPanel.y = Math.max(0, Math.min(this.height - HEADER, my - dragDY));
            }

            for (Panel p : new ArrayList<>(ORDER)) drawPanel(g, tr, p, ALL.indexOf(p), mx, my, lClick, rClick, hov);

            int topAcc = accent(null, 0);
            g.drawText(tr, "ObsClient", 8, 8, topAcc, true);
            g.drawText(tr, VERSION, 8 + tr.getWidth("ObsClient ") , 8, 0xFF8A8A95, false);
            g.fill(sx - 1, sy - 1, sx + sw + 1, sy + 17, searchActive ? topAcc : 0xFF2A2A31);
            g.fill(sx, sy, sx + sw, sy + 16, 0xF0121216);
            String shown = search.isEmpty() && !searchActive ? (EN ? "Search modules..." : "Modül ara...")
                    : search + (searchActive && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
            g.drawText(tr, shown, sx + 5, sy + 4, search.isEmpty() && !searchActive ? 0xFF6A6A75 : 0xFFFFFFFF, false);

            String hint = EN ? "Drag header: move  |  Right click header: collapse  |  Right click module: settings"
                    : "Başlığı sürükle: taşı  |  Başlığa sağ tık: küçült  |  Modüle sağ tık: ayarlar";
            g.drawCenteredTextWithShadow(tr, hint, this.width / 2, this.height - 12, 0xFF70707B);

            if (hoveredMod != null && DESC.value && dragPanel == null) {
                String t = hoveredMod.descLabel();
                if (t != null && !t.isEmpty()) {
                    int tw = tr.getWidth(t) + 8;
                    int tx = Math.min(mx + 10, this.width - tw - 2), ty = Math.min(my + 12, this.height - 16);
                    int a = accent(hov, 0);
                    g.fill(tx - 1, ty - 1, tx + tw + 1, ty + 14, a);
                    g.fill(tx, ty, tx + tw, ty + 13, 0xF0101014);
                    g.drawText(tr, t, tx + 4, ty + 3, 0xFFFFFFFF, false);
                }
            }
        }

        private void drawPanel(DrawContext g, TextRenderer tr, Panel p, int idx, int mx, int my, boolean lClick, boolean rClick, Panel hov) {
            int x = (int) p.x, y = (int) p.y;
            int acc = accent(p, idx);
            int alpha = (int) (OPACITY.value * 255);
            int bodyTop = y + HEADER;
            boolean inter = hov == p && dragPanel == null;

            g.fill(x - 1, y - 1, x + W + 1, y + p.h + 1, 0x70000000);
            g.fillGradient(x, y, x + W, y + HEADER, 0xFF1E1E25, 0xFF131318);
            g.fill(x, y + HEADER - 1, x + W, y + HEADER, acc);
            g.fill(x, y, x + 2, y + HEADER - 1, acc);

            String title = p.cat != null ? p.cat.label : (EN ? "Customize" : "Özelleştir");
            g.drawText(tr, title, x + 7, y + 5, 0xFFFFFFFF, false);

            if (p.cat != null) {
                int on = 0;
                for (Module m : MODULES) if (m.cat == p.cat && m.enabled) on++;
                if (on > 0) { String c = String.valueOf(on); g.drawText(tr, c, x + W - 14 - tr.getWidth(c), y + 5, acc, false); }
            }
            g.drawText(tr, p.collapsed ? "+" : "-", x + W - 10, y + 5, 0xFFAAAAAA, false);

            if (p.bodyVis <= 0) return;

            g.fill(x, bodyTop, x + W, bodyTop + p.bodyVis, withAlpha(0x0F0F13, alpha));
            g.enableScissor(x, bodyTop, x + W, bodyTop + p.bodyVis);
            int curY = bodyTop - (int) p.scroll;

            if (p.cat == null) {
                int sy = curY + 4;
                for (Setting s : SHOWN)
                    sy += drawSetting(g, tr, s, x, sy, mx, my, inter, lClick, rClick, acc, bodyTop, bodyTop + p.bodyVis);
                sy += 2;
                String[] labels = { EN ? "Reset layout" : "Düzeni sıfırla", EN ? "Disable all modules" : "Tüm modülleri kapat" };
                for (int b = 0; b < 2; b++) {
                    boolean h = inter && mx >= x && mx < x + W && my >= Math.max(sy, bodyTop) && my < Math.min(sy + ROW - 2, bodyTop + p.bodyVis);
                    g.fill(x + 4, sy, x + W - 4, sy + ROW - 2, h ? withAlpha(acc, 90) : 0xFF1B1B21);
                    g.drawCenteredTextWithShadow(tr, labels[b], x + W / 2, sy + 4, 0xFFFFFFFF);
                    if (h && lClick) { if (b == 0) resetLayout(); else PANIC = true; }
                    sy += ROW;
                }
            } else {
                for (Module m : mods(p.cat)) {
                    float oa = OA.getOrDefault(m, 0f);
                    int fullSet = settingsFullH(m);
                    int setH = (int) (fullSet * oa);

                    if (curY + ROW > bodyTop && curY < bodyTop + p.bodyVis) {
                        boolean hover = inter && mx >= x && mx < x + W && my >= Math.max(curY, bodyTop) && my < Math.min(curY + ROW, bodyTop + p.bodyVis);

                        g.fill(x, curY, x + W, curY + ROW, withAlpha(0x17171C, alpha));
                        if (m.enabled) {
                            if (MOD_GRADIENT.value) {
                                g.fillGradient(x, curY, x + W, curY + ROW, withAlpha(acc, 90), withAlpha(acc, 20));
                            } else {
                                g.fill(x, curY, x + W, curY + ROW, withAlpha(acc, 55));
                            }
                            g.fill(x, curY, x + 2, curY + ROW, acc);
                        }

                        if (hover) {
                            g.fill(x, curY, x + W, curY + ROW, HOVER_GLOW.value ? withAlpha(acc, 60) : 0x14FFFFFF);
                        }

                        String kn = m.key >= 0 ? "[" + keyName(m.key) + "]" : null;
                        int nameMax = W - 24 - (kn != null ? tr.getWidth(kn) + 4 : 0);
                        g.drawText(tr, tr.trimToWidth(m.label(), nameMax), x + 6, curY + 4, m.enabled ? 0xFFFFFFFF : 0xFFA8A8B3, false);
                        if (kn != null) g.drawText(tr, kn, x + W - 14 - tr.getWidth(kn), curY + 4, 0xFF70707B, false);
                        g.drawText(tr, m.open ? "-" : "+", x + W - 10, curY + 4, 0xFF8A8A95, false);

                        if (hover) {
                            hoveredMod = m;
                            if (lClick) {
                                if (mx >= x + W - 14) m.open = !m.open;
                                else {
                                    m.toggle();
                                    String stateStr = m.enabled ? "§aON" : "§cOFF";
                                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("§bObsClient §8| §f" + m.name + " " + stateStr));
                                }
                            } else if (rClick) m.open = !m.open;
                        }
                    }

                    if (setH > 0) {
                        int top = curY + ROW, bot = top + setH;
                        int cT = Math.max(top, bodyTop), cB = Math.min(bot, bodyTop + p.bodyVis);
                        if (cT < cB) {
                            g.enableScissor(x, cT, x + W, cB);
                            g.fill(x, top, x + W, bot, 0xE60A0A0D);
                            g.fill(x, top, x + 1, bot, withAlpha(acc, 200));
                            int sy = top - (fullSet - setH);
                            boolean si = inter && oa > 0.95f;

                            boolean bh = si && mx >= x && mx < x + W && my >= Math.max(sy, cT) && my < Math.min(sy + BIND_H, cB);
                            if (bh) g.fill(x + 1, sy, x + W, sy + BIND_H, 0x12FFFFFF);
                            g.drawText(tr, EN ? "Bind" : "Tuş", x + 6, sy + 3, 0xFFC8C8D0, false);
                            String bv = listening == m ? "..." : keyName(m.key);
                            g.drawText(tr, bv, x + W - 6 - tr.getWidth(bv), sy + 3, listening == m ? acc : 0xFF8A8A95, false);
                            if (bh && lClick) listening = listening == m ? null : m;
                            sy += BIND_H;

                            for (Setting s : m.settings)
                                sy += drawSetting(g, tr, s, x, sy, mx, my, si, lClick, rClick, acc, cT, cB);
                            g.disableScissor();
                        }
                    }
                    curY += ROW + setH;
                }
            }
            g.disableScissor();

            if (p.contentH > p.bodyFull && p.bodyFull > 0) {
                float ratio = p.bodyFull / (float) p.contentH;
                int barH = Math.max(10, (int) (p.bodyVis * ratio));
                float maxScroll = p.contentH - p.bodyFull;
                int barY = bodyTop + (int) ((p.bodyVis - barH) * (p.scroll / Math.max(1f, maxScroll)));
                g.fill(x + W - 2, barY, x + W - 1, barY + barH, withAlpha(acc, 200));
            }
            g.fill(x, bodyTop + p.bodyVis - 1, x + W, bodyTop + p.bodyVis, withAlpha(acc, 110));
        }

        private int drawSetting(DrawContext g, TextRenderer tr, Setting s, int x, int sy, int mx, int my,
                                boolean inter, boolean lClick, boolean rClick, int acc, int clipT, int clipB) {
            int h = settingH(s);
            boolean hov = inter && mx >= x && mx < x + W && my >= Math.max(sy, clipT) && my < Math.min(sy + h, clipB);
            if (hov) g.fill(x + 1, sy, x + W, sy + h, 0x12FFFFFF);

            if (s instanceof BoolSetting b) {
                g.drawText(tr, b.label(), x + 6, sy + 3, 0xFFC8C8D0, false);
                int tx = x + W - 20, ty = sy + 3;
                g.fill(tx, ty, tx + 14, ty + 8, b.value ? acc : 0xFF2A2A31);
                int kx = b.value ? tx + 8 : tx + 1;
                g.fill(kx, ty + 1, kx + 5, ty + 7, 0xFFFFFFFF);
                if (hov && lClick) b.value = !b.value;
            } else if (s instanceof NumSetting n) {
                String val = (n.step >= 1.0) ? String.valueOf((int) Math.round(n.value)) : String.format("%.2f", n.value);
                g.drawText(tr, n.label(), x + 6, sy + 2, 0xFFC8C8D0, false);
                g.drawText(tr, val, x + W - 6 - tr.getWidth(val), sy + 2, acc, false);
                int bx = x + 6, bw = W - 12, by = sy + 13;
                g.fill(bx, by, bx + bw, by + 2, 0xFF2A2A31);
                double frac = (n.value - n.min) / (n.max - n.min);
                int fw = (int) (bw * Math.max(0, Math.min(1, frac)));
                g.fill(bx, by, bx + fw, by + 2, acc);
                g.fill(bx + fw - 1, by - 1, bx + fw + 2, by + 3, 0xFFFFFFFF);
                if (hov && lClick) dragging = n;
                if (dragging == n) {
                    double f = Math.max(0, Math.min(1, (mx - bx) / (double) bw));
                    double v = n.min + f * (n.max - n.min);
                    v = Math.round(v / n.step) * n.step;
                    n.value = Math.max(n.min, Math.min(n.max, v));
                }
            } else if (s instanceof ModeSetting ms) {
                String v = ms.valueLabel();
                g.drawText(tr, ms.label(), x + 6, sy + 3, 0xFFC8C8D0, false);
                g.drawText(tr, v, x + W - 6 - tr.getWidth(v), sy + 3, acc, false);
                if (hov && lClick) ms.index = (ms.index + 1) % ms.modes.length;
                else if (hov && rClick) ms.index = (ms.index - 1 + ms.modes.length) % ms.modes.length;
            } else if (s instanceof TextSetting ts) {
                // YENI: TextSetting Tıklanabilir ve Yazılabilir
                g.drawText(tr, s.label(), x + 6, sy + 3, 0xFFC8C8D0, false);
                String v = typingSetting == ts ? ts.value + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : tr.trimToWidth(ts.value, 50);
                int color = typingSetting == ts ? acc : 0xFF8A8A95;
                g.drawText(tr, v, x + W - 6 - tr.getWidth(v), sy + 3, color, false);
                if (hov && lClick) {
                    typingSetting = ts;
                    searchActive = false;
                }
            }
            return h;
        }
    }
}