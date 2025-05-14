package eogd.musicplayer;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MusicPlayerPlugin extends JavaPlugin {

    private MusicCommands musicCommands;
    private HttpFileServer httpFileServer;
    private ResourcePackGenerator resourcePackGenerator;

    private final Map<UUID, PendingOnlineSound> pendingSingleUserSounds = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerPendingPackType = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerCurrentMusicPackFile = new ConcurrentHashMap<>();

    private final Map<String, MusicRoom> activeMusicRooms = new ConcurrentHashMap<>();
    private final List<PresetSong> presetSongsList = new ArrayList<>();
    private BukkitTask roomCleanupTask;

    private boolean useMergedPackLogic = false;
    private File basePackFile = null;
    private String basePackFileNameConfig = "";
    private String basePackPromptMessage = "";
    private String originalPackPromptMessage = "";
    private String basePackSha1 = null;

    private final Map<String, ResourcePackGenerator.PackInfo> prewarmedPresetPacks = new ConcurrentHashMap<>();
    private boolean presetPrewarmingEnabled = false;

    public record PendingOnlineSound(String packFileName, String soundEventName, String sha1, Player targetPlayer, String packUrl) {}

    @Override
    public void onEnable() {
        getLogger().info("EogdMusicPlayer 正在启动 (版本 " + getDescription().getVersion() + ")...");

        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                getLogger().severe("无法创建插件数据文件夹: " + getDataFolder().getAbsolutePath() + " - 插件功能可能受限!");
            }
        }
        saveDefaultConfig();
        loadConfiguration();

        if (getConfig().getBoolean("httpServer.enabled", false)) {
            initializeHttpServerAndGenerator();
            if (this.resourcePackGenerator != null && isPresetPrewarmingEnabled()) {
                initializePresetPrewarming();
            }
        } else {
            getLogger().info("内置 HTTP 服务器已在配置中禁用。在线播放功能将不可用。");
        }

        musicCommands = new MusicCommands(this);
        getServer().getPluginManager().registerEvents(new PlayerResourceListener(this, musicCommands), this);
        getLogger().info("事件监听器已注册。");

        PluginCommand bfCommand = getCommand("bf");
        if (bfCommand != null) {
            bfCommand.setExecutor(musicCommands);
            bfCommand.setTabCompleter(musicCommands);
        } else { getLogger().severe("无法获取指令 'bf'！"); }

        PluginCommand playUrlCommand = getCommand("playurl");
        if (playUrlCommand != null) {
            playUrlCommand.setExecutor(musicCommands);
            playUrlCommand.setTabCompleter(musicCommands);
        } else { getLogger().severe("无法获取指令 'playurl'！"); }

        PluginCommand internalJoinRoomCmd = getCommand("internal_join_room");
        if (internalJoinRoomCmd != null) {
            internalJoinRoomCmd.setExecutor(musicCommands);
        } else { getLogger().severe("无法获取指令 'internal_join_room'！");}

        startRoomCleanupTask();
        getLogger().info(getName() + " 已成功启用！(资源包模式: " + (useMergedPackLogic ? "合并基础包" : "独立音乐包") +
                ", 预设预热: " + (isPresetPrewarmingEnabled() ? "开启" : "关闭") + ")");
    }

    private void initializeHttpServerAndGenerator() {
        FileConfiguration config = getConfig();
        String configuredPublicAddress = config.getString("httpServer.publicAddress", "");
        int httpPort = config.getInt("httpServer.port", 8123);
        String servePath = config.getString("httpServer.servePath", "musicpacks");

        if (configuredPublicAddress.isEmpty() && config.getBoolean("httpServer.enabled")) {
            getLogger().warning("HTTP 服务器的 'publicAddress' 未在 config.yml 中配置! 音乐资源包可能无法从外部访问。");
        }

        File tempPackStorageDir = new File(getDataFolder(), config.getString("httpServer.tempDirectory", "temp_packs"));
        if (!tempPackStorageDir.exists()) {
            if (!tempPackStorageDir.mkdirs()){
                getLogger().severe("无法创建 HTTP 服务器的临时目录: " + tempPackStorageDir.getAbsolutePath());
                this.httpFileServer = null;
                this.resourcePackGenerator = null;
                return;
            }
        }

        this.resourcePackGenerator = new ResourcePackGenerator(this, tempPackStorageDir, config.getLong("httpServer.maxDownloadSizeBytes", 0),
                useMergedPackLogic ? basePackFile : null);
        this.httpFileServer = new HttpFileServer(this, servePath, tempPackStorageDir);
        this.httpFileServer.start(httpPort);

        if (!this.httpFileServer.isRunning() && config.getBoolean("httpServer.enabled")) {
            getLogger().severe("内置 HTTP 服务器未能启动！在线播放功能将不可用。");
            this.httpFileServer = null;
        } else if (this.httpFileServer != null && this.httpFileServer.isRunning()){
            getLogger().info("HTTP 服务器已启动。");
        }
    }

    private void loadConfiguration() {
        reloadConfig();
        FileConfiguration config = getConfig();

        this.presetPrewarmingEnabled = config.getBoolean("resourcePack.enablePresetPrewarming", false);

        boolean mergingEnabledByConfig = config.getBoolean("baseResourcePack.enableMerging", false);
        this.basePackFileNameConfig = config.getString("baseResourcePack.fileName", "base_pack.zip");
        this.basePackFile = new File(getDataFolder(), this.basePackFileNameConfig);

        if (mergingEnabledByConfig && this.basePackFile.exists()) {
            try (InputStream fis = new FileInputStream(this.basePackFile)) {
                this.basePackSha1 = DigestUtils.sha1Hex(fis);
                this.useMergedPackLogic = true;
                getLogger().info("基础资源包 '" + this.basePackFileNameConfig + "' 加载成功。SHA-1: " + this.basePackSha1 + ". 将使用合并模式。");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "无法读取基础资源包 '" + this.basePackFileNameConfig + "' 或计算其SHA-1值。将回退到独立音乐包模式。", e);
                this.basePackFile = null;
                this.basePackSha1 = null;
                this.useMergedPackLogic = false;
            }
        } else {
            if (mergingEnabledByConfig && !this.basePackFile.exists()) {
                sendConfigMsg(getServer().getConsoleSender(), "messages.general.basePackMissing");
            }
            this.useMergedPackLogic = false;
            this.basePackFile = null;
            this.basePackSha1 = null;
        }

        this.basePackPromptMessage = ChatColor.translateAlternateColorCodes('&', config.getString("baseResourcePack.promptMessage", "§6加载音乐资源包..."));
        this.originalPackPromptMessage = ChatColor.translateAlternateColorCodes('&', config.getString("baseResourcePack.originalPackPromptMessage", "§6恢复服务器默认资源包..."));

        presetSongsList.clear();
        ConfigurationSection presetSongsSection = config.getConfigurationSection("presetSongs");
        if (presetSongsSection != null) {
            for (String key : presetSongsSection.getKeys(false)) {
                ConfigurationSection songSection = presetSongsSection.getConfigurationSection(key);
                if (songSection != null) {
                    String name = songSection.getString("name", "未命名歌曲");
                    String url = songSection.getString("url");
                    String materialName = songSection.getString("item", "NOTE_BLOCK").toUpperCase();
                    Material material = Material.getMaterial(materialName);
                    if (material == null) {
                        getLogger().warning("预设歌曲 '" + name + "' 的物品材质 '" + materialName + "' 无效。将使用默认的 NOTE_BLOCK。");
                        material = Material.NOTE_BLOCK;
                    }
                    List<String> lore = songSection.getStringList("lore");
                    if (url != null && !url.isEmpty()) {
                        presetSongsList.add(new PresetSong(name, url, material, lore));
                    } else {
                        getLogger().warning("预设歌曲 '" + ChatColor.translateAlternateColorCodes('&', name) + "' 缺少 URL，已被跳过。");
                    }
                }
            }
        }
        getLogger().info("已加载 " + presetSongsList.size() + " 首预设歌曲。");
    }

    private void initializePresetPrewarming() {
        if (!isPresetPrewarmingEnabled() || resourcePackGenerator == null || httpFileServer == null || !httpFileServer.isRunning()) {
            getLogger().info("预设歌曲预热已禁用或必要组件未就绪。");
            prewarmedPresetPacks.clear();
            return;
        }

        getLogger().info("开始预设歌曲资源包预热...");
        prewarmedPresetPacks.clear();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (PresetSong song : getPresetSongs()) {
            String stableIdentifier = createStableIdentifier(song.getUrl());
            String soundEventName = getHttpFileServer().getServePathPrefix() + ".preset." + stableIdentifier;

            CompletableFuture<Void> future = resourcePackGenerator.generateAndServePack(null, song.getUrl(), soundEventName, false, null)
                    .thenAccept(packInfo -> {
                        if (packInfo != null) {
                            prewarmedPresetPacks.put(song.getUrl(), packInfo);
                            getLogger().info("预热成功: " + song.getName() + " -> " + packInfo.packFileName());
                        } else {
                            getLogger().warning("预热失败: " + song.getName() + " (URL: " + song.getUrl() + ")");
                        }
                    }).exceptionally(ex -> {
                        getLogger().log(Level.SEVERE, "预热预设歌曲 " + song.getName() + " 时发生异常: " + ex.getMessage(), ex);
                        return null;
                    });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            getLogger().info("预设歌曲资源包预热完成。共预热 " + prewarmedPresetPacks.size() + "/" + getPresetSongs().size() + " 个资源包。");
        });
    }


    @Override
    public void onDisable() {
        getLogger().info(getName() + " 正在禁用...");
        if (httpFileServer != null && httpFileServer.isRunning()) {
            httpFileServer.stop();
        }
        if (roomCleanupTask != null && !roomCleanupTask.isCancelled()) {
            roomCleanupTask.cancel();
        }

        activeMusicRooms.values().forEach(room -> {
            if (room.getPackFileName() != null && resourcePackGenerator != null && !isPrewarmedPackFile(room.getPackFileName())) {
                resourcePackGenerator.cleanupPack(room.getPackFileName());
            }
        });
        activeMusicRooms.clear();

        pendingSingleUserSounds.values().forEach(sound -> {
            if (sound.packFileName() != null && resourcePackGenerator != null && !isPrewarmedPackFile(sound.packFileName())) {
                resourcePackGenerator.cleanupPack(sound.packFileName());
            }
        });
        pendingSingleUserSounds.clear();

        playerCurrentMusicPackFile.values().forEach(tempPackFile -> {
            if (resourcePackGenerator != null && !isPrewarmedPackFile(tempPackFile)) {
                resourcePackGenerator.cleanupPack(tempPackFile);
            }
        });
        playerCurrentMusicPackFile.clear();
        playerPendingPackType.clear();

        getLogger().info(getName() + " 已被禁用。");
    }

    public void reloadPluginConfiguration() {
        if (httpFileServer != null && httpFileServer.isRunning()) {
            httpFileServer.stop();
        }
        httpFileServer = null;
        resourcePackGenerator = null;

        loadConfiguration();

        if (getConfig().getBoolean("httpServer.enabled", false)) {
            initializeHttpServerAndGenerator();
            if (this.resourcePackGenerator != null && isPresetPrewarmingEnabled()) {
                initializePresetPrewarming();
            } else {
                prewarmedPresetPacks.values().forEach(packInfo -> {
                    File baseDir;
                    if (resourcePackGenerator != null) {
                        baseDir = resourcePackGenerator.getTempPackStorageDir();
                    } else {
                        baseDir = new File(getDataFolder(), getConfig().getString("httpServer.tempDirectory", "temp_packs"));
                    }
                    File packFile = new File(baseDir, packInfo.packFileName());
                    if(packFile.exists() && !packFile.delete()){
                        getLogger().warning("重载配置并禁用预热后，无法删除旧的预热包: " + packInfo.packFileName());
                    }
                });
                prewarmedPresetPacks.clear();
                getLogger().info("预设歌曲预热已在重载后禁用，任何旧的预热包信息已清除。");
            }
        } else {
            getLogger().info("HTTP 服务器在重载后仍为禁用状态。");
            prewarmedPresetPacks.values().forEach(packInfo -> {
                File packFile = new File(new File(getDataFolder(), getConfig().getString("httpServer.tempDirectory", "temp_packs")), packInfo.packFileName());
                if(packFile.exists() && !packFile.delete()){
                    getLogger().warning("重载配置并禁用HTTP服务后，无法删除旧的预热包: " + packInfo.packFileName());
                }
            });
            prewarmedPresetPacks.clear();
        }

        if (roomCleanupTask != null && !roomCleanupTask.isCancelled()) {
            roomCleanupTask.cancel();
        }
        startRoomCleanupTask();
        getLogger().info(getName() + " 的配置已重载。(资源包模式: " + (useMergedPackLogic ? "合并基础包" : "独立音乐包") +
                ", 预设预热: " + (isPresetPrewarmingEnabled() ? "开启" : "关闭") + ")");
    }

    public ResourcePackGenerator getResourcePackGenerator() { return resourcePackGenerator; }
    public HttpFileServer getHttpFileServer() { return httpFileServer; }
    public List<PresetSong> getPresetSongs() { return Collections.unmodifiableList(presetSongsList); }
    public boolean shouldUseMergedPackLogic() { return useMergedPackLogic; }

    public @Nullable File getBasePackFile() { return useMergedPackLogic ? basePackFile : null; }
    public @Nullable String getBasePackSha1() { return useMergedPackLogic ? basePackSha1 : null; }
    public String getMusicPackPromptMessage() { return basePackPromptMessage; }
    public String getOriginalPackPromptMessage() { return useMergedPackLogic ? originalPackPromptMessage : ""; }

    public boolean isPresetPrewarmingEnabled() {
        return presetPrewarmingEnabled;
    }

    @Nullable
    public ResourcePackGenerator.PackInfo getPrewarmedPackInfo(String songUrl) {
        return prewarmedPresetPacks.get(songUrl);
    }

    public boolean isPrewarmedPackFile(String packFileName) {
        if (packFileName == null) return false;
        return prewarmedPresetPacks.values().stream().anyMatch(info -> packFileName.equals(info.packFileName()));
    }

    public String createStableIdentifier(String input) {
        return DigestUtils.sha1Hex(input).substring(0, 16);
    }


    public void addPendingSingleUserSound(UUID playerId, PendingOnlineSound soundInfo) {
        pendingSingleUserSounds.put(playerId, soundInfo);
        playerPendingPackType.put(playerId, "singleUser:" + soundInfo.packFileName());
    }
    public PendingOnlineSound getPendingSingleUserSound(UUID playerId) {
        return pendingSingleUserSounds.get(playerId);
    }
    public void clearPendingSingleUserSound(UUID playerId) {
        pendingSingleUserSounds.remove(playerId);
    }
    public void markPlayerPendingRoomPack(UUID playerId, String roomId, String tempPackFileName) {
        playerPendingPackType.put(playerId, "room:" + roomId + ":" + tempPackFileName);
    }
    public String getPlayerPendingPackType(UUID playerId) {
        return playerPendingPackType.get(playerId);
    }
    public void clearPlayerPendingPackType(UUID playerId){
        playerPendingPackType.remove(playerId);
    }

    public void setPlayerCurrentMusicPack(UUID playerId, String tempPackFileName) {
        if (tempPackFileName == null) {
            playerCurrentMusicPackFile.remove(playerId);
        } else {
            playerCurrentMusicPackFile.put(playerId, tempPackFileName);
        }
    }
    public String getPlayerCurrentMusicPackFile(UUID playerId) {
        return playerCurrentMusicPackFile.get(playerId);
    }
    public boolean isPlayerOnMusicPack(UUID playerId) {
        return playerCurrentMusicPackFile.containsKey(playerId);
    }
    public void clearPlayerCurrentMusicPack(UUID playerId) {
        playerCurrentMusicPackFile.remove(playerId);
    }

    public MusicRoom createMusicRoom(Player creator, String musicUrl, String description) {
        String roomId = "mroom_" + UUID.randomUUID().toString().substring(0, 6);
        String coloredDescription = ChatColor.translateAlternateColorCodes('&', description);
        MusicRoom room = new MusicRoom(roomId, creator, musicUrl, coloredDescription);
        activeMusicRooms.put(roomId, room);
        getLogger().info("音乐室已创建: " + roomId + " 由 " + creator.getName() + " 创建，URL: " + musicUrl);
        return room;
    }
    public MusicRoom getMusicRoom(String roomId) {
        return activeMusicRooms.get(roomId);
    }
    public MusicRoom findMusicRoomByCreatorName(String creatorName) {
        for (MusicRoom room : activeMusicRooms.values()) {
            if (room.getCreator().getName().equalsIgnoreCase(creatorName)) {
                return room;
            }
        }
        return null;
    }
    public void removeMusicRoom(String roomId) {
        MusicRoom room = activeMusicRooms.remove(roomId);
        if (room != null) {
            getLogger().info("音乐室 " + roomId + " (" + room.getDescription() + "§r) 已被移除。");
            String roomSpecificTempPack = room.getPackFileName();

            for (Player member : new HashSet<>(room.getMembers())) {
                if (member.isOnline()) {
                    if (!member.equals(room.getCreator())) {
                        sendConfigMsg(member, "messages.bf.disbandroom.notifyMemberRoomDisbanded", "room_description", room.getDescription());
                    }
                    String playerCurrentTempPack = getPlayerCurrentMusicPackFile(member.getUniqueId());
                    if (roomSpecificTempPack != null && roomSpecificTempPack.equals(playerCurrentTempPack)) {
                        clearPlayerCurrentMusicPack(member.getUniqueId());
                        if (shouldUseMergedPackLogic()) {
                            sendOriginalBasePackToPlayer(member);
                        }
                    } else if (getPlayerPendingPackType(member.getUniqueId()) != null && getPlayerPendingPackType(member.getUniqueId()).endsWith(":" + roomSpecificTempPack)) {
                        clearPlayerPendingPackType(member.getUniqueId());
                        if (shouldUseMergedPackLogic()) {
                            sendOriginalBasePackToPlayer(member);
                        }
                    }
                }
            }
            if (roomSpecificTempPack != null && resourcePackGenerator != null && !isPrewarmedPackFile(roomSpecificTempPack)) {
                resourcePackGenerator.cleanupPack(roomSpecificTempPack);
            }
            playerPendingPackType.entrySet().removeIf(entry -> entry.getValue().endsWith(":" + roomSpecificTempPack));
        }
    }
    public Collection<MusicRoom> getActiveMusicRoomsView() {
        return Collections.unmodifiableCollection(activeMusicRooms.values());
    }

    private void startRoomCleanupTask() {
        long interval = 20L * 60;
        final long inactiveCleanupDelayMillis = getConfig().getLong("httpServer.musicRoomInactiveCleanupDelaySeconds", 600) * 1000L;
        roomCleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long currentTime = System.currentTimeMillis();
            List<String> roomsToClose = new ArrayList<>();
            activeMusicRooms.forEach((id, room) -> {
                if (room.getStatus() != MusicRoom.RoomStatus.CLOSING && room.getStatus() != MusicRoom.RoomStatus.CLOSED) {
                    if (room.isEmpty() && (currentTime - room.getLastActivityTime() > inactiveCleanupDelayMillis)) {
                        getLogger().info("音乐室 " + room.getRoomId() + " (" + room.getDescription() + "§r) 空闲且不活跃，准备关闭。");
                        roomsToClose.add(id);
                    }
                }
            });
            if (!roomsToClose.isEmpty()) {
                Bukkit.getScheduler().runTask(this, () -> {
                    for (String roomIdToClose : roomsToClose) {
                        MusicRoom currentRoom = getMusicRoom(roomIdToClose);
                        if (currentRoom != null && currentRoom.getStatus() != MusicRoom.RoomStatus.CLOSING && currentRoom.getStatus() != MusicRoom.RoomStatus.CLOSED) {
                            currentRoom.setStatus(MusicRoom.RoomStatus.CLOSING);
                            sendConfigMsg(getServer().getConsoleSender(), "musicRoomClosedMessage", "description", currentRoom.getDescription());
                            Bukkit.getScheduler().runTaskLater(this, () -> removeMusicRoom(currentRoom.getRoomId()), 20L);
                        }
                    }
                });
            }
        }, interval, interval);
    }

    public void sendOriginalBasePackToPlayer(Player player) {
        if (!useMergedPackLogic || basePackFile == null || !basePackFile.exists() || basePackSha1 == null) {
            if (useMergedPackLogic) {
                getLogger().warning("尝试为 " + player.getName() + " 发送原始基础资源包，但文件或SHA-1未准备好/未启用合并模式。");
                sendConfigMsg(player, "messages.general.basePackReapplyFailed");
            }
            return;
        }

        File servedBasePack = new File(httpFileServer.getServeDirectory(), basePackFileNameConfig);
        if (!servedBasePack.exists() || !compareFileSha1(servedBasePack, basePackSha1)) {
            try {
                Files.copy(basePackFile.toPath(), servedBasePack.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "无法将原始基础包复制到服务目录以供发送。", e);
                sendConfigMsg(player, "messages.general.basePackReapplyFailed");
                return;
            }
        }
        String packUrl = httpFileServer.getFileUrl(getConfig().getString("httpServer.publicAddress"), getConfig().getInt("httpServer.port"), basePackFileNameConfig);
        try {
            byte[] sha1Bytes = Hex.decodeHex(basePackSha1);
            player.setResourcePack(packUrl, sha1Bytes, getOriginalPackPromptMessage(), true);
            getLogger().info("正在向玩家 " + player.getName() + " 发送原始基础资源包: " + packUrl);
        } catch (DecoderException e) {
            getLogger().log(Level.SEVERE, "无法解码原始基础资源包的SHA-1哈希值: " + basePackSha1, e);
            sendConfigMsg(player, "messages.general.basePackReapplyFailed");
        }
    }

    private boolean compareFileSha1(File file, String expectedSha1) {
        if (!file.exists() || expectedSha1 == null) return false;
        try (InputStream fis = new FileInputStream(file)) {
            String actualSha1 = DigestUtils.sha1Hex(fis);
            return expectedSha1.equalsIgnoreCase(actualSha1);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "比较文件SHA1时出错: " + file.getName(), e);
            return false;
        }
    }

    private String replacePlaceholders(String template, String... substitutions) {
        if (template == null) return "";
        if (substitutions.length % 2 != 0) {
            getLogger().warning("替换占位符时提供的替换项数量无效。必须是键值对。模板: " + template);
            return template;
        }
        String result = template;
        for (int i = 0; i < substitutions.length; i += 2) {
            String key = "<" + substitutions[i] + ">";
            String value = substitutions[i+1];
            if (value == null) value = "";
            result = result.replace(key, value);
        }
        return result;
    }

    public void sendLegacyMsg(CommandSender sender, String template, String... substitutions) {
        if (template == null || template.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "错误: 插件尝试发送空消息模板。");
            getLogger().warning("尝试向 " + sender.getName() + " 发送空消息模板。");
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', replacePlaceholders(template, substitutions)));
    }

    public void sendConfigMsg(CommandSender sender, String configKey, String... substitutions) {
        String template = getConfig().getString(configKey);
        if (template != null && !template.isEmpty()) {
            sendLegacyMsg(sender, template, substitutions);
        } else {
            sendLegacyMsg(sender, ChatColor.RED + "错误: 缺少配置消息，键: " + configKey);
            getLogger().warning("配置中缺少消息键: " + configKey);
        }
    }
}