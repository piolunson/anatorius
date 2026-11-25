package dev.project.anatorius.utils.pathing;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

public final class Pathfinder {
    private static final int[][] AIR_MOVES;
    private static final int[][] GROUND_MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    static {
        List<int[]> moves = new ArrayList<>();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0) moves.add(new int[]{x, y, z});
        AIR_MOVES = moves.toArray(new int[0][]);
    }

    private Pathfinder() {
    }

    public static boolean passable(World world, BlockPos pos) {
        if (!world.isInBuildLimit(pos)) return false;
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.isOf(Blocks.COBWEB) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW)
                || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) return false;
        return state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean solid(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    public static boolean openSky(World world, BlockPos pos) {
        return pos.getY() >= world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    private static final class Node implements Comparable<Node> {
        final long key;
        final int x, y, z;
        double g, f;
        Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.key = BlockPos.asLong(x, y, z);
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }
    }

    private static double h(int x, int y, int z, BlockPos goal) {
        double dx = x - goal.getX(), dy = y - goal.getY(), dz = z - goal.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static List<Vec3d> air(World world, BlockPos start, BlockPos goal, int maxNodes) {
        start = nearestPassable(world, start, 2);
        goal = nearestPassable(world, goal, 3);
        if (start == null || goal == null) return null;
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        Node first = new Node(start.getX(), start.getY(), start.getZ());
        first.f = h(first.x, first.y, first.z, goal);
        nodes.put(first.key, first);
        open.add(first);
        Node best = first;
        double bestH = first.f;
        BlockPos.Mutable p = new BlockPos.Mutable();
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Node n = open.poll();
            if (n.closed) continue;
            n.closed = true;
            expanded++;
            double nh = h(n.x, n.y, n.z, goal);
            if (nh < bestH) { bestH = nh; best = n; }
            if (nh < 1.01) { best = n; break; }
            for (int[] m : AIR_MOVES) {
                int x = n.x + m[0], y = n.y + m[1], z = n.z + m[2];
                if (!passable(world, p.set(x, y, z))) continue;
                if (m[0] != 0 && (m[1] != 0 || m[2] != 0) && !passable(world, p.set(n.x + m[0], n.y, n.z))) continue;
                if (m[1] != 0 && (m[0] != 0 || m[2] != 0) && !passable(world, p.set(n.x, n.y + m[1], n.z))) continue;
                if (m[2] != 0 && (m[0] != 0 || m[1] != 0) && !passable(world, p.set(n.x, n.y, n.z + m[2]))) continue;
                double step = Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
                double cost = n.g + step + (nearWall(world, p, x, y, z) ? 1.5 : 0.0);
                relax(nodes, open, x, y, z, n, cost, goal);
            }
        }
        return build(best, world, true);
    }

    private static boolean nearWall(World world, BlockPos.Mutable p, int x, int y, int z) {
        return solid(world, p.set(x + 1, y, z)) || solid(world, p.set(x - 1, y, z)) || solid(world, p.set(x, y + 1, z))
                || solid(world, p.set(x, y - 1, z)) || solid(world, p.set(x, y, z + 1)) || solid(world, p.set(x, y, z - 1));
    }

    public static boolean standable(World world, BlockPos pos) {
        return passable(world, pos) && passable(world, pos.up()) && solid(world, pos.down());
    }

    public static List<Vec3d> ground(World world, BlockPos start, Predicate<BlockPos> goal, BlockPos bias, int maxNodes) {
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        Node first = new Node(start.getX(), start.getY(), start.getZ());
        nodes.put(first.key, first);
        open.add(first);
        BlockPos.Mutable p = new BlockPos.Mutable();
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Node n = open.poll();
            if (n.closed) continue;
            n.closed = true;
            expanded++;
            if (n != first && goal.test(new BlockPos(n.x, n.y, n.z))) return build(n, world, false);
            for (int[] m : GROUND_MOVES) {
                int x = n.x + m[0], z = n.z + m[1];
                boolean diagonal = m[0] != 0 && m[1] != 0;
                if (diagonal && (!(passable(world, p.set(n.x + m[0], n.y, n.z)) && passable(world, p.set(n.x + m[0], n.y + 1, n.z)))
                        || !(passable(world, p.set(n.x, n.y, n.z + m[1])) && passable(world, p.set(n.x, n.y + 1, n.z + m[1]))))) continue;
                double step = diagonal ? 1.414 : 1.0;
                for (int dy = 1; dy >= -3; dy--) {
                    int y = n.y + dy;
                    if (!standable(world, p.set(x, y, z))) continue;
                    if (dy == 1 && !passable(world, p.set(n.x, n.y + 2, n.z))) break;
                    if (dy < 0) {
                        boolean free = true;
                        for (int k = 0; k > dy; k--) if (!passable(world, p.set(x, n.y + k + 1, z)) || !passable(world, p.set(x, n.y + k, z))) free = false;
                        if (!free) continue;
                    }
                    double cost = n.g + step + (dy == 1 ? 0.8 : dy < 0 ? -dy * 0.3 : 0.0);
                    relax(nodes, open, x, y, z, n, cost, bias);
                    break;
                }
            }
        }
        return null;
    }

    private static void relax(Map<Long, Node> nodes, PriorityQueue<Node> open, int x, int y, int z, Node parent, double cost, BlockPos goal) {
        long key = BlockPos.asLong(x, y, z);
        Node next = nodes.get(key);
        if (next == null) {
            next = new Node(x, y, z);
            nodes.put(key, next);
        } else if (next.closed || cost >= next.g) {
            return;
        }
        next.g = cost;
        next.parent = parent;
        next.f = cost + (goal == null ? 0 : 1.8 * h(x, y, z, goal));
        open.add(next);
    }

    private static List<Vec3d> build(Node end, World world, boolean air) {
        List<Vec3d> points = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent)
            points.add(air ? new Vec3d(n.x + 0.5, n.y + 0.5, n.z + 0.5) : new Vec3d(n.x + 0.5, n.y, n.z + 0.5));
        Collections.reverse(points);
        return air ? smooth(world, points, 0.35) : points;
    }

    private static BlockPos nearestPassable(World world, BlockPos pos, int radius) {
        if (passable(world, pos)) return pos;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.iterate(pos.add(-radius, -radius, -radius), pos.add(radius, radius, radius))) {
            if (!passable(world, q)) continue;
            double d = q.getSquaredDistance(pos);
            if (d < bestD) { bestD = d; best = q.toImmutable(); }
        }
        return best;
    }

    public static List<Vec3d> smooth(World world, List<Vec3d> points, double radius) {
        if (points.size() <= 2) return points;
        List<Vec3d> out = new ArrayList<>();
        int i = 0;
        out.add(points.get(0));
        while (i < points.size() - 1) {
            int j = points.size() - 1;
            while (j > i + 1 && !clear(world, points.get(i), points.get(j), radius)) j--;
            out.add(points.get(j));
            i = j;
        }
        return out;
    }

    public static boolean clear(World world, Vec3d a, Vec3d b, double radius) {
        double length = a.distanceTo(b);
        int steps = Math.max(1, MathHelper.ceil(length / 0.3));
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int s = 0; s <= steps; s++) {
            Vec3d v = a.lerp(b, s / (double) steps);
            for (int c = 0; c < 9; c++) {
                double ox = c == 0 ? 0 : ((c & 1) == 0 ? -radius : radius);
                double oy = c == 0 ? 0 : ((c & 2) == 0 ? -radius : radius);
                double oz = c == 0 ? 0 : ((c & 4) == 0 ? -radius : radius);
                if (!passable(world, p.set(MathHelper.floor(v.x + ox), MathHelper.floor(v.y + oy), MathHelper.floor(v.z + oz)))) return false;
            }
        }
        return true;
    }
}
