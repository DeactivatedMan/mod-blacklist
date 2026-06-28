package me.deac.modBlacklist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public final class ModBlacklist extends JavaPlugin implements Listener {
    private static final String megalistSource = "https://raw.githubusercontent.com/DeactivatedMan/mod-blacklist/refs/heads/master/src/main/resources/megalist.yml";
    private static final int perPage = 16;

    private FileConfiguration megalistConfig;
    private final Map<String, String> blacklistCache = new LinkedHashMap<>();

    private File blacklistFile;
    private FileConfiguration blacklistConfig;

    private MainListener listener;

    @Override
    public void onEnable() {
        downloadMegalist();

        // Load blacklist file
        //File blacklistFile = new File(getDataFolder(), "blacklist.yml");
        //if (!blacklistFile.exists()) saveResource("blacklist.yml", false);
        //YamlConfiguration config = YamlConfiguration.loadConfiguration(blacklistFile);

        loadAllData();

        // Register command
        PluginCommand command = getCommand("mb");
        if (command != null) {
            CommandHandler handler = new CommandHandler(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        // Register mainlistener events
        listener = new MainListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void loadAllData() {
        // Load blacklist once into memory
        this.blacklistFile = new File(getDataFolder(), "blacklist.yml");
        if (!blacklistFile.exists()) saveResource("blacklist.yml", false);
        this.blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);

        // Populate cache
        blacklistCache.clear();
        for (String key : blacklistConfig.getKeys(false)) blacklistCache.put(key, blacklistConfig.getString(key));

        // Load megalist once into memory
        File megalistFile = new File(getDataFolder(), "megalist.yml");
        this.megalistConfig = YamlConfiguration.loadConfiguration(megalistFile);
    }

    public List<String> getBlacklist(boolean getKeys) { return new ArrayList<>( getKeys ? blacklistCache.keySet() : blacklistCache.values() ); }
    private void saveBlacklistFile() {
        try {
            blacklistConfig.save(blacklistFile);
        } catch (IOException e) {
            getLogger().warning("Could not save blacklist.yml: " + e.getMessage());
        }
    }

    public void checkPlayer(Player player) {
        listener.checkPlayer(player, 0);
    }
    public void updateMegalist(CommandSender sender) {
        downloadMegalist();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            File megalistFile = new File(getDataFolder(), "megalist.yml");
            this.megalistConfig = YamlConfiguration.loadConfiguration(megalistFile);
            sender.sendMessage(Component.text("Updated megalist!").color(NamedTextColor.DARK_GREEN));
        }, 20L);
    }

    // Downloads megalist from GitHub
    private void downloadMegalist() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        File targetFile = new File(getDataFolder(), "megalist.yml");
        //if (!targetFile.exists()) saveResource("megalist.yml", false);
        Path targetPath = targetFile.toPath();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(megalistSource))
                .header("User-Agent", "MinecraftPlugin")
                .GET()
                .build();

        getLogger().info("Downloading megalist from github");

        // Fire-and-forget asynchronous request
        client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(
                targetPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )).thenAccept(response -> {
            if (response.statusCode() == 200) getLogger().info("Successfully updated megalist from GitHub!");

            else getLogger().warning("Failed to download. HTTP Status: " + response.statusCode());
        }).exceptionally(ex -> {
            getLogger().severe("Error updating file: " + ex.getMessage());
            return null;
        });
    }

    // Search functions
    public List<String> searchKeys(boolean searchMega, String search, int page) {
        return searchKeys(
                searchMega ? megalistConfig.getKeys(false) : blacklistCache.keySet(),
                search, page
        );
    }
    public List<String> searchKeys(Collection<String> source, String search, int page) {
        getLogger().info("Search function called");
        if (source == null || source.isEmpty()) return Collections.emptyList();
        getLogger().info("Collection not empty!");

        List<String> matches = new ArrayList<>();

        boolean all = search.equals("*");
        int startAfter = page * perPage;
        int found = 0;

        for (String key : source) {
            if (all || key.toLowerCase().contains(search)) { // If match
                if (found > startAfter) matches.add(key);
                if (matches.size() == perPage) break; // When page is full

                found++;
            }
        }

        return matches;
    }

    // Apply functions
    public boolean apply(String search) {
        // Checking memory map is O(1) fast
        if (blacklistCache.containsKey(search)) return false;
        if (megalistConfig == null || !megalistConfig.contains(search)) return false;

        String value = megalistConfig.getString(search);

        // Apply to in-memory cache
        blacklistCache.put(search, value);
        blacklistConfig.set(search, value);

        // Save to blacklist.yml
        saveBlacklistFile();
        return true;
    }
    public int applyAny(String search) {
        if (megalistConfig == null) return 0;

        if (search.equals("*")) { // Just set blacklist to megalist
            blacklistConfig = megalistConfig;
            blacklistCache.clear();
            for (String key : blacklistConfig.getKeys(false)) blacklistCache.put(key, blacklistConfig.getString(key));

            // Save to blacklist.yml
            saveBlacklistFile();
            return -1;
        }
        int amount = 0;

        // Loop through ALL keys in the megalist
        for (String key : megalistConfig.getKeys(false)) {
            if (key.toLowerCase().contains(search)) { // If match
                if (blacklistCache.containsKey(key)) continue; // If already in blacklist

                String value = megalistConfig.getString(key);

                // Apply
                blacklistCache.put(key, value);
                blacklistConfig.set(key, value);

                amount++;
            }
        }

        // Save to blacklist.yml
        if (amount > 0) saveBlacklistFile();
        return amount;
    }

    // Remove functions
    public boolean remove(String search) {
        if (blacklistCache.isEmpty() || !blacklistCache.containsKey(search)) return false;

        // Remove
        blacklistCache.remove(search);
        blacklistConfig.set(search, null);

        // Save to blacklist.yml
        saveBlacklistFile();
        return true;
    }
    public int removeAny(String search) {

        if (search.equals("*")) { // Entirely wipe blacklist
            blacklistCache.clear();
            blacklistConfig = new YamlConfiguration();

            // Save to blacklist.yml
            saveBlacklistFile();
            return -1;
        }

        int amount = 0;

        // Loop through ALL keys in the blacklist
        for (String key : blacklistCache.keySet()) {
            if (key.toLowerCase().contains(search)) {
                // Remove
                blacklistCache.remove(key);
                blacklistConfig.set(key, null);

                amount++;
            }
        }

        // Save to blacklist.yml
        saveBlacklistFile();
        return amount;
    }
}
