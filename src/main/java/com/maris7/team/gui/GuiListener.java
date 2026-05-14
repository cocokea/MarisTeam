package com.maris7.team.gui;

import com.maris7.team.MarisTeam;
import com.maris7.team.model.Member;
import com.maris7.team.model.Team;
import com.maris7.team.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.List;
import java.util.Map;

public final class GuiListener implements Listener {
    private final MarisTeam p;
    private final Msg msg;

    public GuiListener(MarisTeam p) {
        this.p = p;
        this.msg = new Msg(p);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof TeamGui.Holder)) return;
        if (!(e.getWhoClicked() instanceof Player pl)) return;
        e.setCancelled(true);
        int s = e.getRawSlot();
        if (s < 0 || s >= e.getInventory().getSize()) return;
        TeamGui.View v = TeamGui.views.get(pl.getUniqueId());
        if (v == null) return;
        Team t = v.team();
        if (t == null) {
            pl.closeInventory();
            return;
        }

        if (v.type().equals("team")) {
            handleTeamClick(pl, v, t, s);
        } else if (v.type().equals("edit")) {
            handleEditClick(pl, v, t, s);
        } else if (v.type().startsWith("confirm") && s == 15) {
            if (v.type().equals("confirm-disband")) {
                p.teams().disband(t, com.maris7.team.util.Text.color(p.configs().messages().getString("disbanded-by-leader").replace("%leader%", pl.getName())));
                msg.both(pl, "disbanded", "disband");
                pl.closeInventory();
            } else {
                p.teams().kick(pl.getUniqueId());
                pl.closeInventory();
            }
        } else if (v.type().startsWith("confirm") && s == 11) {
            TeamGui.openTeam(p, pl, 0);
        }
    }

    private void handleTeamClick(Player pl, TeamGui.View v, Team t, int s) {
        if (s < 45) {
            List<Member> ms = v.visibleMembers();
            int index = v.page() * 45 + s;
            if (index < ms.size()) {
                Member target = ms.get(index);
                if (target.uuid.equals(pl.getUniqueId())) {
                    msg.both(pl, "cannot-manage-self", "no");
                    return;
                }
                Member me = v.self();
                if (me == null || (!me.manage && !t.leader.equals(pl.getUniqueId()))) {
                    msg.both(pl, "no-permission", "no");
                    return;
                }
                if (t.leader.equals(target.uuid)) {
                    msg.both(pl, "cannot-adjust", "no");
                    return;
                }
                TeamGui.openEdit(p, pl, t, me, target);
            }
            return;
        }
        if (s == 47) {
            pl.closeInventory();
            msg.both(pl, "type-invite", null);
        } else if (s == 46) {
            TeamGui.openTeam(p, pl, v.withSort(TeamGui.nextSort(v.sort())));
        } else if (s == 48) {
            TeamGui.openTeam(p, pl, v.withPage(v.page() - 1));
        } else if (s == 49) {
            TeamGui.openTeam(p, pl, v);
        } else if (s == 50) {
            if (TeamGui.hasPage(p, pl, v, v.page() + 1)) TeamGui.openTeam(p, pl, v.withPage(v.page() + 1));
            else msg.play(pl, "no");
        } else if (s == 45) {
            pl.closeInventory();
            p.signInput().openSearch(pl, input -> TeamGui.openTeam(p, pl, v.withQuery(input)));
        } else if (s == 52) {
            if (!t.hasHome) {
                pl.closeInventory();
                msg.both(pl, "no-home", "no");
            } else {
                World w = Bukkit.getWorld(t.world);
                if (w == null) {
                    pl.closeInventory();
                    msg.both(pl, "no-home", "no");
                    return;
                }
                pl.closeInventory();
                p.homeTeleport().start(pl, new Location(w, t.x, t.y, t.z, t.yaw, t.pitch));
            }
        } else if (s == 53) {
            Member me = v.self();
            if (!t.leader.equals(pl.getUniqueId()) && (me == null || !me.pvp)) {
                msg.both(pl, "no-permission", "no");
                return;
            }
            p.teams().setTeamPvp(t.id, !t.pvp);
            t.pvp = !t.pvp;
            TeamGui.invalidateMembers(t.id);
            msg.play(pl, "click");
            TeamGui.openTeam(p, pl, v);
        }
    }

    private void handleEditClick(Player pl, TeamGui.View v, Team t, int s) {
        Member target = v.targetMember();
        Member me = v.self();
        if (target == null || me == null) return;
        if (!me.manage && !t.leader.equals(pl.getUniqueId())) {
            msg.both(pl, "no-permission", "no");
            return;
        }
        if (s == 11) {
            p.teams().kick(target.uuid);
            TeamGui.invalidateMembers(t.id);
            Player tp = Bukkit.getPlayer(target.uuid);
            if (tp != null) msg.both(tp, "kicked-target", null, Map.of("%player%", pl.getName()));
            msg.both(pl, "kicked", "success", Map.of("%player%", target.name));
            TeamGui.openTeam(p, pl, 0);
            return;
        }
        String col = null;
        if (s == 10) col = "edit_home";
        if (s == 12) col = "manage_members";
        if (s == 13) col = "pvp";
        if (s == 14) col = "visit_home";
        if (s == 15) col = "team_chat";
        if (col != null) {
            boolean cur = switch (col) {
                case "edit_home" -> target.editHome;
                case "manage_members" -> target.manage;
                case "pvp" -> target.pvp;
                case "visit_home" -> target.visitHome;
                default -> target.teamChat;
            };
            p.teams().setToggle(target.uuid, col, !cur);
            TeamGui.invalidateMembers(t.id);
            msg.play(pl, "click");
            Member updated = p.teams().member(target.uuid);
            TeamGui.openEdit(p, pl, t, me, updated);
        }
    }


    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof TeamGui.Holder)) return;
        if (e.getPlayer() instanceof Player pl) TeamGui.views.remove(pl.getUniqueId());
    }
}
