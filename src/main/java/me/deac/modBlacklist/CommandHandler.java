package me.deac.modBlacklist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final ModBlacklist plugin;

    public CommandHandler(ModBlacklist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (args.length > 0 && args[0].equals("update")) {
            handleUpdate(sender);
            return true;
        }
        else if (args.length > 1) {
            switch (args[0]) {
                case "apply" -> {
                    handleApply(sender, args[1]);
                    return true;
                }
                case "applyany" -> {
                    handleApplyAny(sender, args[1]);
                    return true;
                }

                case "remove" -> {
                    handleRemove(sender, args[1]);
                    return true;
                }
                case "removeany" -> {
                    handleRemoveAny(sender, args[1]);
                    return true;
                }

                case "check" -> {
                    handleCheck(sender, args[1]);
                    return true;
                }
                case "search" -> { if (args.length > 2) {
                    plugin.getLogger().info("Args are: " + Arrays.toString(args));
                    handleSearchFor(sender, args[1].equalsIgnoreCase("mega"), args[2], args.length>3 ? tryConvertToInt(args[3])-2 : -1); // I have no idea why it starts at -1
                    return true;
                }}
            }
        }

        handleShowCommands(sender);
        return true;
    }

    /*
    "search mega/local TERM 0-10",
    "apply MOD_ID",
    "remove MOD_ID",
    "check USERNAME",
     */

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        List<String> commands = List.of(
                "apply", "applyany",
                "remove", "removeany",
                "check", "update", "search"
        );

        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], commands, new ArrayList<>());

        switch (args[0]) {
            case "search" -> {
                if (args.length == 2) return List.of("mega", "local");
                else if (args.length == 3) return List.of("search_term_here");
                else if (args.length == 4) return List.of("0","1","2","3","4","5","6","7","8","9","10");
            }
            case "check" -> {
                List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                return StringUtil.copyPartialMatches(args[1], players, new ArrayList<>());
            }
            default -> {return List.of("mod_id_here");}
        }

        return commands;
    }

    private void handleUpdate(CommandSender sender) {
        sender.sendMessage(Component.text("Downloading megalist from GitHub...").color(NamedTextColor.DARK_AQUA));
        plugin.updateMegalist(sender);
    }

    private void handleCheck(CommandSender sender, String username) {
        sender.sendMessage( Component.text( "Attempting to check "+username ).color(NamedTextColor.DARK_AQUA) );
        Player player = Bukkit.getPlayer(username);
        if (player != null) plugin.checkPlayer(player);
    }

    private void handleApply(CommandSender sender, String search) {
        boolean done = plugin.apply(search.toLowerCase().replace(' ', '_'));
        sender.sendMessage(Component.text( (done ? "Successfully banned " : "Could not ban ")+search ).color(done ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_RED));
    }
    private void handleApplyAny(CommandSender sender, String search) {
        int changed = plugin.applyAny(search);
        boolean done = changed!=0;

        sender.sendMessage(Component.text( (done ? "Successfully applied " : "Could not apply any matching "+search+", found ")+changed ).color(done ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_RED));
    }

    private void handleRemove(CommandSender sender, String search) {
        boolean done = plugin.remove(search.toLowerCase().replace(' ', '_'));
        sender.sendMessage(Component.text( (done ? "Successfully unbanned " : "Could not unban ")+search ).color(done ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_RED));
    }
    private void handleRemoveAny(CommandSender sender, String search) {
        int changed = plugin.removeAny(search);
        boolean done = changed!=0;

        sender.sendMessage(Component.text( (done ? "Successfully removed " : "Could not removed any matching "+search+", found ")+changed ).color(done ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_RED));
    }


    private void handleSearchFor(CommandSender sender, boolean searchMega, String search, int page) {
        plugin.getLogger().info(searchMega + ", " + search + ", " + page);
        //List<String> found = plugin.searchKeys(search.toLowerCase().replace(' ', '_'),page);

        List<String> found = plugin.searchKeys(searchMega, search.toLowerCase().replace(' ', '_'), page);

        //found.forEach(value -> {sender.sendMessage(Component.text(" "+value));});
        //sender.sendMessage(Component.text(String.join("\n", found)).color(NamedTextColor.GOLD));

        page += 2;

        sender.sendMessage(Component.text("Page "+page+" of search").color(NamedTextColor.DARK_AQUA));
        found.forEach(value -> sender.sendMessage(
                Component.text(" - ").color(NamedTextColor.GRAY)
                        .append(Component.text(value).color(NamedTextColor.GOLD)
                                .hoverEvent(HoverEvent.showText(Component.text("Copy mod ID")))
                                .clickEvent(ClickEvent.copyToClipboard(value))
                        )
                )
        );
        sender.sendMessage(Component.text("Page "+page+" of search").color(NamedTextColor.DARK_AQUA));
    }
    public int tryConvertToInt(String input) {
        try { return Integer.parseInt(input);
        } catch (NumberFormatException e) { return 1; }
    }

    private void handleShowCommands(CommandSender sender) {
        sender.sendMessage(Component.text("Commands for Mod Blacklist:").color(NamedTextColor.DARK_AQUA));
        Map<String, String> display = Map.of(
                "search mega/local TERM 1-10", "Searches either the megalist or local blacklist for the term you input, with paging to not fill your chat with messages",

                "apply MOD_ID", "Adds the referenced mod to the blacklist",
                "applyany MOD_ID", "Adds any mods with a similar name to the blacklist",

                "remove MOD_ID", "Removes the referenced mod from the blacklist",
                "removeany MOD_ID", "Removes any mods with a similar name from the blacklist",

                "check USERNAME", "Reverifies that a player doesn't have any banned mods",
                "update", "Updates the megalist to newest version from GitHub"
        );
        display.forEach((key, value) -> sender.sendMessage(
                Component.text(" - ").color(NamedTextColor.GRAY)
                        .append(Component.text(key).color(NamedTextColor.GOLD)
                                .hoverEvent(HoverEvent.showText(Component.text(value)))
                                .clickEvent(ClickEvent.suggestCommand("/mb "+key.split(" ")[0]+" "))
                        )
                )
        );
    }
}
