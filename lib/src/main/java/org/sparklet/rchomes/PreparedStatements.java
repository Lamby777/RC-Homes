package org.sparklet.rchomes;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreparedStatements {
    private PreparedStatement _getAllHomes;
    private PreparedStatement _homesWithName;
    private PreparedStatement _homesSegment;
    private PreparedStatement _deleteHome;
    private PreparedStatement _setHome;
    private PreparedStatement _getAreaHomes;

    private PreparedStatement _getPlayerHomes;
    private Logger logger;

    public PreparedStatements(Connection conn, Logger logger) {
        this.logger = logger;

        try {
            _getAllHomes = conn.prepareStatement("SELECT * FROM homes WHERE UUID = ?");

            _homesWithName = conn.prepareStatement(
                    "SELECT * FROM homes WHERE UUID = ? AND NAME = ?");

            _homesSegment = conn.prepareStatement(
                    "SELECT * FROM homes WHERE UUID = ? ORDER BY id DESC LIMIT ?,?");

            _deleteHome = conn.prepareStatement(
                    "DELETE FROM homes WHERE UUID = ? AND NAME = ?");

            _setHome = conn.prepareStatement(
                    "INSERT INTO homes (UUID,Name,world,x,y,z,yaw,pitch,server) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

            _getAreaHomes = conn.prepareStatement(
                    "SELECT * FROM homes WHERE world = ? AND x > ? AND x < ? AND z > ? AND z < ?");

            _getPlayerHomes = conn.prepareStatement(
                    "SELECT * FROM homes WHERE UUID = ?");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to init prepared", e);
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

    ResultSet getAllHomes(String uuid) throws SQLException {
        _getAllHomes.setString(1, uuid);
        return _getAllHomes.executeQuery();
    }

    ResultSet getAreaHomes(String world, int[] coordz) throws SQLException {
        _getAreaHomes.setString(1, world);
        _getAreaHomes.setInt(2, coordz[0]);
        _getAreaHomes.setInt(3, coordz[1]);
        _getAreaHomes.setInt(4, coordz[2]);
        _getAreaHomes.setInt(5, coordz[3]);
        return _getAreaHomes.executeQuery();
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
