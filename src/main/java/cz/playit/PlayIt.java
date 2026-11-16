package cz.playit;

import cz.playit.commands.AdminMusicCommand;
import cz.playit.commands.MusicCommand;
import cz.playit.listeners.PlayerListener;
import cz.playit.managers.AreaManager;
import cz.playit.managers.DataManager;
import cz.playit.managers.MusicManager;
import cz.playit.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayIt extends JavaPlugin {

    private DataManager dataManager;
    private AreaManager areaManager;
    private MusicManager musicManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.dataManager = new DataManager(this);
        this.areaManager = new AreaManager(this);
        this.musicManager = new MusicManager(this);

        musicManager.loadSongs();

        getServer().getPluginManager().registerEvents(musicManager, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand musicCmd = getCommand("music");
        if (musicCmd != null) {
            MusicCommand musicExecutor = new MusicCommand(this);
            musicCmd.setExecutor(musicExecutor);
            musicCmd.setTabCompleter(musicExecutor);
        }

        PluginCommand adminCmd = getCommand("amusic");
        if (adminCmd != null) {
            AdminMusicCommand adminExecutor = new AdminMusicCommand(this);
            adminCmd.setExecutor(adminExecutor);
            adminCmd.setTabCompleter(adminExecutor);
        }

        musicManager.startRadioIfIdle();
        getLogger().info("PlayIt byl úspěšně spuštěn!");
    }

    @Override
    public void onDisable() {
        if (musicManager != null) {
            musicManager.stopRadio();
        }
        if (dataManager != null) {
            dataManager.saveSync();
        }
        getLogger().info("PlayIt byl vypnut.");
    }

    public void reloadPlugin() {
        reloadConfig();
        dataManager.load();
        areaManager.load();
        musicManager.loadSongs();
        musicManager.startRadioIfIdle();
    }

    public Component getMessage(String path, String... placeholders) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bPlayIt&8] &r");
        String raw = getConfig().getString("messages." + path, "&cChybí zpráva v configu: " + path);
        raw = raw.replace("%prefix%", prefix);

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace(placeholders[i], placeholders[i + 1]);
        }

        return ColorUtil.parse(raw);
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public AreaManager getAreaManager() {
        return areaManager;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }
}
