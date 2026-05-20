package com.maris7.team;

import com.github.retrooper.packetevents.PacketEvents;
import com.maris7.team.command.TeamAdminCommand;
import com.maris7.team.command.TeamCommand;
import com.maris7.team.config.Configs;
import com.maris7.team.db.Database;
import com.maris7.team.gui.GuiListener;
import com.maris7.team.hook.MarisPlaceholders;
import com.maris7.team.listener.ChatListener;
import com.maris7.team.listener.LegacyChatListener;
import com.maris7.team.listener.PaperChatBridge;
import com.maris7.team.listener.PlayerListener;
import com.maris7.team.listener.PvpListener;
import com.maris7.team.listener.SignInputPacketListener;
import com.maris7.team.service.SignInputService;
import com.maris7.team.service.HomeTeleportService;
import com.maris7.team.service.TeamService;
import com.maris7.team.util.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarisTeam extends JavaPlugin {
    private static MarisTeam instance;
    private Configs configs;
    private Database database;
    private TeamService teamService;
    private SignInputService signInputService;
    private HomeTeleportService homeTeleportService;
    private PlatformScheduler scheduler;
    private SettingsHook settingsHook;
    private MarisPlaceholders placeholders;
    private volatile boolean shuttingDown;
    private SignInputPacketListener signInputPacketListener;

    public static MarisTeam get() { return instance; }
    public Configs configs() { return configs; }
    public TeamService teams() { return teamService; }
    public SignInputService signInput() { return signInputService; }
    public HomeTeleportService homeTeleport() { return homeTeleportService; }
    public Database database() { return database; }
    public PlatformScheduler scheduler() { return scheduler; }
    public SettingsHook settingsHook() { return settingsHook; }
    public boolean isShuttingDown() { return shuttingDown; }

    @Override public void onEnable() {
        
        saveDefaultConfig();
        MarisPluginStartup.bootstrap(this, "cocokea/MarisTeam");
instance = this;
        shuttingDown = false;
        saveDefaultConfig();
        saveResourceIfMissing("sounds.yml");
        saveResourceIfMissing("message/message_en.yml");
        saveResourceIfMissing("message/message_vi.yml");
        deleteLegacyVnMessage();
        saveResourceIfMissing("guis/en/team.yml"); saveResourceIfMissing("guis/en/edit.yml"); saveResourceIfMissing("guis/en/confirm.yml");
        saveResourceIfMissing("guis/vi/team.yml"); saveResourceIfMissing("guis/vi/edit.yml"); saveResourceIfMissing("guis/vi/confirm.yml");
        fixLegacyYamlQuotes();
        refreshLegacyGuiStyle();
        configs = new Configs(this);
        scheduler = new PlatformScheduler(this);
        settingsHook = new SettingsHook(this);
        settingsHook.init();
        database = new Database(this);
        database.init();
        teamService = new TeamService(this, database);
        signInputService = new SignInputService(this);
        if (signInputService.isPacketEventsAvailable()) {
            signInputPacketListener = new SignInputPacketListener(signInputService);
            PacketEvents.getAPI().getEventManager().registerListener(signInputPacketListener);
        } else {
            getLogger().warning("PacketEvents was not found. Sign input features will stay disabled until PacketEvents is installed.");
        }
        homeTeleportService = new HomeTeleportService(this);
        TeamCommand teamCommand = new TeamCommand(this);
        getCommand("team").setExecutor(teamCommand); getCommand("team").setTabCompleter(teamCommand);
        TeamAdminCommand adminCommand = new TeamAdminCommand(this);
        getCommand("teamadmin").setExecutor(adminCommand); getCommand("teamadmin").setTabCompleter(adminCommand);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        ChatListener chatListener = new ChatListener(this);
        Bukkit.getPluginManager().registerEvents(chatListener, this);
        boolean paperChat = PaperChatBridge.register(this, chatListener);
        if (!scheduler.isFolia() && !paperChat) {
            Bukkit.getPluginManager().registerEvents(new LegacyChatListener(this, chatListener), this);
        }
        Bukkit.getPluginManager().registerEvents(new PlayerListener(signInputService), this);
        Bukkit.getPluginManager().registerEvents(homeTeleportService, this);
        Bukkit.getPluginManager().registerEvents(new PvpListener(this), this);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholders = new MarisPlaceholders(this);
            placeholders.register();
        }
    }

    @Override public void onDisable() {
        shuttingDown = true;
        if (placeholders != null) {
            placeholders.unregister();
            placeholders = null;
        }
        if (signInputService != null) signInputService.clearAll();
        if (signInputPacketListener != null && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(signInputPacketListener);
            signInputPacketListener = null;
        }
        if (database != null) database.close();
    }

    public void reloadAll() { reloadConfig(); configs.reload(); teamService.reload(); }

    private void saveResourceIfMissing(String resourcePath) {
        java.io.File file = new java.io.File(getDataFolder(), resourcePath);
        if (!file.exists()) saveResource(resourcePath, false);
    }

    private void fixLegacyYamlQuotes() {
        for (String lang : new String[]{"en", "vi"}) {
            java.io.File file = new java.io.File(getDataFolder(), "message/message_" + lang + ".yml");
            if (!file.isFile()) continue;
            try {
                java.nio.file.Path path = file.toPath();
                String old = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                String fixed = old.replace("\\'", "''")
                        .replace("Use &#FFF800/team join (name) to join or &#FFF800[CLICK HERE]", "Use &#FFF800/team join %team% &7to join or &#FFF800[CLICK HERE]");
                if (!fixed.contains("team-invite-disabled:")) fixed += "\nteam-invite-disabled: '&cUser disabled team invite'\n";
                if (!fixed.contains("team-full:")) fixed += "team-full: '&cYour team has reached its maximum'\n";
                if (!fixed.contains("home-teleport-done:")) fixed += "home-teleport-done: '&7You have moved back to team home.'\n";
                if (!fixed.contains("home-teleport-countdown:")) fixed += "home-teleport-countdown: '&7Teleporting in &#FFD900%seconds%s'\n";
                if (!fixed.contains("home-teleport-cancelled:")) fixed += "home-teleport-cancelled: '&cTeleport cancelled because you moved'\n";
                if (!old.equals(fixed)) java.nio.file.Files.writeString(path, fixed, java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException ex) {
                getLogger().warning("Could not fix YAML quotes in " + file.getPath() + ": " + ex.getMessage());
            }
        }
    }

    private void deleteLegacyVnMessage() {
        java.io.File file = new java.io.File(getDataFolder(), "message/message_vn.yml");
        if (file.isFile() && !file.delete()) {
            getLogger().warning("Could not delete legacy duplicate message file: " + file.getPath());
        }
    }

    private void refreshLegacyGuiStyle() {
        for (String lang : new String[]{"en", "vi"}) {
            for (String name : new String[]{"team.yml", "edit.yml", "confirm.yml"}) {
                String path = "guis/" + lang + "/" + name;
                java.io.File file = new java.io.File(getDataFolder(), path);
                if (!file.isFile()) continue;
                try {
                    String old = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    if (old.contains("&#00FF79") || (name.equals("team.yml") && (old.contains("material: CAULDRON") || old.contains("material: ANVIL") || old.contains("material: ENDER_PEARL") || old.contains("name: \" \"")))) {
                        saveResource(path, true);
                    }
                } catch (java.io.IOException ex) {
                    getLogger().warning("Could not refresh GUI style in " + file.getPath() + ": " + ex.getMessage());
                }
            }
        }
    }


}
