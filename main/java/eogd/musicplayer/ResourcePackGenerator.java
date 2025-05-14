package eogd.musicplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.digest.DigestUtils;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackGenerator {

    private final MusicPlayerPlugin plugin;
    private final File tempPackStorageDir;
    private final long maxDownloadSizeBytes;
    private final @Nullable File originalBasePackFile;
    private final Gson gson;

    public record PackInfo(String packUrl, String sha1, String packFileName) {}

    private static class DownloadedAudioInfo {
        final File audioFile;
        final String errorKey;

        DownloadedAudioInfo(File audioFile, @Nullable String errorKey) {
            this.audioFile = audioFile;
            this.errorKey = errorKey;
        }
    }

    public ResourcePackGenerator(MusicPlayerPlugin plugin, File tempPackStorageDir, long maxDownloadSizeBytes, @Nullable File originalBasePackFile) {
        this.plugin = plugin;
        this.tempPackStorageDir = tempPackStorageDir;
        this.maxDownloadSizeBytes = maxDownloadSizeBytes;
        this.originalBasePackFile = originalBasePackFile;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        if (!tempPackStorageDir.exists()) {
            if (!tempPackStorageDir.mkdirs()) {
                plugin.getLogger().severe("ResourcePackGenerator: 无法创建临时包存储目录: " + tempPackStorageDir.getAbsolutePath());
            }
        }
    }
    public File getTempPackStorageDir() {
        return tempPackStorageDir;
    }


    public CompletableFuture<PackInfo> generateAndServePack(@Nullable Player player, @NotNull String audioUrl, @NotNull String soundEventName, boolean isRoomPlayback, @Nullable MusicRoom roomContext) {
        if (player != null && plugin.isPresetPrewarmingEnabled() && !isRoomPlayback && soundEventName.startsWith(plugin.getHttpFileServer().getServePathPrefix() + ".preset.")) {
            ResourcePackGenerator.PackInfo prewarmedInfo = plugin.getPrewarmedPackInfo(audioUrl);
            if (prewarmedInfo != null) {
                plugin.getLogger().info("为玩家 " + player.getName() + " 使用预热的资源包: " + prewarmedInfo.packFileName() + " (URL: " + audioUrl + ")");
                return CompletableFuture.completedFuture(prewarmedInfo);
            } else {
                plugin.getLogger().warning("预热已启用，但未找到预设歌曲 " + audioUrl + " 的预热包 (请求的 soundEvent: " + soundEventName + ")。将按需生成。");
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            boolean actuallyUseMerging = plugin.shouldUseMergedPackLogic() && originalBasePackFile != null && originalBasePackFile.exists();
            if (actuallyUseMerging) {
                return generateMergedPack(player, audioUrl, soundEventName, isRoomPlayback, roomContext);
            } else {
                return generateIndependentPack(player, audioUrl, soundEventName, isRoomPlayback, roomContext);
            }
        });
    }

    private DownloadedAudioInfo downloadAudioFile(String audioUrl, String uniqueSuffix, String modeIdentifier) {
        File tempAudioFile = new File(tempPackStorageDir, "audio_" + modeIdentifier + "_" + uniqueSuffix + ".ogg");
        try {
            URL audioSourceUrl = new URL(audioUrl);
            HttpURLConnection connection = (HttpURLConnection) audioSourceUrl.openConnection();
            connection.setRequestProperty("User-Agent", "EogdMusicPlayer/" + plugin.getDescription().getVersion());
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(plugin.getConfig().getInt("httpServer.downloadConnectTimeoutMillis", 5000));
            connection.setReadTimeout(plugin.getConfig().getInt("httpServer.downloadReadTimeoutMillis", 10000));


            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_MOVED_TEMP && responseCode != HttpURLConnection.HTTP_MOVED_PERM) {
                plugin.getLogger().warning("下载音频文件 " + audioUrl + " 失败。响应码: " + responseCode + " (" + connection.getResponseMessage() + ")");
                return new DownloadedAudioInfo(tempAudioFile, "messages.general.downloadFailedResponse");
            }

            long contentLength = connection.getContentLengthLong();
            if (maxDownloadSizeBytes > 0 && contentLength > maxDownloadSizeBytes) {
                plugin.getLogger().warning("音频文件 " + audioUrl + " 过大 (" + modeIdentifier + " 模式): " + contentLength + " bytes (max: " + maxDownloadSizeBytes + ")");
                return new DownloadedAudioInfo(tempAudioFile, "messages.general.fileTooLarge");
            }

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, tempAudioFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return new DownloadedAudioInfo(tempAudioFile, null);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "下载或保存音频文件 " + audioUrl + " 时出错 (" + modeIdentifier + " 模式): " + e.getMessage());
            return new DownloadedAudioInfo(tempAudioFile, "messages.general.downloadException");
        }
    }

    private PackInfo generateMergedPack(@Nullable Player player, @NotNull String audioUrl, @NotNull String soundEventName, boolean isRoomPlayback, @Nullable MusicRoom roomContext) {
        if (originalBasePackFile == null) {
            plugin.getLogger().severe("generateMergedPack called with null originalBasePackFile!");
            return null;
        }
        String uniqueSuffix = plugin.createStableIdentifier(audioUrl + "_" + soundEventName);
        String packFileNamePrefix = "mrg_";
        if (player == null && soundEventName.startsWith(plugin.getHttpFileServer().getServePathPrefix() + ".preset.")) {
            packFileNamePrefix += "prewarm_preset_";
        } else if (isRoomPlayback && roomContext != null) {
            packFileNamePrefix += "room_" + roomContext.getRoomId() + "_";
        } else if (player != null) {
            packFileNamePrefix += "player_" + player.getUniqueId().toString().substring(0, 8) + "_";
        } else {
            packFileNamePrefix += "unknown_";
        }

        File tempMergedPackFile = new File(tempPackStorageDir, packFileNamePrefix + uniqueSuffix + ".zip");
        File tempAudioFile = null;

        try {
            DownloadedAudioInfo audioInfo = downloadAudioFile(audioUrl, uniqueSuffix, "merged");
            if (audioInfo.errorKey != null) {
                if (player != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> plugin.sendConfigMsg(player, audioInfo.errorKey, "url", audioUrl));
                } else {
                    plugin.getLogger().warning("预热音频下载失败 (" + audioInfo.errorKey + ") for URL: " + audioUrl);
                }
                if (audioInfo.audioFile.exists()) {
                    if (!audioInfo.audioFile.delete()) {
                        plugin.getLogger().warning("无法删除部分下载的合并音频文件: " + audioInfo.audioFile.getAbsolutePath());
                    }
                }
                return null;
            }
            tempAudioFile = audioInfo.audioFile;

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempMergedPackFile));
                 ZipFile originalZip = new ZipFile(originalBasePackFile)) {
                JsonObject soundsJson;
                ZipEntry existingSoundsJsonEntry = originalZip.getEntry("assets/minecraft/sounds.json");
                if (existingSoundsJsonEntry != null) {
                    try (InputStream jsonIs = originalZip.getInputStream(existingSoundsJsonEntry)) {
                        soundsJson = JsonParser.parseReader(new InputStreamReader(jsonIs, StandardCharsets.UTF_8)).getAsJsonObject();
                    }
                } else {
                    soundsJson = new JsonObject();
                }
                String soundResourcePath = soundEventName.replace('.', '/');
                JsonObject newSoundEntry = new JsonObject();
                newSoundEntry.addProperty("category", "music");
                newSoundEntry.add("sounds", JsonParser.parseString(String.format("[{\"name\": \"%s\", \"stream\": true}]", soundResourcePath)));
                soundsJson.add(soundEventName, newSoundEntry);

                Enumeration<? extends ZipEntry> entries = originalZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().equals("assets/minecraft/sounds.json")) continue;
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    if (!entry.isDirectory()) {
                        try (InputStream originalIs = originalZip.getInputStream(entry)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = originalIs.read(buffer)) > 0) zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
                ZipEntry soundsJsonZipEntry = new ZipEntry("assets/minecraft/sounds.json");
                zos.putNextEntry(soundsJsonZipEntry);
                zos.write(gson.toJson(soundsJson).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                String audioPathInZip = "assets/minecraft/sounds/" + soundResourcePath + ".ogg";
                ZipEntry audioZipEntry = new ZipEntry(audioPathInZip);
                zos.putNextEntry(audioZipEntry);
                Files.copy(tempAudioFile.toPath(), zos);
                zos.closeEntry();
            }
            plugin.getLogger().info("已创建合并的资源包: " + tempMergedPackFile.getName() + " for sound " + soundEventName);
            String sha1;
            try (InputStream fis = new FileInputStream(tempMergedPackFile)) {
                sha1 = DigestUtils.sha1Hex(fis);
            }
            String publicAddress = plugin.getConfig().getString("httpServer.publicAddress");
            int httpPort = plugin.getConfig().getInt("httpServer.port");
            String packUrl = plugin.getHttpFileServer().getFileUrl(publicAddress, httpPort, tempMergedPackFile.getName());

            if (isRoomPlayback && roomContext != null) {
                String oldTempMergedPackForRoom = roomContext.getPackFileName();
                if (oldTempMergedPackForRoom != null && !oldTempMergedPackForRoom.equals(tempMergedPackFile.getName()) && !plugin.isPrewarmedPackFile(oldTempMergedPackForRoom)) {
                    cleanupPack(oldTempMergedPackForRoom);
                }
                roomContext.setPackFileName(tempMergedPackFile.getName());
            }
            return new PackInfo(packUrl, sha1, tempMergedPackFile.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "生成合并资源包时出错 URL: " + audioUrl + ", SoundEvent: " + soundEventName, e);
            if (tempMergedPackFile.exists()) {
                if (!tempMergedPackFile.delete()) {
                    plugin.getLogger().warning("无法删除生成失败的合并包: " + tempMergedPackFile.getAbsolutePath());
                }
            }
            if (player != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.sendConfigMsg(player, "messages.playurl.packCreationFailed"));
            }
            return null;
        } finally {
            if (tempAudioFile != null && tempAudioFile.exists()) {
                if (!tempAudioFile.delete()) {
                    plugin.getLogger().warning("无法删除临时合并音频文件: " + tempAudioFile.getAbsolutePath());
                }
            }
        }
    }

    private PackInfo generateIndependentPack(@Nullable Player player, @NotNull String audioUrl, @NotNull String soundEventName, boolean isRoomPlayback, @Nullable MusicRoom roomContext) {
        String uniqueSuffix = plugin.createStableIdentifier(audioUrl + "_" + soundEventName);
        String packFileNamePrefix = "ind_";
        if (player == null && soundEventName.startsWith(plugin.getHttpFileServer().getServePathPrefix() + ".preset.")) {
            packFileNamePrefix += "prewarm_preset_";
        } else if (isRoomPlayback && roomContext != null) {
            packFileNamePrefix += "room_" + roomContext.getRoomId() + "_";
        } else if (player != null) {
            packFileNamePrefix += "player_" + player.getUniqueId().toString().substring(0, 8) + "_";
        } else {
            packFileNamePrefix += "unknown_";
        }
        File tempIndependentPackFile = new File(tempPackStorageDir, packFileNamePrefix + uniqueSuffix + ".zip");
        File tempAudioFile = null;

        try {
            DownloadedAudioInfo audioInfo = downloadAudioFile(audioUrl, uniqueSuffix, "independent");
            if (audioInfo.errorKey != null) {
                if (player != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> plugin.sendConfigMsg(player, audioInfo.errorKey, "url", audioUrl));
                } else {
                    plugin.getLogger().warning("预热音频下载失败 (" + audioInfo.errorKey + ") for URL: " + audioUrl);
                }
                if (audioInfo.audioFile.exists()) {
                    if(!audioInfo.audioFile.delete()){
                        plugin.getLogger().warning("无法删除部分下载的独立音频文件: " + audioInfo.audioFile.getAbsolutePath());
                    }
                }
                return null;
            }
            tempAudioFile = audioInfo.audioFile;

            int packFormat = plugin.getConfig().getInt("resourcePack.packFormat", 34);
            String description = plugin.getConfig().getString("resourcePack.description", "§b音乐播放器资源包");
            String escapedDescription = gson.toJson(ChatColor.translateAlternateColorCodes('&',description)).substring(1, gson.toJson(ChatColor.translateAlternateColorCodes('&',description)).length() - 1);
            String packMcMetaContent = String.format("{\n  \"pack\": {\n    \"pack_format\": %d,\n    \"description\": \"%s\"\n  }\n}", packFormat, escapedDescription);

            String soundResourcePath = soundEventName.replace('.', '/');
            JsonObject soundsJson = new JsonObject();
            JsonObject newSoundEntry = new JsonObject();
            newSoundEntry.addProperty("category", "music");
            newSoundEntry.add("sounds", JsonParser.parseString(String.format("[{\"name\": \"%s\", \"stream\": true}]", soundResourcePath)));
            soundsJson.add(soundEventName, newSoundEntry);
            String soundsJsonContent = gson.toJson(soundsJson);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempIndependentPackFile))) {
                ZipEntry packMcMetaEntry = new ZipEntry("pack.mcmeta");
                zos.putNextEntry(packMcMetaEntry);
                zos.write(packMcMetaContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                ZipEntry soundsJsonEntry = new ZipEntry("assets/minecraft/sounds.json");
                zos.putNextEntry(soundsJsonEntry);
                zos.write(soundsJsonContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String audioPathInZip = "assets/minecraft/sounds/" + soundResourcePath + ".ogg";
                ZipEntry audioEntry = new ZipEntry(audioPathInZip);
                zos.putNextEntry(audioEntry);
                Files.copy(tempAudioFile.toPath(), zos);
                zos.closeEntry();
            }
            plugin.getLogger().info("已创建独立的资源包: " + tempIndependentPackFile.getName() + " for sound " + soundEventName);
            String sha1;
            try (InputStream fis = new FileInputStream(tempIndependentPackFile)) {
                sha1 = DigestUtils.sha1Hex(fis);
            }
            String publicAddress = plugin.getConfig().getString("httpServer.publicAddress");
            int httpPort = plugin.getConfig().getInt("httpServer.port");
            String packUrl = plugin.getHttpFileServer().getFileUrl(publicAddress, httpPort, tempIndependentPackFile.getName());

            if (isRoomPlayback && roomContext != null) {
                String oldIndependentPackForRoom = roomContext.getPackFileName();
                if (oldIndependentPackForRoom != null && !oldIndependentPackForRoom.equals(tempIndependentPackFile.getName()) && !plugin.isPrewarmedPackFile(oldIndependentPackForRoom)) {
                    cleanupPack(oldIndependentPackForRoom);
                }
                roomContext.setPackFileName(tempIndependentPackFile.getName());
            }
            return new PackInfo(packUrl, sha1, tempIndependentPackFile.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "生成独立资源包时出错 URL: " + audioUrl + ", SoundEvent: " + soundEventName, e);
            if (tempIndependentPackFile.exists()) {
                if(!tempIndependentPackFile.delete()){
                    plugin.getLogger().warning("无法删除生成失败的独立包: " + tempIndependentPackFile.getAbsolutePath());
                }
            }
            if (player != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.sendConfigMsg(player, "messages.playurl.packCreationFailed"));
            }
            return null;
        } finally {
            if (tempAudioFile != null && tempAudioFile.exists()) {
                if(!tempAudioFile.delete()){
                    plugin.getLogger().warning("无法删除临时独立音频文件: " + tempAudioFile.getAbsolutePath());
                }
            }
        }
    }

    public void cleanupPack(String tempPackFileName) {
        if (tempPackFileName == null || tempPackFileName.isEmpty()) return;

        if (plugin.isPresetPrewarmingEnabled() && plugin.isPrewarmedPackFile(tempPackFileName)) {
            plugin.getLogger().info("跳过清理预热的资源包: " + tempPackFileName);
            return;
        }

        File packFile = new File(tempPackStorageDir, tempPackFileName);
        if (packFile.exists()) {
            if (packFile.delete()) {
                plugin.getLogger().info("已清理临时资源包: " + tempPackFileName);
            } else {
                plugin.getLogger().warning("无法清理临时资源包: " + tempPackFileName + " (可能仍被占用)");
            }
        }
    }
}