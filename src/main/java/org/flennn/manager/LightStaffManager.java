package org.flennn.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.flennn.LightStaff;
import org.flennn.data.StaffPlayerData;
import org.flennn.storage.StorageManager;
import org.flennn.storage.StorageManager.ModerationRecord;
import org.flennn.storage.StorageManager.StorageSession;
import org.flennn.tools.ExitLightStaffTool;
import org.flennn.tools.FlyToggleTool;
import org.flennn.tools.FreezeTool;
import org.flennn.tools.InspectTool;
import org.flennn.tools.PushTool;
import org.flennn.tools.StaffTool;
import org.flennn.tools.ToolDefinition;
import org.flennn.tools.VanishTool;
import org.flennn.util.CommandTemplate;
import org.flennn.util.Console;
import org.flennn.util.MaterialResolver;
import org.flennn.util.SchedulerAdapter;
import org.flennn.config.ToolSlotValidator;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

public class LightStaffManager {
    private final LightStaff plugin;
    private final StorageManager db;
    private FileConfiguration config;
    private FileConfiguration toolsConfig;
    private final Map<UUID, StaffPlayerData> LightStaffPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> freezeReasons = new ConcurrentHashMap<>();
    private final Map<String, ToolDefinition> toolDefinitions = new LinkedHashMap<>();
    private final Map<String, StaffTool> staffTools = new LinkedHashMap<>();
    private final Map<Integer, String> toolKeysBySlot = new ConcurrentHashMap<>();
    private final Set<String> frozenAllowedCommands = ConcurrentHashMap.newKeySet();

    private SchedulerAdapter.TaskHandle actionBarTask;
    private final Map<UUID, Component> lastActionBarCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStateHash = new ConcurrentHashMap<>();
    private String actionbarVanishLabel;
    private String actionbarFlyLabel;
    private String actionbarFrozenLabel;
    private String actionbarLightStaffLabel;

    private static final Component INDICATOR_ICON = Component.text("* ", NamedTextColor.DARK_GRAY);
    public LightStaffManager(LightStaff plugin, FileConfiguration config, FileConfiguration toolsConfig) {
        this.plugin = plugin;
        this.config = config;
        this.toolsConfig = toolsConfig;
        this.db = plugin.getStorageManager();

        loadToolDefinitions();
        reloadCachedSettings();
        rebuildTools();

        loadModerationStatesAsync();
        startActionBarUpdater();
    }

    private void loadToolDefinitions() {
        toolDefinitions.clear();
        registerToolDefinition("vanish", true, 0, "lightstaff.vanish", Material.ENDER_EYE, 1000L);
        registerToolDefinition("push", true, 1, "lightstaff.push", Material.SLIME_BALL, 1000L);
        registerToolDefinition("inspect", true, 2, "lightstaff.inspect", Material.BOOK, 1000L);
        registerToolDefinition("freeze", true, 4, "lightstaff.freeze", Material.PACKED_ICE, 1000L);
        registerToolDefinition("fly", true, 6, "lightstaff.fly", Material.FEATHER, 1000L);
        registerToolDefinition("exit", true, 8, "", Material.FIRE_CHARGE, 0L);
        validateToolDefinitions();
        rebuildSlotIndex();
    }

    public void reload(FileConfiguration config, FileConfiguration toolsConfig) {
        this.config = config;
        this.toolsConfig = toolsConfig;
        loadToolDefinitions();
        reloadCachedSettings();
        lastActionBarCache.clear();
        lastStateHash.clear();
        rebuildTools();
    }

    private void reloadCachedSettings() {
        actionbarVanishLabel = plugin.getMessageManager().raw("actionbar.vanish", "Vanish: ");
        actionbarFlyLabel = plugin.getMessageManager().raw("actionbar.fly", "Fly: ");
        actionbarFrozenLabel = plugin.getMessageManager().raw("actionbar.frozen", "Frozen: ");
        actionbarLightStaffLabel = plugin.getMessageManager().raw("actionbar.staff_mode", "Staff Mode");

        frozenAllowedCommands.clear();
        for (String command : config.getStringList("freeze_allowed_commands")) {
            if (command == null || command.isBlank()) continue;
            String normalized = command.trim().toLowerCase(Locale.ROOT);
            frozenAllowedCommands.add(normalized.startsWith("/") ? normalized : "/" + normalized);
        }
    }

