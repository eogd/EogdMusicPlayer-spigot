package eogd.musicplayer;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class PlayerResourceListener implements Listener {

    private final MusicPlayerPlugin plugin;
    private final MusicCommands musicCommands;

    public PlayerResourceListener(MusicPlayerPlugin plugin, MusicCommands musicCommands) {
        this.plugin = plugin;
        this.musicCommands = musicCommands;
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        String pendingPackFullIdentifier = plugin.getPlayerPendingPackType(player.getUniqueId());

        if (pendingPackFullIdentifier == null) {
            return;
        }

        String[] typeParts = pendingPackFullIdentifier.split(":", 3);
        String packTypeOrSoundSource = typeParts[0];
        String tempPackFileName = null;
        String roomIdForRoomType = null;

        if (packTypeOrSoundSource.equals("singleUser") && typeParts.length >= 2) {
            tempPackFileName = typeParts[1];
        } else if (packTypeOrSoundSource.equals("room") && typeParts.length >= 3) {
            roomIdForRoomType = typeParts[1];
            tempPackFileName = typeParts[2];
        } else if (packTypeOrSoundSource.equals("preset") && typeParts.length >= 2){
            tempPackFileName = typeParts[1];
        }


        switch (status) {
            case SUCCESSFULLY_LOADED:
                plugin.sendConfigMsg(player, "messages.resourcePack.status.successfully_loaded");
                if (tempPackFileName != null) { // 只有成功加载了有效的包才设置
                    plugin.setPlayerCurrentMusicPack(player.getUniqueId(), tempPackFileName);
                }

                if (packTypeOrSoundSource.equals("singleUser") || packTypeOrSoundSource.equals("preset")) {
                    MusicPlayerPlugin.PendingOnlineSound pendingSound = plugin.getPendingSingleUserSound(player.getUniqueId());
                    if (pendingSound != null && tempPackFileName != null && tempPackFileName.equals(pendingSound.packFileName())) {
                        player.playSound(player.getLocation(), pendingSound.soundEventName(), SoundCategory.MUSIC, 1.0f, 1.0f);
                        plugin.getLogger().info("播放独立/预设音乐: " + pendingSound.soundEventName() + " for " + player.getName());
                    } else if (pendingSound != null && (tempPackFileName == null || !tempPackFileName.equals(pendingSound.packFileName()))){
                        plugin.getLogger().warning("玩家 " + player.getName() + " 成功加载了资源包，但待播放的独立/预设音乐信息不匹配或包文件名缺失。文件名: " + tempPackFileName + ", 期望: " + (pendingSound.packFileName() != null ? pendingSound.packFileName() : "null"));
                        if (plugin.shouldUseMergedPackLogic()) {
                            plugin.sendOriginalBasePackToPlayer(player);
                        }
                    }
                } else if (packTypeOrSoundSource.equals("room") && roomIdForRoomType != null) {
                    MusicRoom room = plugin.getMusicRoom(roomIdForRoomType);
                    if (room != null && room.isPlayRequestActive() && plugin.getHttpFileServer() != null &&
                            tempPackFileName != null && Objects.equals(tempPackFileName, room.getPackFileName())) {
                        String soundEventName = plugin.getHttpFileServer().getServePathPrefix() + ".room." + room.getRoomId();
                        player.playSound(player.getLocation(), soundEventName, SoundCategory.MUSIC, 1.0f, 1.0f);
                        plugin.getLogger().info("播放房间音乐: " + soundEventName + " for " + player.getName() + " in room " + roomIdForRoomType);
                        room.updateLastActivityTime();
                        room.setStatus(MusicRoom.RoomStatus.PLAYING);
                    } else if (room != null && (!room.isPlayRequestActive() || (room.getPackFileName() != null && !Objects.equals(tempPackFileName, room.getPackFileName())))) {
                        plugin.getLogger().warning("玩家 " + player.getName() + " 加载了过时的房间资源包 " + tempPackFileName + " (房间 " + roomIdForRoomType + "). 正在恢复基础包。");
                        if (tempPackFileName != null && plugin.getResourcePackGenerator() != null && !plugin.isPrewarmedPackFile(tempPackFileName)) {
                            plugin.getResourcePackGenerator().cleanupPack(tempPackFileName);
                        }
                        plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
                        if (plugin.shouldUseMergedPackLogic()) {
                            plugin.sendOriginalBasePackToPlayer(player);
                        }
                    } else if (room == null) {
                        plugin.getLogger().warning("玩家 " + player.getName() + " 加载了房间资源包，但房间 " + roomIdForRoomType + " 已不存在。");
                        if (tempPackFileName != null && plugin.getResourcePackGenerator() != null && !plugin.isPrewarmedPackFile(tempPackFileName)) {
                            plugin.getResourcePackGenerator().cleanupPack(tempPackFileName);
                        }
                        plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
                        if (plugin.shouldUseMergedPackLogic()) {
                            plugin.sendOriginalBasePackToPlayer(player);
                        }
                    }
                }
                plugin.clearPlayerPendingPackType(player.getUniqueId());
                break;
            case DECLINED:
            case FAILED_DOWNLOAD:
                if (status == PlayerResourcePackStatusEvent.Status.DECLINED) {
                    plugin.sendConfigMsg(player, "messages.resourcePack.status.declined");
                } else {
                    plugin.sendConfigMsg(player, "messages.resourcePack.status.failed");
                }
                if (tempPackFileName != null && plugin.getResourcePackGenerator() != null && !plugin.isPrewarmedPackFile(tempPackFileName)) {
                    plugin.getResourcePackGenerator().cleanupPack(tempPackFileName);
                }
                if (packTypeOrSoundSource.equals("singleUser") || packTypeOrSoundSource.equals("preset")) {
                    plugin.clearPendingSingleUserSound(player.getUniqueId());
                } else if (packTypeOrSoundSource.equals("room") && roomIdForRoomType != null) {
                    MusicRoom room = plugin.getMusicRoom(roomIdForRoomType);
                    if (room != null) {
                        room.setPlayRequestActive(false);
                    }
                }
                plugin.clearPlayerPendingPackType(player.getUniqueId());
                plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
                if (plugin.shouldUseMergedPackLogic()) {
                    plugin.sendOriginalBasePackToPlayer(player);
                }
                break;
            case ACCEPTED:
                plugin.sendConfigMsg(player, "messages.resourcePack.status.accepted");
                break;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryView view = event.getView();

        MusicGUI currentGui = MusicGUI.getPlayerOpenGUI(player);
        if (currentGui == null) {
            return;
        }

        List<PresetSong> presetSongs = plugin.getPresetSongs();
        int totalPages = (int) Math.ceil((double) presetSongs.size() / (double) MusicGUI.ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        String guiTitleFromConfig = plugin.getConfig().getString("gui.title", "§9音乐播放器");
        String expectedTitleString = guiTitleFromConfig;
        if (totalPages > 1) {
            expectedTitleString += " §7(第 " + (currentGui.getCurrentPage() + 1) + "/" + totalPages + " 页)";
        }
        String coloredExpectedTitle = ChatColor.translateAlternateColorCodes('&', expectedTitleString);

        if (!view.getTitle().equals(coloredExpectedTitle)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || meta.getDisplayName().isEmpty()) return;

        String itemName = meta.getDisplayName();
        String nextPageName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.nextPageName", "§a下一页 ->"));
        String prevPageName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.prevPageName", "§c<- 上一页"));

        if (itemName.equals(nextPageName)) {
            currentGui.changePage(player, 1);
        } else if (itemName.equals(prevPageName)) {
            currentGui.changePage(player, -1);
        } else {
            PresetSong selectedSong = plugin.getPresetSongs().stream()
                    .filter(song -> {
                        String songDisplayItemName = ChatColor.translateAlternateColorCodes('&', song.getName());
                        return songDisplayItemName.equals(itemName);
                    })
                    .findFirst().orElse(null);

            if (selectedSong != null) {
                player.closeInventory();
                musicCommands.handlePlay(player, selectedSong.getUrl(), MusicCommands.PlaybackContextType.PRESET, null, selectedSong);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        String pendingPackFullIdentifier = plugin.getPlayerPendingPackType(player.getUniqueId());
        if (pendingPackFullIdentifier != null) {
            String[] typeParts = pendingPackFullIdentifier.split(":", 3);
            String packTypeOrSoundSource = typeParts[0];
            String tempPackFileName = null;
            if (packTypeOrSoundSource.equals("singleUser") && typeParts.length >= 2) tempPackFileName = typeParts[1];
            else if (packTypeOrSoundSource.equals("room") && typeParts.length >= 3) tempPackFileName = typeParts[2];
            else if (packTypeOrSoundSource.equals("preset") && typeParts.length >= 2) tempPackFileName = typeParts[1];

            if (tempPackFileName != null && plugin.getResourcePackGenerator() != null && !plugin.isPrewarmedPackFile(tempPackFileName)) {
                plugin.getResourcePackGenerator().cleanupPack(tempPackFileName);
            }
        }
        plugin.clearPendingSingleUserSound(player.getUniqueId());
        plugin.clearPlayerPendingPackType(player.getUniqueId());

        String currentTempMusicFile = plugin.getPlayerCurrentMusicPackFile(player.getUniqueId());
        if (currentTempMusicFile != null && plugin.getResourcePackGenerator() != null && !plugin.isPrewarmedPackFile(currentTempMusicFile)) {
            plugin.getResourcePackGenerator().cleanupPack(currentTempMusicFile);
        }
        plugin.clearPlayerCurrentMusicPack(player.getUniqueId());

        for (MusicRoom room : new HashSet<>(plugin.getActiveMusicRoomsView())) {
            if (room.isMember(player)) {
                room.removeMember(player);
                plugin.getLogger().info("玩家 " + player.getName() + " 因断开连接离开音乐室 " + room.getRoomId());
            }
        }
        MusicGUI.removePlayerOpenGUI(player);
    }
}