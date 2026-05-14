package com.maris7.team.service;

import com.maris7.team.MarisTeam;
import com.maris7.team.SettingsHook;
import com.maris7.team.db.Database;
import com.maris7.team.model.Member;
import com.maris7.team.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class TeamService {
    public static final int MAX_MEMBERS = 45;
    private static final Set<String> TOGGLE_COLUMNS = Set.of("edit_home", "manage_members", "pvp", "visit_home", "team_chat", "chat_toggle");
    private final MarisTeam p;
    private final Database db;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Set<UUID> teamCache = ConcurrentHashMap.newKeySet();
    private final Set<UUID> chatToggleCache = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> teamIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> teamPvpCache = new ConcurrentHashMap<>();

    public TeamService(MarisTeam p, Database db) {
        this.p = p;
        this.db = db;
    }

    public void reload() {
        teamCache.clear();
        chatToggleCache.clear();
        teamIdCache.clear();
        teamPvpCache.clear();
    }

    record Invite(String team, long expires) {}

    public boolean validName(String n) {
        return n != null && n.matches("[A-Za-z0-9_]{3,7}");
    }

    public Team teamOf(UUID u) {
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("SELECT t.* FROM teams t JOIN members m ON m.team_id=t.id WHERE m.uuid=?")) {
            ps.setString(1, u.toString());
            ResultSet r = ps.executeQuery();
            Team t = r.next() ? mapTeam(r) : null;
            if (t == null) {
                clearMemberCache(u);
            } else {
                teamCache.add(u);
                teamIdCache.put(u, t.id);
                teamPvpCache.put(t.id, t.pvp);
            }
            return t;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Member member(UUID u) {
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("SELECT m.*,t.leader FROM members m JOIN teams t ON t.id=m.team_id WHERE m.uuid=?")) {
            ps.setString(1, u.toString());
            ResultSet r = ps.executeQuery();
            Member m = r.next() ? mapMember(r, UUID.fromString(r.getString("leader"))) : null;
            cacheMember(u, m);
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    public Team byName(String n) {
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("SELECT * FROM teams WHERE lower(name)=lower(?)")) {
            ps.setString(1, n);
            ResultSet r = ps.executeQuery();
            return r.next() ? mapTeam(r) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean create(Player pl, String name) {
        if (byName(name) != null) return false;
        try (Connection c = db.con()) {
            c.setAutoCommit(false);
            PreparedStatement ps = c.prepareStatement("INSERT INTO teams(name,leader,pvp) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, pl.getUniqueId().toString());
            ps.setBoolean(3, false);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            int id = keys.getInt(1);
            PreparedStatement ms = c.prepareStatement("INSERT INTO members(uuid,name,team_id,joined_at,edit_home,manage_members,pvp,visit_home,team_chat,chat_toggle) VALUES(?,?,?,?,?,?,?,?,?,?)");
            fillMember(ms, pl.getUniqueId(), pl.getName(), id, true, true, true, true, true, false);
            ms.executeUpdate();
            c.commit();
            teamCache.add(pl.getUniqueId());
            teamIdCache.put(pl.getUniqueId(), id);
            teamPvpCache.put(id, false);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void fillMember(PreparedStatement ms, UUID u, String name, int id, boolean eh, boolean mg, boolean pvpb, boolean vh, boolean tc, boolean tog) throws SQLException {
        ms.setString(1, u.toString());
        ms.setString(2, name);
        ms.setInt(3, id);
        ms.setLong(4, System.currentTimeMillis());
        ms.setBoolean(5, eh);
        ms.setBoolean(6, mg);
        ms.setBoolean(7, pvpb);
        ms.setBoolean(8, vh);
        ms.setBoolean(9, tc);
        ms.setBoolean(10, tog);
    }

    public List<Member> members(int teamId) {
        List<Member> l = new ArrayList<>();
        UUID leader = null;
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("SELECT leader FROM teams WHERE id=?")) {
            ps.setInt(1, teamId);
            ResultSet r = ps.executeQuery();
            if (r.next()) leader = UUID.fromString(r.getString("leader"));
        } catch (Exception ignored) {
        }
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("SELECT * FROM members WHERE team_id=? ORDER BY joined_at")) {
            ps.setInt(1, teamId);
            ResultSet r = ps.executeQuery();
            while (r.next()) l.add(mapMember(r, leader));
        } catch (Exception ignored) {
        }
        return l;
    }

    public void invite(String team, Player target) {
        invites.put(target.getUniqueId(), new Invite(team, System.currentTimeMillis() + p.getConfig().getLong("invite-expire-seconds", 60) * 1000));
    }

    public boolean join(Player pl, String teamName) {
        Invite i = invites.get(pl.getUniqueId());
        if (i == null || !i.team.equalsIgnoreCase(teamName) || i.expires < System.currentTimeMillis()) return false;
        Team t = byName(teamName);
        if (t == null) return false;
        try (Connection c = db.con()) {
            c.setAutoCommit(false);
            try (PreparedStatement count = c.prepareStatement("SELECT COUNT(*) FROM members WHERE team_id=?")) {
                count.setInt(1, t.id);
                ResultSet r = count.executeQuery();
                if (r.next() && r.getInt(1) >= MAX_MEMBERS) {
                    c.rollback();
                    return false;
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO members(uuid,name,team_id,joined_at,edit_home,manage_members,pvp,visit_home,team_chat,chat_toggle) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                fillMember(ps, pl.getUniqueId(), pl.getName(), t.id, false, false, false, false, true, false);
                ps.executeUpdate();
            }
            c.commit();
            invites.remove(pl.getUniqueId());
            teamCache.add(pl.getUniqueId());
            teamIdCache.put(pl.getUniqueId(), t.id);
            teamPvpCache.put(t.id, t.pvp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void kick(UUID u) {
        setTeamChat(u, false);
        invites.remove(u);
        clearMemberCache(u);
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE uuid=?")) {
            ps.setString(1, u.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public void disband(Team t, String msg) {
        String colored = com.maris7.team.util.Text.color(msg);
        for (Member m : members(t.id)) {
            setTeamChat(m.uuid, false);
            clearMemberCache(m.uuid);
            Player pl = Bukkit.getPlayer(m.uuid);
            if (pl != null) p.scheduler().entity(pl, () -> pl.sendMessage(colored));
            else offline(m.uuid, msg);
        }
        try (Connection c = db.con()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE team_id=?")) {
                ps.setInt(1, t.id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM teams WHERE id=?")) {
                ps.setInt(1, t.id);
                ps.executeUpdate();
            }
            c.commit();
            teamPvpCache.remove(t.id);
        } catch (Exception ignored) {
        }
    }

    public boolean cachedHasTeam(UUID u) {
        return teamCache.contains(u);
    }

    public Integer cachedTeamId(UUID u) {
        return teamIdCache.get(u);
    }

    public Boolean cachedTeamPvp(int teamId) {
        return teamPvpCache.get(teamId);
    }

    public boolean cachedChatToggle(UUID u) {
        return teamChatEnabled(u);
    }

    public boolean teamChatEnabled(UUID u) {
        return teamChatEnabled(u, chatToggleCache.contains(u));
    }

    private boolean teamChatEnabled(UUID u, boolean fallback) {
        SettingsHook hook = p.settingsHook();
        if (hook != null && hook.isAvailable()) {
            return hook.isEnabled(u, "TEAM_CHAT", fallback);
        }
        return fallback;
    }

    public boolean toggleTeamChat(UUID u) {
        return setTeamChat(u, !teamChatEnabled(u));
    }

    public boolean setTeamChat(UUID u, boolean enabled) {
        SettingsHook hook = p.settingsHook();
        if (hook != null && hook.isAvailable()) {
            Boolean stored = hook.set(u, "TEAM_CHAT", enabled);
            if (stored != null) {
                updateChatCache(u, stored);
                return stored;
            }
        }
        setToggle(u, "chat_toggle", enabled);
        return enabled;
    }

    public void refreshCache(UUID u) {
        member(u);
    }

    public void offline(UUID u, String message) {
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("INSERT INTO pending_messages(uuid,message) VALUES(?,?)")) {
            ps.setString(1, u.toString());
            ps.setString(2, message);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public void setToggle(UUID u, String col, boolean v) {
        if (!TOGGLE_COLUMNS.contains(col)) return;
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("UPDATE members SET " + col + "=? WHERE uuid=?")) {
            ps.setBoolean(1, v);
            ps.setString(2, u.toString());
            ps.executeUpdate();
            if (col.equals("chat_toggle")) updateChatCache(u, v);
        } catch (Exception ignored) {
        }
    }

    public void setTeamPvp(int id, boolean v) {
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("UPDATE teams SET pvp=? WHERE id=?")) {
            ps.setBoolean(1, v);
            ps.setInt(2, id);
            ps.executeUpdate();
            teamPvpCache.put(id, v);
        } catch (Exception ignored) {
        }
    }

    public boolean teamInvitesEnabled(UUID u) {
        SettingsHook hook = p.settingsHook();
        if (hook != null && hook.isAvailable()) {
            return hook.isEnabled(u, "TEAM_TOGGLE", true);
        }
        return true;
    }

    public void setHome(Team t, Location l) {
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("UPDATE teams SET world=?,x=?,y=?,z=?,yaw=?,pitch=? WHERE id=?")) {
            ps.setString(1, l.getWorld().getName());
            ps.setDouble(2, l.getX());
            ps.setDouble(3, l.getY());
            ps.setDouble(4, l.getZ());
            ps.setFloat(5, l.getYaw());
            ps.setFloat(6, l.getPitch());
            ps.setInt(7, t.id);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public void delHome(Team t) {
        try (Connection c = db.con(); PreparedStatement ps = c.prepareStatement("UPDATE teams SET world=NULL,x=NULL,y=NULL,z=NULL,yaw=NULL,pitch=NULL WHERE id=?")) {
            ps.setInt(1, t.id);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private Team mapTeam(ResultSet r) throws SQLException {
        Team t = new Team(r.getInt("id"), r.getString("name"), UUID.fromString(r.getString("leader")), r.getBoolean("pvp"));
        t.world = r.getString("world");
        t.hasHome = t.world != null;
        t.x = r.getDouble("x");
        t.y = r.getDouble("y");
        t.z = r.getDouble("z");
        t.yaw = r.getFloat("yaw");
        t.pitch = r.getFloat("pitch");
        return t;
    }

    private void cacheMember(UUID u, Member m) {
        if (m == null) {
            clearMemberCache(u);
        } else {
            teamCache.add(u);
            teamIdCache.put(u, m.teamId);
            updateChatCache(u, m.chatToggle);
        }
    }

    private void clearMemberCache(UUID u) {
        teamCache.remove(u);
        chatToggleCache.remove(u);
        teamIdCache.remove(u);
    }

    private void updateChatCache(UUID u, boolean enabled) {
        if (enabled) chatToggleCache.add(u);
        else chatToggleCache.remove(u);
    }

    private Member mapMember(ResultSet r) throws SQLException {
        return mapMember(r, null);
    }

    private Member mapMember(ResultSet r, UUID leader) throws SQLException {
        Member m = new Member();
        m.uuid = UUID.fromString(r.getString("uuid"));
        m.name = r.getString("name");
        m.teamId = r.getInt("team_id");
        m.joinedAt = r.getLong("joined_at");
        m.editHome = r.getBoolean("edit_home");
        m.manage = r.getBoolean("manage_members");
        m.pvp = r.getBoolean("pvp");
        m.visitHome = r.getBoolean("visit_home");
        m.teamChat = r.getBoolean("team_chat");
        boolean localChatToggle = r.getBoolean("chat_toggle");
        m.chatToggle = teamChatEnabled(m.uuid, localChatToggle);
        m.isLeader = leader != null && leader.equals(m.uuid);
        return m;
    }
}