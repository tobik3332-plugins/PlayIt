package cz.playit.managers;

import cz.playit.PlayIt;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    public record PlayerData(boolean enabled, int volume) {
    }

    private final PlayIt plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private boolean dirty = false;

    public DataManager(PlayIt plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public void load() {
        playerDataMap.clear();
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nepodařilo se vytvořit players.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = dataConfig.getConfigurationSection("players");
        if (section != null) {
            boolean defaultEnabled = getDefaultEnabled();
            int defaultVolume = getDefaultVolume();

            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    // Zpětná kompatibilita: pokud byl v players.yml uložen jen boolean
                    if (section.isBoolean(uuidStr)) {
                        boolean enabled = section.getBoolean(uuidStr);
                        playerDataMap.put(uuid, new PlayerData(enabled, defaultVolume));
                    } else if (section.isConfigurationSection(uuidStr)) {
                        boolean enabled = section.getBoolean(uuidStr + ".enabled", defaultEnabled);
                        int volume = clampVolume(section.getInt(uuidStr + ".volume", defaultVolume));
                        playerDataMap.put(uuid, new PlayerData(enabled, volume));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        dirty = false;
    }

    private boolean getDefaultEnabled() {
        return plugin.getConfig().getBoolean("settings.default-music-enabled", true);
    }

    private int getDefaultVolume() {
        return clampVolume(plugin.getConfig().getInt("settings.default-player-volume", 25));
    }

    private int clampVolume(int volume) {
        return Math.max(1, Math.min(100, volume));
    }

    public boolean isMusicEnabled(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        return data != null ? data.enabled() : getDefaultEnabled();
    }

    public int getPlayerVolume(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        return data != null ? data.volume() : getDefaultVolume();
    }

    public void setMusicEnabled(UUID uuid, boolean enabled) {
        int currentVolume = getPlayerVolume(uuid);
        playerDataMap.put(uuid, new PlayerData(enabled, currentVolume));
        dirty = true;
        saveAsync();
    }

    public void setPlayerVolume(UUID uuid, int volume) {
        int clamped = clampVolume(volume);
        boolean currentEnabled = isMusicEnabled(uuid);
        playerDataMap.put(uuid, new PlayerData(currentEnabled, clamped));
        dirty = true;
        saveAsync();
    }

    public void saveAsync() {
        if (!dirty) return;
        dirty = false;

        Map<UUID, PlayerData> snapshot = new HashMap<>(playerDataMap);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> saveSnapshot(snapshot));
    }

    public void saveSync() {
        if (!dirty) return;
        dirty = false;
        saveSnapshot(new HashMap<>(playerDataMap));
    }

    private synchronized void saveSnapshot(Map<UUID, PlayerData> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> entry : snapshot.entrySet()) {
            String base = "players." + entry.getKey().toString();
            yaml.set(base + ".enabled", entry.getValue().enabled());
            yaml.set(base + ".volume", entry.getValue().volume());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Chyba při ukládání players.yml: " + e.getMessage());
        }
    }
}