    private void rebuildTools() {
        staffTools.clear();
        staffTools.put("vanish", new VanishTool(this));
        staffTools.put("push", new PushTool(this));
        staffTools.put("inspect", new InspectTool(this));
        staffTools.put("freeze", new FreezeTool(this));
        staffTools.put("fly", new FlyToggleTool(this));
        staffTools.put("exit", new ExitLightStaffTool(this));
    }

    private void rebuildSlotIndex() {
        toolKeysBySlot.clear();
        for (ToolDefinition definition : toolDefinitions.values()) {
            if (definition.enabled()) {
                toolKeysBySlot.put(definition.slot(), definition.key());
            }
        }
    }

    private void registerToolDefinition(String key, boolean defaultEnabled, int defaultSlot, String defaultPermission, Material defaultMaterial, long defaultCooldown) {
        String path = "tools." + key + ".";
        boolean enabled = toolsConfig.getBoolean(path + "enabled", defaultEnabled);
        int slot = toolsConfig.getInt(path + "slot", defaultSlot);
        if (slot < 0 || slot > 8) {
            Console.warn("Invalid slot for tool '" + key + "': " + slot + ". Using " + defaultSlot + ".");
            slot = defaultSlot;
        }

        String permission = toolsConfig.getString(path + "permission", defaultPermission);
        Material material = MaterialResolver.firstAvailable(toolsConfig.getString(path + "material"), defaultMaterial);
        long cooldown = toolsConfig.getLong(path + "cooldown", defaultCooldown);
        if (cooldown < 0L) {
            Console.warn("Invalid cooldown for tool '" + key + "': " + cooldown + ". Using " + defaultCooldown + ".");
            cooldown = defaultCooldown;
        }
        String displayName = toolsConfig.getString(path + "display_name", key.substring(0, 1).toUpperCase() + key.substring(1));
        List<String> lore = toolsConfig.getStringList(path + "lore");
        org.bukkit.Sound sound = parseSound(toolsConfig.getString(path + "sound"), null);
        float soundVolume = (float) toolsConfig.getDouble(path + "sound_volume", 0.5D);
        float soundPitch = (float) toolsConfig.getDouble(path + "sound_pitch", 0.5D);

        toolDefinitions.put(key, new ToolDefinition(key, enabled, slot, permission, material, cooldown, displayName, lore, sound, soundVolume, soundPitch));
    }

    private org.bukkit.Sound parseSound(String configured, org.bukkit.Sound fallback) {
        if (configured == null || configured.isBlank()) return fallback;
        try {
            return org.bukkit.Sound.valueOf(configured.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Console.warn("Invalid tool sound '" + configured + "'. Sound disabled for this tool.");
            return fallback;
        }
    }

    private void validateToolDefinitions() {
        Map<String, Integer> enabledSlots = new LinkedHashMap<>();
        for (ToolDefinition definition : toolDefinitions.values()) {
            if (definition.enabled()) {
                enabledSlots.put(definition.key(), definition.slot());
            }
        }
        for (String warning : ToolSlotValidator.validate(enabledSlots)) {
            Console.warn(warning);
        }
    }

    private void startActionBarUpdater() {
        long interval = Math.max(1L, config.getLong("actionbar_interval", 2L));
        actionBarTask = plugin.getSchedulerAdapter().runGlobalRepeating(() -> {
            if (LightStaffPlayers.isEmpty()) return;

            for (UUID uuid : LightStaffPlayers.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    removePlayerFromTracking(uuid);
                    continue;
                }

                plugin.getSchedulerAdapter().runEntity(player, () -> {
                    if (player.isOnline() && isInLightStaff(player)) {
                        updateActionBar(player);
                    }
                });
            }
        }, 1L, interval);
    }

