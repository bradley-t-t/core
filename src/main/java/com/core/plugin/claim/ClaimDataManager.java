package com.core.plugin.claim;

import com.core.plugin.CorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * YAML-based persistence for {@link ClaimRegion} instances.
 * All claims are stored in a single {@code claims.yml} file under the plugin data folder.
 */
public final class ClaimDataManager {

    private final CorePlugin plugin;
    private final File claimsFile;

    public ClaimDataManager(CorePlugin plugin) {
        this.plugin = plugin;
        this.claimsFile = new File(plugin.getDataFolder(), "claims.yml");
    }

    /** Loads all claims from disk. Returns an empty list if the file is missing or empty. */
    public List<ClaimRegion> loadAll() {
        if (!claimsFile.exists()) return new ArrayList<>();

        var config = YamlConfiguration.loadConfiguration(claimsFile);
        var section = config.getConfigurationSection("claims");
        if (section == null) return new ArrayList<>();

        List<ClaimRegion> claims = new ArrayList<>();
        for (String idString : section.getKeys(false)) {
            ClaimRegion claim = readClaim(section, idString);
            if (claim != null) claims.add(claim);
        }
        return claims;
    }

    /** Writes all claims to disk, replacing the entire file. */
    public void saveAll(Collection<ClaimRegion> claims) {
        var config = new YamlConfiguration();
        for (ClaimRegion claim : claims) {
            writeClaim(config, claim);
        }
        save(config);
    }

    /** Inserts or updates a single claim in the file without disturbing other entries. */
    public void saveClaim(ClaimRegion claim) {
        var config = YamlConfiguration.loadConfiguration(claimsFile);
        writeClaim(config, claim);
        save(config);
    }

    /** Removes a single claim by ID from the file. */
    public void deleteClaim(UUID claimId) {
        var config = YamlConfiguration.loadConfiguration(claimsFile);
        config.set("claims." + claimId, null);
        save(config);
    }

    private ClaimRegion readClaim(ConfigurationSection section, String idString) {
        try {
            var claimSection = section.getConfigurationSection(idString);
            if (claimSection == null) return null;

            UUID claimId = UUID.fromString(idString);
            UUID owner = UUID.fromString(claimSection.getString("owner", ""));
            String name = claimSection.getString("name", "Unnamed");
            String world = claimSection.getString("world", "");
            int minX = claimSection.getInt("min-x");
            int minZ = claimSection.getInt("min-z");
            int maxX = claimSection.getInt("max-x");
            int maxZ = claimSection.getInt("max-z");
            long createdAt = claimSection.getLong("created-at");

            Set<UUID> trusted = new HashSet<>();
            for (String uuidStr : claimSection.getStringList("trusted")) {
                trusted.add(UUID.fromString(uuidStr));
            }

            return new ClaimRegion(claimId, owner, name, world, minX, minZ, maxX, maxZ, trusted, createdAt);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load claim: " + idString, e);
            return null;
        }
    }

    private void writeClaim(YamlConfiguration config, ClaimRegion claim) {
        String path = "claims." + claim.claimId();
        config.set(path + ".owner", claim.ownerId().toString());
        config.set(path + ".name", claim.name());
        config.set(path + ".world", claim.worldName());
        config.set(path + ".min-x", claim.minX());
        config.set(path + ".min-z", claim.minZ());
        config.set(path + ".max-x", claim.maxX());
        config.set(path + ".max-z", claim.maxZ());
        config.set(path + ".trusted", claim.trustedPlayerIds().stream().map(UUID::toString).toList());
        config.set(path + ".created-at", claim.createdAt());
    }

    private void save(YamlConfiguration config) {
        try {
            config.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claims.yml", e);
        }
    }
}
