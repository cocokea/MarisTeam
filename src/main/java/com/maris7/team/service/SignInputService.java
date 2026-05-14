package com.maris7.team.service;

import com.maris7.team.MarisTeam;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SignInputService {
    private final MarisTeam plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SignInputService(MarisTeam plugin) {
        this.plugin = plugin;
    }

    public void openSearch(Player player, Consumer<String> callback) {
        open(player, new String[]{"^^^^^^^^^^^^", "Search", "", ""}, callback);
    }

    public boolean matches(UUID uniqueId, Location location) {
        Session session = sessions.get(uniqueId);
        if (session == null || location == null) return false;
        return session.location().getWorld() != null
                && session.location().getWorld().equals(location.getWorld())
                && session.location().getBlockX() == location.getBlockX()
                && session.location().getBlockY() == location.getBlockY()
                && session.location().getBlockZ() == location.getBlockZ();
    }

    public void complete(Player player, String[] lines) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        restore(session);

        Set<String> ignored = getIgnoredLines();
        String value = "";
        if (lines != null) {
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !ignored.contains(trimmed)) {
                    value = trimmed;
                    break;
                }
            }
        }
        session.callback().accept(value);
    }

    public void clear(UUID uniqueId) {
        Session removed = sessions.remove(uniqueId);
        if (removed != null) restore(removed);
    }

    public void clearAll() {
        for (UUID uniqueId : Set.copyOf(sessions.keySet())) clear(uniqueId);
    }

    private void open(Player player, String[] lines, Consumer<String> callback) {
        Block block = player.getEyeLocation().clone().add(0.0D, 1.0D, 0.0D).getBlock();
        Location location = block.getLocation();
        Material originalType = block.getType();
        org.bukkit.block.data.BlockData originalData = block.getBlockData().clone();

        plugin.scheduler().region(location, () -> {
            World world = location.getWorld();
            if (world == null) {
                return;
            }
            Block target = world.getBlockAt(location);
            target.setType(Material.OAK_SIGN, false);
            if (!(target.getState() instanceof Sign sign)) {
                return;
            }

            SignSide side = sign.getSide(Side.FRONT);
            for (int index = 0; index < 4; index++) {
                side.setLine(index, index < lines.length && lines[index] != null ? lines[index] : "");
            }
            trySetAllowedEditor(sign, player.getUniqueId());
            sign.update(true, false);
            sessions.put(player.getUniqueId(), new Session(location, originalType, originalData, callback));

            final String[] clientLines = lines.clone();
            plugin.scheduler().entity(player, () -> {
                if (!player.isOnline()) {
                    clear(player.getUniqueId());
                    return;
                }
                player.sendSignChange(location, clientLines);
                plugin.scheduler().entityDelayed(player, () -> {
                    if (!player.isOnline()) {
                        clear(player.getUniqueId());
                        return;
                    }
                    World w = location.getWorld();
                    if (w == null) {
                        return;
                    }
                    Block at = w.getBlockAt(location);
                    if (!(at.getState() instanceof Sign openSign)) {
                        return;
                    }
                    player.openSign(openSign, Side.FRONT);
                }, 2L);
            });
        });
    }

    private Set<String> getIgnoredLines() {
        Set<String> ignored = new LinkedHashSet<>();
        ignored.addAll(Arrays.asList("^^^^^^^^^^^^", "Search"));
        ignored.removeIf(line -> line == null || line.isBlank());
        return ignored;
    }

    private void restore(Session session) {
        World world = session.location().getWorld();
        if (world == null) return;
        plugin.scheduler().region(session.location(), () -> {
            Block block = world.getBlockAt(session.location());
            block.setType(session.originalType(), false);
            try {
                block.setBlockData(session.originalData().clone(), false);
            } catch (Throwable ignored) {
            }
        });
    }

    private void trySetAllowedEditor(Sign sign, UUID uuid) {
        try {
            Method method = sign.getClass().getMethod("setAllowedEditorUniqueId", UUID.class);
            method.invoke(sign, uuid);
        } catch (Throwable ignored) {
        }
    }

    public record Session(Location location, Material originalType, org.bukkit.block.data.BlockData originalData, Consumer<String> callback) {}
}
