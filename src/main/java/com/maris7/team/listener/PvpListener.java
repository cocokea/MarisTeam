package com.maris7.team.listener;

import com.maris7.team.MarisTeam;
import com.maris7.team.model.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class PvpListener implements Listener {
    private final MarisTeam p;

    public PvpListener(MarisTeam p) {
        this.p = p;
    }

    @EventHandler
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getDamager() instanceof Player attacker)) return;
        Integer victimTeam = p.teams().cachedTeamId(victim.getUniqueId());
        Integer attackerTeam = p.teams().cachedTeamId(attacker.getUniqueId());
        if (victimTeam == null || attackerTeam == null) {
            refresh(victim, attacker);
            return;
        }
        if (!victimTeam.equals(attackerTeam)) return;
        Boolean pvp = p.teams().cachedTeamPvp(victimTeam);
        if (pvp == null) {
            refresh(victim, attacker);
            return;
        }
        if (!pvp) e.setCancelled(true);
    }

    private void refresh(Player victim, Player attacker) {
        p.scheduler().async(() -> {
            Team vt = p.teams().teamOf(victim.getUniqueId());
            Team at = p.teams().teamOf(attacker.getUniqueId());
        });
    }
}
