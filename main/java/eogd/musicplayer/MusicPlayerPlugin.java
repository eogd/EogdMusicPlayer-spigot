package eogd.musicplayer;

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

// Adventure API (MiniMessage, Component, etc.) are not directly used for sending messages to players anymore.
// They are kept if any internal logic or other plugins might interact using Components.
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
// import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder; // No longer used for sendConfigMsg
// import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver; // No longer used for sendConfigMsg
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;


import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MusicPlayerPlugin extends JavaPlugin {

    private MusicCommands musicCommands;
    private HttpFileServer httpFileServer;
    private ResourcePackGenerator resourcePackGenerator;

    private MiniMessage miniMessage; // Kept for potential internal Component creation/parsing
    private LegacyComponentSerializer legacySectionSerializer; // For converting Components to legacy § strings if needed

    private final Map<UUID, PendingOnlineSound> pendingSingleUserSounds = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerPendingPackType = new ConcurrentHashMap<>();

    private final Map<String, MusicRoom> activeMusicRooms = new ConcurrentHashMap<>();
    private List<PresetSong> presetSongsList = new ArrayList<>();
    private BukkitTask roomCleanupTask;

    public static class PendingOnlineSound {
        public final String packFileName;
        public final String soundEventName;
        public final String sha1;
        public final Player targetPlayer;
        public final String packUrl;

        public PendingOnlineSound(String packFileName, String soundEventName, String sha1, Player targetPlayer, String packUrl) {
            this.packFileName = packFileName;
            this.soundEventName = soundEventName;
            this.sha1 = sha1;
            this.targetPlayer = targetPlayer;
            this.packUrl = packUrl;
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("EogdMusicPlayer 正在启动 (版本 " + getDescription().getVersion() + ")...");

        this.miniMessage = MiniMessage.miniMessage();
        this.legacySectionSerializer = LegacyComponentSerializer.legacySection();

        saveDefaultConfig();
        loadConfiguration();

        if (getConfig().getBoolean("httpServer.enabled", false)) {
            initializeHttpServerAndGenerator();
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
        getLogger().info(getName() + " 已成功启用！");
    }

    private void initializeHttpServerAndGenerator() {
        FileConfiguration config = getConfig();
        String configuredPublicAddress = config.getString("httpServer.publicAddress", "");
        int httpPort = config.getInt("httpServer.port", 8123);
        String servePath = config.getString("httpServer.servePath", "musicpacks");

        if (configuredPublicAddress.isEmpty()) {
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().severe("HTTP 服务器的 'publicAddress' 未在 config.yml 中配置!");
            getLogger().severe("在线播放功能将无法对外部玩家工作!");
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }

        File tempPackStorageDir = new File(getDataFolder(), config.getString("httpServer.tempDirectory", "temp_packs"));
        if (!tempPackStorageDir.exists() && !tempPackStorageDir.mkdirs()) {
            getLogger().severe("无法创建 HTTP 服务器的临时目录: " + tempPackStorageDir.getAbsolutePath());
            this.httpFileServer = null;
            this.resourcePackGenerator = null;
            return;
        }

        this.resourcePackGenerator = new ResourcePackGenerator(this, tempPackStorageDir, config.getLong("httpServer.maxDownloadSizeBytes", 0));
        this.httpFileServer = new HttpFileServer(this, servePath, tempPackStorageDir);
        this.httpFileServer.start(httpPort);

        if (!this.httpFileServer.isRunning()) {
            getLogger().severe("内置 HTTP 服务器未能启动！在线播放功能将不可用。");
            this.httpFileServer = null;
        } else {
            getLogger().info("HTTP 服务器已启动。示例 URL: " +
                    this.httpFileServer.getFileUrl(configuredPublicAddress, httpPort, "example.zip"));
        }
    }

    private void loadConfiguration() {
        reloadConfig();
        FileConfiguration config = getConfig();

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
                        getLogger().info("已加载预设歌曲: " + ChatColor.translateAlternateColorCodes('&', name));
                    } else {
                        getLogger().warning("预设歌曲 '" + ChatColor.translateAlternateColorCodes('&', name) + "' 缺少 URL，已被跳过。");
                    }
                }
            }
        }
        getLogger().info("已加载 " + presetSongsList.size() + " 首预设歌曲。");
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
            if (room.getPackFileName() != null && resourcePackGenerator != null) {
                resourcePackGenerator.cleanupPack(room.getPackFileName());
            }
        });
        activeMusicRooms.clear();
        pendingSingleUserSounds.values().forEach(sound -> {
            if (sound.packFileName != null && resourcePackGenerator != null) {
                resourcePackGenerator.cleanupPack(sound.packFileName);
            }
        });
        pendingSingleUserSounds.clear();
        playerPendingPackType.clear();
        getLogger().info(getName() + " 已被禁用。");
    }

    public void reloadPluginConfiguration() {
        loadConfiguration();

        if (httpFileServer != null && httpFileServer.isRunning()) {
            httpFileServer.stop();
        }
        httpFileServer = null;
        resourcePackGenerator = null;

        if (getConfig().getBoolean("httpServer.enabled", false)) {
            initializeHttpServerAndGenerator();
        } else {
            getLogger().info("HTTP 服务器在重载后仍为禁用状态。");
        }
        getLogger().info(getName() + " 的配置已重载。");
    }

    public ResourcePackGenerator getResourcePackGenerator() { return resourcePackGenerator; }
    public HttpFileServer getHttpFileServer() { return httpFileServer; }
    public List<PresetSong> getPresetSongs() { return Collections.unmodifiableList(presetSongsList); }

    public void addPendingSingleUserSound(UUID playerId, PendingOnlineSound soundInfo) {
        pendingSingleUserSounds.put(playerId, soundInfo);
        playerPendingPackType.put(playerId, "singleUser:" + soundInfo.packFileName);
    }
    public PendingOnlineSound getPendingSingleUserSound(UUID playerId) {
        return pendingSingleUserSounds.get(playerId);
    }
    public void clearPendingSingleUserSound(UUID playerId) {
        pendingSingleUserSounds.remove(playerId);
        String currentPending = playerPendingPackType.get(playerId);
        if (currentPending != null && currentPending.startsWith("singleUser:")) {
            playerPendingPackType.remove(playerId);
        }
    }

    public void markPlayerPendingRoomPack(UUID playerId, String roomId, String packFileName) {
        playerPendingPackType.put(playerId, "room:" + roomId + ":" + packFileName);
    }

    public String getPlayerPendingPackType(UUID playerId) {
        return playerPendingPackType.get(playerId);
    }
    public void clearPlayerPendingPackType(UUID playerId){
        playerPendingPackType.remove(playerId);
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
            getLogger().info("音乐室 " + roomId + " (" + room.getDescription() + "§r) 已被移除。"); // Append reset code for console
            String notifyMsgTemplate = getConfig().getString("messages.bf.disbandroom.notifyMemberRoomDisbanded",
                    "§e你所在的音乐室 '§f<room_description>§e' 已被创建者解散。");

            for (Player member : room.getMembers()) {
                if (!member.equals(room.getCreator()) && member.isOnline()) {
                    sendLegacyMsg(member, notifyMsgTemplate, "room_description", room.getDescription());
                }
            }

            if (room.getPackFileName() != null && resourcePackGenerator != null) {
                resourcePackGenerator.cleanupPack(room.getPackFileName());
                getLogger().info("音乐室 " + roomId + " 的资源包 " + room.getPackFileName() + " 已清理。");
            }
            playerPendingPackType.entrySet().removeIf(entry -> {
                String[] parts = entry.getValue().split(":");
                return parts.length > 1 && parts[0].equals("room") && parts[1].equals(roomId);
            });
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
                            String msgTemplate = getConfig().getString("musicRoomClosedMessage", "§c音乐室 '§e<description>§c' 已关闭。");

                            String consoleMessage = ChatColor.translateAlternateColorCodes('&',
                                    replacePlaceholders(msgTemplate, "description", currentRoom.getDescription())
                            ) + " (ID: " + currentRoom.getRoomId() + ")";
                            getLogger().info(consoleMessage);

                            for (Player member : currentRoom.getMembers()) {
                                if (member.isOnline()) sendLegacyMsg(member, msgTemplate, "description", currentRoom.getDescription());
                            }
                            Bukkit.getScheduler().runTaskLater(this, () -> removeMusicRoom(currentRoom.getRoomId()), 20L * 2);
                        } else if (currentRoom == null) {
                            getLogger().info("尝试关闭音乐室 " + roomIdToClose + "，但它已经被移除了。");
                        }
                    }
                });
            }
        }, interval, interval);
    }

    private String replacePlaceholders(String template, String... substitutions) {
        if (template == null) return "";
        if (substitutions == null || substitutions.length == 0) {
            return template;
        }
        if (substitutions.length % 2 != 0) {
            getLogger().warning("Invalid number of substitutions provided for placeholder replacement. Must be key-value pairs. Template: " + template);
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
            String errorMsg = ChatColor.RED + "Error: Plugin tried to send an empty message template.";
            sender.sendMessage(errorMsg);
            getLogger().warning("Attempted to send an empty message template to " + sender.getName());
            return;
        }
        String messageWithPlaceholders = replacePlaceholders(template, substitutions);
        String coloredMessage = ChatColor.translateAlternateColorCodes('&', messageWithPlaceholders);
        sender.sendMessage(coloredMessage);
    }

    public void sendConfigMsg(CommandSender sender, String configKey, String... substitutions) {
        String template = getConfig().getString(configKey);
        if (template != null && !template.isEmpty()) {
            sendLegacyMsg(sender, template, substitutions);
        } else {
            String errorMsgContent = "Error: Missing config message for key: " + configKey;
            sendLegacyMsg(sender, ChatColor.RED + errorMsgContent);
        }
    }
}