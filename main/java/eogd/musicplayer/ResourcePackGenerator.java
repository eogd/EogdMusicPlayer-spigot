package eogd.musicplayer;

import org.apache.commons.codec.digest.DigestUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackGenerator {

    private final MusicPlayerPlugin plugin;
    private final File tempPackStorageDir;
    private final long maxDownloadSizeBytes;


    public static class PackInfo {
        private final String packUrl;
        private final String sha1;
        private final String packFileName;

        public PackInfo(String packUrl, String sha1, String packFileName) {
            this.packUrl = packUrl;
            this.sha1 = sha1;
            this.packFileName = packFileName;
        }
        public String getPackUrl() { return packUrl; }
        public String getSha1() { return sha1; }
        public String getPackFileName() { return packFileName; }
    }


    public ResourcePackGenerator(MusicPlayerPlugin plugin, File tempPackStorageDir, long maxDownloadSizeBytes) {
        this.plugin = plugin;
        this.tempPackStorageDir = tempPackStorageDir;
        this.maxDownloadSizeBytes = maxDownloadSizeBytes;
        if (!tempPackStorageDir.exists()) {
            tempPackStorageDir.mkdirs();
        }
    }

    public CompletableFuture<PackInfo> generateAndServePack(@NotNull Player player, @NotNull String audioUrl, @NotNull String soundEventName, boolean isRoomPlayback, @Nullable MusicRoom roomContext) {
        return CompletableFuture.supplyAsync(() -> {
            File audioFile = null;
            File packFile = null;
            String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
            String baseName = isRoomPlayback && roomContext != null ? "room_" + roomContext.getRoomId() : "player_" + player.getUniqueId().toString().substring(0,8);
            String packFileName = baseName + "_" + uniqueSuffix + ".zip";


            try {
                URL url = new URL(audioUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "EogdMusicPlayer/" + plugin.getDescription().getVersion());
                connection.setInstanceFollowRedirects(true);

                long contentLength = connection.getContentLengthLong();
                if (maxDownloadSizeBytes > 0 && contentLength > maxDownloadSizeBytes) {
                    plugin.getLogger().warning("Audio file at " + audioUrl + " is too large: " + contentLength + " bytes (max: " + maxDownloadSizeBytes + ")");
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.sendConfigMsg(player, "messages.general.invalidUrl")
                    );
                    return null;
                }

                String contentType = connection.getContentType();
                plugin.getLogger().info("Downloading " + audioUrl + " (Content-Type: " + (contentType != null ? contentType : "N/A") + ", Length: " + contentLength + ")");

                audioFile = new File(tempPackStorageDir, "temp_audio_" + uniqueSuffix + ".ogg");
                try (InputStream inputStream = connection.getInputStream()) {
                    Files.copy(inputStream, audioFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                plugin.getLogger().info("Audio downloaded to: " + audioFile.getAbsolutePath());

                String soundsJsonContent = createSoundsJson(soundEventName);
                String packMcMetaContent = createPackMcMeta();

                packFile = new File(tempPackStorageDir, packFileName);
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(packFile))) {
                    ZipEntry packMcMetaEntry = new ZipEntry("pack.mcmeta");
                    zos.putNextEntry(packMcMetaEntry);
                    zos.write(packMcMetaContent.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    ZipEntry soundsJsonEntry = new ZipEntry("assets/minecraft/sounds.json");
                    zos.putNextEntry(soundsJsonEntry);
                    zos.write(soundsJsonContent.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    String soundPathInZip = "assets/minecraft/sounds/" + soundEventName.replace('.', '/') + ".ogg";
                    ZipEntry audioEntry = new ZipEntry(soundPathInZip);
                    zos.putNextEntry(audioEntry);
                    Files.copy(audioFile.toPath(), zos);
                    zos.closeEntry();
                }
                plugin.getLogger().info("Resource pack created: " + packFile.getAbsolutePath());

                String sha1;
                try (InputStream fis = new FileInputStream(packFile)) {
                    sha1 = DigestUtils.sha1Hex(fis);
                }
                plugin.getLogger().info("SHA1 for " + packFileName + ": " + sha1);

                String publicAddress = plugin.getConfig().getString("httpServer.publicAddress");
                int httpPort = plugin.getConfig().getInt("httpServer.port");
                String packUrl = plugin.getHttpFileServer().getFileUrl(publicAddress, httpPort, packFileName);

                if (isRoomPlayback && roomContext != null) {
                    if (roomContext.getPackFileName() != null && !roomContext.getPackFileName().equals(packFileName)) {
                        plugin.getLogger().info("Cleaning up old room pack: " + roomContext.getPackFileName() + " for room " + roomContext.getRoomId());
                        cleanupPack(roomContext.getPackFileName());
                    }
                    roomContext.setPackFileName(packFileName);
                }
                return new PackInfo(packUrl, sha1, packFileName);

            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error generating resource pack for URL: " + audioUrl, e);
                if (packFile != null) packFile.delete();
                return null;
            } finally {
                if (audioFile != null) {
                    audioFile.delete();
                }
            }
        });
    }

    private String createSoundsJson(String soundEventName) {
        String soundResourcePath = soundEventName.replace('.', '/');
        // Added "category": "music"
        return String.format("{\n" +
                "  \"%s\": {\n" +
                "    \"category\": \"music\",\n" +
                "    \"sounds\": [\n" +
                "      {\n" +
                "        \"name\": \"%s\",\n" +
                "        \"stream\": true\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}", soundEventName, soundResourcePath);
    }

    private String createPackMcMeta() {
        int packFormat = plugin.getConfig().getInt("resourcePack.packFormat", 32);
        String description = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("resourcePack.description", "§bMusic Player Resource Pack"));
        return String.format("{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": %d,\n" +
                "    \"description\": \"%s\"\n" +
                "  }\n" +
                "}", packFormat, description.replace("\"", "\\\""));
    }

    public void cleanupPack(String packFileName) {
        if (packFileName == null || packFileName.isEmpty()) return;
        File packFile = new File(tempPackStorageDir, packFileName);
        if (packFile.exists()) {
            if (packFile.delete()) {
                plugin.getLogger().info("Cleaned up resource pack: " + packFileName);
            } else {
                plugin.getLogger().warning("Failed to clean up resource pack: " + packFileName);
            }
        }
    }
}