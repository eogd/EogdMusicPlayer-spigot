package eogd.musicplayer;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final MusicPlayerPlugin plugin;
    private Connection connection;
    private final String defaultMusicUrlFromUser = "https://example.com/default_music.ogg"; // 替换为有效的默认ogg

    public static final String MUSIC_DOWNLOAD_URL_KEY = "music_download_url";
    public static final String RESOURCE_PACK_URL_KEY = "resource_pack_url";
    public static final String RESOURCE_PACK_SHA1_KEY = "resource_pack_sha1";

    public DatabaseManager(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            File dataFolder = new File(plugin.getDataFolder(), "EogdMusicPlayer.db");
            if (!dataFolder.exists()) {
                try {
                    if (!dataFolder.getParentFile().exists()) {
                        dataFolder.getParentFile().mkdirs();
                    }
                    dataFolder.createNewFile();
                } catch (java.io.IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "创建数据库文件失败!", e);
                    throw new SQLException("创建数据库文件失败", e);
                }
            }
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder.getAbsolutePath());
            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite JDBC 驱动 (org.sqlite.JDBC) 未找到!", e);
                throw new SQLException("SQLite JDBC 驱动未找到!", e);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "连接到 SQLite 数据库失败: " + dataFolder.getAbsolutePath(), e);
                throw e;
            }
        }
        return connection;
    }

    public synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("数据库连接已关闭。");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "关闭 SQLite 连接时出错", e);
        }
    }

    public void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS music_settings (" +
                " key TEXT PRIMARY KEY NOT NULL," +
                " value TEXT" +
                ");";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            plugin.getLogger().fine("数据库表 'music_settings' 初始化/检查完毕。");

            if (getSetting(MUSIC_DOWNLOAD_URL_KEY) == null) {
                saveSetting(MUSIC_DOWNLOAD_URL_KEY, defaultMusicUrlFromUser);
                plugin.getLogger().info("默认的音乐下载URL ('" + defaultMusicUrlFromUser + "') 已设置到数据库。");
            }
            if (getSetting(RESOURCE_PACK_URL_KEY) == null) {
                saveSetting(RESOURCE_PACK_URL_KEY, "");
                plugin.getLogger().info("资源包URL已在数据库中初始化为空字符串。");
            }
            if (getSetting(RESOURCE_PACK_SHA1_KEY) == null) {
                saveSetting(RESOURCE_PACK_SHA1_KEY, "");
                plugin.getLogger().info("资源包SHA1已在数据库中初始化为空字符串。");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "初始化数据库表或设置默认值时出错", e);
        }
    }

    public synchronized void saveSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO music_settings (key, value) VALUES (?, ?);";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存设置到数据库: " + key, e);
        }
    }

    public synchronized String getSetting(String key) {
        String sql = "SELECT value FROM music_settings WHERE key = ?;";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "无法从数据库检索设置: " + key, e);
        }
        return null;
    }

    public String getSetting(String key, String defaultValue) {
        String value = getSetting(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }
}