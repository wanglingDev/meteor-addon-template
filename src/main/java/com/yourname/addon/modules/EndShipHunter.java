package com.yourname.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.EntityControl;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class EndShipHunter extends Module {

    private static final int   EC_REGION_SIZE = 20;
    private static final int   EC_CHUNK_RANGE = 9;
    private static final long  EC_SALT        = 10387313L;
    private static final float SHIP_CHANCE    = 1f / 8f;
    private static final long  LCG_MULT       = 0x5DEECE66DL;
    private static final long  LCG_ADD        = 0xBL;
    private static final long  LCG_MASK       = (1L << 48) - 1L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> worldSeed = sgGeneral.add(new StringSetting.Builder()
        .name("world-seed")
        .description("Numeric seed. Use /seed in-game.")
        .defaultValue("0")
        .build()
    );

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("search-radius")
        .description("Region radius to scan.")
        .defaultValue(50).min(5).max(200).sliderRange(5, 200)
        .build()
    );

    private final Setting<Boolean> requireShip = sgGeneral.add(new BoolSetting.Builder()
        .name("require-ship")
        .description("Only target End Cities predicted to have a ship.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-entity-fly")
        .description("Auto-enable EntityControl flight on activate.")
        .defaultValue(true)
        .build()
    );

    private final List<BlockPos> candidates = new ArrayList<>();
    private BlockPos target      = null;
    private boolean  navigating  = false;

    private EntityControl ec            = null;
    private boolean       ecWasActive   = false;
    private boolean       ecPrevFly     = false;
    private double        ecPrevFall    = 0;
    private boolean       ecPrevAntiKick = true;

    public EndShipHunter() {
        super(com.yourname.addon.YourAddon.CATEGORY,
            "end-ship-hunter",
            "Locates nearest End Ship via seed maths.");
    }

    @Override
    public void onActivate() {
        candidates.clear();
        target    = null;
        navigating = false;

        long seed;
        try {
            seed = Long.parseLong(worldSeed.get().trim());
        } catch (NumberFormatException e) {
            ChatUtils.error("EndShipHunter", "Invalid seed.");
            toggle();
            return;
        }

        if (autoFlight.get()) applyEntityControl();

        scanEndCities(seed);

        if (candidates.isEmpty()) {
            ChatUtils.warning("EndShipHunter", "No candidates found. Increase radius.");
            toggle();
            return;
        }

        pickNearest();
        ChatUtils.info("EndShipHunter", String.format(
            "Found %d candidate(s). Nearest: %d %d %d",
            candidates.size(), target.getX(), target.getY(), target.getZ()));

        navigating = true;
    }

    @Override
    public void onDeactivate() {
        navigating = false;
        if (autoFlight.get()) restoreEntityControl();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !navigating || target == null) return;
        Vec3d pos  = mc.player.getPos();
        Vec3d dest = Vec3d.ofCenter(target);
        if (pos.distanceTo(dest) < 16.0) {
            ChatUtils.info("EndShipHunter", "Arrived! Grab that Elytra!");
            navigating = false;
            toggle();
        }
    }

    private void applyEntityControl() {
        ec = Modules.get().get(EntityControl.class);
        if (ec == null) return;
        ecWasActive    = ec.isActive();
        ecPrevFly      = getSetting(ec, "fly",           Boolean.class, false);
        ecPrevFall     = getSetting(ec, "fall-speed",    Double.class,  0.0);
        ecPrevAntiKick = getSetting(ec, "anti-fly-kick", Boolean.class, true);
        setSetting(ec, "fly",           true);
        setSetting(ec, "fall-speed",    0.0);
        setSetting(ec, "anti-fly-kick", true);
        if (!ecWasActive) ec.toggle();
    }

    private void restoreEntityControl() {
        if (ec == null) return;
        setSetting(ec, "fly",           ecPrevFly);
        setSetting(ec, "fall-speed",    ecPrevFall);
        setSetting(ec, "anti-fly-kick", ecPrevAntiKick);
        if (!ecWasActive && ec.isActive()) ec.toggle();
        ec = null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getSetting(Module m, String name, Class<T> type, T fallback) {
        for (SettingGroup g : m.settings)
            for (Setting<?> s : g)
                if (s.name.equals(name) && type.isInstance(s.get()))
                    return (T) s.get();
        return fallback;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setSetting(Module m, String name, Object val) {
        for (SettingGroup g : m.settings)
            for (Setting s : g)
                if (s.name.equals(name)) { s.set(val); return; }
    }

    private void scanEndCities(long worldSeed) {
        int r = searchRadius.get();
        for (int rx = -r; rx <= r; rx++) {
            for (int rz = -r; rz <= r; rz++) {
                long s = regionSeed(worldSeed, rx, rz, EC_SALT);
                int[] xi = nextInt(s, EC_CHUNK_RANGE); s = xi[0];
                int[] zi = nextInt(s, EC_CHUNK_RANGE); s = zi[0];
                if (requireShip.get()) {
                    int[] fi = nextFloat(s);
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

    private static int[] nextFloat(long seed) {
        seed = (seed * LCG_MULT + LCG_ADD) & LCG_MASK;
        return new int[]{ (int) seed, (int)(seed >>> 24) };
    }

    private void pickNearest() {
        if (mc.player == null || candidates.isEmpty()) return;
        Vec3d pos = mc.player.getPos();
        target = candidates.stream()
            .min((a, b) -> Double.compare(
                pos.squaredDistanceTo(a.getX(), pos.y, a.getZ()),
                pos.squaredDistanceTo(b.getX(), pos.y, b.getZ())))
            .orElse(candidates.get(0));
    }
}
