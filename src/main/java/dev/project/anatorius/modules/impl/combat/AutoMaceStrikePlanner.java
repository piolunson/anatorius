package dev.project.anatorius.modules.impl.combat;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

final class AutoMaceStrikePlanner {
    private static final double[] RADII = {2.5, 4.5, 7.0, 10.0};
    private static final double[] HEIGHTS = {0.5, 2.0, 4.0};
    private static final double[] CENTER_HEIGHTS = {5.0, 9.0};
    private static final int BEARINGS = 16;
    private static final int MAX_TICKS = 80;
    private static final int TICK_BUDGET = 3000;
    private static final double HALF_WIDTH = 0.4, BODY_BELOW = 0.1, BODY_TOP = 0.7, EYE = 0.4;
    private static final double DRIFT_SCALE = 0.5, DRIFT_GAIN = 0.85;
    private static final float RAD = (float) Math.PI / 180.0f;
    private static final double DEG = 180.0 / Math.PI;
    private static final Box[] NO_BOXES = new Box[0];

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Long2ObjectOpenHashMap<Box[]> collisionCache = new Long2ObjectOpenHashMap<>();
    private final BlockPos.Mutable mutable = new BlockPos.Mutable();
    private ClientWorld world;
    private ShapeContext shapeContext;

    private Box targetBox;
    private double targetX, targetY, targetZ, motionX, motionZ;
    private double startX, startY, startZ, startVX, startVY, startVZ, startFall;
    private double gravity, rangeSq;
    private int rocketTicks;
    private boolean fallLimit;

    private double px, py, pz, vx, vy, vz;
    private double gx, gy, gz;
    private double lookX, lookY, lookZ;
    private float yaw, pitch;

    private float simYaw, simPitch;
    private int simStage, simHitTicks;
    private double score;
    private boolean limitReached, pathBlocked;
    private int simTicks;

    private boolean active, solution;
    private Vec3d selectedOffset;
    private int selectedMode;
    private int refreshTicks, searchCursor;
    private float selectedYaw, selectedPitch;
    private int selectedHitTicks = Integer.MAX_VALUE;

    void reset() {
        active = false;
        solution = false;
        selectedOffset = null;
        selectedMode = 0;
        selectedHitTicks = Integer.MAX_VALUE;
        refreshTicks = 4;
        searchCursor = 0;
        collisionCache.clear();
    }

    boolean isActive() {
        return active && solution;
    }

    float yaw() {
        return selectedYaw;
    }

    float pitch() {
        return selectedPitch;
    }

    int hitTicks() {
        return isActive() ? selectedHitTicks : Integer.MAX_VALUE;
    }

    void update(LivingEntity target, Vec3d motion, double range, int rocketTicks, boolean fallLimit, double fall) {
        var player = mc.player;
        world = mc.world;
        active = true;
        collisionCache.clear();
        shapeContext = ShapeContext.of(player);
        gravity = player.getFinalGravity();
        targetBox = target.getBoundingBox();
        targetX = target.getX();
        targetY = target.getY();
        targetZ = target.getZ();
        motionX = motion.x;
        motionZ = motion.z;
        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (speed > 1.0) {
            motionX /= speed;
            motionZ /= speed;
        }
        startX = player.getX();
        startY = player.getY();
        startZ = player.getZ();
        Vec3d velocity = player.getVelocity();
        startVX = velocity.x;
        startVY = velocity.y;
        startVZ = velocity.z;
        startFall = fall;
        rangeSq = range * range;
        this.rocketTicks = rocketTicks;
        this.fallLimit = fallLimit;
        simTicks = 0;

        boolean previousWorks = solution && simulate(selectedOffset, selectedMode);
        boolean good = previousWorks && acceptable();
        boolean finalWorks = previousWorks;
        if (!good && (selectedOffset != null || !solution)) {
            finalWorks = false;
            if (simulate(null, 1) && acceptable()) {
                selectedOffset = null;
                selectedMode = 1;
                good = true;
                previousWorks = true;
                solution = true;
                refreshTicks = searchCursor = 0;
            }
        }
        boolean search;
        if (previousWorks) search = !good && ++refreshTicks >= 10;
        else if (!solution && searchCursor == 0) search = ++refreshTicks >= 5;
        else search = true;
        if (search) {
            refreshSearch(previousWorks);
            finalWorks = false;
        }
        if (!finalWorks) finalWorks = solution && simulate(selectedOffset, selectedMode);
        if (finalWorks) {
            selectedMode = simStage;
            selectedHitTicks = simHitTicks;
            selectedYaw = simYaw;
            selectedPitch = simPitch;
        } else {
            solution = false;
            selectedHitTicks = Integer.MAX_VALUE;
        }
    }