    private void updateActionBar(Player player) {
        UUID uuid = player.getUniqueId();
        long currentStateHash = createStateHash(player);
        Long lastHash = lastStateHash.get(uuid);
        boolean forceUpdate = lastHash == null || (System.currentTimeMillis() % 5000 < 200);

        if (forceUpdate || lastHash != currentStateHash) {
            Component actionBar = buildActionBarComponent(player);
            lastActionBarCache.put(uuid, actionBar);
            lastStateHash.put(uuid, currentStateHash);
            player.sendActionBar(actionBar);
            return;
        }

        Component cached = lastActionBarCache.get(uuid);
        if (cached != null) {
            player.sendActionBar(cached);
        }
    }

    private long createStateHash(Player player) {
        long hash = 0;
        hash += isVanished(player) ? 1L : 0L;
        hash += player.isFlying() ? 2L : 0L;
        hash += isFrozen(player) ? 4L : 0L;
        hash += player.getGameMode().ordinal() * 8L;
        hash += (System.currentTimeMillis() / 1000) * 64L;
        return hash;
    }

    private Component buildActionBarComponent(Player player) {
        return Component.text()
                .append(buildIndicator(actionbarVanishLabel, isVanished(player)))
                .append(Component.space())
                .append(buildIndicator(actionbarFlyLabel, player.isFlying()))
                .append(Component.space())
                .append(buildIndicator(actionbarFrozenLabel, isFrozen(player)))
                .append(Component.space())
                .append(Component.text(actionbarLightStaffLabel, NamedTextColor.GREEN))
                .build();
    }

