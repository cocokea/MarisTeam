package com.maris7.team.command;

import com.maris7.team.MarisTeam;
import com.maris7.team.gui.TeamGui;
import com.maris7.team.model.Member;
import com.maris7.team.model.Team;
import com.maris7.team.service.TeamService;
import com.maris7.team.util.Msg;
import com.maris7.team.util.Text;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TeamCommand implements CommandExecutor, TabCompleter {
    private final MarisTeam p;
    private final Msg msg;

    public TeamCommand(MarisTeam p) {
        this.p = p;
        this.msg = new Msg(p);
    }

    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player pl)) return true;

        Team t = p.teams().teamOf(pl.getUniqueId());
        if (a.length == 0) {
            if (t == null) msg.both(pl, "no-team-create", "no");
            else TeamGui.openTeam(p, pl, 0);
            return true;
        }

        String sub = a[0].toLowerCase();
        if (t == null) {
            if (sub.equals("create") && a.length >= 2) {
                if (!p.teams().validName(a[1])) {
                    msg.both(pl, "invalid-name", "no");
                    return true;
                }
                if (p.teams().create(pl, a[1])) msg.both(pl, "team-created", "success");
                else msg.both(pl, "team-name-taken", "no");
                return true;
            }
            if (sub.equals("join") && a.length >= 2) {
                if (p.teams().join(pl, a[1])) {
                    msg.both(pl, "joined", "success");
                    Team nt = p.teams().teamOf(pl.getUniqueId());
                    if (nt != null) for (Member m : p.teams().members(nt.id)) {
                        Player mp = Bukkit.getPlayer(m.uuid);
                        if (mp != null && !mp.equals(pl)) msg.both(mp, "player-joined-broadcast", null, Map.of("%player%", pl.getName()));
                    }
                } else msg.both(pl, "not-invited", "no");
                return true;
            }
            msg.both(pl, "no-team-create", "no");
            return true;
        }

        if (sub.equals("create")) {
            msg.both(pl, "already-team", "no");
            return true;
        }

        Member me = p.teams().member(pl.getUniqueId());
        switch (sub) {
            case "chat" -> {
                if (!me.teamChat) {
                    msg.both(pl, "team-chat-no-permission", "no");
                    break;
                }
                if (a.length >= 2) {
                    String raw = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                    String f = p.configs().messages().getString("team-chat-format").replace("%player%", pl.getName()).replace("%message%", raw);
                    for (Member m : p.teams().members(t.id)) {
                        Player r = Bukkit.getPlayer(m.uuid);
                        if (r != null) r.sendMessage(Text.color(f));
                    }
                } else {
                    boolean enabled = p.teams().toggleTeamChat(pl.getUniqueId());
                    msg.both(pl, enabled ? "team-chat-enabled" : "team-chat-disabled", null);
                }
            }
            case "home" -> startHomeTeleport(pl, t);
            case "sethome" -> {
                if (!t.leader.equals(pl.getUniqueId()) && !me.editHome) {
                    msg.both(pl, "no-permission", "no");
                    break;
                }
                if (isBlacklistedWorld(pl.getWorld())) {
                    msg.both(pl, "home-blacklist", "no");
                    break;
                }
                p.teams().setHome(t, pl.getLocation());
                msg.both(pl, "home-set", null);
            }
            case "delhome" -> {
                if (!t.leader.equals(pl.getUniqueId()) && !me.editHome) {
                    msg.both(pl, "no-permission", "no");
                    break;
                }
                p.teams().delHome(t);
                msg.both(pl, "home-deleted", null);
            }
            case "invite" -> {
                if (!t.leader.equals(pl.getUniqueId()) && !me.manage) {
                    msg.both(pl, "no-permission", "no");
                    break;
                }
                if (a.length < 2) break;
                if (p.teams().members(t.id).size() >= TeamService.MAX_MEMBERS) {
                    msg.both(pl, "team-full", "no");
                    break;
                }
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null) {
                    msg.both(pl, "user-offline", "no");
                    break;
                }
                if (p.teams().teamOf(target.getUniqueId()) != null) {
                    msg.both(pl, "player-has-team", null);
                    break;
                }
                if (!p.teams().teamInvitesEnabled(target.getUniqueId())) {
                    msg.both(pl, "team-invite-disabled", "no");
                    break;
                }
                p.teams().invite(t.name, target);
                msg.both(pl, "invite-sent", "success", Map.of("%player%", target.getName()));
                BaseComponent[] tc = TextComponent.fromLegacyText(Text.color(p.configs().messages().getString("invite-received").replace("%team%", t.name)));
                for (BaseComponent component : tc) component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team join " + t.name));
                target.spigot().sendMessage(tc);
            }
            case "kick" -> {
                if (!t.leader.equals(pl.getUniqueId()) && !me.manage) {
                    msg.both(pl, "no-permission", "no");
                    break;
                }
                if (a.length < 2) break;
                for (Member m : p.teams().members(t.id)) if (m.name.equalsIgnoreCase(a[1])) {
                    p.teams().kick(m.uuid);
                    msg.both(pl, "kicked", "success", Map.of("%player%", m.name));
                    Player tp = Bukkit.getPlayer(m.uuid);
                    if (tp != null) msg.both(tp, "kicked-target", null, Map.of("%player%", pl.getName()));
                    break;
                }
            }
            case "leave" -> {
                if (t.leader.equals(pl.getUniqueId())) msg.both(pl, "no-permission", "no");
                else TeamGui.openConfirm(p, pl, false);
            }
            case "disband" -> {
                if (!t.leader.equals(pl.getUniqueId())) msg.both(pl, "no-permission", "no");
                else TeamGui.openConfirm(p, pl, true);
            }
            default -> TeamGui.openTeam(p, pl, 0);
        }
        return true;
    }

    private boolean isBlacklistedWorld(World world) {
        if (world == null) return false;
        for (String configured : p.getConfig().getStringList("blacklist-worlds")) {
            if (matchesWorld(world, configured)) return true;
        }
        return false;
    }

    private boolean matchesWorld(World world, String configured) {
        if (configured == null || configured.isBlank()) return false;
        String key = world.getKey().getKey();
        return configured.equalsIgnoreCase(world.getName())
                || configured.equalsIgnoreCase(world.getKey().toString())
                || configured.equalsIgnoreCase(key)
                || configured.equalsIgnoreCase(key.substring(key.lastIndexOf('/') + 1));
    }

    private void startHomeTeleport(Player pl, Team t) {
        if (!t.hasHome) {
            msg.both(pl, "no-home", "no");
            return;
        }
        World w = Bukkit.getWorld(t.world);
        if (w == null) {
            msg.both(pl, "no-home", "no");
            return;
        }
        Location home = new Location(w, t.x, t.y, t.z, t.yaw, t.pitch);
        p.homeTeleport().start(pl, home);
    }

    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player pl)) return List.of();
        if (a.length == 1) {
            if (!p.teams().cachedHasTeam(pl.getUniqueId())) return List.of("create", "join");
            return List.of("chat", "home", "sethome", "delhome", "leave", "invite", "kick", "disband");
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("invite") && p.teams().cachedHasTeam(pl.getUniqueId())) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(player -> !player.getUniqueId().equals(pl.getUniqueId()))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
