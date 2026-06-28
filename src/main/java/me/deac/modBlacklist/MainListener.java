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
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayer(player), 20L);
    }

    public void checkPlayer(Player player) {
        List<String> blacklist = plugin.getBlacklist(false);
        Location loc = player.getLocation().getBlock().getLocation();
        BlockData airData = Material.AIR.createBlockData();
        UUID uuid = player.getUniqueId();

        for (int i = 0; i < blacklist.size(); i += 4) {
            CheckRecord record = checkCache.getIfPresent(uuid);
            if (record != null && record.index == -1) return; // Checks if player has been kicked

            // Get list of 4 values
            List<String> rawChunk = blacklist.subList(i, Math.min(i + 4, blacklist.size()));
            plugin.getLogger().info(rawChunk.toString());

            // Append empty strings if not full 4
            List<String> chunk = new ArrayList<>(rawChunk);
            while (chunk.size() < 4) chunk.add("");
            plugin.getLogger().info(chunk.toString());

            // Update cache and make list of components
            checkCache.put(uuid, new CheckRecord(i, chunk));
            List<@NotNull TranslatableComponent> lines = chunk.stream().map(Component::translatable).toList();

            // Open+close virtual sign
            player.sendBlockChange(loc, Material.OAK_SIGN.createBlockData());
            plugin.getLogger().info("showing sign");
            player.sendSignChange(loc, lines);
            player.openVirtualSign(Position.fine(loc.getX(), loc.getY(), loc.getZ()), Side.FRONT);
            plugin.getLogger().info("removing sign");
            player.sendBlockChange(loc, airData);
        }
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

        UUID uuid = player.getUniqueId();

        CheckRecord fromCache = checkCache.getIfPresent(uuid);
        List<String> cacheValues = fromCache == null ? new ArrayList<>() : fromCache.values;

        if (fromCache != null && !values.equals(cacheValues)) {
            for (int i=0; i<Math.min(values.size(), cacheValues.size()); i++) {
                if (!values.get(i).equals(cacheValues.get(i))) {
                    String name = plugin.getBlacklist(true).get(fromCache.index + i);

                    plugin.getLogger().info("Player "+player.getName()+" ( "+uuid+" ) has blacklisted mod " + name);
                    checkCache.put(uuid, new CheckRecord(-1, null));
                    player.kick(Component.text("Blacklisted mod "+name+" installed"), PlayerKickEvent.Cause.PLUGIN);

                    break;
                }
            }
        }

        event.setCancelled(true);
    }
}
