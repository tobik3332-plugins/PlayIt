package cz.playit.commands;

import cz.playit.PlayIt;
import cz.playit.managers.AreaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminMusicCommand implements CommandExecutor, TabCompleter {

    private final PlayIt plugin;

    public AdminMusicCommand(PlayIt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("admin-usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("playit.reload")) {
                sender.sendMessage(plugin.getMessage("no-permission"));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(plugin.getMessage("reload-success",
                    "%songs%", String.valueOf(plugin.getMusicManager().getSongCount()),
                    "%areas%", String.valueOf(plugin.getAreaManager().getAreaCount())));
            return true;
        }

        if (!sender.hasPermission("playit.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        switch (sub) {
            case "play" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("admin-usage"));
                    return true;
                }
                String songName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                if (plugin.getMusicManager().playSong(songName)) {
                    String actualName = plugin.getMusicManager().getCurrentSongName();
                    sender.sendMessage(plugin.getMessage("song-played", "%song%", actualName != null ? actualName : songName));
                } else {
                    sender.sendMessage(plugin.getMessage("song-not-found", "%song%", songName));
                }
            }
            case "skip" -> {
                if (plugin.getMusicManager().getSongCount() == 0) {
                    sender.sendMessage(plugin.getMessage("no-songs-loaded"));
                    return true;
                }
                plugin.getMusicManager().playRandomNext();
                String actualName = plugin.getMusicManager().getCurrentSongName();
                sender.sendMessage(plugin.getMessage("song-skipped", "%song%", actualName != null ? actualName : ""));
            }
            case "area" -> handleAreaCommand(sender, args);
            default -> sender.sendMessage(plugin.getMessage("admin-usage"));
        }

        return true;
    }

    private void handleAreaCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessage("area-usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        AreaManager areaManager = plugin.getAreaManager();

        if (action.equals("list")) {
            if (areaManager.getAreaCount() == 0) {
                sender.sendMessage(plugin.getMessage("area-list-empty"));
                return;
            }
            sender.sendMessage(plugin.getMessage("area-list-header", "%count%", String.valueOf(areaManager.getAreaCount())));
            for (AreaManager.MusicArea area : areaManager.getAreas()) {
                sender.sendMessage(plugin.getMessage("area-list-item", "%area%", area.name(), "%world%", area.world()));
            }
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("only-players"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessage("area-usage"));
            return;
        }

        String areaName = args[2];

        switch (action) {
            case "create" -> {
                if (areaManager.areaExists(areaName)) {
                    player.sendMessage(plugin.getMessage("area-already-exists", "%area%", areaName));
                    return;
                }

                if (!areaManager.isCreating(player.getUniqueId(), areaName)) {
                    areaManager.startCreation(player.getUniqueId(), areaName);
                    player.sendMessage(plugin.getMessage("area-create-started", "%area%", areaName));
                    return;
                }

                AreaManager.FinishResult result = areaManager.finishCreation(player.getUniqueId(), areaName);
                switch (result) {
                    case SUCCESS -> {
                        player.sendMessage(plugin.getMessage("area-created", "%area%", areaName));
                        plugin.getMusicManager().refreshAllOnlinePlayers();
                    }
                    case MISSING_POSITIONS -> player.sendMessage(plugin.getMessage("area-missing-positions", "%area%", areaName));
                    case DIFFERENT_WORLDS -> player.sendMessage(plugin.getMessage("area-different-worlds", "%area%", areaName));
                }
            }
            case "pos1" -> {
                if (areaManager.areaExists(areaName)) {
                    player.sendMessage(plugin.getMessage("area-already-exists", "%area%", areaName));
                    return;
                }
                if (areaManager.setPos1(player.getUniqueId(), areaName, player.getLocation())) {
                    player.sendMessage(plugin.getMessage("area-pos1-set", "%area%", areaName));
                } else {
                    player.sendMessage(plugin.getMessage("area-not-started", "%area%", areaName));
                }
            }
            case "pos2" -> {
                if (areaManager.areaExists(areaName)) {
                    player.sendMessage(plugin.getMessage("area-already-exists", "%area%", areaName));
                    return;
                }
                if (areaManager.setPos2(player.getUniqueId(), areaName, player.getLocation())) {
                    player.sendMessage(plugin.getMessage("area-pos2-set", "%area%", areaName));
                } else {
                    player.sendMessage(plugin.getMessage("area-not-started", "%area%", areaName));
                }
            }
            case "delete" -> {
                AreaManager.DeleteResult result = areaManager.requestOrConfirmDelete(player.getUniqueId(), areaName);
                switch (result) {
                    case NOT_FOUND -> player.sendMessage(plugin.getMessage("area-not-found", "%area%", areaName));
                    case AWAITING_CONFIRMATION -> player.sendMessage(plugin.getMessage("area-delete-confirm", "%area%", areaName));
                    case DELETED -> {
                        player.sendMessage(plugin.getMessage("area-deleted", "%area%", areaName));
                        plugin.getMusicManager().refreshAllOnlinePlayers();
                    }
                }
            }
            default -> player.sendMessage(plugin.getMessage("area-usage"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("playit.admin")) {
                if ("play".startsWith(input)) completions.add("play");
                if ("skip".startsWith(input)) completions.add("skip");
                if ("area".startsWith(input)) completions.add("area");
            }
            if (sender.hasPermission("playit.reload")) {
                if ("reload".startsWith(input)) completions.add("reload");
            }
            return completions;
        }

        if (!sender.hasPermission("playit.admin")) return List.of();

        if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            for (String song : plugin.getMusicManager().getSongNames()) {
                if (song.toLowerCase(Locale.ROOT).startsWith(input)) {
                    completions.add(song);
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("area")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            for (String sub : List.of("create", "pos1", "pos2", "delete", "list")) {
                if (sub.startsWith(input)) completions.add(sub);
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("area") && args[1].equalsIgnoreCase("delete")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            for (AreaManager.MusicArea area : plugin.getAreaManager().getAreas()) {
                if (area.name().toLowerCase(Locale.ROOT).startsWith(input)) {
                    completions.add(area.name());
                }
            }
            return completions;
        }

        return List.of();
    }
}
