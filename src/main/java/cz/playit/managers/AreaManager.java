package cz.playit.managers;

import cz.playit.PlayIt;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AreaManager {

    public record MusicArea(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(String worldName, int x, int y, int z) {
            return world.equals(worldName)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    private static class AreaBuilder {
        Location pos1;
        Location pos2;
    }

    private final PlayIt plugin;
    private final File areasFile;
    private FileConfiguration areasConfig;

    // Uložené registrované oblasti (klíč je lowercase název)
    private final Map<String, MusicArea> areas = new ConcurrentHashMap<>();
    // Rychlé pole pro iteraci bez alokace paměti při pohybu hráčů
    private volatile MusicArea[] cachedAreasArray = new MusicArea[0];

    // Rozpracované oblasti adminů: UUID admina -> (lowercase název oblasti -> builder)
    private final Map<UUID, Map<String, AreaBuilder>> pendingCreations = new ConcurrentHashMap<>();

    // Potvrzení smazání (5 sekund): UUID admina -> (lowercase název oblasti -> timestamp vypršení)
    private final Map<UUID, Map<String, Long>> pendingDeletions = new ConcurrentHashMap<>();

    public AreaManager(PlayIt plugin) {
        this.plugin = plugin;
        this.areasFile = new File(plugin.getDataFolder(), "areas.yml");
        load();
    }

    public void load() {
        areas.clear();
        if (!areasFile.exists()) {
            try {
                areasFile.getParentFile().mkdirs();
                areasFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nepodařilo se vytvořit areas.yml: " + e.getMessage());
            }
        }
        areasConfig = YamlConfiguration.loadConfiguration(areasFile);
        ConfigurationSection section = areasConfig.getConfigurationSection("areas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String name = section.getString(key + ".name", key);
                String world = section.getString(key + ".world");
                if (world == null) continue;

                int minX = section.getInt(key + ".minX");
                int minY = section.getInt(key + ".minY");
                int minZ = section.getInt(key + ".minZ");
                int maxX = section.getInt(key + ".maxX");
                int maxY = section.getInt(key + ".maxY");
                int maxZ = section.getInt(key + ".maxZ");

                areas.put(key.toLowerCase(Locale.ROOT), new MusicArea(name, world, minX, minY, minZ, maxX, maxY, maxZ));
            }
        }
        rebuildCache();
    }

    private void rebuildCache() {
        cachedAreasArray = areas.values().toArray(new MusicArea[0]);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, MusicArea> entry : areas.entrySet()) {
            String key = entry.getKey();
            MusicArea area = entry.getValue();
            yaml.set("areas." + key + ".name", area.name());
            yaml.set("areas." + key + ".world", area.world());
            yaml.set("areas." + key + ".minX", area.minX());
            yaml.set("areas." + key + ".minY", area.minY());
            yaml.set("areas." + key + ".minZ", area.minZ());
            yaml.set("areas." + key + ".maxX", area.maxX());
            yaml.set("areas." + key + ".maxY", area.maxY());
            yaml.set("areas." + key + ".maxZ", area.maxZ());
        }
        try {
            yaml.save(areasFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Chyba při ukládání areas.yml: " + e.getMessage());
        }
    }

    public boolean isInAnyArea(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        MusicArea[] snapshot = cachedAreasArray;
        if (snapshot.length == 0) return false;

        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (MusicArea area : snapshot) {
            if (area.contains(worldName, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean areaExists(String name) {
        return areas.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public boolean isCreating(UUID adminId, String name) {
        Map<String, AreaBuilder> map = pendingCreations.get(adminId);
        return map != null && map.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public void startCreation(UUID adminId, String name) {
        pendingCreations.computeIfAbsent(adminId, k -> new ConcurrentHashMap<>())
                .put(name.toLowerCase(Locale.ROOT), new AreaBuilder());
    }

    public boolean setPos1(UUID adminId, String name, Location loc) {
        Map<String, AreaBuilder> map = pendingCreations.get(adminId);
        if (map == null) return false;
        AreaBuilder builder = map.get(name.toLowerCase(Locale.ROOT));
        if (builder == null) return false;
        builder.pos1 = loc.clone();
        return true;
    }

    public boolean setPos2(UUID adminId, String name, Location loc) {
        Map<String, AreaBuilder> map = pendingCreations.get(adminId);
        if (map == null) return false;
        AreaBuilder builder = map.get(name.toLowerCase(Locale.ROOT));
        if (builder == null) return false;
        builder.pos2 = loc.clone();
        return true;
    }

    public enum FinishResult {
        SUCCESS,
        MISSING_POSITIONS,
        DIFFERENT_WORLDS
    }

    public FinishResult finishCreation(UUID adminId, String originalName) {
        String key = originalName.toLowerCase(Locale.ROOT);
        Map<String, AreaBuilder> map = pendingCreations.get(adminId);
        if (map == null || !map.containsKey(key)) {
            return FinishResult.MISSING_POSITIONS;
        }

        AreaBuilder builder = map.get(key);
        if (builder.pos1 == null || builder.pos2 == null) {
            return FinishResult.MISSING_POSITIONS;
        }

        if (builder.pos1.getWorld() == null || builder.pos2.getWorld() == null
                || !builder.pos1.getWorld().equals(builder.pos2.getWorld())) {
            return FinishResult.DIFFERENT_WORLDS;
        }

        int minX = Math.min(builder.pos1.getBlockX(), builder.pos2.getBlockX());
        int minY = Math.min(builder.pos1.getBlockY(), builder.pos2.getBlockY());
        int minZ = Math.min(builder.pos1.getBlockZ(), builder.pos2.getBlockZ());
        int maxX = Math.max(builder.pos1.getBlockX(), builder.pos2.getBlockX());
        int maxY = Math.max(builder.pos1.getBlockY(), builder.pos2.getBlockY());
        int maxZ = Math.max(builder.pos1.getBlockZ(), builder.pos2.getBlockZ());

        MusicArea area = new MusicArea(originalName, builder.pos1.getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ);
        areas.put(key, area);
        map.remove(key);
        rebuildCache();
        save();
        return FinishResult.SUCCESS;
    }

    public enum DeleteResult {
        NOT_FOUND,
        AWAITING_CONFIRMATION,
        DELETED
    }

    public DeleteResult requestOrConfirmDelete(UUID adminId, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!areas.containsKey(key)) {
            return DeleteResult.NOT_FOUND;
        }

        long now = System.currentTimeMillis();
        Map<String, Long> userDeletions = pendingDeletions.computeIfAbsent(adminId, k -> new ConcurrentHashMap<>());
        Long expireTime = userDeletions.get(key);

        if (expireTime != null && now <= expireTime) {
            userDeletions.remove(key);
            areas.remove(key);
            rebuildCache();
            save();
            return DeleteResult.DELETED;
        }

        // Nastaví platnost potvrzení na 5000 ms (5 sekund)
        userDeletions.put(key, now + 5000L);
        return DeleteResult.AWAITING_CONFIRMATION;
    }

    public Collection<MusicArea> getAreas() {
        return Collections.unmodifiableCollection(areas.values());
    }

    public int getAreaCount() {
        return areas.size();
    }
}
