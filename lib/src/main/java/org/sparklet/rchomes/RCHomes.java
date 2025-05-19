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
    public static final int PAGE_LENGTH = 16;
    // At least this percent of the name should match
    public static final double HOME_SEARCH_STRICTNESS = 0.30;

    private SemVerHelper semverHelper = new SemVerHelper(this);

    public String host, port, database, username, password;
    // static MysqlDataSource data = new MysqlDataSource();
    static Statement stmt;
    static Connection conn;
    static Statement query;
    private PreparedStatements prepared;
    private DatabaseLogin dbLogin;
    ResultSet Lookup;

    private void newConnection() {
        try {
            conn = DriverManager.getConnection(
                    "jdbc:mysql://" + dbLogin.address + "/" + dbLogin.database, dbLogin.username, dbLogin.password);
            prepared = new PreparedStatements(conn, getLogger());

            // TODO prepared statement
            stmt = conn.createStatement();
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS homes (ID int PRIMARY KEY NOT NULL AUTO_INCREMENT, UUID varchar(255), Name varchar(255), world varchar(255), x double, y double, z double, yaw float DEFAULT - 1.0, pitch float DEFAULT - 1.0)");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    void migrateOldData() throws SQLException {
        getLogger().info("Removing server column if exists");
        prepared.migrationDeleteServerCol();
        getLogger().info("Done!");

        getLogger().info("RCH Migration complete. Will now overwrite config `last-version`.");
        semverHelper.overwriteLastVersion(this);
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
            migrateOldData();
        } catch (SQLException e) {
            Logger.getLogger(RCHomes.class.getName())
                    .log(
                            Level.WARNING,
                            "Failed to run backwards-compatibility checks... Trying again next load.",
                            e);
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
                case "newhome":
                    return cmdNewHome(player, args);

                case "sethome":
                    cmdSetHome(player, args);
                    return true;

                case "homes":
                    return cmdListHomes(player, args);

                case "homeshelp":
                    // if no args, the page should be null
                    showHelp(player, args.length == 0 ? null : args[0]);
                    return true;

                case "home":
                    // returns bool from inside fn
                    return gotoHome(player, args);

                case "delhome":
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

                        } else if (args[0].equalsIgnoreCase("help")) {
                            player.sendMessage("/homemanager area 9 -> shows homes in a radius of 9");
                            player.sendMessage("/homemanager delhome homename username -> deletes home");
                            player.sendMessage("/homemanager tp username homename -> teleports to user home");
                            player.sendMessage("/homemanager playerhomes playername");

                        } else if (args[0].equalsIgnoreCase("tp")) {
                            cmdJumpHomeOther(player, args);
                        }
                    }
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

        switch (page) {
            case null:
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
                player.sendMessage("Swap Commands:");
                player.sendMessage("`/brb` - Set your swap home. Short for `/sethome __swap`.");
                player.sendMessage("`/ret` - Return to your swap home. Short for `/home __swap`.");
                player.sendMessage("`/swap` - Swaps your current location with the home `__swap`.");
                break;
            case "admin":
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

    boolean cmdJumpHomeOther(Player player, String[] args) {

        if (args.length < 1) {
            return true;
        } else {
            UUID uuid = Bukkit.getPlayerUniqueId(args[1]);
            String home = args[2];

            try {
                ResultSet rs = prepared.homesWithName(uuid.toString(), home);
                if (!rs.next()) {
                    player.sendMessage("Home not found");
                    return false;
                }

                player.sendMessage("| Going to: " + home + " | ");

                Location loc = player.getLocation();
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

                player.teleport(loc);
                player.sendMessage("Teleported to: " + home);
            } catch (SQLException e) {
                skillIssue(e);
            }
            return true;
        }
    }

    // TODO what the actual hell is this method
    void cmdSearchHomes(Player player, int pos) {
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

    void cmdDelHomeOther(Player player, String[] args) {
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

    boolean cmdNewHome(Player player, String[] args) {
        String home = args.length > 0 ? args[0] : "home";
        String uuid = player.getUniqueId().toString();

        boolean exists;

        try {
            exists = prepared.homeExists(uuid, home);
        } catch (SQLException e) {
            player.sendMessage("Error occurred while checking if home exists...");
            skillIssue(e);

            return false;
        }

        if (exists) {
            player.sendMessage("Home " + home +
                    " already exists! Use /sethome to skip this check.");
        } else {
            baseSetHome(player, home);
            player.sendMessage("New home created: " + home);
        }

        return exists;
    }

    void cmdSetHome(Player player, String[] args) {
        String home = args.length > 0 ? args[0] : "home";

        baseSetHome(player, home);
        player.sendMessage("Home set: " + home);
    }

    void baseSetHome(Player player, String homename) {
        Location loc = player.getLocation();
        String uuid = player.getUniqueId().toString();

        HomeLocation hloc = new HomeLocation(loc, player.getWorld().getName());
        prepared.setHome(uuid, homename, hloc);
    }

    private static enum SearchMode {
        LEVENSHTEIN,
        EITHER_CONTAINS,
    }

    boolean searchHomes(Player player, String query, SearchMode mode) {
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

    boolean cmdListHomes(Player player, String[] args) {
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

                    return searchHomes(player, query, mode);
                }

                // not searching, so it must be a page number
                try {
                    page = Integer.parseInt(args[0]) - 1;
                } catch (NumberFormatException e) {
                    fail = true;
                } finally {
                    if (fail || page < 0) {
                        player.sendMessage("Usage: /homes [page]");
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
            return false;
        }

        return true;
    }

    boolean gotoHome(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();
        String home = args.length > 0 ? args[0] : "home";

        try {
            ResultSet rs = prepared.homesWithName(uuid, home);
            if (!rs.next()) {
                player.sendMessage("Home not found");
                return false;
            }

            player.sendMessage("| Going to: " + home + " | ");

            Location loc = player.getLocation();
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

            player.teleport(loc);
            player.sendMessage("Teleported to: " + home);
        } catch (SQLException e) {
            skillIssue(e);
        }
        return true;
    }

    boolean deleteHome(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();

        try {
            if (args.length > 0) {
                String home = args[0];

                if (prepared.homeExists(uuid, home)) {
                    prepared.deleteHome(uuid, home);
                    player.sendMessage("Home " + home + " Deleted");
                } else {
                    player.sendMessage("Home " + home + " not found");
                }
            } else {
                player.sendMessage("Usage: /delhome homename");
            }

        } catch (SQLException e) {
            skillIssue(e);
        }
        return true;
    }

    /**
     * Generic severe error logger
     */
    static void skillIssue(Exception e) {
        Logger.getLogger(RCHomes.class.getName()).log(Level.SEVERE, null, e);
    }
}
