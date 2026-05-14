package com.maris7.team.config;

import com.maris7.team.MarisTeam;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public final class Configs {
    private final MarisTeam plugin; private FileConfiguration sounds, messages, teamGui, editGui, confirmGui;
    public Configs(MarisTeam plugin){this.plugin=plugin; reload();}
    public void reload(){
        sounds = yml("sounds.yml");
        String lang = plugin.getConfig().getString("language", "en");
        messages = yml("message/message_" + lang + ".yml");
        teamGui = yml("guis/" + lang + "/team.yml"); editGui = yml("guis/" + lang + "/edit.yml"); confirmGui = yml("guis/" + lang + "/confirm.yml");
    }
    private FileConfiguration yml(String path){ return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path)); }
    public FileConfiguration sounds(){return sounds;} public FileConfiguration messages(){return messages;} public FileConfiguration teamGui(){return teamGui;} public FileConfiguration editGui(){return editGui;} public FileConfiguration confirmGui(){return confirmGui;}
}
