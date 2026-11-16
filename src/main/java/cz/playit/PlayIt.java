package cz.playit;

import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.NoteBlockPlayerMain;
import cz.playit.commands.AdminMusicCommand;
import cz.playit.commands.MusicCommand;
import cz.playit.listeners.PlayerListener;
import cz.playit.managers.AreaManager;
import cz.playit.managers.DataManager;
import cz.playit.managers.MusicManager;
import cz.playit.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayIt extends JavaPlugin {

    private NoteBlockAPI noteBlockAPI;
    private NoteBlockPlayerMain legacyNoteBlockMain;
    private DataManager dataManager;
    private AreaManager areaManager;
    private MusicManager musicManager;

    private static class EmbeddedNoteBlockAPI extends NoteBlockAPI {
        private PlayIt host;

        @Override
        public void doSync(Runnable runnable) {
            if (host != null && host.isEnabled()) {
                Bukkit.getScheduler().runTask(host, runnable);
            }
        }

        @Override
        public void doAsync(Runnable runnable) {
            if (host != null && host.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(host, runnable);
            }
        }
    }

    private static class EmbeddedNoteBlockPlayerMain extends NoteBlockPlayerMain {
        private PlayIt host;

        @Override
        public void doSync(Runnable runnable) {
            if (host != null && host.isEnabled()) {
                Bukkit.getScheduler().runTask(host, runnable);
            }
        }

        @Override
        public void doAsync(Runnable runnable) {
            if (host != null && host.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(host, runnable);
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Inicializace vestavěného NoteBlockAPI (včetně legacy bridge) bez duplicitního JavaPlugin konstruktoru
        initEmbeddedNoteBlockAPI();

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

    @SuppressWarnings("deprecation")
    private void initEmbeddedNoteBlockAPI() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            // 1. Hlavní NoteBlockAPI instance
            EmbeddedNoteBlockAPI adapter = (EmbeddedNoteBlockAPI) unsafe.allocateInstance(EmbeddedNoteBlockAPI.class);
            adapter.host = this;
            this.noteBlockAPI = adapter;

            for (Field field : NoteBlockAPI.class.getDeclaredFields()) {
                field.setAccessible(true);
                switch (field.getName()) {
                    case "plugin" -> field.set(null, this.noteBlockAPI);
                    case "disabling" -> field.set(this.noteBlockAPI, false);
                    case "playingSongs", "playerVolume" -> field.set(this.noteBlockAPI, new ConcurrentHashMap<>());
                }
            }

            // 2. Legacy NoteBlockPlayerMain instance (odstraní WARN při CallUpdate)
            EmbeddedNoteBlockPlayerMain legacyAdapter = (EmbeddedNoteBlockPlayerMain) unsafe.allocateInstance(EmbeddedNoteBlockPlayerMain.class);
            legacyAdapter.host = this;
            this.legacyNoteBlockMain = legacyAdapter;

            for (Field field : NoteBlockPlayerMain.class.getDeclaredFields()) {
                field.setAccessible(true);
                switch (field.getName()) {
                    case "plugin" -> field.set(null, this.legacyNoteBlockMain);
                    case "disabling" -> field.set(this.legacyNoteBlockMain, false);
                    case "playingSongs", "playerVolume" -> field.set(this.legacyNoteBlockMain, new ConcurrentHashMap<>());
                }
            }
        } catch (Exception e) {
            getLogger().severe("Nepodařilo se inicializovat NoteBlockAPI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDisable() {
        if (musicManager != null) {
            musicManager.stopRadio();
        }
        if (noteBlockAPI != null) {
            try {
                Field disablingField = NoteBlockAPI.class.getDeclaredField("disabling");
                disablingField.setAccessible(true);
                disablingField.set(noteBlockAPI, true);
            } catch (Exception ignored) {
            }
        }
        if (legacyNoteBlockMain != null) {
            try {
                Field disablingField = NoteBlockPlayerMain.class.getDeclaredField("disabling");
                disablingField.setAccessible(true);
                disablingField.set(legacyNoteBlockMain, true);
            } catch (Exception ignored) {
            }
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
