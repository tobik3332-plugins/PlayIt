package cz.playit.managers;

import cz.playit.PlayIt;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private final PlayIt plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, Boolean> playerStates = new ConcurrentHashMap<>();
    private boolean dirty = false;

    public DataManager(PlayIt plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public void load() {
        playerStates.clear();
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nepodařilo se vytvořit players.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (dataConfig.isConfigurationSection("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean enabled = dataConfig.getBoolean("players." + uuidStr);
                    playerStates.put(uuid, enabled);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        dirty = false;
    }

    public boolean isMusicEnabled(UUID uuid) {
        return playerStates.getOrDefault(uuid, plugin.getConfig().getBoolean("settings.default-music-enabled", true));
    }

    public void setMusicEnabled(UUID uuid, boolean enabled) {
        playerStates.put(uuid, enabled);
        dirty = true;
        saveAsync();
    }

    public void saveAsync() {
        if (!dirty) return;
        dirty = false;

        Map<UUID, Boolean> snapshot = Map.copyOf(playerStates);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> saveSnapshot(snapshot));
    }

    public void saveSync() {
        if (!dirty) return;
        dirty = false;
        saveSnapshot(Map.copyOf(playerStates));
    }

    private synchronized void saveSnapshot(Map<UUID, Boolean> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Boolean> entry : snapshot.entrySet()) {
            yaml.set("players." + entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Chyba při ukládání players.yml: " + e.getMessage());
        }
    }
}
