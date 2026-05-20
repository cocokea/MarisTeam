package com.maris7.team.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import com.maris7.team.MarisTeam;
import com.maris7.team.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SignInputService {
    private static final int SIGN_LINE_COUNT = 4;
    private static final WrappedBlockState FAKE_SIGN_STATE = WrappedBlockState.getByString("minecraft:oak_sign[rotation=0,waterlogged=false]");

    private final MarisTeam plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SignInputService(MarisTeam plugin) {
        this.plugin = plugin;
    }

    public boolean isPacketEventsAvailable() {
        return PacketEvents.getAPI() != null;
    }

    public void openSearch(Player player, Consumer<String> callback) {
        open(player, configuredLines("sign.search", new String[]{"^^^^^^^^^^^^", "Search", "", ""}), callback);
    }

    public boolean hasSession(UUID uniqueId) {
        return sessions.containsKey(uniqueId);
    }

    public void handleSignResponse(Player player, Vector3i position, String[] lines) {
        if (player == null || position == null) {
            return;
        }
        plugin.scheduler().entity(player, () -> {
            Session session = sessions.get(player.getUniqueId());
            if (session == null || !session.matches(position)) {
                return;
            }
            sessions.remove(player.getUniqueId());
            restore(player, session);
            session.callback().accept(extractValue(lines, session.promptLines()));
        });
    }

    public void clear(UUID uniqueId) {
        Session removed = sessions.remove(uniqueId);
        if (removed == null) {
            return;
        }
        runPlayer(uniqueId, player -> restore(player, removed));
    }

    public void clearAll() {
        for (UUID uniqueId : Set.copyOf(sessions.keySet())) {
            clear(uniqueId);
        }
    }

    private void open(Player player, String[] lines, Consumer<String> callback) {
        clear(player.getUniqueId());
        if (!isPacketEventsAvailable()) {
            plugin.getLogger().warning("PacketEvents is not available; cannot open sign input for " + player.getName());
            return;
        }
        plugin.scheduler().entity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location location = findTemporaryLocation(player);
            Block block = location.getBlock();
            Session session = new Session(location.clone(), block.getBlockData().clone(), normalizedPromptLines(lines), callback);
            sessions.put(player.getUniqueId(), session);
            sendFakeSign(player, session, lines);
        });
    }

    private void sendFakeSign(Player player, Session session, String[] lines) {
        Vector3i position = session.position();
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerBlockChange(position, FAKE_SIGN_STATE));
        player.sendSignChange(session.location(), colorLines(lines));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerOpenSignEditor(position, true));
    }

    private void restore(Player player, Session session) {
        if (!player.isOnline()) {
            return;
        }
        player.sendBlockChange(session.location(), session.originalData());
    }

    private String extractValue(String[] lines, Set<String> ignoredLines) {
        if (lines == null) {
            return "";
        }
        for (String line : lines) {
            String normalized = normalize(line);
            if (!normalized.isEmpty() && !ignoredLines.contains(normalized)) {
                return line == null ? "" : line.trim();
            }
        }
        return "";
    }

    private Set<String> normalizedPromptLines(String[] lines) {
        Set<String> ignored = new LinkedHashSet<>();
        for (String line : lines) {
            String normalized = normalize(line);
            if (!normalized.isEmpty()) {
                ignored.add(normalized);
            }
        }
        ignored.addAll(Arrays.stream(configuredLines("sign.ignored-lines", new String[]{"^^^^^^^^^^^^", "Search", "", ""}))
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .toList());
        return ignored;
    }

    private String[] configuredLines(String path, String[] fallback) {
        java.util.List<String> configured = plugin.configs().messages().getStringList(path);
        String[] lines = fallback.clone();
        for (int index = 0; index < Math.min(SIGN_LINE_COUNT, configured.size()); index++) {
            lines[index] = configured.get(index);
        }
        return lines;
    }

    private String normalize(String line) {
        return ChatColor.stripColor(Text.color(line == null ? "" : line)).trim();
    }

    private Location findTemporaryLocation(Player player) {
        Location base = player.getLocation();
        int y = Math.max(player.getWorld().getMinHeight() + 1, base.getBlockY() + 5);
        return new Location(player.getWorld(), base.getBlockX(), y, base.getBlockZ());
    }

    private String[] colorLines(String[] lines) {
        String[] colored = new String[SIGN_LINE_COUNT];
        for (int index = 0; index < SIGN_LINE_COUNT; index++) {
            colored[index] = Text.color(index < lines.length && lines[index] != null ? lines[index] : "");
        }
        return colored;
    }

    private void runPlayer(UUID uniqueId, Consumer<Player> action) {
        plugin.scheduler().global(() -> {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player == null || !player.isOnline()) {
                return;
            }
            plugin.scheduler().entity(player, () -> {
                if (player.isOnline()) {
                    action.accept(player);
                }
            });
        });
    }

    public record Session(Location location, org.bukkit.block.data.BlockData originalData, Set<String> promptLines, Consumer<String> callback) {
        private Vector3i position() {
            return new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private boolean matches(Vector3i other) {
            return other.getX() == location.getBlockX()
                    && other.getY() == location.getBlockY()
                    && other.getZ() == location.getBlockZ();
        }
    }
}
