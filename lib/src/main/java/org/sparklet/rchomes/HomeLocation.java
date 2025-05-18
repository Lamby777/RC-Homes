package org.sparklet.rchomes;

import org.bukkit.Location;

public class HomeLocation {
    Location loc;
    public String worldname, servername;

    public HomeLocation(Location loc, String worldname, String servername) {
        this.loc = loc;
        this.worldname = worldname;
        this.servername = servername; // is this even necessary?
    }
}
