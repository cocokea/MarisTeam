package com.maris7.team.hook;

import com.maris7.team.MarisTeam;
import com.maris7.team.model.Team;
import com.maris7.team.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class MarisPlaceholders extends PlaceholderExpansion {
    private final MarisTeam p;

    public MarisPlaceholders(MarisTeam p) {
        this.p = p;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "maristeam";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Maris7";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) return "N/A";
        if (p.isShuttingDown() || !p.isEnabled() || p.database() == null || p.database().isClosed() || p.teams() == null) return "N/A";

        if (!params.equalsIgnoreCase("team") && !params.equalsIgnoreCase("team_smallcap")) return null;

        try {
            Team t = p.teams().teamOf(player.getUniqueId());
            String name = t == null ? "N/A" : t.name;
            if (params.equalsIgnoreCase("team")) return name;
            return t == null ? "N/A" : Text.smallCaps(name);
        } catch (Throwable ignored) {
            return "N/A";
        }
    }
}
