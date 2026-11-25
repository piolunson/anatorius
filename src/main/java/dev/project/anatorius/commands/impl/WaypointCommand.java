package dev.project.anatorius.commands.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.managers.WaypointManager;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Locale;

@RegisterCommand(name = "waypoint", tag = "Waypoint", description = "Allows you to manage the client's custom waypoints.",
        syntax = "<add|del> <name> [x z|x y z] [overworld|nether|end] | <clear|list>", aliases = {"w"})
public class WaypointCommand extends Command {
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            messageSyntax();
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && action.equals("clear")) {
            Anatorius.WAYPOINT_MANAGER.clear();
            save();
            Anatorius.CHAT_MANAGER.tagged("Successfully cleared your custom waypoints.", getTag(), getName() + "-list");
        } else if (args.length == 1 && action.equals("list")) {
            list();
        } else if (args.length == 2 && (action.equals("del") || action.equals("remove"))) {
            if (!Anatorius.WAYPOINT_MANAGER.contains(args[1])) {
                Anatorius.CHAT_MANAGER.tagged("There is no waypoint called " + ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + ".", getTag(), getName());
                return;
            }
            Anatorius.WAYPOINT_MANAGER.remove(args[1]);
            save();
            Anatorius.CHAT_MANAGER.tagged("Successfully removed " + ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + " from your custom waypoints.", getTag(), getName());
        } else if (args.length >= 2 && action.equals("add")) {
            add(args);
        } else {
            messageSyntax();
        }
    }

    private void add(String[] args) {
        if (mc.player == null) return;
        String name = args[1];
        int numbers = args.length - 2;
        String dimension = WorldUtils.getDimension();
        if (numbers > 0) {
            String dim = dimension(args[args.length - 1]);
            if (dim != null) {
                dimension = dim;
                numbers--;
            }
        }
        if (numbers != 0 && numbers != 2 && numbers != 3) {
            messageSyntax();
            return;
        }
        double x, y, z;
        try {
            if (numbers == 0) {
                x = Math.floor(mc.player.getX());
                y = Math.floor(mc.player.getY()) + 1;
                z = Math.floor(mc.player.getZ());
                if (!dimension.equals(WorldUtils.getDimension())) {
                    double k = scale(WorldUtils.getDimension(), dimension);
                    x = Math.floor(x * k);
                    z = Math.floor(z * k);
                }
            } else {
                x = Integer.parseInt(args[2]);
                y = numbers == 3 ? Integer.parseInt(args[3]) : Math.floor(mc.player.getY()) + 1;
                z = Integer.parseInt(args[numbers == 3 ? 4 : 3]);
            }
        } catch (NumberFormatException exception) {
            Anatorius.CHAT_MANAGER.tagged("Please input valid " + ChatUtils.getPrimary() + "integer" + ChatUtils.getSecondary() + " numbers for the coordinates.", getTag(), getName());
            return;
        }
        if (Anatorius.WAYPOINT_MANAGER.contains(name)) {
            Anatorius.CHAT_MANAGER.tagged("A waypoint called " + ChatUtils.getPrimary() + name + ChatUtils.getSecondary() + " already exists (.waypoint del " + name + ").", getTag(), getName());
            return;
        }
        Anatorius.WAYPOINT_MANAGER.add(name, new Vec3d(x, y, z), dimension, Anatorius.SERVER_MANAGER.getServer());
        save();
        String text = "Successfully added " + ChatUtils.getPrimary() + name + " [" + (int) x + ", " + (int) y + ", " + (int) z + "] " + label(dimension) + ChatUtils.getSecondary() + " to your custom waypoints.";
        String other = WaypointManager.pairedDimension(dimension);
        if (other != null) {
            double k = scale(dimension, other);
            text += " In the " + label(other) + ": " + ChatUtils.getPrimary() + "[" + (int) Math.floor(x * k) + ", " + (int) Math.floor(z * k) + "]" + ChatUtils.getSecondary() + ".";
        }
        Anatorius.CHAT_MANAGER.tagged(text, getTag(), getName());
    }

    private void list() {
        ArrayList<WaypointManager.Waypoint> waypoints = Anatorius.WAYPOINT_MANAGER.getWaypoints();
        if (waypoints.isEmpty()) {
            Anatorius.CHAT_MANAGER.tagged("You currently have no custom waypoints set.", getTag());
            return;
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (WaypointManager.Waypoint waypoint : waypoints) {
            index++;
            builder.append(ChatUtils.getSecondary()).append(waypoint.getName())
                    .append(" [").append((int) waypoint.getPos().x).append(", ").append((int) waypoint.getPos().z).append(" ").append(label(waypoint.getDimension())).append("]")
                    .append(index == waypoints.size() ? "" : ", ");
        }
        Anatorius.CHAT_MANAGER.message("Custom waypoints " + ChatUtils.getPrimary() + "[" + ChatUtils.getSecondary() + waypoints.size() + ChatUtils.getPrimary() + "]: " + ChatUtils.getSecondary() + builder, getName() + "-list");
    }

    private static String dimension(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "overworld", "ow", "o" -> "overworld";
            case "nether", "the_nether", "n" -> "the_nether";
            case "end", "the_end", "e" -> "the_end";
            default -> null;
        };
    }

    private static double scale(String from, String to) {
        return WaypointManager.scale(from, to);
    }

    private static String label(String dimension) {
        return WaypointManager.dimensionName(dimension);
    }

    private static void save() {
        try {
            Anatorius.CONFIG_MANAGER.saveWaypoints();
        } catch (Exception exception) {
            Anatorius.LOGGER.error("Failed to save the waypoints", exception);
        }
    }

    @Override
    public java.util.List<String> suggest(String[] args) {
        if (args.length == 0) return java.util.List.of("add", "del", "list", "clear");
        if (args.length == 1 && args[0].equalsIgnoreCase("del")) {
            return Anatorius.WAYPOINT_MANAGER.getWaypoints().stream().map(WaypointManager.Waypoint::getName).toList();
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("add")) return java.util.List.of("overworld", "nether", "end");
        return java.util.List.of();
    }
}
