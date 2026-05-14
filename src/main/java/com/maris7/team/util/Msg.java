package com.maris7.team.util;

import com.maris7.team.MarisTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;

public final class Msg {
    private final MarisTeam p;

    public Msg(MarisTeam p) {
        this.p = p;
    }

    public void both(Player player, String key, String sound, Map<String, String> ph) {
        String raw = p.configs().messages().getString(key, "");
        if (Text.empty(raw)) return;
        for (var e : ph.entrySet()) raw = raw.replace(e.getKey(), e.getValue());
        String colored = Text.color(raw);
        player.sendMessage(colored);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(colored));
        play(player, sound);
    }

    public void both(Player player, String key, String sound) {
        both(player, key, sound, Map.of());
    }

    public void play(Player player, String key) {
        try {
            String s = p.configs().sounds().getString(key, key);
            player.playSound(player.getLocation(), Sound.valueOf(s), 1f, 1f);
        } catch (Exception ignored) {
        }
    }
}
