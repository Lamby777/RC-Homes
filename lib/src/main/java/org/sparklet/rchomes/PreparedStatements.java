package org.sparklet.rchomes;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreparedStatements {
    private PreparedStatement _homesWithName;
    private PreparedStatement _homesSegment;
    private PreparedStatement _deleteHome;
    private PreparedStatement _setHome;
    private PreparedStatement _getHomesAmount;
    private PreparedStatement _getAreaHomes;
    private PreparedStatement _migrationDeleteServerCol;
    private PreparedStatement _getPlayerHomes;
    private Logger logger;

    public PreparedStatements(Connection conn, Logger logger) {
        this.logger = logger;

        try {
            _getPlayerHomes = conn.prepareStatement("SELECT * FROM homes WHERE UUID = ?");

            _homesWithName = conn.prepareStatement(
                    "SELECT * FROM homes WHERE UUID = ? AND NAME = ?");

            _homesSegment = conn.prepareStatement(
                    "SELECT * FROM homes WHERE UUID = ? ORDER BY id DESC LIMIT ?,?");

            _deleteHome = conn.prepareStatement(
                    "DELETE FROM homes WHERE UUID = ? AND NAME = ?");

            _setHome = conn.prepareStatement(
                    "INSERT INTO homes (UUID,Name,world,x,y,z,yaw,pitch,server) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

            _getHomesAmount = conn.prepareStatement("SELECT COUNT(*) from homes where UUID = ?");

            _migrationDeleteServerCol = conn.prepareStatement("ALTER TABLE homes DROP COLUMN IF EXISTS server");

            _getAreaHomes = conn.prepareStatement(
                    "SELECT * FROM homes WHERE world = ? AND x > ? AND x < ? AND z > ? AND z < ?");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to init prepared", e);
        }
    }

    void migrationDeleteServerCol() {
        try {
            _migrationDeleteServerCol.executeQuery();
        } catch (SQLException e) {
            RCHomes.skillIssue(e);
        }
    }

    void setHome(String uuid, String home, HomeLocation hloc) {
        deleteHome(uuid, home);

        logger.info("Inserting user home " + uuid + " with Name:" + home);

        try {
            _setHome.setString(1, uuid);
            _setHome.setString(2, home);
            _setHome.setString(3, hloc.worldname);
            _setHome.setDouble(4, hloc.loc.getX());
            _setHome.setDouble(5, hloc.loc.getY());
            _setHome.setDouble(6, hloc.loc.getZ());
            _setHome.setFloat(7, hloc.loc.getYaw());
            _setHome.setFloat(8, hloc.loc.getPitch());

            // phew, it's over
            _setHome.execute();
        } catch (SQLException e) {
            RCHomes.skillIssue(e);
        }
    }

    ResultSet homesWithName(String uuid, String home) throws SQLException {
        _homesWithName.setString(1, uuid);
        _homesWithName.setString(2, home);
        return _homesWithName.executeQuery();
    }

    ResultSet getPlayerHomes(String uuid) throws SQLException {
        _getPlayerHomes.setString(1, uuid);
        return _getPlayerHomes.executeQuery();
    }

    ResultSet getAreaHomes(String world, int[] coords) throws SQLException {
        _getAreaHomes.setString(1, world);
        _getAreaHomes.setInt(2, coords[0]);
        _getAreaHomes.setInt(3, coords[1]);
        _getAreaHomes.setInt(4, coords[2]);
        _getAreaHomes.setInt(5, coords[3]);
        return _getAreaHomes.executeQuery();
    }

    ResultSet getHomesAmount(String uuid) throws SQLException {
        _getHomesAmount.setString(1, uuid);
        return _getHomesAmount.executeQuery();
    }

    ResultSet homesSegment(String uuid, int segment) throws SQLException {
        _homesSegment.setString(1, uuid);

        int start = segment * RCHomes.PAGE_LENGTH;
        _homesSegment.setInt(2, start);
        _homesSegment.setInt(3, start + RCHomes.PAGE_LENGTH);

        return _homesSegment.executeQuery();
    }

    boolean homeExists(String uuid, String home) throws SQLException {
        return homesWithName(uuid, home).next();
    }

    boolean deleteHome(String uuid, String home) {
        try {
            _deleteHome.setString(1, uuid);
            _deleteHome.setString(2, home);
            _deleteHome.execute();
            return true;
        } catch (SQLException e) {
            RCHomes.skillIssue(e);
        }
        return false;
    }
}
