package me.deac.modBlacklist;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainListener implements Listener {

    private Cache<UUID, CheckRecord> checkCache;
    private record CheckRecord(int index, List<String> values) {}

    private final ModBlacklist plugin;

    public MainListener(ModBlacklist plugin) {
        checkCache = CacheBuilder.newBuilder()
                .expireAfterAccess(1L, TimeUnit.SECONDS)
                .build();
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("Showing player a sign in 20 ticks");
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayer(player, 0), 20L);
    }

    public void checkPlayer(Player player, int index) {
        List<String> blacklist = plugin.getBlacklist(false);
        if (blacklist.size() <= index) {
            plugin.getLogger().info("Player " + player.getName() + " ("+player.getUniqueId()+") has been verified clear!");
            return;
        }

        Location loc = player.getLocation().getBlock().getLocation();
        UUID uuid = player.getUniqueId();

        //for (int i = 0; i < blacklist.size(); i += 4) {
            //CheckRecord record = checkCache.getIfPresent(uuid);
            //if (record != null && record.index == -1) return; // Checks if player has been kicked

        // Get list of 4 values
        List<String> rawChunk = blacklist.subList(index, Math.min(index + 4, blacklist.size()));
        if (rawChunk.isEmpty()) {
            plugin.getLogger().info("Player " + player.getName() + " ("+player.getUniqueId()+") has been verified clear!");
            return;
        }
        plugin.getLogger().info("Raw chunk data: " + rawChunk);

        // Append empty strings if not full 4
        List<String> chunk = new ArrayList<>(rawChunk);
        while (chunk.size() < 4) chunk.add("");
        plugin.getLogger().info("Standard chunk data: " + chunk);

        // Update cache and make list of components
        checkCache.put(uuid, new CheckRecord(index, chunk));
        List<@NotNull TranslatableComponent> lines = chunk.stream().map(Component::translatable).toList();

        // Open sign with translation data
        player.sendBlockChange(loc, Material.OAK_SIGN.createBlockData());
        plugin.getLogger().info("showing sign");
        player.sendSignChange(loc, lines);
        player.openVirtualSign(Position.fine(loc.getX(), loc.getY(), loc.getZ()), Side.FRONT);
        plugin.getLogger().info("removing sign");
        //}
        // Resync block
        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }

    @EventHandler
    public void onSignSubmit(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("close event!");

        Location loc = player.getLocation().getBlock().getLocation();
        player.sendBlockChange(loc, loc.getBlock().getBlockData());

        List<String> values = event.lines().stream()
                .map(component -> {
                    // Check if the component is actually a translation component
                    if (component instanceof TranslatableComponent translatable) {
                        return translatable.key();
                    }
                    // Fallback for normal text components if any slipped in
                    return PlainTextComponentSerializer.plainText().serialize(component);
                })
                .toList();
        plugin.getLogger().info("Sign returned with data: " + values);

        UUID uuid = player.getUniqueId();

        CheckRecord fromCache = checkCache.getIfPresent(uuid);
        int cacheIndex = fromCache == null ? -1 : fromCache.index;
        List<String> cacheValues = fromCache == null ? new ArrayList<>() : fromCache.values;

        if (fromCache != null && !values.equals(cacheValues)) {
            for (int i=0; i<Math.min(values.size(), cacheValues.size()); i++) {
                if (!values.get(i).equals(cacheValues.get(i))) {
                    String name = plugin.getBlacklist(true).get(cacheIndex + i);

                    plugin.getLogger().info("Player "+player.getName()+" ( "+uuid+" ) has blacklisted mod " + name);
                    event.setCancelled(true);
                    player.kick(Component.text("Blacklisted mod "+name+" installed"), PlayerKickEvent.Cause.PLUGIN);
                    return;
                }
            }
        }

        event.setCancelled(true);
        if (cacheIndex != -1) checkPlayer(player, cacheIndex+4);
    }
}
