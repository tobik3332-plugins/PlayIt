package cz.playit.commands;

import cz.playit.PlayIt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicCommand implements CommandExecutor, TabCompleter {

    private final PlayIt plugin;

    public MusicCommand(PlayIt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("only-players"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getMessage("music-usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "on" -> {
                if (!player.hasPermission("playit.on-off")) {
                    player.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                if (plugin.getDataManager().isMusicEnabled(player.getUniqueId())) {
                    player.sendMessage(plugin.getMessage("music-already-enabled"));
                    return true;
                }
                plugin.getDataManager().setMusicEnabled(player.getUniqueId(), true);
                plugin.getMusicManager().updatePlayerState(player);
                player.sendMessage(plugin.getMessage("music-enabled"));
            }
            case "off" -> {
                if (!player.hasPermission("playit.on-off")) {
                    player.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                if (!plugin.getDataManager().isMusicEnabled(player.getUniqueId())) {
                    player.sendMessage(plugin.getMessage("music-already-disabled"));
                    return true;
                }
                plugin.getDataManager().setMusicEnabled(player.getUniqueId(), false);
                plugin.getMusicManager().updatePlayerState(player);
                player.sendMessage(plugin.getMessage("music-disabled"));
            }
            case "playing" -> {
                if (!player.hasPermission("playit.playing")) {
                    player.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                String current = plugin.getMusicManager().getCurrentSongName();
                if (current == null) {
                    player.sendMessage(plugin.getMessage("music-nothing-playing"));
                } else {
                    player.sendMessage(plugin.getMessage("music-playing", "%song%", current));
                }
            }
            default -> player.sendMessage(plugin.getMessage("music-usage"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase(Locale.ROOT);

            if (sender.hasPermission("playit.on-off")) {
                if ("on".startsWith(input)) completions.add("on");
                if ("off".startsWith(input)) completions.add("off");
            }
            if (sender.hasPermission("playit.playing")) {
                if ("playing".startsWith(input)) completions.add("playing");
            }
            return completions;
        }
        return List.of();
    }
}
