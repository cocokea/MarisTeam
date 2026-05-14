package com.maris7.team.listener;

import com.maris7.team.service.SignInputService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final SignInputService signInputService;

    public PlayerListener(SignInputService signInputService) {
        this.signInputService = signInputService;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        signInputService.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!signInputService.matches(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) return;
        event.setCancelled(true);
        signInputService.complete(event.getPlayer(), event.getLines());
    }
}
