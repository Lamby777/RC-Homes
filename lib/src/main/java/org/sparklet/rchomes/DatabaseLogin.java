package org.sparklet.rchomes;

import org.bukkit.configuration.file.FileConfiguration;

public class DatabaseLogin {
    public String address;
    public String username;
    public String password;
    public String database;
    public int port;

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
        this.port = port; // TODO actually use this
    }
}
