package org.flennn;

import lombok.Getter;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.flennn.commands.FreezeCommand;
import org.flennn.commands.LightStaffCommand;
import org.flennn.commands.StaffTpCommand;
import org.flennn.commands.StaffWhitelistCommand;
import org.flennn.commands.VanishCommand;
import org.flennn.config.ConfigManager;
import org.flennn.config.LightStaffConfigValidator;
import org.flennn.storage.StorageManager;
import org.flennn.logging.AuditLogger;
import org.flennn.message.MessageManager;
import org.flennn.manager.LightStaffManager;
import co.aikar.commands.PaperCommandManager;
import org.flennn.listeners.LightStaffListener;
import org.flennn.listeners.StaffWhitelistListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.flennn.permission.PermissionManager;
import org.flennn.util.Console;
import org.flennn.util.SchedulerAdapter;

import java.util.List;

public final class LightStaff extends JavaPlugin {
    @Getter
    private static LightStaff instance;
    private StorageManager storageManager;
    @Getter
    private LightStaffManager LightStaffManager;
    @Getter
    private AuditLogger auditLogger;
    private FileConfiguration config;
    @Getter
    private FileConfiguration toolsConfig;
    @Getter
    private FileConfiguration messagesConfig;
    @Getter
    private MessageManager messageManager;
    @Getter
    private PermissionManager permissionManager;
    @Getter
    private ConfigManager configManager;
    @Getter
    private SchedulerAdapter schedulerAdapter;

    public StorageManager getStorageManager() {
        return storageManager;
    }
    public FileConfiguration getPluginConfig() { return config; }
    private final NamespacedKey STAFF_TOOL_KEY = new NamespacedKey(this, "staff_tool");
    private final NamespacedKey STAFF_TOOL_ID_KEY = new NamespacedKey(this, "staff_tool_id");

    public static String getPrefix() {
        if (instance == null) return "<gray>[LightStaff]</gray> ";
        return instance.getMessageManager().prefix();
    }

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        configManager = new ConfigManager(this);
        schedulerAdapter = new SchedulerAdapter(this);
        syncConfigReferences();
        messageManager = new MessageManager(messagesConfig);
        permissionManager = new PermissionManager(this);
        List<String> warnings = LightStaffConfigValidator.validate(config, toolsConfig, messagesConfig);
        logConfigWarnings(warnings);
        auditLogger = new AuditLogger(this);
        storageManager = new StorageManager(this);
        StaffWhitelistCommand.loadState();
        LightStaffManager = new LightStaffManager(this, config, toolsConfig);
        PaperCommandManager commandManager = new PaperCommandManager(this);
        commandManager.enableUnstableAPI("help");
        commandManager.registerCommand(new LightStaffCommand());
        commandManager.registerCommand(new StaffWhitelistCommand());
        commandManager.registerCommand(new VanishCommand());
        commandManager.registerCommand(new StaffTpCommand());
        commandManager.registerCommand(new FreezeCommand());


        getServer().getPluginManager().registerEvents(new LightStaffListener(LightStaffManager), this);
        getServer().getPluginManager().registerEvents(new StaffWhitelistListener(), this);
        logStartup(warnings);
    }

    @Override
    public void onDisable() {
        if (LightStaffManager != null) {
            getServer().getOnlinePlayers().forEach(player -> {
                if (LightStaffManager.isInLightStaff(player)) {
                    LightStaffManager.disableLightStaff(player);
                }
            });
        }
        if (LightStaffManager != null) {
            LightStaffManager.shutdown();
        }

        if (storageManager != null) storageManager.close();
        if (auditLogger != null) auditLogger.close();
        if (schedulerAdapter != null) schedulerAdapter.shutdown();
    }

    public NamespacedKey getStaffToolKey() {
        return STAFF_TOOL_KEY;
    }

    public NamespacedKey getStaffToolIdKey() {
        return STAFF_TOOL_ID_KEY;
    }

    public List<String> reloadLightStaffConfig() {
        configManager.reload();
        syncConfigReferences();
        messageManager.reload(messagesConfig);
        List<String> warnings = LightStaffConfigValidator.validate(config, toolsConfig, messagesConfig);
        logConfigWarnings(warnings);
        if (LightStaffManager != null) {
            LightStaffManager.reload(config, toolsConfig);
        }
        StaffWhitelistCommand.loadState();
        return warnings;
    }

    public void audit(String action, String actor, String target, String details) {
        if (config != null && config.getBoolean("audit_log_enabled", true) && auditLogger != null) {
            auditLogger.log(action, actor, target, details);
        }
    }

    private void logCompatibilityProfile() {
        Console.info("Compatibility profile: api-version=1.19, java=" + System.getProperty("java.version")
                + ", server=" + getServer().getVersion()
                + ", bukkit=" + getServer().getBukkitVersion());
    }

    private void logStartup(List<String> warnings) {
        logCompatibilityProfile();
        Console.startup(new Console.StartupReport(
                getDescription().getVersion(),
                warnings == null || warnings.isEmpty(),
                storageManager == null ? "Unavailable" : storageManager.getStorageDisplayName(),
                LightStaffManager == null ? 0 : LightStaffManager.getEnabledToolCount(),
                LightStaffManager == null ? 0 : LightStaffManager.getTotalToolCount(),
                warnings == null ? 0 : warnings.size(),
                detectPlatform(),
                System.getProperty("java.version")
        ));
    }

    private String detectPlatform() {
        String name = getServer().getName();
        if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("paper")) {
            return "Paper";
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return "Folia";
        } catch (ClassNotFoundException ignored) {
            if (schedulerAdapter != null && schedulerAdapter.isFolia()) return "Folia";
            return name == null || name.isBlank() ? "Bukkit" : name;
        }
    }

    private void logConfigWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            Console.success("Config validation passed.");
            return;
        }

        Console.warn("Config validation found " + warnings.size() + " warning(s):");
        for (String warning : warnings) {
            Console.warn("- " + warning);
        }
    }

    private void syncConfigReferences() {
        config = configManager.settings();
        toolsConfig = configManager.tools();
        messagesConfig = configManager.messages();
    }
}
