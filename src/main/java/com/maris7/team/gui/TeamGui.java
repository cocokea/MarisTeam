package com.maris7.team.gui;

import com.maris7.team.MarisTeam;
import com.maris7.team.model.Member;
import com.maris7.team.model.Team;
import com.maris7.team.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamGui {
    public static final List<String> SORTS = List.of("JOIN_DATE", "ONLINE", "A_Z");
    public static final Map<UUID, View> views = new ConcurrentHashMap<>();
    private static final Map<Integer, CachedMembers> memberCache = new ConcurrentHashMap<>();
    private static final long MEMBER_CACHE_TTL_MS = 2000L;

    public static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public record View(String type, int page, UUID target, String query, String sort, Team team, Member self, Member targetMember, List<Member> visibleMembers) {
        public View(String type, int page, UUID target, String query, String sort) {
            this(type, page, target, query, sort, null, null, null, List.of());
        }
        public View withPage(int nextPage) { return new View(type, Math.max(0, nextPage), target, query, sort, team, self, targetMember, visibleMembers); }
        public View withQuery(String nextQuery) { return new View(type, 0, target, nextQuery == null || nextQuery.isBlank() ? null : nextQuery.trim(), sort, team, self, targetMember, visibleMembers); }
        public View withSort(String nextSort) { return new View(type, 0, target, query, nextSort, team, self, targetMember, visibleMembers); }
        public View withTeamState(Team nextTeam, Member nextSelf) { return new View(type, page, target, query, sort, nextTeam, nextSelf, targetMember, visibleMembers); }
        public View withVisibleMembers(List<Member> nextVisible) { return new View(type, page, target, query, sort, team, self, targetMember, List.copyOf(nextVisible)); }
        public View withTargetMember(Member nextTarget) { return new View(type, page, nextTarget == null ? target : nextTarget.uuid, query, sort, team, self, nextTarget, visibleMembers); }
    }

    public static void openTeam(MarisTeam p, Player pl, int page) {
        openTeam(p, pl, new View("team", page, null, null, SORTS.get(0)));
    }

    public static void openTeam(MarisTeam p, Player pl, View view) {
        Team t = p.teams().teamOf(pl.getUniqueId());
        if (t == null) return;
        List<Member> members = new ArrayList<>(membersCached(p, t.id));
        Member self = null;
        for (Member m : members) {
            if (m.uuid.equals(pl.getUniqueId())) {
                self = m;
                break;
            }
        }
        View state = normalize(view.withTeamState(t, self), members.size());
        applySearchAndSort(members, state.query(), state.sort());
        state = normalize(state, members.size()).withVisibleMembers(members);
        Inventory inv = Bukkit.createInventory(new Holder(), 54, Text.color(p.configs().teamGui().getString("title").replace("%pages%", String.valueOf(state.page() + 1))));
        ConfigurationSection it = p.configs().teamGui().getConfigurationSection("items");
        ItemStack fill = guiItem(it.getConfigurationSection("fill"), Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 45; i++) inv.setItem(i, fill);
        ConfigurationSection invite = it.getConfigurationSection("invite");
        if (invite != null) {
            int inviteSlot = invite.getInt("slot", 47);
            if (inviteSlot >= 0 && inviteSlot < inv.getSize()) {
                inv.setItem(inviteSlot, guiItem(invite, Material.EMERALD, "&#00FF8Cɪɴᴠɪᴛᴇ", List.of("&fClick to invite a new player")));
            }
        }

        int from = state.page() * 45;
        int slot = 0;
        for (int i = from; i < members.size() && slot < 45; i++) {
            Member m = members.get(i);
            boolean on = Bukkit.getPlayer(m.uuid) != null;
            inv.setItem(slot++, playerHead(
                    (on ? it.getString("online-member.name") : it.getString("offline-member.name")).replace("%player%", m.name),
                    on ? it.getStringList("online-member.lore") : it.getStringList("offline-member.lore")));
        }

        set(inv, it, "sort", Map.of("%sort%", pretty(state.sort())));
        set(inv, it, "back", Map.of());
        set(inv, it, "info", Map.of("%team%", Text.smallCaps(t.name)));
        set(inv, it, "next", Map.of());
        set(inv, it, "search", Map.of("%search%", state.query() == null ? "None" : state.query()));
        set(inv, it, "home", Map.of());
        set(inv, it, "pvp", Map.of("%toggle%", t.pvp ? "&a&lON" : "&#FF0000&lOFF"));
        pl.openInventory(inv);
        views.put(pl.getUniqueId(), state);
    }

    private static View normalize(View view, int memberCount) {
        String sort = view.sort() == null || !SORTS.contains(view.sort()) ? SORTS.get(0) : view.sort();
        int maxPage = Math.max(0, (memberCount - 1) / 45);
        int page = Math.max(0, Math.min(view.page(), maxPage));
        return new View("team", page, null, view.query(), sort, view.team(), view.self(), view.targetMember(), view.visibleMembers());
    }

    public static boolean hasPage(MarisTeam p, Player pl, View view, int page) {
        if (page < 0) return false;
        Team t = view.team();
        if (t == null) {
            t = p.teams().teamOf(pl.getUniqueId());
            if (t == null) return false;
        }
        List<Member> members = new ArrayList<>(membersCached(p, t.id));
        applySearchAndSort(members, view.query(), view.sort());
        return page * 45 < members.size();
    }

    private static void applySearchAndSort(List<Member> members, String query, String sort) {
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            members.removeIf(member -> member.name == null || !member.name.toLowerCase(Locale.ROOT).contains(q));
        }
        if ("ONLINE".equals(sort)) {
            members.sort(Comparator.comparing((Member m) -> Bukkit.getPlayer(m.uuid) == null).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
        } else if ("A_Z".equals(sort)) {
            members.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
        }
    }

    private static void set(Inventory inv, ConfigurationSection root, String key, Map<String, String> ph) {
        ConfigurationSection s = root.getConfigurationSection(key);
        if (s == null) return;
        String name = s.getString("name", key);
        List<String> lore = new ArrayList<>(s.getStringList("lore"));
        if (key.equals("sort")) {
            lore = new ArrayList<>();
            String selected = s.getString("selected-format", "&#00FF8C• %name%");
            String normal = s.getString("normal-format", "&f• %name%");
            String current = ph.getOrDefault("%sort%", pretty(SORTS.get(0)));
            for (String mode : SORTS) lore.add((pretty(mode).equalsIgnoreCase(current) ? selected : normal).replace("%name%", pretty(mode)));
        }
        for (var e : ph.entrySet()) {
            name = name.replace(e.getKey(), e.getValue());
            for (int i = 0; i < lore.size(); i++) lore.set(i, lore.get(i).replace(e.getKey(), e.getValue()));
        }
        inv.setItem(s.getInt("slot"), Text.item(Material.valueOf(s.getString("material")), name, lore));
    }

    private static ItemStack playerHead(String name, List<String> lore) {
        return Text.item(Material.PLAYER_HEAD, name, lore);
    }

    private static ItemStack guiItem(ConfigurationSection section, Material defaultMaterial, String defaultName, List<String> defaultLore) {
        if (section == null) return Text.item(defaultMaterial, defaultName, defaultLore);
        Material material = Material.valueOf(section.getString("material", defaultMaterial.name()));
        return Text.item(material, section.getString("name", defaultName), section.getStringList("lore").isEmpty() ? defaultLore : section.getStringList("lore"));
    }

    private static List<Member> membersCached(MarisTeam p, int teamId) {
        long now = System.currentTimeMillis();
        CachedMembers cached = memberCache.get(teamId);
        if (cached != null && now - cached.loadedAt() <= MEMBER_CACHE_TTL_MS) return cached.members();
        List<Member> fresh = List.copyOf(p.teams().members(teamId));
        memberCache.put(teamId, new CachedMembers(now, fresh));
        return fresh;
    }

    public static void invalidateMembers(int teamId) {
        memberCache.remove(teamId);
    }

    private record CachedMembers(long loadedAt, List<Member> members) {}

    public static void openEdit(MarisTeam p, Player pl, Member target) {
        Team t = p.teams().teamOf(pl.getUniqueId());
        Member self = p.teams().member(pl.getUniqueId());
        openEdit(p, pl, t, self, target);
    }

    public static void openEdit(MarisTeam p, Player pl, Team t, Member self, Member target) {
        if (target == null) return;
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Text.color(p.configs().editGui().getString("title").replace("%player%", target.name)));
        var root = p.configs().editGui().getConfigurationSection("items");
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            String col = switch (k) {
                case "edit-home" -> target.editHome ? "&a&lON" : "&#FF0000&lOFF";
                case "manage" -> target.manage ? "&a&lON" : "&#FF0000&lOFF";
                case "pvp" -> target.pvp ? "&a&lON" : "&#FF0000&lOFF";
                case "visit-home" -> target.visitHome ? "&a&lON" : "&#FF0000&lOFF";
                case "team-chat" -> target.teamChat ? "&a&lON" : "&#FF0000&lOFF";
                default -> "";
            };
            List<String> lore = new ArrayList<>(s.getStringList("lore"));
            for (int i = 0; i < lore.size(); i++) lore.set(i, lore.get(i).replace("%player%", target.name).replace("%toggle%", col));
            inv.setItem(s.getInt("slot"), Text.item(Material.valueOf(s.getString("material")), s.getString("name").replace("%player%", target.name).replace("%toggle%", col), lore));
        }
        pl.openInventory(inv);
        views.put(pl.getUniqueId(), new View("edit", 0, target.uuid, null, SORTS.get(0), t, self, target, List.of()));
    }

    public static void openConfirm(MarisTeam p, Player pl, boolean disband) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Text.color(p.configs().confirmGui().getString(disband ? "disband-title" : "leave-title")));
        var c = p.configs().confirmGui();
        for (String k : List.of("cancel", "confirm")) {
            var s = c.getConfigurationSection(k);
            inv.setItem(s.getInt("slot"), Text.item(Material.valueOf(s.getString("material")), s.getString("name"), s.getStringList("lore")));
        }
        pl.openInventory(inv);
        Team t = p.teams().teamOf(pl.getUniqueId());
        Member self = p.teams().member(pl.getUniqueId());
        views.put(pl.getUniqueId(), new View(disband ? "confirm-disband" : "confirm-leave", 0, null, null, SORTS.get(0), t, self, null, List.of()));
    }

    public static String nextSort(String current) {
        int index = SORTS.indexOf(current);
        return SORTS.get((index + 1) % SORTS.size());
    }

    private static String pretty(String sort) {
        return switch (sort) {
            case "ONLINE" -> "Online Members";
            case "A_Z" -> "Alphabetically";
            default -> "Join Date";
        };
    }
}
