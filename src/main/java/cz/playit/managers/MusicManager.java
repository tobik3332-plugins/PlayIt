package cz.playit.managers;

import com.xxmicloxx.NoteBlockAPI.event.SongEndEvent;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.model.playmode.MonoMode;
import com.xxmicloxx.NoteBlockAPI.model.playmode.MonoStereoMode;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import cz.playit.PlayIt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MusicManager implements Listener {

    private final PlayIt plugin;
    private final File songsFolder;

    // Název písničky (bez .nbs) -> Song objekt
    private final Map<String, Song> songs = new LinkedHashMap<>();
    private final List<String> songNames = new ArrayList<>();

    private RadioSongPlayer radioPlayer;
    private String currentSongName = null;
    private String lastPlayedSongName = null;
    private BukkitTask nextSongTask = null;

    public MusicManager(PlayIt plugin) {
        this.plugin = plugin;
        this.songsFolder = new File(plugin.getDataFolder(), "songs");
        if (!songsFolder.exists()) {
            songsFolder.mkdirs();
        }
    }

    public void loadSongs() {
        songs.clear();
        songNames.clear();

        if (!songsFolder.exists()) {
            songsFolder.mkdirs();
        }

        File[] files = songsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".nbs"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                try {
                    Song song = NBSDecoder.parse(file);
                    if (song != null) {
                        String rawName = file.getName();
                        String cleanName = rawName.substring(0, rawName.length() - 4);
                        songs.put(cleanName.toLowerCase(Locale.ROOT), song);
                        songNames.add(cleanName);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Nepodařilo se načíst skladbu " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        plugin.getLogger().info("Načteno " + songNames.size() + " .nbs skladeb.");
    }

    public void startRadioIfIdle() {
        if (songNames.isEmpty()) {
            stopRadio();
            return;
        }
        if ((radioPlayer == null || !radioPlayer.isPlaying()) && nextSongTask == null) {
            playRandomNext();
        } else {
            applySettingsToPlayer();
            refreshAllOnlinePlayers();
        }
    }

    public void playRandomNext() {
        cancelPendingNextSong();

        if (songNames.isEmpty()) {
            stopRadio();
            return;
        }

        String nextName;
        if (songNames.size() == 1) {
            nextName = songNames.get(0);
        } else {
            // Vybere náhodnou skladbu, která není stejná jako předchozí
            int index;
            do {
                index = ThreadLocalRandom.current().nextInt(songNames.size());
                nextName = songNames.get(index);
            } while (nextName.equalsIgnoreCase(lastPlayedSongName));
        }

        playSong(nextName);
    }

    public boolean playSong(String songName) {
        Song song = songs.get(songName.toLowerCase(Locale.ROOT));
        if (song == null) {
            return false;
        }

        cancelPendingNextSong();

        // Získání přesného názvu s původními velkými/malými písmeny
        String displaySongName = songNames.stream()
                .filter(n -> n.equalsIgnoreCase(songName))
                .findFirst()
                .orElse(songName);

        stopRadio();

        this.currentSongName = displaySongName;
        this.lastPlayedSongName = displaySongName;
        this.radioPlayer = new RadioSongPlayer(song);
        applySettingsToPlayer();

        // Připojíme všechny hráče, kteří jsou v oblasti a mají zapnutou hudbu
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldHearMusic(player)) {
                radioPlayer.addPlayer(player);
            }
        }

        radioPlayer.setPlaying(true);
        sendNowPlayingActionBar();
        return true;
    }

    private void applySettingsToPlayer() {
        if (radioPlayer == null) return;
        byte volume = (byte) Math.max(0, Math.min(100, plugin.getConfig().getInt("settings.volume", 100)));
        boolean tenOctave = plugin.getConfig().getBoolean("settings.enable-10-octave", true);
        boolean monoSound = plugin.getConfig().getBoolean("settings.mono-sound", true);

        radioPlayer.setVolume(volume);
        radioPlayer.setEnable10Octave(tenOctave);

        // MonoMode přehraje zvuk přímo v místě hlavy hráče -> stejná hlasitost ze všech stran (bez tahání na North)
        if (monoSound) {
            radioPlayer.setChannelMode(new MonoMode());
        } else {
            radioPlayer.setChannelMode(new MonoStereoMode());
        }
    }

    public void stopRadio() {
        cancelPendingNextSong();
        if (radioPlayer != null) {
            radioPlayer.setPlaying(false);
            radioPlayer.destroy();
            radioPlayer = null;
        }
        currentSongName = null;
    }

    private void cancelPendingNextSong() {
        if (nextSongTask != null) {
            nextSongTask.cancel();
            nextSongTask = null;
        }
    }

    public void updatePlayerState(Player player) {
        if (radioPlayer == null) return;

        boolean shouldHear = shouldHearMusic(player);
        boolean isListening = radioPlayer.getPlayerUUIDs().contains(player.getUniqueId());

        if (shouldHear && !isListening) {
            radioPlayer.addPlayer(player);
        } else if (!shouldHear && isListening) {
            radioPlayer.removePlayer(player);
        }
    }

    public void removePlayer(Player player) {
        if (radioPlayer != null) {
            radioPlayer.removePlayer(player);
        }
    }

    public void refreshAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerState(player);
        }
    }

    private boolean shouldHearMusic(Player player) {
        if (!player.isOnline()) return false;
        if (!plugin.getDataManager().isMusicEnabled(player.getUniqueId())) return false;
        return plugin.getAreaManager().isInAnyArea(player.getLocation());
    }

    private void sendNowPlayingActionBar() {
        if (currentSongName == null) return;
        if (!plugin.getConfig().getBoolean("settings.actionbar-enabled", true)) return;

        Component actionBar = plugin.getMessage("actionbar-now-playing", "%song%", currentSongName);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldHearMusic(player)) {
                player.sendActionBar(actionBar);
            }
        }
    }

    @EventHandler
    public void onSongEnd(SongEndEvent event) {
        if (radioPlayer != null && event.getSongPlayer().equals(radioPlayer)) {
            int delaySeconds = Math.max(0, plugin.getConfig().getInt("settings.song-delay-seconds", 2));
            long delayTicks = delaySeconds * 20L;

            // Vyčistíme ukončený přehrávač během pauzy
            if (radioPlayer != null) {
                radioPlayer.destroy();
                radioPlayer = null;
            }
            currentSongName = null;

            if (delayTicks <= 0L) {
                Bukkit.getScheduler().runTask(plugin, this::playRandomNext);
            } else {
                cancelPendingNextSong();
                nextSongTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    nextSongTask = null;
                    playRandomNext();
                }, delayTicks);
            }
        }
    }

    public String getCurrentSongName() {
        return currentSongName;
    }

    public List<String> getSongNames() {
        return Collections.unmodifiableList(songNames);
    }

    public int getSongCount() {
        return songNames.size();
    }
}