    private Component buildIndicator(String label, boolean enabled) {
        return Component.text()
                .append(Component.text(label, NamedTextColor.GRAY))
                .append(INDICATOR_ICON.color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build();
    }

    public boolean enableLightStaff(Player player) {
        if (player == null || isInLightStaff(player)) return false;

        StaffPlayerData data = new StaffPlayerData(player);
        LightStaffPlayers.put(player.getUniqueId(), data);
        saveSession(player, data, true);

        plugin.getSchedulerAdapter().runEntity(player, () -> giveLightStaffTools(player));

        applyLightStaffEntryState(player);
        setVanished(player, config.getBoolean("auto_vanish_on_enter", false));
        setFrozen(player, false);
        updateActionBar(player);
        plugin.audit("staff_mode_enable", player.getName(), player.getName(), "gameMode=" + player.getGameMode().name() + ", flying=" + player.isFlying());
        return true;
    }

    public boolean disableLightStaff(Player player) {
        if (player == null || !isInLightStaff(player)) return false;
        StaffPlayerData data = LightStaffPlayers.remove(player.getUniqueId());
        if (data == null) return false;

        data.restorePlayer(player);
        player.setGlowing(false);
        player.setInvisible(false);
        setVanished(player, false);
        setFrozen(player, false);
        saveSession(player, data, false);
        removePlayerFromTracking(player.getUniqueId());
        player.sendActionBar(Component.empty());
        plugin.audit("staff_mode_disable", player.getName(), player.getName(), "restored=true");
        return true;
    }

    public boolean isInLightStaff(Player player) {
        return player != null && LightStaffPlayers.containsKey(player.getUniqueId());
    }

    private void giveLightStaffTools(Player player) {
        if (player == null || !player.isOnline()) return;
        player.getInventory().clear();
        for (Map.Entry<String, StaffTool> entry : staffTools.entrySet()) {
            ToolDefinition definition = toolDefinitions.get(entry.getKey());
            if (definition != null && definition.enabled()) {
                player.getInventory().setItem(definition.slot(), entry.getValue().createTool());
            }
        }
    }

    public boolean handleToolInteraction(Player player, int slot) {
        String key = getToolKeyBySlot(slot);
        return key != null && handleToolInteraction(player, key);
    }

    public boolean handleToolInteraction(Player player, ItemStack item) {
        String key = getStaffToolId(item);
        return key != null && handleToolInteraction(player, key);
    }

    private boolean handleToolInteraction(Player player, String key) {
        if (!isInLightStaff(player)) return false;
        ToolDefinition definition = toolDefinitions.get(key);
        StaffTool tool = staffTools.get(key);
        if (!canUseTool(player, definition) || tool == null) return false;
        if (isOnCooldown(player, definition.cooldownMs())) return true;
        return tool.handleInteraction(player);
    }

    public boolean handlePlayerInteraction(Player staff, Player target, int slot) {
        String key = getToolKeyBySlot(slot);
        return key != null && handlePlayerInteraction(staff, target, key);
    }

    public boolean handlePlayerInteraction(Player staff, Player target, ItemStack item) {
        String key = getStaffToolId(item);
        return key != null && handlePlayerInteraction(staff, target, key);
    }

    private boolean handlePlayerInteraction(Player staff, Player target, String key) {
        if (!isInLightStaff(staff)) return false;
        ToolDefinition definition = toolDefinitions.get(key);
        StaffTool tool = staffTools.get(key);
        if (!canUseTool(staff, definition) || tool == null) return false;
        if (isOnCooldown(staff, definition.cooldownMs())) return true;
        return tool.handlePlayerInteraction(staff, target);
    }

    private String getToolKeyBySlot(int slot) {
        return toolKeysBySlot.get(slot);
    }

    private boolean canUseTool(Player player, ToolDefinition definition) {
        if (player == null || definition == null || !definition.enabled()) return false;
        return !definition.hasPermissionNode() || plugin.getPermissionManager().hasPermission(player, definition.permission());
    }

    public boolean hasToolPermission(Player player, ItemStack item) {
        return canUseTool(player, toolDefinitions.get(getStaffToolId(item)));
    }

    public String getToolDisplayName(ItemStack item) {
        String key = getStaffToolId(item);
        if (key == null) return "Unknown Tool";
        ToolDefinition definition = toolDefinitions.get(key);
        return definition == null ? key.substring(0, 1).toUpperCase() + key.substring(1) + " Tool" : definition.displayName();
    }

    public void setCooldown(Player player) {
        if (player != null) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public boolean isOnCooldown(Player player, long cooldownMs) {
        if (player == null || cooldownMs <= 0L) return false;
        Long lastUse = cooldowns.get(player.getUniqueId());
        return lastUse != null && System.currentTimeMillis() - lastUse < cooldownMs;
    }

    public long getGlobalCooldownRemainingMs(Player player) {
        if (player == null) return 0L;
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse == null) return 0L;
        long remaining = getCooldown() - (System.currentTimeMillis() - lastUse);
        return Math.max(0L, remaining);
    }

    private void saveSession(Player player, StaffPlayerData data, boolean enabled) {
        db.saveSession(
                player.getUniqueId(),
                new StorageSession(
                enabled,
                StaffPlayerData.serializeItemsBase64(data.getSavedInventory()),
                StaffPlayerData.serializeItemsBase64(data.getSavedArmor()),
                StaffPlayerData.serializeItemBase64(data.getSavedOffhand()),
                data.getSavedExp(), data.getSavedLevel(),
                data.isSavedFlying(), data.isSavedAllowFlight(),
                data.getSavedGameMode().name()
                )
        );
    }

    public void loadLightStaffDataAsync(Player player) {
        if (player == null) return;
        hideVanishedPlayersFromJoiner(player);

        db.loadSession(player.getUniqueId(), false).thenAccept(session -> {
                    if (session != null && session.enabled()) {
                        StaffPlayerData data = readStaffPlayerData(session);
                        plugin.getSchedulerAdapter().runEntity(player, () -> {
                            LightStaffPlayers.put(player.getUniqueId(), data);
                            giveLightStaffTools(player);
                            applyLightStaffEntryState(player);
                            setVanished(player, config.getBoolean("auto_vanish_on_enter", false));
                            setFrozen(player, false);
                            updateActionBar(player);
                        });
                    }
                });
    }

    private StaffPlayerData readStaffPlayerData(StorageSession session) {
        String gameModeName = session.gameMode();
        GameMode gameMode = GameMode.SURVIVAL;
        if (gameModeName != null) {
            try {
                gameMode = GameMode.valueOf(gameModeName);
            } catch (IllegalArgumentException ignored) {
                Console.warn("Invalid saved game mode '" + gameModeName + "'. Falling back to SURVIVAL.");
            }
        }

        return new StaffPlayerData(
                StaffPlayerData.deserializeItemsBase64(session.inventory()),
                StaffPlayerData.deserializeItemsBase64(session.armor()),
                StaffPlayerData.deserializeItemBase64(session.offhand()),
                session.exp(),
                session.level(),
                session.flying(),
                session.allowFlight(),
                gameMode
        );
    }

    private void applyLightStaffEntryState(Player player) {
        GameMode gameMode = resolveEntryGameMode(player);
        if (gameMode != null) {
            player.setGameMode(gameMode);
        }

        boolean allowFlight = config.getBoolean("allow_flight_on_enter", true);
        boolean startFlying = config.getBoolean("start_flying_on_enter", true);
        if (allowFlight) {
            player.setAllowFlight(true);
            player.setFlying(startFlying);
        } else if (!player.getAllowFlight()) {
            player.setFlying(false);
        }
    }

    private GameMode resolveEntryGameMode(Player player) {
        String configured = config.getString("gamemode_on_enter", "permission");
        String normalized = configured == null ? "permission" : configured.trim().toLowerCase();
        switch (normalized) {
            case "keep":
                return null;
            case "permission":
                return plugin.getPermissionManager().canUseCreativeBypass(player) ? GameMode.CREATIVE : GameMode.SURVIVAL;
            case "creative":
                return plugin.getPermissionManager().canUseCreativeBypass(player) ? GameMode.CREATIVE : GameMode.SURVIVAL;
            case "survival":
                return GameMode.SURVIVAL;
            case "adventure":
                return GameMode.ADVENTURE;
            case "spectator":
                return GameMode.SPECTATOR;
            default:
                Console.warn("Invalid gamemode_on_enter '" + configured + "'. Falling back to permission.");
                return plugin.getPermissionManager().canUseCreativeBypass(player) ? GameMode.CREATIVE : GameMode.SURVIVAL;
        }
    }

    private void loadModerationStatesAsync() {
        db.loadActiveModerationStates().thenAccept(records -> {
                    for (ModerationRecord record : records) {
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(record.uuid());
                        } catch (IllegalArgumentException e) {
                            Console.warn("Ignoring invalid moderation state UUID: " + record.uuid());
                            continue;
                        }

                        if (record.vanished()) {
                            vanishedPlayers.add(uuid);
                        }
                        if (record.frozen()) {
                            frozenPlayers.add(uuid);
                            String reason = record.freezeReason();
                            freezeReasons.put(uuid, reason == null || reason.isBlank() ? getFreezeBanMessage() : reason);
                        }
                    }

                    plugin.getSchedulerAdapter().runGlobal(() -> {
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            plugin.getSchedulerAdapter().runEntity(online, () -> {
                                if (vanishedPlayers.contains(online.getUniqueId())) {
                                    setVanished(online, true);
                                }
                                if (frozenPlayers.contains(online.getUniqueId())) {
                                    setFrozen(online, true, freezeReasons.getOrDefault(online.getUniqueId(), getFreezeBanMessage()));
                                }
                                hideVanishedPlayersFromJoiner(online);
                            });
                        }
                    });
                });
    }

    private void hideVanishedPlayersFromJoiner(Player joiner) {
        if (joiner == null || plugin.getPermissionManager().canSeeVanished(joiner)) return;

        for (UUID vanishedUuid : vanishedPlayers) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedUuid);
            if (vanishedPlayer != null && vanishedPlayer.isOnline() && !vanishedPlayer.equals(joiner)) {
                joiner.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    private void removePlayerFromTracking(UUID uuid) {
        cooldowns.remove(uuid);
        lastActionBarCache.remove(uuid);
        lastStateHash.remove(uuid);
    }

    public boolean isLightStaffItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(LightStaff.getInstance().getStaffToolKey(), PersistentDataType.BYTE);
    }

    public String getStaffToolId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
                LightStaff.getInstance().getStaffToolIdKey(),
                PersistentDataType.STRING
        );
    }

    public Material resolveToolMaterial(String key, Material fallback) {
        ToolDefinition definition = toolDefinitions.get(key);
        return definition != null ? definition.material() : fallback;
    }

    public ToolDefinition getToolDefinition(String key) {
        return toolDefinitions.get(key);
    }

    public int getEnabledToolCount() {
        int count = 0;
        for (ToolDefinition definition : toolDefinitions.values()) {
            if (definition.enabled()) count++;
        }
        return count;
    }

    public int getTotalToolCount() {
        return toolDefinitions.size();
    }

    public double getToolDouble(String key, String path, double fallback) {
        return toolsConfig.getDouble("tools." + key + "." + path, fallback);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public boolean isVanished(Player player) {
        return player != null && vanishedPlayers.contains(player.getUniqueId());
    }

    public void setVanished(Player player, boolean vanished) {
        setVanished(player, vanished, player == null ? "-" : player.getName());
    }

    public void setVanished(Player player, boolean vanished, String actor) {
        if (player == null) return;
        boolean wasVanished = vanishedPlayers.contains(player.getUniqueId());
        String auditActor = actor == null || actor.isBlank() ? "-" : actor;

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "vanished_" + player.getUniqueId().toString().substring(0, 14);
        Team team = scoreboard.getTeam(teamName);

        if (vanished) {
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            }

            vanishedPlayers.add(player.getUniqueId());
            team.addEntry(player.getName());
            player.setGlowing(true);

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                plugin.getSchedulerAdapter().runEntity(online, () -> {
                    if (!plugin.getPermissionManager().canSeeVanished(online)) {
                        online.hidePlayer(plugin, player);
                    }
                });
            }
            saveModerationState(player.getUniqueId());
            if (!wasVanished) {
                plugin.audit("vanish_enable", auditActor, player.getName(), "visibleToSeePermission=true");
            }
            return;
        }

        vanishedPlayers.remove(player.getUniqueId());
        player.setGlowing(false);

        if (team != null) {
            team.removeEntry(player.getName());
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                plugin.getSchedulerAdapter().runEntity(online, () -> online.showPlayer(plugin, player));
            }
        }
        saveModerationState(player.getUniqueId());
        if (wasVanished) {
            plugin.audit("vanish_disable", auditActor, player.getName(), "visible=true");
        }
    }

    public void setFrozen(Player player, boolean frozen) {
        setFrozen(player, frozen, getFreezeBanMessage());
    }

    public void setFrozen(Player player, boolean frozen, String reason) {
        setFrozen(player, frozen, reason, "-");
    }

    public void setFrozen(Player player, boolean frozen, String reason, String actor) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        boolean wasFrozen = frozenPlayers.contains(uuid);
        if (frozen) {
            frozenPlayers.add(uuid);
            freezeReasons.put(uuid, reason == null || reason.isBlank() ? getFreezeBanMessage() : reason);
            saveModerationState(uuid);
            if (!wasFrozen) {
                plugin.audit("freeze_enable", actor, player.getName(), "reason=" + freezeReasons.get(uuid));
            }
            return;
        }

        frozenPlayers.remove(uuid);
        freezeReasons.remove(uuid);
        saveModerationState(uuid);
        if (wasFrozen) {
            plugin.audit("freeze_disable", actor, player.getName(), "reason=cleared");
        }
    }

    public boolean isFrozen(Player player) {
        return player != null && frozenPlayers.contains(player.getUniqueId());
    }

    public boolean isFrozenCommandAllowed(String command) {
        if (command == null || command.isBlank()) return false;
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        return frozenAllowedCommands.contains(normalized.startsWith("/") ? normalized : "/" + normalized);
    }

    public String getFreezeReason(Player player) {
        if (player == null) return "";
        return freezeReasons.getOrDefault(player.getUniqueId(), "");
    }

    public void handleFrozenQuit(Player player) {
        if (!isFrozen(player)) return;

        String reason = freezeReasons.getOrDefault(player.getUniqueId(), getFreezeBanMessage());
        String duration = getFreezeBanDuration();
        String configuredCommand = config.getString("freeze_punishment_command", "");

        plugin.getSchedulerAdapter().runEntity(player, () -> {
            if (configuredCommand != null && !configuredCommand.isBlank()) {
                String parsedCommand = CommandTemplate.render(configuredCommand, Map.of(
                        "player", player.getName(),
                        "uuid", player.getUniqueId().toString(),
                        "duration", duration,
                        "reason", reason
                ));
                plugin.getSchedulerAdapter().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand));
            } else {
                Console.warn(player.getName() + " disconnected while frozen: " + reason);
            }
            plugin.audit("frozen_disconnect", player.getName(), player.getName(), "duration=" + duration + ", reason=" + reason);
            player.kick(Component.text(getFreezeBanMessage()));
        });
    }

    private void saveModerationState(UUID uuid) {
        boolean vanished = vanishedPlayers.contains(uuid);
        boolean frozen = frozenPlayers.contains(uuid);
        db.saveModerationState(uuid, vanished, frozen, freezeReasons.get(uuid));
    }

    public String buildStatus(Player player) {
        if (player == null) return "Player is not online.";
        String freezeReason = getFreezeReason(player);
        return "Staff Mode status for " + player.getName() +
                ": staffMode=" + isInLightStaff(player) +
                ", vanished=" + isVanished(player) +
                ", frozen=" + isFrozen(player) +
                (freezeReason.isBlank() ? "" : ", freezeReason=\"" + freezeReason + "\"") +
                ", flying=" + player.isFlying() +
                ", gameMode=" + player.getGameMode().name() +
                ", cooldownRemainingMs=" + getGlobalCooldownRemainingMs(player);
    }

    public void recoverSession(Player target, CommandSender sender) {
        if (target == null) {
            plugin.getMessageManager().send(sender, "commands.player_not_online", "<red>Player is not online.</red>");
            return;
        }

        if (isInLightStaff(target)) {
            if (disableLightStaff(target)) {
                plugin.getMessageManager().send(sender, "lightstaff.recover_active", "<green>Recovered active Staff Mode session for {player}.</green>", Map.of("player", target.getName()));
                plugin.audit("session_recover_active", sender.getName(), target.getName(), "source=memory");
            } else {
                plugin.getMessageManager().send(sender, "lightstaff.recover_failed", "<red>Could not recover active Staff Mode session for {player}.</red>", Map.of("player", target.getName()));
            }
            return;
        }

        db.loadSession(target.getUniqueId(), true).thenAccept(session -> {
                    if (session == null) {
                        plugin.getSchedulerAdapter().runGlobal(() ->
                                plugin.getMessageManager().send(sender, "lightstaff.recover_none", "<yellow>No saved active Staff Mode session found for {player}.</yellow>", Map.of("player", target.getName())));
                        return;
                    }
                    StaffPlayerData data = readStaffPlayerData(session);
                    plugin.getSchedulerAdapter().runEntity(target, () -> {
                        data.restorePlayer(target);
                        setVanished(target, false);
                        setFrozen(target, false);
                        saveSession(target, data, false);
                        plugin.getMessageManager().send(sender, "lightstaff.recover_saved", "<green>Recovered saved Staff Mode session for {player}.</green>", Map.of("player", target.getName()));
                        plugin.audit("session_recover_saved", sender.getName(), target.getName(), "source=sqlite");
                    });
                });
    }

    public void shutdown() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        lastActionBarCache.clear();
        lastStateHash.clear();
    }

    public long getCooldown() {
        return config.getLong("cooldown", 1000);
    }

    public boolean shouldRestoreOnQuit() {
        return config.getBoolean("restore_on_quit", true);
    }

    public int getFreezeBanDays() {
        return config.getInt("freeze_ban_days", 30);
    }

    public String getFreezeBanDuration() {
        return getFreezeBanDays() + "d";
    }

    public String getFreezeBanMessage() {
        if (config.isList("freeze_ban_message")) {
            List<String> lines = config.getStringList("freeze_ban_message")
                    .stream()
                    .filter(line -> line != null && !line.isBlank())
                    .toList();
            if (!lines.isEmpty()) {
                return String.join("\n", lines);
            }
        }
        return config.getString("freeze_ban_message", "Disconnected while frozen by staff.\nOpen a ticket if this was a mistake.");
    }
}
