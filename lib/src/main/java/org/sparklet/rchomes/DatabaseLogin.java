package org.sparklet.rchomes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
}
