package eogd.musicplayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MusicCommands implements CommandExecutor, TabCompleter {

    private final MusicPlayerPlugin plugin;
    private final MiniMessage miniMessage;
    private final DatabaseManager dbManager;
    private final LegacyComponentSerializer legacySerializer;

    public MusicCommands(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.dbManager = plugin.getDatabaseManager();
        this.legacySerializer = LegacyComponentSerializer.legacySection();
    }

    private void sendMessage(CommandSender sender, Component component) {
        sender.sendMessage(legacySerializer.serialize(component));
    }
    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(message);
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (plugin.getMusicCommandsInstance() == null) {
            sendMessage(sender, Component.text("插件指令处理器未正确初始化，请检查服务器控制台错误！", NamedTextColor.RED));
            return true;
        }

        if (command.getName().equalsIgnoreCase("bf")) {
            return handleBfCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("playurl")) {
            return handlePlayUrlCommand(sender, args);
        }
        return false;
    }

    private boolean handleBfCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "d": case "download":
                if (!(sender instanceof Player)) {
                    sendMessage(sender, Component.text("此指令只能由玩家执行。", NamedTextColor.RED)); return true;
                }
                handleDownloadResourcePack((Player) sender); break;
            case "start": case "play":
                if (!(sender instanceof Player)) {
                    sendMessage(sender, Component.text("此指令只能由玩家执行。", NamedTextColor.RED)); return true;
                }
                handlePlayMusic((Player) sender); break;
            case "setconfig":
                if (!sender.hasPermission("musicplayer.admin")) {
                    sendPermissionError(sender); return true;
                }
                handleSetConfig(sender, args); break;
            case "downloadlatestmusic":
                if (!sender.hasPermission("musicplayer.admin")) {
                    sendPermissionError(sender); return true;
                }
                handleDownloadSourceMusic(sender); break;
            case "reload":
                if (!sender.hasPermission("musicplayer.admin")) {
                    sendPermissionError(sender); return true;
                }
                plugin.reloadPluginConfiguration();
                sendMessage(sender, Component.text(plugin.getName() + " 的配置已成功重载。", NamedTextColor.GREEN));
                break;
            case "help": default: sendHelp(sender); break;
        }
        return true;
    }


    private boolean handlePlayUrlCommand(CommandSender sender, String[] args) {
        FileConfiguration config = plugin.getConfig();

        if (!config.getBoolean("httpServer.enabled", false) || plugin.getHttpFileServer() == null || !plugin.getHttpFileServer().isRunning()) {
            sendMessage(sender, Component.text("在线播放功能当前未启用或 HTTP 服务器未运行。", NamedTextColor.RED));
            return true;
        }
        if (plugin.getResourcePackGenerator() == null) {
            sendMessage(sender, Component.text("在线播放功能的资源包生成器未初始化。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sendMessage(sender, Component.text("用法: /playurl <URL> [玩家名]", NamedTextColor.RED));
            return true;
        }

        String musicUrlString = args[0];
        try {
            new URL(musicUrlString).toURI();
            if (!musicUrlString.toLowerCase().startsWith("http://") && !musicUrlString.toLowerCase().startsWith("https://")) {
                throw new MalformedURLException("URL 必须以 http:// 或 https:// 开头");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("提供的音乐 URL 无效: " + musicUrlString + " - " + e.getMessage());
            sendMessage(sender, Component.text("提供的音乐 URL 无效: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }

        Player targetPlayer;
        if (args.length >= 2) {
            if (!sender.hasPermission("musicplayer.playurl.others")) {
                sendPermissionError(sender); return true;
            }
            targetPlayer = Bukkit.getPlayerExact(args[1]);
            if (targetPlayer == null) {
                sendMessage(sender, Component.text("玩家 " + args[1] + " 未找到或不在线。", NamedTextColor.RED)); return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sendMessage(sender, Component.text("控制台请指定玩家名: /playurl <URL> <玩家名>", NamedTextColor.RED)); return true;
            }
            targetPlayer = (Player) sender;
            if (!targetPlayer.hasPermission("musicplayer.playurl")) {
                sendPermissionError(targetPlayer); return true;
            }
        }

        final Player finalTargetPlayer = targetPlayer;
        final String finalMusicUrlString = musicUrlString;
        sendMessage(sender, Component.text("正在处理音乐 URL 给玩家 " + finalTargetPlayer.getName() + "...", NamedTextColor.YELLOW));

        new BukkitRunnable() {
            @Override
            public void run() {
                ResourcePackGenerator.GeneratedPackInfo packInfo = plugin.getResourcePackGenerator().generateForUrl(finalMusicUrlString);

                if (packInfo == null) {
                    new BukkitRunnable() { @Override public void run() {
                        sendMessage(sender, Component.text("无法生成音乐资源包 (URL: " +cropString(finalMusicUrlString, 30) +")，请检查服务器日志。", NamedTextColor.RED));
                    }}.runTask(plugin);
                    return;
                }

                FileConfiguration currentCfg = plugin.getConfig();
                String publicAddress = currentCfg.getString("httpServer.publicAddress", "");
                int httpPort = currentCfg.getInt("httpServer.port", 8123); // 使用配置的HTTP端口

                if (publicAddress.isEmpty()) {
                    new BukkitRunnable() { @Override public void run() {
                        plugin.getLogger().warning("HTTP服务器的 publicAddress 未在 config.yml 中正确配置！在线播放 ('" + cropString(finalMusicUrlString, 30) + "') 可能无法对外部玩家工作。");
                        if (sender instanceof Player && ((Player)sender).getUniqueId().equals(finalTargetPlayer.getUniqueId())) {
                            sendMessage(sender, Component.text("警告: HTTP 服务器的 publicAddress 未配置，在线播放可能失败。", NamedTextColor.YELLOW));
                        } else {
                            sendMessage(sender, Component.text("目标玩家 " + finalTargetPlayer.getName() + " 的在线播放可能失败 (HTTP publicAddress 未配置)。", NamedTextColor.YELLOW));
                        }
                    }}.runTask(plugin);
                }

                String packUrl = plugin.getHttpFileServer().getFileUrl(publicAddress, httpPort, packInfo.zipFileNameForUrl);
                plugin.getLogger().info("准备为玩家 " + finalTargetPlayer.getName() + " 发送资源包: " + packUrl + " (SHA1: " + packInfo.sha1 + ")");
                plugin.addPendingOnlineSound(finalTargetPlayer.getUniqueId(),
                        new MusicPlayerPlugin.PendingOnlineSound(packInfo.zipFileNameForUrl, packInfo.soundEventName, packInfo.sha1, finalTargetPlayer));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            Component promptComponent = miniMessage.deserialize(currentCfg.getString("onlinePlayPromptMessage", "<gold>请接受临时音乐包...</gold>"));
                            String legacyPrompt = legacySerializer.serialize(promptComponent);
                            finalTargetPlayer.setResourcePack(packUrl, Hex.decodeHex(packInfo.sha1.toCharArray()), legacyPrompt, true);
                            if (!(sender instanceof Player) || !((Player)sender).getUniqueId().equals(finalTargetPlayer.getUniqueId())) {
                                sendMessage(sender, Component.text("已向 " + finalTargetPlayer.getName() + " 发送在线音乐资源包请求。", NamedTextColor.GREEN));
                            }
                        } catch (DecoderException e) {
                            plugin.getLogger().log(Level.SEVERE, "SHA-1 解码失败: " + packInfo.sha1, e);
                            sendMessage(sender, Component.text("发送资源包时发生内部错误 (SHA-1)。", NamedTextColor.RED));
                            plugin.clearPendingOnlineSound(finalTargetPlayer.getUniqueId());
                            plugin.getResourcePackGenerator().cleanupPack(packInfo.zipFileNameForUrl);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "发送资源包时发生未知错误。", e);
                            sendMessage(sender, Component.text("发送资源包时发生未知错误。", NamedTextColor.RED));
                            plugin.clearPendingOnlineSound(finalTargetPlayer.getUniqueId());
                            plugin.getResourcePackGenerator().cleanupPack(packInfo.zipFileNameForUrl);
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    private String cropString(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, miniMessage.deserialize("<gradient:gold:yellow>--- EogdMusicPlayer 帮助 (/bf) ---</gradient>"));
        sendMessage(sender, miniMessage.deserialize("<aqua>/bf d (或 download)</aqua> <gray>- 提示下载服务器预设音乐资源包。</gray>"));
        sendMessage(sender, miniMessage.deserialize("<aqua>/bf start (或 play)</aqua> <gray>- 播放预设资源包中的音乐。</gray>"));
        if (sender.hasPermission("musicplayer.admin")) {
            sendMessage(sender, miniMessage.deserialize("<gold>管理员指令 (/bf):</gold>"));
            sendMessage(sender, miniMessage.deserialize("<aqua>/bf setconfig musicurl <URL></aqua> <gray>- 设置预设音乐源文件URL。</gray>"));
            sendMessage(sender, miniMessage.deserialize("<aqua>/bf setconfig rpackurl <URL></aqua> <gray>- 设置预设资源包 .zip 文件的URL。</gray>"));
            sendMessage(sender, miniMessage.deserialize("<aqua>/bf setconfig rpacksha1 <SHA1></aqua> <gray>- 设置预设资源包的SHA-1哈希值。</gray>"));
            sendMessage(sender, miniMessage.deserialize("<aqua>/bf downloadlatestmusic</aqua> <gray>- 下载预设 musicurl 的音乐文件到服务器。</gray>"));
            sendMessage(sender, miniMessage.deserialize("<aqua>/bf reload</aqua> <gray>- 重载插件的 config.yml 文件。</gray>"));
        }
        sendMessage(sender, miniMessage.deserialize("<gradient:blue:aqua>--- 在线播放 (/playurl) ---</gradient>"));
        if (sender.hasPermission("musicplayer.playurl")) {
            sendMessage(sender, miniMessage.deserialize("<aqua>/playurl <URL></aqua> <gray>- 为你自己播放在线音乐。</gray>"));
        }
        if (sender.hasPermission("musicplayer.playurl.others")) {
            sendMessage(sender, miniMessage.deserialize("<aqua>/playurl <URL> <玩家名></aqua> <gray>- 为指定玩家播放在线音乐。</gray>"));
        }
        sendMessage(sender, miniMessage.deserialize("<gray>插件作者: eogd</gray>"));
    }

    private void sendPermissionError(CommandSender sender) {
        sendMessage(sender, Component.text("你没有权限执行此指令。", NamedTextColor.RED));
    }

    private void handleSetConfig(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, miniMessage.deserialize("<red>用法: /bf setconfig <键> <值></red>"));
            sendMessage(sender, miniMessage.deserialize("<yellow>可用键: musicurl, rpackurl, rpacksha1</yellow>"));
            return;
        }
        String key = args[1].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        switch (key) {
            case "musicurl":
                dbManager.saveSetting(DatabaseManager.MUSIC_DOWNLOAD_URL_KEY, value);
                sendMessage(sender, miniMessage.deserialize("<green>音乐下载URL已更新为: " + value + "</green>")); break;
            case "rpackurl":
                dbManager.saveSetting(DatabaseManager.RESOURCE_PACK_URL_KEY, value);
                sendMessage(sender, miniMessage.deserialize("<green>资源包URL已更新为: " + value + "</green>")); break;
            case "rpacksha1":
                if (value.length() != 40 || !value.matches("[a-f0-9]{40}")) {
                    sendMessage(sender, miniMessage.deserialize("<red>无效的SHA-1哈希值。必须是40位小写十六进制字符。</red>")); return;
                }
                dbManager.saveSetting(DatabaseManager.RESOURCE_PACK_SHA1_KEY, value);
                sendMessage(sender, miniMessage.deserialize("<green>资源包SHA-1哈希值已更新。</green>")); break;
            default:
                sendMessage(sender, miniMessage.deserialize("<red>无效的配置键: " + key + "</red>"));
                sendMessage(sender, miniMessage.deserialize("<yellow>可用键: musicurl, rpackurl, rpacksha1</yellow>")); return;
        }
        sendMessage(sender, miniMessage.deserialize("<gold>提示: 数据库设置已立即生效。config.yml 中的设置在 /bf reload 后生效。</gold>"));
    }

    private void handleDownloadResourcePack(Player player) {
        String resourcePackURL = dbManager.getSetting(DatabaseManager.RESOURCE_PACK_URL_KEY, "");
        String resourcePackSHA1String = dbManager.getSetting(DatabaseManager.RESOURCE_PACK_SHA1_KEY, "");
        String promptMessageStringRaw = plugin.getConfig().getString("resourcePackPromptMessage", "<gold>服务器音乐包可用！</gold>");
        if (resourcePackURL.isEmpty() || resourcePackSHA1String.isEmpty()) {
            sendMessage(player, miniMessage.deserialize("<red>服务器尚未配置预设资源包。请联系管理员。</red>")); return;
        }
        if (resourcePackSHA1String.length() != 40 || !resourcePackSHA1String.matches("[a-f0-9]{40}")) {
            sendMessage(player, miniMessage.deserialize("<red>服务器预设资源包的SHA-1哈希值配置无效。请联系管理员。</red>")); return;
        }
        Component promptComponent = miniMessage.deserialize(promptMessageStringRaw);
        String legacyPrompt = legacySerializer.serialize(promptComponent);
        try {
            player.setResourcePack(resourcePackURL, Hex.decodeHex(resourcePackSHA1String.toCharArray()), legacyPrompt, false);
            sendMessage(player, miniMessage.deserialize("<green>已发送预设资源包下载请求。</green>"));
        } catch (DecoderException e) {
            sendMessage(player, miniMessage.deserialize("<red>预设资源包SHA-1哈希值格式错误。请联系管理员。</red>"));
        }
    }

    private void handlePlayMusic(Player player) {
        String soundEventName = plugin.getConfig().getString("soundEventName", "");
        if (soundEventName.isEmpty()) {
            sendMessage(player, miniMessage.deserialize("<red>预设音乐的声音事件尚未配置。请联系管理员。</red>")); return;
        }
        player.playSound(player.getLocation(), soundEventName, SoundCategory.RECORDS, 1.0f, 1.0f);
        sendMessage(player, miniMessage.deserialize("<green>▶ 正在播放预设音乐... (" + soundEventName + ")</green>"));
    }

    private void handleDownloadSourceMusic(CommandSender sender) {
        String musicUrlString = dbManager.getSetting(DatabaseManager.MUSIC_DOWNLOAD_URL_KEY);
        if (musicUrlString == null || musicUrlString.isEmpty()) {
            sendMessage(sender, miniMessage.deserialize("<red>预设音乐下载URL尚未设置。请使用: /bf setconfig musicurl <URL></red>")); return;
        }
        final String finalMusicUrl = musicUrlString;
        sendMessage(sender, miniMessage.deserialize("<yellow>正在开始从以下URL下载预设音乐: " + finalMusicUrl + "</yellow>"));
        new BukkitRunnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(finalMusicUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("User-Agent", plugin.getName() + "/" + plugin.getDescription().getVersion());
                    connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(true);
                    int responseCode = connection.getResponseCode();
                    if (responseCode < 200 || responseCode >=300) {
                        final String errorMsg = "HTTP Error " + responseCode;
                        new BukkitRunnable() { @Override public void run() {
                            sendMessage(sender, miniMessage.deserialize("<red>下载预设音乐失败: " + errorMsg + "</red>"));
                        }}.runTask(plugin); return;
                    }
                    String fileName = "downloaded_preset_music.ogg";
                    String disposition = connection.getHeaderField("Content-Disposition");
                    if (disposition != null) {
                        String search = "filename="; int index = disposition.toLowerCase().indexOf(search);
                        if (index > 0) {
                            fileName = disposition.substring(index + search.length());
                            if (fileName.startsWith("\"") && fileName.endsWith("\"")) fileName = fileName.substring(1, fileName.length() -1);
                        }
                    } else {
                        String path = url.getPath();
                        if (path != null && !path.trim().isEmpty() && path.contains("/")) {
                            fileName = path.substring(path.lastIndexOf('/') + 1);
                            if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf("?"));
                            if (fileName.trim().isEmpty()) fileName = "downloaded_from_path.dat";
                        }
                    }
                    fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (fileName.length() > 100) fileName = fileName.substring(0, 100);

                    File downloadDir = new File(plugin.getDataFolder(), "downloads");
                    if (!downloadDir.exists()) downloadDir.mkdirs();
                    File outputFile = new File(downloadDir, fileName);
                    try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                        Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        new BukkitRunnable() { @Override public void run() {
                            sendMessage(sender, miniMessage.deserialize("<green>预设音乐文件已成功下载到: <aqua>" + outputFile.getPath().replace("\\", "/") + "</aqua></green>"));
                        }}.runTask(plugin);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "下载预设音乐时出错 ("+finalMusicUrl+")", e);
                    final String errorMsg = e.getMessage();
                    new BukkitRunnable() { @Override public void run() {
                        sendMessage(sender, miniMessage.deserialize("<red>下载预设音乐时发生错误: " + errorMsg + "</red>"));
                    }}.runTask(plugin);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();
        if (command.getName().equalsIgnoreCase("bf")) {
            if (args.length == 1) {
                Stream.of("d", "download", "start", "play", "help", "setconfig", "downloadlatestmusic", "reload")
                        .filter(s -> s.startsWith(currentArg)).forEach(completions::add);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("setconfig") && sender.hasPermission("musicplayer.admin")) {
                Stream.of("musicurl", "rpackurl", "rpacksha1")
                        .filter(s -> s.startsWith(currentArg)).forEach(completions::add);
            }
        } else if (command.getName().equalsIgnoreCase("playurl")) {
            if (args.length == 1) {
                if ("<URL>".startsWith(currentArg) || currentArg.isEmpty()) completions.add("<URL>");
            } else if (args.length == 2 && sender.hasPermission("musicplayer.playurl.others")) {
                Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(currentArg)).forEach(completions::add);
            }
        }
        return completions;
    }
}