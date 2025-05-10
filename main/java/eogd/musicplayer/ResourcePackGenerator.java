package eogd.musicplayer;

import org.apache.commons.codec.digest.DigestUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackGenerator {

    private final JavaPlugin plugin;
    private final File tempPackDir;
    private final long maxDownloadSizeBytes;

    public ResourcePackGenerator(JavaPlugin plugin, File tempPackDir, long maxDownloadSizeBytes) {
        this.plugin = plugin;
        this.tempPackDir = tempPackDir;
        this.maxDownloadSizeBytes = maxDownloadSizeBytes;
        if (!this.tempPackDir.exists()) {
            this.tempPackDir.mkdirs();
        }
    }

    public GeneratedPackInfo generateForUrl(String musicUrl) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String oggFileNameInZip = "onlinesong_" + uniqueId + ".ogg";
        File downloadedOggFileOnServer = new File(tempPackDir, "source_" + oggFileNameInZip);

        try {
            plugin.getLogger().info("开始下载音乐文件: " + musicUrl + " 到: " + downloadedOggFileOnServer.getAbsolutePath());
            URL url = new URL(musicUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", plugin.getName() + "/" + plugin.getDescription().getVersion());
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                plugin.getLogger().warning("下载音乐文件失败，URL: " + musicUrl + "，HTTP 状态码: " + responseCode);
                return null;
            }

            long fileSize = connection.getContentLengthLong();
            if (maxDownloadSizeBytes > 0 && fileSize > maxDownloadSizeBytes) {
                plugin.getLogger().warning("音乐文件太大: " + fileSize + " 字节 (限制: " + maxDownloadSizeBytes + " 字节). URL: " + musicUrl);
                return null;
            }
            if (fileSize <= 0) {
                plugin.getLogger().info("无法获取音乐文件大小或文件为空 (Content-Length: " + fileSize + "). URL: " + musicUrl + ". 仍尝试下载...");
            }

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, downloadedOggFileOnServer.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (!downloadedOggFileOnServer.exists() || downloadedOggFileOnServer.length() == 0) {
                plugin.getLogger().severe("音乐文件下载后不存在或为空: " + downloadedOggFileOnServer.getAbsolutePath());
                return null;
            }
            plugin.getLogger().info("音乐文件下载成功: " + downloadedOggFileOnServer.getAbsolutePath() + " (" + downloadedOggFileOnServer.length() + " bytes)");

            String soundEventName = "eogdmusic.online." + uniqueId;
            String packFileNameOnServer = "temp_music_pack_" + uniqueId + ".zip";
            File zipFileOnServer = new File(tempPackDir, packFileNameOnServer);

            String packMetaContent = "{\n" +
                    "  \"pack\": {\n" +
                    "    \"pack_format\": 32,\n" +
                    "    \"description\": \"EogdMusicPlayer - Online Song (" + uniqueId + ")\"\n" +
                    "  }\n" +
                    "}";
            String soundJsonPathInZip = "eogd_online_music/" + oggFileNameInZip.replace(".ogg", "");
            String soundsJsonContent = String.format("{\n" +
                    "  \"%s\": {\n" +
                    "    \"sounds\": [ {\"name\": \"%s\", \"stream\": true} ]\n" + // Corrected sounds.json format
                    "  }\n" +
                    "}", soundEventName, soundJsonPathInZip);

            plugin.getLogger().info("准备创建资源包: " + zipFileOnServer.getAbsolutePath());
            plugin.getLogger().fine("Sounds.json 内容:\n" + soundsJsonContent);

            try (FileOutputStream fos = new FileOutputStream(zipFileOnServer);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                zos.putNextEntry(new ZipEntry("pack.mcmeta"));
                zos.write(packMetaContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("assets/minecraft/sounds.json"));
                zos.write(soundsJsonContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                String oggZipPath = "assets/minecraft/sounds/eogd_online_music/" + oggFileNameInZip;
                zos.putNextEntry(new ZipEntry(oggZipPath));
                Files.copy(downloadedOggFileOnServer.toPath(), zos);
                zos.closeEntry();
            }
            plugin.getLogger().info("临时资源包创建成功: " + zipFileOnServer.getAbsolutePath() + " (" + zipFileOnServer.length() + " bytes)");

            String sha1;
            try (InputStream is = Files.newInputStream(zipFileOnServer.toPath())) {
                sha1 = DigestUtils.sha1Hex(is);
            }
            plugin.getLogger().info("资源包 SHA-1: " + sha1);

            if (!downloadedOggFileOnServer.delete()) {
                plugin.getLogger().warning("无法删除临时的原始 OGG 文件: " + downloadedOggFileOnServer.getAbsolutePath());
            }

            return new GeneratedPackInfo(zipFileOnServer, packFileNameOnServer, soundEventName, sha1);

        } catch (MalformedURLException e) {
            plugin.getLogger().log(Level.WARNING, "提供的音乐 URL 格式不正确: " + musicUrl, e);
            return null;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "生成在线音乐资源包时发生 IO 错误 ("+ e.getClass().getSimpleName() +"): " + musicUrl + " - " + e.getMessage());
            if (downloadedOggFileOnServer.exists()) {
                downloadedOggFileOnServer.delete();
            }
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "生成在线音乐资源包时发生未知错误: " + musicUrl, e);
            if (downloadedOggFileOnServer.exists()) {
                downloadedOggFileOnServer.delete();
            }
            return null;
        }
    }

    public boolean cleanupPack(String packFileNameOnServer) {
        File fileToDelete = new File(tempPackDir, packFileNameOnServer);
        if (fileToDelete.exists()) {
            boolean deleted = fileToDelete.delete();
            if (deleted) {
                plugin.getLogger().info("已清理临时资源包: " + packFileNameOnServer);
            } else {
                plugin.getLogger().warning("无法清理临时资源包: " + packFileNameOnServer);
            }
            return deleted;
        }
        plugin.getLogger().fine("尝试清理资源包，但文件已不存在: " + packFileNameOnServer);
        return true;
    }

    public static class GeneratedPackInfo {
        public final File zipFileOnServer;
        public final String zipFileNameForUrl;
        public final String soundEventName;
        public final String sha1;

        public GeneratedPackInfo(File zipFileOnServer, String zipFileNameForUrl, String soundEventName, String sha1) {
            this.zipFileOnServer = zipFileOnServer;
            this.zipFileNameForUrl = zipFileNameForUrl;
            this.soundEventName = soundEventName;
            this.sha1 = sha1;
        }
    }
}