    private boolean acceptable() {
        return !limitReached && !pathBlocked && simHitTicks != Integer.MAX_VALUE;
    }

    private void refreshSearch(boolean previousStillWorks) {
        refreshTicks = 0;
        List<Vec3d> candidates = candidateOffsets();
        boolean found = false;
        Vec3d bestOffset = null;
        int bestMode = 0;
        double bestScore = 0.0;
        if (previousStillWorks && simulate(selectedOffset, selectedMode)) {
            found = true;
            bestOffset = selectedOffset;
            bestMode = selectedMode;
            bestScore = score + 1.0;
        }
        if (simulate(null, 1) && (!found || score > bestScore)) {
            found = true;
            bestOffset = null;
            bestMode = 1;
            bestScore = score;
        }
        int start = searchCursor;
        searchCursor = 0;
        double eyeToX = (targetBox.minX + targetBox.maxX) * 0.5, eyeToY = (targetBox.minY + targetBox.maxY) * 0.5, eyeToZ = (targetBox.minZ + targetBox.maxZ) * 0.5;
        for (int i = 0; i < candidates.size(); i++) {
            if (simTicks >= TICK_BUDGET) {
                searchCursor = (start + i) % candidates.size();
                break;
            }
            Vec3d offset = candidates.get((start + i) % candidates.size());
            double wx = targetX + offset.x, wy = targetY + offset.y, wz = targetZ + offset.z;
            if (bodyClear(wx, wy, wz) && lineClear(wx, wy + EYE, wz, eyeToX, eyeToY, eyeToZ) && simulate(offset, 0)
                    && (!found || score > bestScore)) {
                found = true;
                bestOffset = offset;
                bestMode = 0;
                bestScore = score;
                if (acceptable()) break;
            }
        }
        solution = found;
        if (found) {
            selectedOffset = bestOffset;
            selectedMode = bestMode;
        }
    }

    private List<Vec3d> candidateOffsets() {
        List<Vec3d> candidates = new ArrayList<>(CENTER_HEIGHTS.length + BEARINGS * RADII.length * HEIGHTS.length);
        for (double height : CENTER_HEIGHTS) candidates.add(new Vec3d(0.0, height, 0.0));
        double bearing = Math.atan2(startZ - targetZ, startX - targetX);
        for (int index = 0; index < BEARINGS; index++) {
            int step = (index + 1) / 2 * (index % 2 == 1 ? 1 : -1);
            double angle = bearing + step * Math.PI * 2.0 / BEARINGS;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (double radius : RADII) {
                for (double height : HEIGHTS) candidates.add(new Vec3d(cos * radius, height, sin * radius));
            }
        }
        return candidates;
    }

