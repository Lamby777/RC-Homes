package org.sparklet.rchomes;

import java.util.Arrays;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;

public class SemVerHelper {
    private final int[] lastVersion;
    private final int[] currentVersion;

    public SemVerHelper(RCHomes rch) {
        PluginDescriptionFile pdf = rch.getDescription();
        FileConfiguration config = rch.getConfig();

        lastVersion = parseSemVer(config.getString("last-version"));
        currentVersion = parseSemVer(pdf.getVersion());
    }

    /**
     * Takes a reference to the plugin instance so it can save the config
     */
    public void overwriteLastVersion(RCHomes rch) {
        PluginDescriptionFile pdf = rch.getDescription();

        rch.getLogger().info("RCH Migration complete.");
        rch.getConfig().set("last-version", pdf.getVersion());
        rch.saveConfig();
        rch.getLogger().info("RCH config saved after migration.");
    }

    private static int[] parseSemVer(String semVerStr) {
        String[] sep = semVerStr.split("\\.");
        int[] parsed = new int[sep.length];

        for (int i = 0; i < sep.length; i++) {
            parsed[i] = Integer.parseInt(sep[i]);
        }

        return parsed;
    }

    private static Ord semVerCmp(int[] first, int[] second) {
        if (Arrays.equals(first, second)) {
            return Ord.EQUAL;
        }

        final boolean firstEq = (first[0] == second[0]);
        final boolean secondEq = (first[1] == second[1]);

        final boolean firstGreater = (first[0] > second[0]) || (firstEq && (first[1] > second[1])) ||
                (firstEq && secondEq && (first[2] > second[2]));

        return firstGreater ? Ord.GREATER : Ord.LESS;
    }
}
