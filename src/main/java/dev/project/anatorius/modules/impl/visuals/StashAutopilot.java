package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import dev.project.anatorius.modules.impl.player.AutoFireworkModule;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.minecraft.FlightInput;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.project.anatorius.utils.pathing.BaritoneBridge;
import dev.project.anatorius.utils.pathing.Pathfinder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StashAutopilot implements IMinecraft {
    private enum State { TAKEOFF, FLY, LAND, HOVER }

    private enum Flyer {
        ELYTRAFLY_CONTROL("ElytraFly (Control)", true, true),
        ELYTRAFLY_PACKET("ElytraFly (Packet)", true, true),
        FAKEFLY_TICK("FakeFly (Tick)", true, false),
        FAKEFLY_GRIM("FakeFly (Grim)", false, false),
        BARITONE("Baritone", false, true);

        final String label;
        final boolean hovers;
        final boolean wearsElytra;

        Flyer(String label, boolean hovers, boolean wearsElytra) {
            this.label = label;
            this.hovers = hovers;
            this.wearsElytra = wearsElytra;
        }
    }

    private enum Engine { ELYTRAFLY, FAKEFLY, BARITONE }

    private static final double REACHED = 24;
    private static final double BARITONE_HANDOFF = 160;
    private static final double STRAIGHT_STEP = 512;
    private static final int LOW_DURABILITY = 30;

    private final StashFinderModule module;
    private State state;
    private Engine engine;
    private final Map<Module, Boolean> switched = new LinkedHashMap<>();
    private Vec3d origin;
    private String routeOf;
    private boolean finished;
    private Vec3d corner;
    private int leg;
    private int cornerNo;
    private float straightYaw;
    private Vec3d waypoint;
    private String status = "";
    private int vertical;
    private double verticalSpeed = Double.NaN;
    private float forward;
    private boolean manual;
    private int hoverTicks;
    private int ticksNotDown;
    private double spiralTo = Double.NaN;
    String debug = "";

    int cornerNo() { return state == null ? -1 : cornerNo; }
    private List<Vec3d> walkPath;
    private int walkIndex, walkAge;
    private int glideWait;
    private boolean airRestart;

    StashAutopilot(StashFinderModule module) {
        this.module = module;
    }

    boolean active() {
        return state != null;
    }

    String status() {
        return status;
    }

    Vec3d waypoint() {
        return state == null ? null : waypoint;
    }

    void start() {
        if (mc.player == null || state != null) return;
        boolean nether = mc.world.getRegistryKey() == World.NETHER;
        ElytraFlyModule ef = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
        FakeFlyModule ff = Anatorius.MODULE_MANAGER.getModule(FakeFlyModule.class);
        engine = switch (module.engine.getValue()) {
            case "ElytraFly" -> Engine.ELYTRAFLY;
            case "FakeFly" -> Engine.FAKEFLY;
            case "Baritone" -> Engine.BARITONE;
            default -> nether ? Engine.BARITONE : ff != null && ff.isToggled() ? Engine.FAKEFLY : Engine.ELYTRAFLY;
        };
        if (nether && engine != Engine.BARITONE) {
            say("Under the Nether roof only Baritone can find a way: flying with Baritone.");
            engine = Engine.BARITONE;
        }
        if (engine == Engine.BARITONE && !BaritoneBridge.available()) {
            if (nether) { fail("Baritone isn't loaded, and nothing else can fly under the Nether roof."); return; }
            say("Baritone isn't loaded: flying with ElytraFly.");
            engine = Engine.ELYTRAFLY;
        }
        if ((engine == Engine.ELYTRAFLY && ef == null) || (engine == Engine.FAKEFLY && ff == null)) { fail("That flight module isn't available."); return; }
        if (!hasElytra()) { fail("No elytra worn or in the inventory."); return; }
        Flyer flyer = flyer();
        if ((flyer == Flyer.BARITONE || flyer == Flyer.FAKEFLY_GRIM) && countRockets() == 0) {
            fail(flyer.label + " flies with rockets: put firework rockets in your inventory.");
            return;
        }

        switchModule(ef, engine == Engine.ELYTRAFLY);
        switchModule(ff, engine == Engine.FAKEFLY);
        switchModule(Anatorius.MODULE_MANAGER.getModule(AutoFireworkModule.class), false);

        routeOf = routeKey();
        finished = false;
        boolean resumed = module.resume.getValue() && resumeRoute();
        if (!resumed) {
            origin = new Vec3d(Math.floor(mc.player.getX()) + 0.5, 0, Math.floor(mc.player.getZ()) + 0.5);
            corner = origin;
            leg = 0;
            cornerNo = 0;
            straightYaw = Math.round(mc.player.getYaw() / 45f) * 45f;
            waypoint = nextWaypoint();
        }
        state = State.TAKEOFF;
        String route = module.pattern.getValue().equals("Straight")
                ? "a straight line " + compass(straightYaw)
                : "a spiral with lanes " + laneSpacing() / 16 + " chunks (" + laneSpacing() + " m) apart";
        if (resumed) say("Automatic on: " + flyer.label + ", going on with the last route (" + route + ") at corner " + cornerNo
                + ", started at " + (int) origin.x + ", " + (int) origin.z + ". Resume off starts a new one.");
        else say("Automatic on: " + flyer.label + ", " + route + ", out to " + maxLabel() + ". Hold a movement key to steer yourself.");
    }

    private void fail(String why) {
        say(why);
        module.automatic.setValue(false);
    }

    void stop(String why) {
        if (state == null) return;
        if (engine == Engine.BARITONE) BaritoneBridge.stop();
        boolean airborne = mc.player != null && !mc.player.isOnGround();
        Flyer flyer = mc.player == null ? null : flyer();
        for (Map.Entry<Module, Boolean> e : switched.entrySet()) {
            Module m = e.getKey();
            boolean holdsUs = airborne && flyer != null && flyer.hovers && m == flightModule();
            boolean wouldClash = airborne && e.getValue() && m != flightModule()
                    && (m instanceof ElytraFlyModule || m instanceof FakeFlyModule);
            if (holdsUs || wouldClash) continue;
            if (m.isToggled() != e.getValue()) m.setToggled(e.getValue());
        }
        switched.clear();
        saveRoute();
        state = null;
        walkPath = null;
        forward = 0;
        vertical = 0;
        spiralTo = Double.NaN;
        if (why != null) say(why);
    }

    private void switchModule(Module m, boolean on) {
        if (m == null || m.isToggled() == on) return;
        switched.putIfAbsent(m, m.isToggled());
        m.setToggled(on);
    }

    private Module flightModule() {
        return switch (engine) {
            case ELYTRAFLY -> Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
            case FAKEFLY -> Anatorius.MODULE_MANAGER.getModule(FakeFlyModule.class);
            case BARITONE -> null;
        };
    }

    private Flyer flyer() {
        return switch (engine) {
            case ELYTRAFLY -> {
                ElytraFlyModule ef = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
                yield ef != null && ef.mode.getValue().equalsIgnoreCase("Packet") ? Flyer.ELYTRAFLY_PACKET : Flyer.ELYTRAFLY_CONTROL;
            }
            case FAKEFLY -> {
                FakeFlyModule ff = Anatorius.MODULE_MANAGER.getModule(FakeFlyModule.class);
                yield Flyer.FAKEFLY_TICK;
            }
            case BARITONE -> Flyer.BARITONE;
        };
    }

    private boolean flying(Flyer f) {
        return switch (f) {
            case ELYTRAFLY_CONTROL, FAKEFLY_GRIM, BARITONE -> mc.player.isGliding();
            case ELYTRAFLY_PACKET -> !mc.player.isOnGround() && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
            case FAKEFLY_TICK -> !mc.player.isOnGround();
        };
    }

    void tick() {
        if (state == null || mc.player == null) return;
        if (mc.player.isDead()) { stop(null); module.automatic.setValue(false); return; }
        forward = 0;
        vertical = 0;
        verticalSpeed = Double.NaN;
        Flyer f = flyer();
        Module fm = flightModule();
        if (fm != null && !fm.isToggled()) {
            stop(fm.getName() + " was switched off: Automatic stopped.");
            module.automatic.setValue(false);
            return;
        }

        switch (state) {
            case TAKEOFF -> takeoff(f);
            case FLY -> fly(f);
            case LAND -> land(f);
            case HOVER -> status = "route finished, hovering";
        }
        if (!manual && engine != Engine.BARITONE) {
            if (f == Flyer.ELYTRAFLY_PACKET && vertical != 0 && Double.isNaN(verticalSpeed)) verticalSpeed = module.climbSpeed.getValue().doubleValue();
            FlightInput.autopilot(vertical, verticalSpeed);
        }
    }

    private void takeoff(Flyer f) {
        status = "taking off";
        if (f.wearsElytra && !mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            int slot = findElytra();
            if (slot == -1) { stop("No elytra left."); module.automatic.setValue(false); return; }
            swapWithChest(slot);
            return;
        }
        if (flying(f)) {
            walkPath = null;
            glideWait = 0;
            airRestart = false;
            state = State.FLY;
            if (f == Flyer.BARITONE) BaritoneBridge.elytraTo(baritoneGoal());
            return;
        }
        if (mc.player.isOnGround()) {
            airRestart = false;
            if (!Pathfinder.openSky(mc.world, mc.player.getBlockPos())) { walkToOpenSky(); return; }
            walkPath = null;
            if (f == Flyer.FAKEFLY_TICK) vertical = 1;
            else mc.player.jump();
            return;
        }
        if (f == Flyer.ELYTRAFLY_PACKET || f == Flyer.FAKEFLY_TICK || f == Flyer.FAKEFLY_GRIM) {
            if (f == Flyer.FAKEFLY_TICK) { vertical = 1; antiFloat(f); }
            return;
        }
        if ((mc.player.getVelocity().y < 0 || airRestart) && glideWait-- <= 0) {
            mc.player.startGliding();
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            glideWait = 5;
            if (f == Flyer.BARITONE) useRocket();
        }
    }

    private void fly(Flyer f) {
        if (!flying(f)) {
            if (f == Flyer.BARITONE) BaritoneBridge.stop();
            airRestart = !mc.player.isOnGround();
            state = State.TAKEOFF;
            return;
        }
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (f.wearsElytra && chest.isOf(Items.ELYTRA) && chest.getMaxDamage() - chest.getDamage() <= LOW_DURABILITY) {
            int spare = findElytra();
            if (spare != -1) {
                swapWithChest(spare);
                say("Elytra almost broken, swapped in a spare.");
            } else {
                say("Elytra almost broken and no spare.");
                state = State.LAND;
            }
            return;
        }
        if ((f == Flyer.BARITONE || f == Flyer.FAKEFLY_GRIM) && countRockets() == 0) {
            handOver("Out of rockets.");
            return;
        }

        double dx = waypoint.x - mc.player.getX(), dz = waypoint.z - mc.player.getZ();
        double reached = f == Flyer.BARITONE ? BARITONE_HANDOFF : REACHED;
        if (dx * dx + dz * dz < reached * reached) {
            Vec3d next = nextWaypoint();
            if (next == null) {
                finished = true;
                String done = "Route finished (" + maxLabel() + " out).";
                if (!f.hovers) { handOver(done); return; }
                say(done + " Hovering here.");
                state = State.HOVER;
                return;
            }
            waypoint = next;
            if (f == Flyer.BARITONE) BaritoneBridge.elytraTo(baritoneGoal());
            return;
        }
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        switch (f) {
            case BARITONE -> {
                status = "Baritone: " + dist + " m to corner " + cornerNo;
                if (!BaritoneBridge.elytraActive()) BaritoneBridge.elytraTo(baritoneGoal());
            }
            case FAKEFLY_GRIM -> steerGliding(dx, dz, dist);
            default -> steerHovering(f, dx, dz, dist);
        }
    }

    private void steerHovering(Flyer f, double dx, double dz, int dist) {
        float wanted = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float diff = MathHelper.wrapDegrees(wanted - mc.player.getYaw());
        mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(diff, -15f, 15f));

        double speed = Math.max(horizontalSpeed(f), mc.player.getVelocity().horizontalLength());
        Vec3d dir = heading(wanted);
        Corridor far = corridor(dir, Math.min(MathHelper.clamp(speed * 24, 48, 160), loadedReach()));
        double stopping = f == Flyer.ELYTRAFLY_PACKET ? mc.player.getVelocity().horizontalLength() / 0.09 + 3 : 0;
        Corridor near = corridor(dir, Math.max(Math.max(8, speed * 6), stopping));
        double y = mc.player.getY();
        double clearance = module.clearance.getValue().doubleValue();
        double want = far.top + clearance;
        if (y < want - 0.5) vertical = 1;
        else if (y > want + 10 && far.loaded) vertical = -1;
        if (f == Flyer.ELYTRAFLY_PACKET && vertical != 0) {
            double gap = vertical > 0 ? want - y : y - (want + 10);
            verticalSpeed = MathHelper.clamp(gap * 0.25, 0.05, module.climbSpeed.getValue().doubleValue());
        }

        boolean aligned = Math.abs(diff) < 20;
        boolean clearAhead = mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(dir.multiply(Math.max(2, speed * 2))).expand(0.3));
        boolean nearOk = near.loaded && y >= near.top + Math.min(clearance, 6);
        forward = aligned && far.loaded && clearAhead && nearOk ? 1 : 0;
        if (forward == 0 && f == Flyer.ELYTRAFLY_PACKET && mc.player.getVelocity().dotProduct(dir) > 0.15) forward = -1;
        debug = String.format(java.util.Locale.ROOT, "far=%d/%s near=%d/%s want=%.0f y=%.1f speed=%.2f fwd=%.0f vert=%d", far.top, far.loaded, near.top, near.loaded, want, y, speed, forward, vertical);
        status = !far.loaded ? "waiting for chunks to load" : !aligned ? "turning to corner " + cornerNo
                : forward == 0 ? "climbing over terrain" : y < want - 3 ? "climbing, " + dist + " m to corner " + cornerNo
                : dist + " m to corner " + cornerNo;

        antiFloat(f);
    }

    private void antiFloat(Flyer f) {
        if (f == Flyer.ELYTRAFLY_CONTROL) return;
        if (mc.player.getVelocity().y < -0.05 || vertical < 0) { ticksNotDown = 0; return; }
        if (++ticksNotDown >= 30) {
            vertical = -1;
            if (f == Flyer.ELYTRAFLY_PACKET) verticalSpeed = 0.05;
            ticksNotDown = 0;
        }
    }

    private void steerGliding(double dx, double dz, int dist) {
        float wanted = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        Vec3d dir = heading(mc.player.getYaw());
        Corridor c = corridor(dir, Math.min(96, loadedReach()));
        double y = mc.player.getY();
        double want = c.top + module.clearance.getValue().doubleValue();
        boolean clearAhead = mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(dir.multiply(6)).expand(0.5));
        boolean tooSteep = want - y > Math.max(4, c.topDistance - 6) * 0.55;
        if (tooSteep || !clearAhead) spiralTo = Double.isNaN(spiralTo) ? want : Math.max(spiralTo, want);
        if (!Double.isNaN(spiralTo) && y >= spiralTo - 2) spiralTo = Double.NaN;
        boolean spiral = !Double.isNaN(spiralTo);
        if (spiral) want = Math.max(want, spiralTo);
        if (!c.loaded && !spiral) {
            mc.player.setYaw(mc.player.getYaw() + 7f);
            status = "circling while chunks load";
        } else if (spiral) {
            mc.player.setYaw(mc.player.getYaw() + 8f);
            status = "spiralling up to y " + (int) spiralTo;
        } else {
            float diff = MathHelper.wrapDegrees(wanted - mc.player.getYaw());
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(diff, -6f, 6f));
            status = y < want - 3 ? "climbing, " + dist + " m to corner " + cornerNo : dist + " m to corner " + cornerNo;
        }
        if (y < want - 0.5 || !clearAhead) vertical = 1;
        else if (y > want + 12) vertical = -1;
        mc.player.setPitch(vertical > 0 ? -35f : vertical < 0 ? 25f : -2f);
        forward = 1;
    }

    private double loadedReach() {
        return Math.max(24, module.seenRadius() * 16 - 16);
    }

    private double horizontalSpeed(Flyer f) {
        if (f == Flyer.FAKEFLY_TICK) {
            FakeFlyModule ff = Anatorius.MODULE_MANAGER.getModule(FakeFlyModule.class);
            return ff == null ? 1.5 : ff.tickSpeed();
        }
        ElytraFlyModule ef = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
        return ef == null ? 2 : ef.horizontal.getValue().doubleValue();
    }

    private static Vec3d heading(float yaw) {
        return new Vec3d(-MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE), 0, MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE));
    }

    private record Corridor(int top, double topDistance, boolean loaded) { }

    private Corridor corridor(Vec3d dir, double length) {
        int top = mc.world.getBottomY();
        double topDistance = length;
        boolean loaded = true;
        double px = mc.player.getX(), pz = mc.player.getZ();
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        for (double d = 0; d <= length; d += 0.7) {
            for (int s = -3; s <= 3; s++) {
                int x = MathHelper.floor(px + dir.x * d + side.x * s), z = MathHelper.floor(pz + dir.z * d + side.z * s);
                if (mc.world.getChunkManager().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null) { loaded = false; continue; }
                int h = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                if (h > top) { top = h; topDistance = d; }
            }
        }
        return new Corridor(top, topDistance, loaded);
    }

    private void handOver(String why) {
        status = "stopped";
        stop(why + " Automatic stopped: you're gliding, land yourself.");
        module.automatic.setValue(false);
    }

    private void land(Flyer f) {
        status = "landing";
        if (!f.hovers) { handOver("Can't land by itself in " + f.label + "."); return; }
        if (mc.player.isOnGround() || !flying(f)) { stop("Landed."); module.automatic.setValue(false); return; }
        int ground = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, mc.player.getBlockX(), mc.player.getBlockZ());
        if (mc.player.getY() > ground + 3) { vertical = -1; hoverTicks = 0; return; }
        if (++hoverTicks < 10) return;
        Module fm = flightModule();
        Boolean before = switched.remove(fm);
        if (before != null && fm != null) fm.setToggled(before);
        stop(before != null && !before ? "Landed." : "Down to 2 blocks above the ground: step off yourself.");
        module.automatic.setValue(false);
    }

    private void walkToOpenSky() {
        status = "walking out from under the roof";
        if (walkPath == null || ++walkAge > 60) {
            walkPath = Pathfinder.ground(mc.world, mc.player.getBlockPos(), p -> Pathfinder.openSky(mc.world, p), null, 4000);
            walkIndex = 1;
            walkAge = 0;
            if (walkPath == null) { stop("No open sky to take off from nearby."); module.automatic.setValue(false); return; }
            if (BaritoneBridge.available()) BaritoneBridge.goTo(BlockPos.ofFloored(walkPath.get(walkPath.size() - 1)));
        }
        if (BaritoneBridge.isPathing()) return;
        Vec3d pos = mc.player.getEntityPos();
        while (walkIndex < walkPath.size() - 1 && pos.squaredDistanceTo(walkPath.get(walkIndex).x, pos.y, walkPath.get(walkIndex).z) < 0.35 * 0.35) walkIndex++;
        Vec3d p = walkPath.get(Math.min(walkIndex, walkPath.size() - 1));
        if (p.y > pos.y + 0.5) mc.player.jump();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(p.z - pos.z, p.x - pos.x)) - 90f);
        forward = 1;
    }

    void keyboard(KeyboardTickEvent event) {
        if (state == null || mc.player == null) return;
        manual = event.getMovementForward() != 0 || event.getMovementSideways() != 0
                || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed();
        if (manual) status = "you're steering";
        if (manual || (engine == Engine.BARITONE && state == State.FLY)) return;
        event.setMovementForward(forward);
        event.setMovementSideways(0);
        event.setCancelled(true);
    }

    private static final Path ROUTES = Path.of(Anatorius.MOD_NAME, "stashroutes.json");

    private static String routeKey() {
        return Anatorius.SERVER_MANAGER.getServer() + "|" + WorldUtils.getDimension();
    }

    private static JsonObject readRoutes() {
        try {
            if (Files.exists(ROUTES)) return JsonParser.parseString(Files.readString(ROUTES, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {  }
        return new JsonObject();
    }

    private void saveRoute() {
        if (origin == null || routeOf == null) return;
        try {
            JsonObject all = readRoutes();
            if (finished || waypoint == null) all.remove(routeOf);
            else {
                JsonObject r = new JsonObject();
                r.addProperty("pattern", module.pattern.getValue());
                r.addProperty("ox", origin.x);
                r.addProperty("oz", origin.z);
                r.addProperty("cx", corner.x);
                r.addProperty("cz", corner.z);
                r.addProperty("leg", leg);
                r.addProperty("no", cornerNo);
                r.addProperty("yaw", straightYaw);
                all.add(routeOf, r);
            }
            Files.createDirectories(ROUTES.getParent());
            Files.writeString(ROUTES, all.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {  }
    }

    private boolean resumeRoute() {
        JsonObject all = readRoutes();
        if (!all.has(routeOf)) return false;
        try {
            JsonObject r = all.getAsJsonObject(routeOf);
            if (!module.pattern.getValue().equals(r.get("pattern").getAsString())) return false;
            Vec3d o = new Vec3d(r.get("ox").getAsDouble(), 0, r.get("oz").getAsDouble());
            double max = module.maxDistance.isAtMaxLabel() ? Double.MAX_VALUE : module.maxDistance.getValue().doubleValue();
            if (Math.max(Math.abs(mc.player.getX() - o.x), Math.abs(mc.player.getZ() - o.z)) > max + laneSpacing()) return false;
            origin = o;
            corner = new Vec3d(r.get("cx").getAsDouble(), 0, r.get("cz").getAsDouble());
            leg = r.get("leg").getAsInt();
            cornerNo = r.get("no").getAsInt();
            straightYaw = r.get("yaw").getAsFloat();
            waypoint = corner;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Vec3d nextWaypoint() {
        RouteStep s = step(corner, leg);
        if (s == null) return null;
        corner = s.corner;
        leg = s.leg;
        cornerNo++;
        return s.corner;
    }

    private record RouteStep(Vec3d corner, int leg) { }

    private RouteStep step(Vec3d from, int legNo) {
        double max = module.maxDistance.isAtMaxLabel() ? Double.MAX_VALUE : module.maxDistance.getValue().doubleValue();
        if (module.pattern.getValue().equals("Straight")) {
            double r = (legNo + 1) * STRAIGHT_STEP;
            if (r > max + STRAIGHT_STEP) return null;
            r = Math.min(r, max);
            Vec3d d = heading(straightYaw);
            return new RouteStep(origin.add(d.x * r, 0, d.z * r), legNo + 1);
        }
        int dir = legNo % 4;
        double len = (legNo / 2 + 1) * laneSpacing();
        Vec3d next = from.add(dir == 0 ? len : dir == 2 ? -len : 0, 0, dir == 1 ? len : dir == 3 ? -len : 0);
        if (Math.max(Math.abs(next.x - origin.x), Math.abs(next.z - origin.z)) > max) return null;
        return new RouteStep(next, legNo + 1);
    }

    List<Vec3d> preview(int count) {
        List<Vec3d> out = new ArrayList<>();
        if (state == null || waypoint == null) return out;
        out.add(waypoint);
        Vec3d c = corner;
        int l = leg;
        for (int i = 0; i < count; i++) {
            RouteStep s = step(c, l);
            if (s == null) break;
            out.add(s.corner);
            c = s.corner;
            l = s.leg;
        }
        return out;
    }

    int laneSpacing() {
        int chunks = module.laneSpacing.getValue().intValue();
        if (chunks <= 0) chunks = Math.max(2, 2 * module.seenRadius());
        return chunks * 16;
    }

    private String maxLabel() {
        return module.maxDistance.isAtMaxLabel() ? "no limit" : (int) module.maxDistance.getValue().doubleValue() + " m";
    }

    private static String compass(float yaw) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        return names[Math.floorMod(Math.round(yaw / 45f), 8)];
    }

    private BlockPos baritoneGoal() {
        return new BlockPos(MathHelper.floor(waypoint.x), mc.player.getBlockY(), MathHelper.floor(waypoint.z));
    }

    private boolean hasElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) || findElytra() != -1;
    }

    private int findElytra() {
        for (int i = 9; i <= 44; i++) {
            ItemStack s = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (s.isOf(Items.ELYTRA) && s.getMaxDamage() - s.getDamage() > LOW_DURABILITY) return i;
        }
        return -1;
    }

    private void swapWithChest(int slot) {
        int syncId = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private int countRockets() {
        return mc.player.getInventory().count(Items.FIREWORK_ROCKET);
    }

    private void useRocket() {
        int prev = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) continue;
            mc.player.getInventory().setSelectedSlot(i);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.getInventory().setSelectedSlot(prev);
            return;
        }
    }

    private void say(String text) {
        Anatorius.CHAT_MANAGER.tagged(text, "StashFinder");
    }
}
