package com.maris7.team.listener;

import com.maris7.team.MarisTeam;
import com.maris7.team.model.Member;
import com.maris7.team.model.Team;
import com.maris7.team.util.Msg;
import com.maris7.team.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public final class ChatListener implements Listener {
    private final MarisTeam p;
    private final Msg msg;

    public ChatListener(MarisTeam p) {
        this.p = p;
        this.msg = new Msg(p);
    }

    public void handleTeamChat(Player sender, String message) {
        p.scheduler().async(() -> {
            Member m = p.teams().member(sender.getUniqueId());
            if (m == null || !m.chatToggle) return;
            if (!m.teamChat) {
                p.scheduler().entity(sender, () -> msg.both(sender, "team-chat-no-permission", "no"));
                return;
            }
            Team t = p.teams().teamOf(sender.getUniqueId());
            if (t == null) return;
            List<Member> recipients = p.teams().members(t.id);
            String f = p.configs().messages().getString("team-chat-format", "&#FFF800[TEAM] %player%: &f%message%")
                    .replace("%player%", sender.getName())
                    .replace("%message%", message);
            String colored = Text.color(f);
            for (Member x : recipients) {
                Player r = Bukkit.getPlayer(x.uuid);
                if (r != null) p.scheduler().entity(r, () -> r.sendMessage(colored));
            }
        });
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        p.scheduler().async(() -> p.teams().refreshCache(e.getPlayer().getUniqueId()));
        for (String m : p.database().takePending(e.getPlayer().getUniqueId())) e.getPlayer().sendMessage(Text.color(m));
    }
}
