package com.maris7.team.listener;

import com.maris7.team.MarisTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class LegacyChatListener implements Listener {
    private final MarisTeam p;
    private final ChatListener delegate;

    public LegacyChatListener(MarisTeam p, ChatListener delegate) {
        this.p = p;
        this.delegate = delegate;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncPlayerChatEvent e) {
        Player sender = e.getPlayer();
        if (!p.teams().cachedChatToggle(sender.getUniqueId())) return;
        e.setCancelled(true);
        delegate.handleTeamChat(sender, e.getMessage());
    }
}
