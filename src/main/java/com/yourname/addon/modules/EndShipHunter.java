package com.yourname.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.EntityControl;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class EndShipHunter extends Module {

    // ── cubiomes constants ────────────────────────────────────────────────────
    private static final int   EC_REGION_SIZE = 20;
    private static final int   EC_CHUNK_RANGE = 9;
    private static final long  EC_SALT        = 10387313L;
    private static final float SHIP_CHANCE    = 1f / 8f;

    private static final long LCG_MULT = 0x5DEECE66DL;
    private static final long LCG_ADD  = 0xBL;
    private static final long LCG_MASK = (1L << 48) - 1L;

    // ── Settings ──────────────────────────────────────────────────────────────
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> worldSeed = sgGeneral.add(new StringSetting.Builder()
        .name("world-seed")
        .description("Numeric seed. Use /seed in-game.")
        .defaultValue("0")
        .build()
    );

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("search-radius")
        .description("Region radius to scan (1 region = 320 blocks).")
        .defaultValue(50).min(5).max(200).sliderRange(5, 200)
        .build()
    );

    private final Setting<Boolean> requireShip = sgGeneral.add(new BoolSetting.Builder()
        .name("require-ship")
        .description("Only target End Cities predicted to have a ship.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoNavigate = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-navigate")
        .description("Send nearest target to Baritone automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-entity-fly")
        .description("Auto-enable EntityControl flight. Restores state on deactivation.")
        .defaultValue(true)
        .build()
    );

    // ── Runtime state ─────────────────────────────────────────────────────────
    private final List<BlockPos> candidates = new ArrayList<>();
    private BlockPos target    = null;
    private boolean navigating = false;

    // EntityControl saved state
    private EntityControl ec              = null;
    private boolean       ecWasActive     = false;
    private boolean       ecPrevFly       = false;
    private double        ecPrevFallSpeed = 0;
    private boolean       ecPrevAntiKick  = true;

    // ── Constructor ───────────────────────────────────────────────────────────
    public EndShipHunter() {
        super(
            com.yourname.addon.YourAddon.CATEGORY,
            "end-ship-hunter",
            "Locates nearest End Ship via seed maths, navigates via Baritone."
        );
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onActivate() {
        candidates.clear();
        target     = null;
        navigating = false;

        long seed;
        try {
            seed = Long.parseLong(worldSeed.get().trim());
        } catch (NumberFormatException e) {
            ChatUtils.error("EndShipHunter", "Invalid seed — use /seed in-game.");
            toggle();
            return;
        }

        if (autoFlight.get()) applyEntityControl();

        ChatUtils.info("EndShipHunter", "Scanning " + searchRadius.get() + " regions...");
        scanEndCities(seed);

        if (candidates.isEmpty()) {
            ChatUtils.warning("EndShipHunter", requireShip.get()
                ? "No End Ships found. Try disabling require-ship or increasing radius."
                : "No End Cities found. Increase search-radius.");
            toggle();
            return;
        }

        pickNearest();
        ChatUtils.info("EndShipHunter", String.format(
            "Found %d candidate(s). Nearest → %d, %d, %d",
            candidates.size(), target.getX(), target.getY(), target.getZ()));

        if (autoNavigate.get()) {
            startBaritone(target);
            navigating = true;
        }
    }

    @Override
    public void onDeactivate() {
        stopBaritone();
        navigating = false;
        if (autoFlight.get()) restoreEntityControl();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !navigating || target == null) return;

        // Mojang mappings: position() instead of getPos()
        Vec3 pos  = mc.player.position();
        Vec3 dest = Vec3.atCenterOf(target);

        if (pos.distanceTo(dest) < 16.0) {
            ChatUtils.info("EndShipHunter", "Arrived! Go grab that Elytra!");
            stopBaritone();
            navigating = false;
            toggle();
        }
    }

    // ── EntityControl integration ─────────────────────────────────────────────
    private void applyEntityControl() {
        ec = Modules.get().get(EntityControl.class);
        if (ec == null) {
            ChatUtils.warning("EndShipHunter", "EntityControl not found — flight unavailable.");
            return;
        }

        ecWasActive     = ec.isActive();
        ecPrevFly       = getSettingValue(ec, "fly",           Boolean.class, false);
        ecPrevFallSpeed = getSettingValue(ec, "fall-speed",    Double.class,  0.0);
        ecPrevAntiKick  = getSettingValue(ec, "anti-fly-kick", Boolean.class, true);

        setSettingValue(ec, "fly",           true);
        setSettingValue(ec, "fall-speed",    0.0);
        setSettingValue(ec, "anti-fly-kick", true);

        if (!ecWasActive) ec.toggle();
        ChatUtils.info("EndShipHunter", "EntityControl flight enabled.");
    }

    private void restoreEntityControl() {
        if (ec == null) return;
        setSettingValue(ec, "fly",           ecPrevFly);
        setSettingValue(ec, "fall-speed",    ecPrevFallSpeed);
        setSettingValue(ec, "anti-fly-kick", ecPrevAntiKick);
        if (!ecWasActive && ec.isActive()) ec.toggle();
        ec = null;
        ChatUtils.info("EndShipHunter", "EntityControl restored.");
    }

    @SuppressWarnings("unchecked")
    private <T> T getSettingValue(Module module, String name, Class<T> type, T fallback) {
        for (SettingGroup g : module.settings)
            for (Setting<?> s : g)
                if (s.name.equals(name)) {
                    Object v = s.get();
                    return type.isInstance(v) ? (T) v : fallback;
                }
        return fallback;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setSettingValue(Module module, String name, Object value) {
        for (SettingGroup g : module.settings)
            for (Setting s : g)
                if (s.name.equals(name)) { s.set(value); return; }
    }

    // ── End City scanner ──────────────────────────────────────────────────────
    private void scanEndCities(long worldSeed) {
        int r = searchRadius.get();
        for (int rx = -r; rx <= r; rx++) {
            for (int rz = -r; rz <= r; rz++) {
                long s = regionSeed(worldSeed, rx, rz, EC_SALT);

                int[] xi = nextInt(s, EC_CHUNK_RANGE); s = xi[0];
                int[] zi = nextInt(s, EC_CHUNK_RANGE); s = zi[0];

                if (requireShip.get()) {
                    int[] fi = nextIntFloat(s);
                    if (fi[1] / (float)(1 << 24) >= SHIP_CHANCE) continue;
                }

                candidates.add(new BlockPos(
                    (rx * EC_REGION_SIZE + xi[1]) * 16,
                    65,
                    (rz * EC_REGION_SIZE + zi[1]) * 16
                ));
            }
        }
    }

    // ── LCG helpers ───────────────────────────────────────────────────────────
    private static long regionSeed(long worldSeed, int rx, int rz, long salt) {
        long c = (long) rx * 341873128712L + (long) rz * 132897987541L + worldSeed + salt;
        return (c ^ LCG_MULT) & LCG_MASK;
    }

    private static int[] nextInt(long seed, int bound) {
        int bits, val;
        do {
            seed = (seed * LCG_MULT + LCG_ADD) & LCG_MASK;
            bits = (int)(seed >>> 17);
            val  = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return new int[]{ (int) seed, val };
    }

    private static int[] nextIntFloat(long seed) {
        seed = (seed * LCG_MULT + LCG_ADD) & LCG_MASK;
        return new int[]{ (int) seed, (int)(seed >>> 24) };
    }

    // ── Target selection ──────────────────────────────────────────────────────
    private void pickNearest() {
        if (mc.player == null || candidates.isEmpty()) return;
        Vec3 pos = mc.player.position();
        target = candidates.stream()
            .min((a, b) -> Double.compare(
                pos.distanceToSqr(a.getX(), pos.y, a.getZ()),
                pos.distanceToSqr(b.getX(), pos.y, b.getZ())))
            .orElse(candidates.get(0));
    }

    // ── Baritone ──────────────────────────────────────────────────────────────
    private void startBaritone(BlockPos dest) {
        IBaritone bar = BaritoneAPI.getProvider().getPrimaryBaritone();
        bar.settings().elytraFlightNoLand.value = true;
        bar.getCustomGoalProcess().setGoalAndPath(new GoalBlock(dest));
        ChatUtils.info("EndShipHunter",
            "Baritone → " + dest.getX() + ", " + dest.getY() + ", " + dest.getZ());
    }

    private void stopBaritone() {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone()
                .getPathingBehavior().cancelEverything();
        } catch (Exception ignored) {}
    }
}
