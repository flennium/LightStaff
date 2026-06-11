package org.flennn.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.Plugin;
import org.flennn.LightStaff;
import org.flennn.util.Console;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StorageManager {
    private final Plugin plugin;
    private Connection connection;
    private final StorageType storageType;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final File jsonSessionsFile;
    private final File jsonModerationFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Type sessionMapType = new TypeToken<Map<String, StorageSession>>() {}.getType();
    private final Type moderationMapType = new TypeToken<Map<String, ModerationRecord>>() {}.getType();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "LightStaff-Storage");
        thread.setDaemon(true);
        return thread;
    });

    public StorageManager(Plugin plugin) {
        this.plugin = plugin;
        this.storageType = resolveStorageType();
        ConnectionSettings settings = buildConnectionSettings();
        this.jdbcUrl = settings.jdbcUrl();
        this.username = settings.username();
        this.password = settings.password();
        File dataFolder = ensureDataFolder();
        this.jsonSessionsFile = new File(dataFolder, "sessions.json");
        this.jsonModerationFile = new File(dataFolder, "moderation_states.json");
        if (storageType == StorageType.JSON) {
            setupJsonFiles();
        } else {
            connect();
            setupTables();
        }
    }

    private StorageType resolveStorageType() {
        String configured = LightStaff.getInstance().getPluginConfig().getString("storage.type", "sqlite");
        try {
            return StorageType.valueOf((configured == null ? "sqlite" : configured).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Console.warn("Invalid storage.type '" + configured + "'. Falling back to sqlite.");
            return StorageType.SQLITE;
        }
    }

    private ConnectionSettings buildConnectionSettings() {
        if (storageType == StorageType.SQLITE) {
            File dataFolder = ensureDataFolder();
            File targetFile = new File(dataFolder, "lightstaff.db");
            return new ConnectionSettings("jdbc:sqlite:" + targetFile.getAbsolutePath(), null, null);
        }

        if (storageType == StorageType.JSON) {
            return new ConnectionSettings(null, null, null);
        }

        String root = "storage." + storageType.configKey() + ".";
        String host = LightStaff.getInstance().getPluginConfig().getString(root + "host", "localhost");
        int port = LightStaff.getInstance().getPluginConfig().getInt(root + "port", 3306);
        String database = LightStaff.getInstance().getPluginConfig().getString(root + "database", "lightstaff");
        String user = LightStaff.getInstance().getPluginConfig().getString(root + "username", "root");
        String pass = LightStaff.getInstance().getPluginConfig().getString(root + "password", "");
        boolean useSsl = LightStaff.getInstance().getPluginConfig().getBoolean(root + "use_ssl", false);
        String extra = LightStaff.getInstance().getPluginConfig().getString(root + "extra_parameters", "");
        String separator = extra == null || extra.isBlank() ? "" : "&" + extra;

        if (storageType == StorageType.MYSQL) {
            return new ConnectionSettings("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl + separator, user, pass);
        }
        return new ConnectionSettings("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSsl=" + useSsl + separator, user, pass);
    }

    private synchronized void connect() {
        if (storageType == StorageType.JSON) return;
        try {
            loadDriver();
            if (storageType == StorageType.SQLITE) {
                connection = DriverManager.getConnection(jdbcUrl);
                Console.success("SQLite connected at data/lightstaff.db");
            } else {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
                Console.success(storageType.displayName() + " connected.");
            }
        } catch (SQLException e) {
            Console.error("Failed to connect to " + storageType.displayName() + ": " + e.getMessage());
        }
    }

    private void loadDriver() throws SQLException {
        String driverClass = switch (storageType) {
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case JSON -> "";
        };
        if (driverClass.isBlank()) return;
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + driverClass, e);
        }
    }

    private void setupTables() {
        if (storageType == StorageType.JSON) return;
        if (connection == null) {
            Console.error("Cannot create storage tables because the connection is not available.");
            return;
        }

        String uuidType = storageType == StorageType.SQLITE ? "TEXT" : "VARCHAR(36)";
        String textType = storageType == StorageType.SQLITE ? "TEXT" : "LONGTEXT";
        String keyType = storageType == StorageType.SQLITE ? "TEXT" : "VARCHAR(64)";

        String sessionsSql = "CREATE TABLE IF NOT EXISTS lightstaff_players (" +
                "uuid " + uuidType + " PRIMARY KEY," +
                "enabled INTEGER NOT NULL," +
                "inventory " + textType + "," +
                "armor " + textType + "," +
                "offhand " + textType + "," +
                "exp REAL," +
                "level INTEGER," +
                "flying INTEGER," +
                "allow_flight INTEGER," +
                "game_mode VARCHAR(32)" +
                ")";
        String moderationSql = "CREATE TABLE IF NOT EXISTS moderation_states (" +
                "uuid " + uuidType + " PRIMARY KEY," +
                "vanished INTEGER NOT NULL DEFAULT 0," +
                "frozen INTEGER NOT NULL DEFAULT 0," +
                "freeze_reason " + textType + "," +
                "updated_at INTEGER NOT NULL" +
                ")";
        String schemaSql = "CREATE TABLE IF NOT EXISTS schema_meta (" +
                "meta_key " + keyType + " PRIMARY KEY," +
                "meta_value " + textType + " NOT NULL" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sessionsSql);
            stmt.execute(moderationSql);
            stmt.execute(schemaSql);
            writeSchemaVersion(stmt);
        } catch (SQLException e) {
            Console.error("Failed to create LightStaff storage tables: " + e.getMessage());
        }
    }

    private void writeSchemaVersion(Statement stmt) throws SQLException {
        stmt.executeUpdate("REPLACE INTO schema_meta (meta_key, meta_value) VALUES ('schema_version', '1')");
    }

    public synchronized Connection getConnection() throws SQLException {
        if (storageType == StorageType.JSON) {
            throw new SQLException("JSON storage does not use JDBC connections.");
        }
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    public String getStorageDisplayName() {
        return storageType.displayName();
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                Console.warn("Timed out waiting for LightStaff storage tasks to finish.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            Console.error("Failed to close storage connection: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> runAsyncUpdate(String sql, Object... params) {
        if (storageType == StorageType.JSON) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                Console.error(storageType.displayName() + " update failed: " + e.getMessage());
            }
        }, executor);
    }

    public <T> CompletableFuture<T> runAsyncQuery(String sql, ResultSetHandler<T> handler, Object... params) {
        if (storageType == StorageType.JSON) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    return handler.handle(rs);
                }
            } catch (SQLException e) {
                Console.error(storageType.displayName() + " query failed: " + e.getMessage());
                return null;
            }
        }, executor);
    }

    private enum StorageType {
        SQLITE,
        MYSQL,
        MARIADB,
        JSON;

        String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }

        String displayName() {
            return switch (this) {
                case SQLITE -> "SQLite";
                case MYSQL -> "MySQL";
                case MARIADB -> "MariaDB";
                case JSON -> "JSON";
            };
        }
    }

    public CompletableFuture<Void> saveSession(UUID uuid, StorageSession session) {
        if (storageType == StorageType.JSON) {
            return CompletableFuture.runAsync(() -> {
                Map<String, StorageSession> sessions = readSessionJson();
                sessions.put(uuid.toString(), session);
                writeJsonMap(jsonSessionsFile, sessions);
            }, executor);
        }

        return runAsyncUpdate(
                "REPLACE INTO lightstaff_players (uuid, enabled, inventory, armor, offhand, exp, level, flying, allow_flight, game_mode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                uuid.toString(), session.enabled() ? 1 : 0,
                session.inventory(), session.armor(), session.offhand(),
                session.exp(), session.level(),
                session.flying() ? 1 : 0, session.allowFlight() ? 1 : 0,
                session.gameMode()
        );
    }

    public CompletableFuture<StorageSession> loadSession(UUID uuid, boolean enabledOnly) {
        if (storageType == StorageType.JSON) {
            return CompletableFuture.supplyAsync(() -> {
                StorageSession session = readSessionJson().get(uuid.toString());
                if (session == null || (enabledOnly && !session.enabled())) return null;
                return session;
            }, executor);
        }

        String sql = "SELECT * FROM lightstaff_players WHERE uuid = ?" + (enabledOnly ? " AND enabled = 1" : "");
        return runAsyncQuery(sql, rs -> rs.next() ? readSqlSession(rs) : null, uuid.toString());
    }

    public CompletableFuture<List<ModerationRecord>> loadActiveModerationStates() {
        if (storageType == StorageType.JSON) {
            return CompletableFuture.supplyAsync(() -> readModerationJson()
                    .values()
                    .stream()
                    .filter(record -> record.vanished() || record.frozen())
                    .toList(), executor);
        }

        return runAsyncQuery(
                "SELECT * FROM moderation_states WHERE vanished = 1 OR frozen = 1",
                rs -> {
                    java.util.ArrayList<ModerationRecord> records = new java.util.ArrayList<>();
                    while (rs.next()) {
                        records.add(new ModerationRecord(
                                rs.getString("uuid"),
                                rs.getInt("vanished") == 1,
                                rs.getInt("frozen") == 1,
                                rs.getString("freeze_reason"),
                                rs.getLong("updated_at")
                        ));
                    }
                    return records;
                }
        );
    }

    public CompletableFuture<Void> saveModerationState(UUID uuid, boolean vanished, boolean frozen, String freezeReason) {
        if (storageType == StorageType.JSON) {
            return CompletableFuture.runAsync(() -> {
                Map<String, ModerationRecord> states = readModerationJson();
                if (!vanished && !frozen) {
                    states.remove(uuid.toString());
                } else {
                    states.put(uuid.toString(), new ModerationRecord(uuid.toString(), vanished, frozen, freezeReason, System.currentTimeMillis()));
                }
                writeJsonMap(jsonModerationFile, states);
            }, executor);
        }

        if (!vanished && !frozen) {
            return runAsyncUpdate("DELETE FROM moderation_states WHERE uuid = ?", uuid.toString());
        }

        return runAsyncUpdate(
                "REPLACE INTO moderation_states (uuid, vanished, frozen, freeze_reason, updated_at) VALUES (?, ?, ?, ?, ?)",
                uuid.toString(),
                vanished ? 1 : 0,
                frozen ? 1 : 0,
                freezeReason,
                System.currentTimeMillis()
        );
    }

    private StorageSession readSqlSession(ResultSet rs) throws SQLException {
        return new StorageSession(
                rs.getInt("enabled") == 1,
                rs.getString("inventory"),
                rs.getString("armor"),
                rs.getString("offhand"),
                rs.getFloat("exp"),
                rs.getInt("level"),
                rs.getInt("flying") == 1,
                rs.getInt("allow_flight") == 1,
                rs.getString("game_mode")
        );
    }

    private File ensureDataFolder() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            Console.error("Could not create data folder.");
        }
        return dataFolder;
    }

    private void setupJsonFiles() {
        createJsonFile(jsonSessionsFile);
        createJsonFile(jsonModerationFile);
        Console.success("JSON storage connected at data/sessions.json");
    }

    private void createJsonFile(File file) {
        if (file.exists()) return;
        writeJsonMap(file, new LinkedHashMap<>());
    }

    private <T> Map<String, T> readJsonMap(File file, Type type) {
        if (!file.exists()) return new LinkedHashMap<>();
        try (FileReader reader = new FileReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            Map<String, T> data = gson.fromJson(reader, type);
            return data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        } catch (IOException e) {
            Console.error("Could not read " + file.getName() + ": " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, StorageSession> readSessionJson() {
        return readJsonMap(jsonSessionsFile, sessionMapType);
    }

    private Map<String, ModerationRecord> readModerationJson() {
        return readJsonMap(jsonModerationFile, moderationMapType);
    }

    private void writeJsonMap(File file, Map<?, ?> data) {
        try (FileWriter writer = new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8, false)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            Console.error("Could not write " + file.getName() + ": " + e.getMessage());
        }
    }

    public record StorageSession(
            boolean enabled,
            String inventory,
            String armor,
            String offhand,
            float exp,
            int level,
            boolean flying,
            boolean allowFlight,
            String gameMode
    ) {
    }

    public record ModerationRecord(
            String uuid,
            boolean vanished,
            boolean frozen,
            String freezeReason,
            long updatedAt
    ) {
    }

    private record ConnectionSettings(String jdbcUrl, String username, String password) {
    }

    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
} 
