package org.sparklet.rchomes;

import java.sql.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Cherry <refcherry@sparklet.org>
 */
public class RCHomes extends JavaPlugin {
    // homes listed per /homes page
    static final int PAGE_LENGTH = 16;

    // At least this percent of the name should match
    private static final double HOME_SEARCH_STRICTNESS = 0.30;

    // the name used for the home processed by swap commands
    private static final String SWAP_HOME_NAME = "__swap";

    SemVerHelper semverHelper = new SemVerHelper(this);
    private static Connection conn;
    private static DatabaseLogin dbLogin;
    private PreparedStatements prepared;

    private void newConnection() {
        try {
            // TODO holy crap did they actually never code this to .close() connections?
            // i swear if they aren't gc'd or something like that i'm gonna explode
            conn = dbLogin.getConnection();
            prepared = new PreparedStatements(conn, getLogger());

            conn.createStatement().execute("""
                        CREATE TABLE IF NOT EXISTS homes (
                            ID INT PRIMARY KEY NOT NULL AUTO_INCREMENT,
                            UUID VARCHAR(255),
                            Name VARCHAR(255),
                            world VARCHAR(255),
                            x DOUBLE, y DOUBLE, z DOUBLE,
                            yaw FLOAT DEFAULT -1.0,
                            pitch FLOAT DEFAULT -1.0
                        )
                    """);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Server server = getServer();
        ConsoleCommandSender cs = server.getConsoleSender();
        cs.sendMessage("Establishing Database connection");

        dbLogin = DatabaseLogin.fromConfig(this);
        newConnection();

        try {
            // stuff to run when updating from older version
            dbLogin.migrateOldData(this);
        } catch (SQLException e) {
            Logger.getLogger(RCHomes.class.getName()).log(
                    Level.WARNING,
                    "Failed to run backwards-compatibility checks... Trying again next load.", e);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin Disabled");
    }

    @Override
    public boolean onCommand(CommandSender interpreter, Command cmd, String input,
            String[] args) {

        Player player = (Player) interpreter;
        newConnection();

        if (interpreter instanceof Player) {
            switch (input) {
                case "homeshelp":
                    // if no args, the page should be null
                    showHelp(player, args.length == 0 ? null : args[0]);
                    return true;

                case "newhome":
                    cmdNewHome(player, args);
                    return true;

                case "sethome":
                    cmdSetHome(player, args);
                    return true;

                case "homes":
                    return cmdListHomes(player, args);

                case "home":
                    cmdHome(player, args);
                    return true;

                case "brb":
                    cmdSetHome(player, new String[] { "__swap" });
                    return true;

                case "ret":
                    cmdHome(player, new String[] { "__swap" });
                    return true;

                case "swap":
                    cmdSwapHome(player);
                    return true;

                case "delhome":
                    // TODO wildcards
                    return deleteHome(player, args);

                case "homemanager":
                    if (args.length > 0) {
                        if (args[0].equalsIgnoreCase("area")) {
                            if (args.length > 1) {
                                int pos = Integer.parseInt(args[1]);
                                player.sendMessage("looking for homes:");
                                cmdSearchHomes(player, pos);
                            }

                        } else if (args[0].equalsIgnoreCase("delhome")) {
                            cmdDelHomeOther(player, args);

                        } else if (args[0].equalsIgnoreCase("tp")) {
                            cmdHomeOther(player, args);
                        }
                    }
                    // TODO actually validate the subcommands
                    return true;
            }
        }

        return false;
    }

    /**
     * Shows help for the plugin
     *
     * @param player The player to show the help to
     * @param page   The category page to show (or null for default)
     */
    private void showHelp(Player player, String page) {
        player.sendMessage("RCHomes by refcherry");
        String warning = "Warning: You don't have permission to use these!";

        switch (page) {
            case null:
                if (!player.hasPermission("rchomes.user")) {
                    player.sendMessage(warning);
                }

                player.sendMessage("Common Commands:");
                player.sendMessage("`/home` - Teleports to your home");
                player.sendMessage("`/home [name]` - Teleports to the specified home. Defaults to `home`.");
                player.sendMessage("`/homes` - Lists all your homes");
                player.sendMessage("`/homes search [name]` - Searches for homes matching the name.");
                player.sendMessage("`/homes fuzzy [name]` - Searches for homes that almost match the name.");
                player.sendMessage("`/sethome [name]` - Sets a home at your current location. Defaults to `home`.");
                player.sendMessage("`/newhome [name]` - Like `/sethome`, but won't override existing homes.");
                player.sendMessage("`/delhome [name]` - Deletes the specified home.");
                player.sendMessage("For swap/temp home commands, do /homeshelp swap");
                player.sendMessage("For admin commands, do /homeshelp admin");
                break;

            case "swap":
                if (!player.hasPermission("rchomes.swap")) {
                    player.sendMessage(warning);
                }

                player.sendMessage("Swap Commands:");
                player.sendMessage("`/brb` - Set your swap home. Short for `/sethome __swap`.");
                player.sendMessage("`/ret` - Return to your swap home. Short for `/home __swap`.");
                player.sendMessage("`/swap` - Swaps your current location with the home `__swap`.");
                break;

            case "admin":
                if (!player.hasPermission("rchomes.admin")) {
                    player.sendMessage(warning);
                }

                player.sendMessage("Admin Commands:");
                player.sendMessage("`/homemanager area <n>` - Shows homes in a cube of radius (half-length) <n>.");
                player.sendMessage("`/homemanager delhome <name> <username>` - Deletes the specified home.");
                player.sendMessage("`/homemanager tp <username> <name>` - Teleports to the specified user's home.");
                player.sendMessage("`/homemanager playerhomes <username>` - Shows all homes of the specified user.");
                break;

            default:
                player.sendMessage("Unknown help page: " + page);
                break;
        }
    }

    // TODO what the actual hell is this method
    // possibly sus exploitable code in the clickevent part
    private void cmdSearchHomes(Player player, int pos) {
        String inWorld = player.getWorld().getName();
        Location ploc = player.getLocation();
        int[] coords = {
                (int) (ploc.getX() - pos),
                (int) (ploc.getX() + pos),
                (int) (ploc.getZ() - pos),
                (int) (ploc.getZ() + pos),
        };

        try {
            ResultSet homes = prepared.getAreaHomes(inWorld, coords);
            while (homes.next()) {
                String UIhome = homes.getString("Name");
                String UIhomeowner = Bukkit.getOfflinePlayer(UUID.fromString(homes.getString("UUID"))).getName();

                Component delHome = Component.text("[DEL]")
                        .clickEvent(ClickEvent.runCommand("/homemanager delhome " + UIhome + " " + UIhomeowner))
                        .color(NamedTextColor.RED);

                Component homeDelUI = Component.text(UIhome + " | " + UIhomeowner + " ");

                Component tpHome = Component.text("[teleport]")
                        .clickEvent(ClickEvent.runCommand("/homemanager tp " + UIhomeowner + " " + UIhome))
                        .color(NamedTextColor.LIGHT_PURPLE);

                player.sendMessage(delHome.append(tpHome).append(homeDelUI));
            }
        } catch (SQLException e) {
            player.sendMessage("no homes found");
            Bukkit.getConsoleSender().sendMessage("error " + e.toString());
        }
    }

    private void cmdDelHomeOther(Player player, String[] args) {
        String homeName = args[1];
        String homeOwnerUUID = Bukkit.getPlayerUniqueId(args[2]).toString();

        String homePlayer = Bukkit.getOfflinePlayer(UUID.fromString(homeOwnerUUID)).getName();
        player.sendMessage("trying to delete home " + homeName + " from " + homePlayer);
        if (prepared.deleteHome(homeOwnerUUID, homeName)) {
            player.sendMessage("home " + homeName + " deleted from player " + homePlayer);
        } else {
            player.sendMessage("error deleting home");
        }
    }

    private boolean cmdNewHome(Player player, String[] args) {
        if (args.length > 1) {
            player.sendMessage("Only one argument allowed");
            return false;
        }
        String home = args.length > 0 ? args[0] : "home";
        String uuid = player.getUniqueId().toString();

        boolean exists;

        try {
            exists = prepared.homeExists(uuid, home);
        } catch (SQLException e) {
            player.sendMessage("Error occurred while checking if home exists...");
            skillIssue(e);
            return true;
        }

        if (exists) {
            player.sendMessage("Home " + home + " already exists! Use /sethome to skip this check.");
        } else {
            var success = setHomeAtSelf(player, home);
            if (success) {
                player.sendMessage("New home created: " + home);
            } else {
                player.sendMessage("Error occurred while creating home...");
            }
        }

        return true;
    }

    private boolean cmdSetHome(Player player, String[] args) {
        if (args.length > 1) {
            player.sendMessage("Only one argument allowed");
            return false;
        }
        String home = args.length > 0 ? args[0] : "home";

        var success = setHomeAtSelf(player, home);
        if (success) {
            player.sendMessage("Home set: " + home);
        } else {
            player.sendMessage("Error occurred while setting home...");
        }

        return true;
    }

    /**
     * Sets the home at the given player's current location
     *
     * @param player   The player to set the home for
     * @param homename The name of the home
     * @return true if the home was set successfully, false otherwise
     */
    private boolean setHomeAtSelf(Player player, String homename) {
        Location loc = player.getLocation();
        return setHomeAt(loc, player, homename);
    }

    /**
     * Sets the home at the given location for the given player
     *
     * @param loc      The location to set the home at
     * @param player   The player to set the home for
     * @param homename The name of the home
     * @return true if the home was set successfully, false otherwise
     */
    private boolean setHomeAt(Location loc, Player player, String homename) {
        String uuid = player.getUniqueId().toString();

        HomeLocation hloc = new HomeLocation(loc, player.getWorld().getName());
        try {
            prepared.setHome(uuid, homename, hloc);
            return true;
        } catch (SQLException e) {
            skillIssue(e);
            return false;
        }
    }

    private static enum SearchMode {
        LEVENSHTEIN,
        EITHER_CONTAINS,
    }

    private boolean searchHomes(Player player, String query, SearchMode mode) {
        String uuid = player.getUniqueId().toString();
        ResultSet homes;

        try {
            homes = prepared.getPlayerHomes(uuid);

            player.sendMessage(ChatColor.BOLD + "Searching for homes... `" + query +
                    "`");

            for (int i = 0; homes.next(); i++) {
                String homeName = homes.getString("Name");

                boolean matches = false;

                switch (mode) {
                    case LEVENSHTEIN:
                        matches = levenshteinScore(query, homeName);
                        break;
                    case EITHER_CONTAINS:
                        matches = query.contains(homeName) || homeName.contains(query);
                        break;
                }

                // skip if doesn't match
                if (!matches)
                    continue;

                player.sendMessage(ChatColor.DARK_AQUA + String.valueOf(i + 1) + " | " +
                        homeName + " | " + homes.getString("world"));
            }
        } catch (SQLException e) {
            skillIssue(e);
            return false;
        }

        return true;
    }

    /**
     * Returns true if `name` is "close enough" to `query`
     *
     * This is the formula to tweak if my definition of
     * "close enough" isn't as good as yours.
     */
    private boolean levenshteinScore(String query, String name) {
        if (query.isEmpty()) {
            return true;
        }

        double distance = new LevenshteinDistance().apply(query, name);
        double ratio = distance / query.length();

        return ratio <= (1.0 - HOME_SEARCH_STRICTNESS);
    }

    private boolean cmdListHomes(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        try {
            int page = 0;

            if (args.length > 0) {
                boolean fail = false;

                String firstArg = args[0].toLowerCase();
                if (firstArg.contains("search")) {
                    String[] queryW = Arrays.copyOfRange(args, 1, args.length);
                    String query = String.join(" ", queryW);

                    SearchMode mode = firstArg.equals("fuzzy")
                            ? SearchMode.LEVENSHTEIN
                            : SearchMode.EITHER_CONTAINS;

                    // TODO make the method only responsible for searching, not printing
                    searchHomes(player, query, mode);
                    return true;
                }

                // not searching, so it must be a page number
                try {
                    page = Integer.parseInt(args[0]) - 1;
                } catch (NumberFormatException e) {
                    fail = true;
                } finally {
                    if (fail || page < 0) {
                        return false;
                    }
                }
            }

            ResultSet rs = prepared.homesSegment(uuid, page);
            ResultSet amount = prepared.getHomesAmount(uuid);

            amount.next();
            String pagezamount = (Integer.toString((int) Math.ceil(((double) amount.getInt(1)) / PAGE_LENGTH)));
            player.sendMessage(ChatColor.BOLD + "Homes (Page " + (page + 1) + "/" + pagezamount + ")");

            int start = page * PAGE_LENGTH;
            for (int i = start; rs.next() && i < start + PAGE_LENGTH; i++) {
                Component home_object = Component.text(" | " + rs.getString("Name") + " | " + rs.getString("world"))
                        .clickEvent(ClickEvent.runCommand("/home " + rs.getString("Name")))
                        .color(NamedTextColor.DARK_AQUA);
                player.sendMessage(home_object);
            }

            Component page_previous = Component.text("previous")
                    .clickEvent(ClickEvent.runCommand("/homes " + page))
                    .color(NamedTextColor.YELLOW);

            Component page_next = Component.text("next")
                    .clickEvent(ClickEvent.runCommand("/homes " + (page + 2)))
                    .color(NamedTextColor.GOLD);

            Component spacer = Component.text(" || ");

            player.sendMessage(page_previous.append(spacer).append(page_next));

        } catch (SQLException e) {
            skillIssue(e);
        }

        return true;
    }

    private void cmdSwapHome(Player player) {
        Location oldLocation = player.getLocation();

        try {
            var exists = sendPlayerToOwnHome(player, SWAP_HOME_NAME);

            if (exists) {
                setHomeAt(oldLocation, player, SWAP_HOME_NAME);
                player.sendMessage("Swap successful!");
            } else {
                player.sendMessage("Swap home not found. Use `/brb` first!");
            }
        } catch (SQLException e) {
            skillIssue(e);
        }
    }

    private void cmdHome(Player player, String[] args) {
        String home = args.length > 0 ? args[0] : "home";

        try {
            player.sendMessage("| Going to: " + home + " |");
            var exists = sendPlayerToOwnHome(player, home);

            if (exists) {
                player.sendMessage("Teleported to: " + home);
            } else {
                player.sendMessage("Home not found");
            }
        } catch (SQLException e) {
            skillIssue(e);
        }
    }

    /**
     * Command that teleports the player to another player's home
     *
     * @param player The player to teleport
     * @param args   The arguments passed to the command
     */
    private void cmdHomeOther(Player player, String[] args) {
        String homeowner = args[1];
        String home = args[2];

        String homeownerUUID = Bukkit.getPlayerUniqueId(homeowner).toString();

        try {
            player.sendMessage("| Going to: " + home + " (User: " + homeowner + ") |");
            var exists = sendPlayerToOtherHome(player, homeownerUUID, home);

            if (exists) {
                player.sendMessage("Teleported to: " + home + " (User: " + homeowner + ")");
            } else {
                player.sendMessage("Home not found");
            }
        } catch (SQLException e) {
            skillIssue(e);
        }
    }

    /**
     * Internal method that teleports the player to their home with the given name
     *
     * Just calls `sendPlayerToOtherHome` under the hood
     *
     * @param target The player to teleport
     * @param home   The name of the home
     * @return true if the home exists, false otherwise
     * @throws SQLException if the database query fails
     */
    private boolean sendPlayerToOwnHome(Player target, String home) throws SQLException {
        String uuid = target.getUniqueId().toString();
        return sendPlayerToOtherHome(target, uuid, home);
    }

    /**
     * Internal method that teleports the player to the home with the given name
     * and owner UUID
     *
     * Use `sendPlayerToOwnHome` if you want the owner to be the same as the player
     *
     * @param target    The player to teleport
     * @param homeowner The home owner's UUID
     * @param home      The name of the home
     * @return true if the home exists, false otherwise
     * @throws SQLException if the database query fails
     */
    private boolean sendPlayerToOtherHome(Player target, String homeownerUUID, String home) throws SQLException {
        ResultSet rs = prepared.homesWithName(homeownerUUID, home);
        if (!rs.next()) {
            return false;
        }

        Location loc = target.getLocation();

        loc.setWorld(Bukkit.getWorld(rs.getString("world")));
        loc.setX(rs.getDouble("x"));
        loc.setY(rs.getDouble("y"));
        loc.setZ(rs.getDouble("z"));

        float yaw = rs.getFloat("yaw");
        float pitch = rs.getFloat("pitch");

        if (yaw != -1.0) {
            loc.setYaw(yaw);
        }

        if (pitch != -1.0) {
            loc.setPitch(pitch);
        }

        target.teleport(loc);
        return true;
    }

    private boolean deleteHome(Player player, String[] args) {
        if (args.length == 0) {
            return false;
        }

        String uuid = player.getUniqueId().toString();

        try {
            String home = args[0];

            if (prepared.homeExists(uuid, home)) {
                prepared.deleteHome(uuid, home);
                player.sendMessage("Home " + home + " Deleted");
            } else {
                player.sendMessage("Home " + home + " not found");
            }
        } catch (SQLException e) {
            skillIssue(e);
        }

        return true;
    }

    /**
     * Generic severe error logging method
     */
    static void skillIssue(Exception e) {
        Logger.getLogger(RCHomes.class.getName()).log(Level.SEVERE, null, e);
    }
}
