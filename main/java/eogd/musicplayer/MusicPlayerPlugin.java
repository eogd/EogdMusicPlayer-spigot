package eogd.musicplayer;

import net.kyori.adventure.text.Component;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MusicPlayerPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private MusicCommands musicCommands;
    private HttpFileServer httpFileServer;
    private ResourcePackGenerator resourcePackGenerator;

    private final Map<UUID, PendingOnlineSound> pendingOnlineSounds = new ConcurrentHashMap<>();

    public static class PendingOnlineSound {
        public final String packFileName;
        public final String soundEventName;
        public final String sha1;
        public final Player targetPlayer;

        public PendingOnlineSound(String packFileName, String soundEventName, String sha1, Player targetPlayer) {
            this.packFileName = packFileName;
            this.soundEventName = soundEventName;
            this.sha1 = sha1;
            this.targetPlayer = targetPlayer;
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("EogdMusicPlayer 正在启动 (版本 " + getDescription().getVersion() + ")...");
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        getLogger().info("正在初始化 DatabaseManager...");
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initializeDatabase();
            getLogger().info("DatabaseManager 初始化成功。");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "初始化 DatabaseManager 时发生严重错误！", e);
        }

        if (config.getBoolean("httpServer.enabled", false)) {
            String configuredPublicAddress = config.getString("httpServer.publicAddress", "");
            int httpPort = config.getInt("httpServer.port", 8123);
            String servePath = config.getString("httpServer.servePath", "musicpacks");

            if (configuredPublicAddress.isEmpty()) {
                getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                getLogger().severe("HTTP 服务器的 'publicAddress' 未在 config.yml 中配置! 在线播放功能将无法对外部玩家工作!");
                getLogger().severe("请将其设置为你的服务器的公共域名或IP地址 (不含端口号)。");
                getLogger().severe("例如: publicAddress: \"play.example.com\" 或 publicAddress: \"123.45.67.89\"");
                getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }

            File tempPackStorageDir = new File(getDataFolder(), config.getString("httpServer.tempDirectory", "temp_packs"));
            if (!tempPackStorageDir.exists() && !tempPackStorageDir.mkdirs()) {
                getLogger().severe("无法创建 HTTP 服务器的临时目录: " + tempPackStorageDir.getAbsolutePath() + "。在线播放功能将不可用。");
            } else {
                resourcePackGenerator = new ResourcePackGenerator(this, tempPackStorageDir, config.getLong("httpServer.maxDownloadSizeBytes", 0));
                httpFileServer = new HttpFileServer(this, servePath, tempPackStorageDir);
                httpFileServer.start(httpPort);
                if (!httpFileServer.isRunning()) {
                    getLogger().severe("内置 HTTP 服务器未能启动！在线播放功能将不可用。");
                    httpFileServer = null;
                    resourcePackGenerator = null;
                } else {
                    getLogger().info("HTTP 服务器已启动。临时资源包将通过类似以下 URL 提供: " +
                            httpFileServer.getFileUrl(configuredPublicAddress, httpPort, "example.zip"));
                }
            }
        } else {
            getLogger().info("内置 HTTP 服务器已在配置中禁用。在线播放功能将不可用。");
        }

        getLogger().info("正在创建 MusicCommands 实例...");
        try {
            this.musicCommands = new MusicCommands(this);
            getLogger().info("MusicCommands 实例已创建。");

            PluginCommand bfCommand = this.getCommand("bf");
            if (bfCommand != null) {
                bfCommand.setExecutor(this.musicCommands);
                bfCommand.setTabCompleter(this.musicCommands);
            } else { getLogger().severe("无法获取指令 'bf'！"); }

            PluginCommand playUrlCommand = this.getCommand("playurl");
            if (playUrlCommand != null) {
                playUrlCommand.setExecutor(this.musicCommands);
                playUrlCommand.setTabCompleter(this.musicCommands);
            } else { getLogger().severe("无法获取指令 'playurl'！"); }
            getLogger().info("指令已注册。");

        } catch (NoClassDefFoundError ncdfe) {
            getLogger().log(Level.SEVERE, "创建 MusicCommands 实例时发生 NoClassDefFoundError！", ncdfe);
            this.musicCommands = null;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "创建 MusicCommands 实例或注册指令时发生未知错误。", e);
            this.musicCommands = null;
        }

        getServer().getPluginManager().registerEvents(new PlayerResourceListener(this), this);
        getLogger().info("PlayerResourceListener 已注册。");

        File downloadDir = new File(getDataFolder(), "downloads");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        getLogger().info("========================================");
        if (this.musicCommands != null && (httpFileServer != null || !config.getBoolean("httpServer.enabled", false))) {
            getLogger().info(getName() + " 已成功启用!");
        } else {
            getLogger().severe(getName() + " 未能完全启用 (HTTP 服务器或指令可能存在问题).");
        }
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("EogdMusicPlayer 正在卸载...");
        if (httpFileServer != null && httpFileServer.isRunning()) {
            httpFileServer.stop();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        File tempPackStorageDir = new File(getDataFolder(), getConfig().getString("httpServer.tempDirectory", "temp_packs"));
        if (tempPackStorageDir.exists() && tempPackStorageDir.isDirectory()) {
            File[] tempFiles = tempPackStorageDir.listFiles();
            if (tempFiles != null) {
                for (File file : tempFiles) {
                    if ((file.getName().startsWith("temp_music_pack_") && file.getName().endsWith(".zip")) ||
                            (file.getName().startsWith("source_onlinesong_") && file.getName().endsWith(".ogg")) ) {
                        if (file.delete()) {
                            getLogger().info("已删除临时文件: " + file.getName());
                        } else {
                            getLogger().warning("无法删除临时文件: " + file.getName());
                        }
                    }
                }
            }
        }
        getLogger().info(getName() + " 已禁用。");
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public MusicCommands getMusicCommandsInstance() { return this.musicCommands; }
    public ResourcePackGenerator getResourcePackGenerator() { return resourcePackGenerator; }
    public HttpFileServer getHttpFileServer() { return httpFileServer; }
    public void addPendingOnlineSound(UUID playerId, PendingOnlineSound soundInfo) { pendingOnlineSounds.put(playerId, soundInfo); }
    public PendingOnlineSound getPendingOnlineSound(UUID playerId) { return pendingOnlineSounds.get(playerId); }
    public void clearPendingOnlineSound(UUID playerId) { pendingOnlineSounds.remove(playerId); }

    public void reloadPluginConfiguration() {
        reloadConfig();
        if (httpFileServer != null && httpFileServer.isRunning()) {
            httpFileServer.stop();
            httpFileServer = null;
        }
        if (resourcePackGenerator != null) {
            resourcePackGenerator = null;
        }

        FileConfiguration newConfig = getConfig();
        if (newConfig.getBoolean("httpServer.enabled", false)) {
            String configuredPublicAddress = newConfig.getString("httpServer.publicAddress", "");
            int httpPort = newConfig.getInt("httpServer.port", 8123);
            String servePath = newConfig.getString("httpServer.servePath", "musicpacks");

            if (configuredPublicAddress.isEmpty()) {
                getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                getLogger().severe("HTTP 服务器的 'publicAddress' 在重载后仍未配置! 在线播放功能将无法对外部玩家工作!");
                getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }

            File tempPackStorageDir = new File(getDataFolder(), newConfig.getString("httpServer.tempDirectory", "temp_packs"));
            if (!tempPackStorageDir.exists() && !tempPackStorageDir.mkdirs()) {
                getLogger().severe("重载时无法创建 HTTP 服务器的临时目录: " + tempPackStorageDir.getAbsolutePath());
            } else {
                resourcePackGenerator = new ResourcePackGenerator(this, tempPackStorageDir, newConfig.getLong("httpServer.maxDownloadSizeBytes", 0));
                httpFileServer = new HttpFileServer(this, servePath, tempPackStorageDir);
                httpFileServer.start(httpPort);
                if (!httpFileServer.isRunning()) {
                    getLogger().severe("重载后，内置 HTTP 服务器未能启动！在线播放功能将不可用。");
                    httpFileServer = null;
                    resourcePackGenerator = null;
                } else {
                    getLogger().info("HTTP 服务器已根据新配置重载并启动。");
                }
            }
        } else {
            getLogger().info("重载后，内置 HTTP 服务器已在配置中禁用。");
        }
        getLogger().info(getName() + " 的配置已重载。");
    }
}