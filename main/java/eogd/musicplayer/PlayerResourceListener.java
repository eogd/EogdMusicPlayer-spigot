package eogd.musicplayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.logging.Level;

public class PlayerResourceListener implements Listener {

    private final MusicPlayerPlugin plugin;
    private final LegacyComponentSerializer legacySerializer;

    public PlayerResourceListener(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
        this.legacySerializer = LegacyComponentSerializer.legacySection();
    }

    private void sendMessageToPlayer(Player player, Component component) {
        player.sendMessage(legacySerializer.serialize(component));
    }

    @EventHandler
    public void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        MusicPlayerPlugin.PendingOnlineSound pendingSound = plugin.getPendingOnlineSound(playerId);

        if (pendingSound == null) {
            return;
        }

        plugin.getLogger().info("玩家 " + player.getName() + " 的在线音乐资源包状态: " + status + " (针对包: " + pendingSound.packFileName + ", 事件: " + pendingSound.soundEventName + ")");

        switch (status) {
            case SUCCESSFULLY_LOADED:
                sendMessageToPlayer(player, Component.text("临时音乐资源包已加载！正在播放...", NamedTextColor.GREEN));
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            player.playSound(player.getLocation(), pendingSound.soundEventName, SoundCategory.RECORDS, 1.0f, 1.0f);
                            plugin.getLogger().info("为玩家 " + player.getName() + " 播放在线声音: " + pendingSound.soundEventName);
                        } else {
                            plugin.getLogger().info("玩家 " + player.getName() + " 在播放前已离线，取消播放: " + pendingSound.soundEventName);
                        }
                    }
                }.runTaskLater(plugin, 20L);
                break;
            case DECLINED:
                sendMessageToPlayer(player, Component.text("你拒绝了临时音乐资源包。", NamedTextColor.RED));
                break;
            case FAILED_DOWNLOAD:
                sendMessageToPlayer(player, Component.text("临时音乐资源包下载失败。", NamedTextColor.RED));
                plugin.getLogger().warning("玩家 " + player.getName() + " 下载资源包 " + pendingSound.packFileName + " 失败。URL可能无效或网络问题。");
                break;
            case ACCEPTED:
                sendMessageToPlayer(player, Component.text("正在下载临时音乐资源包...", NamedTextColor.YELLOW));
                return;
            case INVALID_URL:
                sendMessageToPlayer(player, Component.text("提供的资源包 URL 无效。", NamedTextColor.RED));
                plugin.getLogger().warning("玩家 " + player.getName() + " 收到 INVALID_URL 状态，针对包 " + pendingSound.packFileName + "。请检查插件生成的 URL 和 HTTP 服务器配置。");
                break;
            default:
                plugin.getLogger().info("玩家 " + player.getName() + " 资源包状态未知: " + status);
                return;
        }

        plugin.clearPendingOnlineSound(playerId);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getResourcePackGenerator() != null) {
                    plugin.getResourcePackGenerator().cleanupPack(pendingSound.packFileName);
                }
            }
        }.runTaskLaterAsynchronously(plugin, plugin.getConfig().getLong("httpServer.cleanupDelaySeconds", 300) * 20L);
    }
}