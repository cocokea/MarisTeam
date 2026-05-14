package com.maris7.team.listener;

import com.maris7.team.MarisTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

public final class PaperChatBridge {
    private PaperChatBridge() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean register(MarisTeam plugin, ChatListener listener) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("io.papermc.paper.event.player.AsyncChatEvent").asSubclass(Event.class);
            Method playerMethod = eventClass.getMethod("getPlayer");
            Method messageMethod = eventClass.getMethod("message");
            Method cancelledMethod = eventClass.getMethod("setCancelled", boolean.class);
            Method viewersMethod = eventClass.getMethod("viewers");
            Method serializeMethod = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer").getMethod("plainText");
            Object serializer = serializeMethod.invoke(null);
            Method serialize = serializer.getClass().getMethod("serialize", Class.forName("net.kyori.adventure.text.Component"));
            EventExecutor executor = (ignored, event) -> {
                try {
                    Player player = (Player) playerMethod.invoke(event);
                    if (!plugin.teams().cachedChatToggle(player.getUniqueId())) return;
                    cancelledMethod.invoke(event, true);
                    Object viewers = viewersMethod.invoke(event);
                    if (viewers instanceof java.util.Collection<?> collection) collection.clear();
                    String message = (String) serialize.invoke(serializer, messageMethod.invoke(event));
                    plugin.scheduler().entity(player, () -> listener.handleTeamChat(player, message));
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Could not handle Paper chat bridge event: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent((Class) eventClass, new Listener() {}, EventPriority.LOWEST, executor, plugin, false);
            plugin.getLogger().info("Paper AsyncChatEvent bridge enabled for team chat.");
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().info("Paper AsyncChatEvent bridge is not available; using Bukkit chat listener only.");
            return false;
        }
    }
}
