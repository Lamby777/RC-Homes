package org.sparklet.rchomes;

import java.sql.*;

import org.bukkit.configuration.file.FileConfiguration;

public class DatabaseLogin {
    private String address;
    private String username;
    private String password;
    private String database;
    private int port;

    public static DatabaseLogin fromConfig(RCHomes rch) {
        FileConfiguration config = rch.getConfig();

        return new DatabaseLogin(
                config.getString("db.address"),
                config.getString("db.username"),
                config.getString("db.password"),
                config.getString("db.database"),
                config.getInt("db.port"));
    }

    public DatabaseLogin(String address, String username, String password, String database, int port) {
        this.address = address;
        this.username = username;
        this.password = password;
        this.database = database;
        this.port = port;
    }

    public Connection getConnection() throws SQLException {
        String addr = "jdbc:mysql://" + address + ":" + port + "/" + database;
        return DriverManager.getConnection(addr, username, password);
    }

    public void migrateOldData(RCHomes rch) throws SQLException {
        boolean migrated = false;
        final var logger = rch.getLogger();
        final var conn = getConnection();
        final DatabaseMetaData md = conn.getMetaData();

        // 0.1.0 Migration - Remove server column
        var serverColumnExists = md.getColumns(null, null, database, "server")
                .next();

        if (serverColumnExists) {
            logger.info("Removing server column...");
            var stmt = conn.createStatement();
            stmt.execute("ALTER TABLE homes DROP COLUMN server");

            logger.info("Done!");
            migrated = true;
        }

        if (!migrated) {
            logger.info("No migrations were necessary.");
            return;
        }

        logger.info("RCH migrations are complete. Will now overwrite config `last-version`.");
        // TODO decouple file writing stuff from semverHelper
        rch.semverHelper.overwriteLastVersion(rch);
        conn.close();
    }
}
