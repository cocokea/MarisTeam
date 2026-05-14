package com.maris7.team.service;

import com.maris7.team.MarisTeam;
import com.maris7.team.util.Msg;
import com.maris7.team.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HomeTeleportService implements Listener {
    private final MarisTeam plugin;
    private final Msg msg;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public HomeTeleportService(MarisTeam plugin) {
        this.plugin = plugin;
        this.msg = new Msg(plugin);
    }

    public void start(Player player, Location home) {
        Session session = new Session(player.getLocation().clone(), home, System.nanoTime());
        sessions.put(player.getUniqueId(), session);
        tick(player, session, 5);
    }

    private void tick(Player player, Session session, int seconds) {
        plugin.scheduler().entity(player, () -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) return;
            if (seconds <= 0) {
                sessions.remove(player.getUniqueId(), session);
                plugin.scheduler().teleport(player, session.home());
                msg.both(player, "home-teleport-done", null);
                plugin.scheduler().entityDelayed(player, () -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    if (session.home().getWorld() != null) session.home().getWorld().playSound(session.home(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }, 1L);
                return;
            }
            actionbar(player, plugin.configs().messages().getString("home-teleport-countdown", "&7Teleporting in &#FFD900%seconds%s").replace("%seconds%", String.valueOf(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);
            plugin.scheduler().entityDelayed(player, () -> tick(player, session, seconds - 1), 20L);
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || event.getTo() == null) return;
        Location from = session.start();
        Location to = event.getTo();
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            cancel(event.getPlayer(), session);
            return;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        if ((dx * dx) + (dy * dy) + (dz * dz) >= 1.0D) cancel(event.getPlayer(), session);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null) cancel(player, session);
        }
    }

    private void cancel(Player player, Session session) {
        if (!sessions.remove(player.getUniqueId(), session)) return;
        msg.both(player, "home-teleport-cancelled", "no");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void actionbar(Player player, String raw) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(Text.color(raw));
        player.sendActionBar(component);
    }

    private record Session(Location start, Location home, long id) {}
}
