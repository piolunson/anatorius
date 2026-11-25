package dev.project.anatorius.managers;

import lombok.Getter;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

@Getter
public class
WaypointManager implements IMinecraft {
    private final ArrayList<Waypoint> waypoints = new ArrayList<>();

    public boolean contains(String name) {
        return waypoints.stream().anyMatch(w -> w.getName().equalsIgnoreCase(name));
    }

    public void add(String name, Vec3d pos) {
        add(name, pos, WorldUtils.getDimension(), Anatorius.SERVER_MANAGER.getServer());
    }

    public void add(String name, Vec3d pos, String dimension, String server) {
        if (contains(name)) return;
        waypoints.add(new Waypoint(name, pos, dimension, server));
    }

    public void remove(String name) {
        waypoints.removeIf(w -> w.getName().equalsIgnoreCase(name));
    }

    public void clear() {
        waypoints.clear();
    }

    public static String pairedDimension(String dimension) {
        return switch (dimension) {
            case "overworld" -> "the_nether";
            case "the_nether" -> "overworld";
            default -> null;
        };
    }

    public static double scale(String from, String to) {
        if (from.equals("the_nether") && to.equals("overworld")) return 8;
        if (from.equals("overworld") && to.equals("the_nether")) return 1 / 8.0;
        return 1;
    }

    public static String dimensionName(String dimension) {
        return switch (dimension) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> dimension;
        };
    }

    @Getter
    public static class Waypoint {
        private final String name;
        private final Vec3d pos;
        private final String dimension;
        private final String server;

        public Waypoint(String name, Vec3d pos, String dimension, String server) {
            this.name = name;
            this.pos = pos;
            this.dimension = dimension;
            this.server = server;
        }
    }
}