    private boolean simulate(Vec3d offset, int requestedStage) {
        px = startX;
        py = startY;
        pz = startZ;
        vx = startVX;
        vy = startVY;
        vz = startVZ;
        double fall = startFall;
        limitReached = false;
        pathBlocked = false;
        int stage = offset == null ? 1 : requestedStage;
        double wx = offset == null ? targetX : targetX + offset.x;
        double wy = offset == null ? targetY : targetY + offset.y;
        double wz = offset == null ? targetZ : targetZ + offset.z;

        if (inAttackRange(px, py, pz, 0)) {
            aimAt(targetX, targetY + 0.25, targetZ, false);
            simStage = 1;
            simYaw = yaw;
            simPitch = pitch;
            simHitTicks = 0;
            score = fall;
            return true;
        }
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            simTicks++;
            double mx = motionX * tick, mz = motionZ * tick;
            boolean rocket = tick < rocketTicks;
            if (stage == 0) {
                double dx = px - (wx + mx), dy = py - wy, dz = pz - (wz + mz);
                if (py < wy || dx * dx + dy * dy + dz * dz <= 9.0) stage = 1;
            }
            if (stage == 0) aimAt(wx + mx, wy, wz + mz, rocket);
            else aimAt(targetX + mx, targetY + 0.25, targetZ + mz, rocket);
            if (tick == 0) {
                simStage = stage;
                simYaw = yaw;
                simPitch = pitch;
            }
            if (fallLimit && vy > -0.5 && fall > 1.0) {
                fall = 1.0;
                limitReached = true;
            }
            double lastX = px, lastY = py, lastZ = pz, lastVX = vx, lastVY = vy, lastVZ = vz;
            glide(vx, vy, vz);
            vx = gx;
            vy = gy;
            vz = gz;
            if (!movementClear(px, py, pz, vx, vy, vz)) return false;
            px += vx;
            py += vy;
            pz += vz;
            if (vy < 0.0) fall -= vy;
            else if (vy > 0.0) fall = 0.0;
            if (rocket) {
                vx += lookX * 0.1 + (lookX * 1.5 - vx) * 0.5;
                vy += lookY * 0.1 + (lookY * 1.5 - vy) * 0.5;
                vz += lookZ * 0.1 + (lookZ * 1.5 - vz) * 0.5;
            }
            if (inAttackRange(px, py, pz, tick + 1)) {
                simHitTicks = tick + 1;
                score = fall - 0.15 * simHitTicks;
                if (!followOnHit(lastX, lastY, lastZ, lastVX, lastVY, lastVZ, tick)) {
                    score -= 10.0;
                    pathBlocked = true;
                }
                return true;
            }
        }
        simHitTicks = Integer.MAX_VALUE;
        score = fall - 100.0;
        return true;
    }

    private boolean followOnHit(double x, double y, double z, double velX, double velY, double velZ, int tick) {
        for (int i = 0; i < 6; i++) {
            if (inAttackRange(x, y, z, tick + i)) return true;
            if (!movementClear(x, y, z, velX, velY, velZ)) return false;
            x += velX;
            y += velY;
            z += velZ;
            velX *= 0.91F;
            velY = (velY - gravity) * 0.98F;
            velZ *= 0.91F;
        }
        return false;
    }

    private void aimAt(double tx, double ty, double tz, boolean rocket) {
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux, uy, uz;
        if (length < 1.0E-4) {
            ux = 0.0;
            uy = -1.0;
            uz = 0.0;
        } else {
            ux = dx / length;
            uy = dy / length;
            uz = dz / length;
        }
        double ax = ux, ay = uy, az = uz;
        if (rocket) {
            for (int i = 0; i < 2; i++) {
                setAim(ax, ay, az, false);
                glide(vx, vy, vz);
                double qx = gx * DRIFT_SCALE, qy = gy * DRIFT_SCALE, qz = gz * DRIFT_SCALE;
                double dot = qx * ux + qy * uy + qz * uz;
                qx -= ux * dot;
                qy -= uy * dot;
                qz -= uz * dot;
                double amount = Math.sqrt(qx * qx + qy * qy + qz * qz);
                if (amount >= DRIFT_GAIN) {
                    ax = -qx;
                    ay = -qy;
                    az = -qz;
                } else {
                    double along = Math.sqrt(1.0 - amount * amount / (DRIFT_GAIN * DRIFT_GAIN));
                    ax = ux * along - qx / DRIFT_GAIN;
                    ay = uy * along - qy / DRIFT_GAIN;
                    az = uz * along - qz / DRIFT_GAIN;
                }
            }
        }
        setAim(ax, ay, az, true);
    }

    private void setAim(double dx, double dy, double dz, boolean clampPitch) {
        yaw = MathHelper.wrapDegrees((float) (Math.atan2(dz, dx) * DEG) - 90.0f);
        pitch = (float) (Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)) * DEG);
        if (clampPitch) pitch = Math.max(0.0f, pitch);
        float pitchRad = pitch * RAD, yawRad = -yaw * RAD;
        float cosPitch = MathHelper.cos(pitchRad);
        lookX = MathHelper.sin(yawRad) * cosPitch;
        lookY = -MathHelper.sin(pitchRad);
        lookZ = MathHelper.cos(yawRad) * cosPitch;
    }

    private void glide(double cx, double cy, double cz) {
        double p = pitch * RAD;
        double cos = Math.cos(p), cosSq = cos * cos;
        double lookHorizontal = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double horizontal = Math.sqrt(cx * cx + cz * cz);
        double x = cx, y = cy + gravity * (-1.0 + cosSq * 0.75), z = cz;
        if (lookHorizontal > 0.0) {
            if (y < 0.0) {
                double lift = y * -0.1 * cosSq;
                x += lookX * lift / lookHorizontal;
                y += lift;
                z += lookZ * lift / lookHorizontal;
            }
            if (p < 0.0) {
                double lift = horizontal * -Math.sin(p) * 0.04;
                x -= lookX * lift / lookHorizontal;
                y += lift * 3.2;
                z -= lookZ * lift / lookHorizontal;
            }
            x += (lookX / lookHorizontal * horizontal - x) * 0.1;
            z += (lookZ / lookHorizontal * horizontal - z) * 0.1;
        }
        gx = x * 0.99F;
        gy = y * 0.98F;
        gz = z * 0.99F;
    }

    private boolean inAttackRange(double x, double y, double z, int tick) {
        double ex = x, ey = y + EYE, ez = z;
        double ox = motionX * tick, oz = motionZ * tick;
        double nx = MathHelper.clamp(ex, targetBox.minX + ox, targetBox.maxX + ox);
        double ny = MathHelper.clamp(ey, targetBox.minY, targetBox.maxY);
        double nz = MathHelper.clamp(ez, targetBox.minZ + oz, targetBox.maxZ + oz);
        double dx = nx - ex, dy = ny - ey, dz = nz - ez;
        return dx * dx + dy * dy + dz * dz <= rangeSq && lineClear(ex, ey, ez, nx, ny, nz);
    }

    private Box[] collisionBoxes(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Box[] cached = collisionCache.get(key);
        if (cached != null) return cached;
        mutable.set(x, y, z);
        VoxelShape shape = world.getBlockState(mutable).getCollisionShape(world, mutable, shapeContext);
        Box[] boxes;
        if (shape.isEmpty()) {
            boxes = NO_BOXES;
        } else {
            List<Box> list = shape.getBoundingBoxes();
            boxes = new Box[list.size()];
            for (int i = 0; i < boxes.length; i++) boxes[i] = list.get(i).offset(x, y, z);
        }
        collisionCache.put(key, boxes);
        return boxes;
    }

    private boolean bodyClear(double x, double y, double z) {
        double minX = x - HALF_WIDTH, minY = y - BODY_BELOW, minZ = z - HALF_WIDTH;
        double maxX = x + HALF_WIDTH, maxY = y + BODY_TOP, maxZ = z + HALF_WIDTH;
        int x1 = MathHelper.ceil(maxX), y1 = MathHelper.ceil(maxY), z1 = MathHelper.ceil(maxZ);
        for (int bx = MathHelper.floor(minX); bx < x1; bx++) {
            for (int bz = MathHelper.floor(minZ); bz < z1; bz++) {
                for (int by = MathHelper.floor(minY) - 1; by < y1; by++) {
                    for (Box box : collisionBoxes(bx, by, bz)) {
                        if (box.intersects(minX, minY, minZ, maxX, maxY, maxZ)) return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean movementClear(double x, double y, double z, double velX, double velY, double velZ) {
        int steps = Math.max(1, MathHelper.ceil(Math.sqrt(velX * velX + velY * velY + velZ * velZ) / 0.5));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            if (!bodyClear(x + velX * t, y + velY * t, z + velZ * t)) return false;
        }
        return true;
    }

    private boolean pointClear(double x, double y, double z) {
        int bx = MathHelper.floor(x), by = MathHelper.floor(y), bz = MathHelper.floor(z);
        for (int yy = by - 1; yy <= by; yy++) {
            for (Box box : collisionBoxes(bx, yy, bz)) {
                if (box.minX < x && box.maxX > x && box.minY < y && box.maxY > y && box.minZ < z && box.maxZ > z) return false;
            }
        }
        return true;
    }

    private boolean lineClear(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        int steps = Math.max(1, MathHelper.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.25));
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            if (!pointClear(fromX + dx * t, fromY + dy * t, fromZ + dz * t)) return false;
        }
        return true;
    }
}
