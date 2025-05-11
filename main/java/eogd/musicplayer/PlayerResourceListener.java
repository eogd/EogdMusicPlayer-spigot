package eogd.musicplayer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor; // For GUI item names/lore

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;


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

        if (pendingPackFullIdentifier == null) return;

        String[] typeParts = pendingPackFullIdentifier.split(":", 3);
        String packType = typeParts[0];

        switch (status) {
            case SUCCESSFULLY_LOADED:
                plugin.sendConfigMsg(player, "messages.resourcePack.status.successfully_loaded");

                if (packType.equals("singleUser")) {
                    MusicPlayerPlugin.PendingOnlineSound pendingSound = plugin.getPendingSingleUserSound(player.getUniqueId());
                    if (pendingSound != null) {
                        player.playSound(player.getLocation(), pendingSound.soundEventName, 1.0f, 1.0f);
                        plugin.clearPlayerPendingPackType(player.getUniqueId());
                    }
                } else if (packType.equals("room") && typeParts.length >= 3) {
                    String roomId = typeParts[1];
                    MusicRoom room = plugin.getMusicRoom(roomId);
                    if (room != null && room.isPlayRequestActive() && plugin.getHttpFileServer() != null) {
                        String soundEventName = plugin.getHttpFileServer().getServePath() + ".room." + room.getRoomId();
                        player.playSound(player.getLocation(), soundEventName, 1.0f, 1.0f);
                        room.updateLastActivityTime();
                        room.setStatus(MusicRoom.RoomStatus.PLAYING);
                    }
                    plugin.clearPlayerPendingPackType(player.getUniqueId());
                }
                break;
            case DECLINED:
                plugin.sendConfigMsg(player, "messages.resourcePack.status.declined");
                cleanupFailedOrDeclinedPack(player, pendingPackFullIdentifier);
                break;
            case FAILED_DOWNLOAD:
                plugin.sendConfigMsg(player, "messages.resourcePack.status.failed");
                cleanupFailedOrDeclinedPack(player, pendingPackFullIdentifier);
                break;
            case ACCEPTED:
                plugin.sendConfigMsg(player, "messages.resourcePack.status.accepted");
                break;
        }
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            plugin.clearPlayerPendingPackType(player.getUniqueId());
        }
    }

    private void cleanupFailedOrDeclinedPack(Player player, String pendingPackFullIdentifier) {
        if (pendingPackFullIdentifier == null) return;
        String[] typeParts = pendingPackFullIdentifier.split(":", 3);
        String packType = typeParts[0];

        if (packType.equals("singleUser")) {
            MusicPlayerPlugin.PendingOnlineSound pendingSound = plugin.getPendingSingleUserSound(player.getUniqueId());
            if (pendingSound != null && plugin.getResourcePackGenerator() != null && pendingSound.packFileName != null) {
                if (pendingPackFullIdentifier.endsWith(pendingSound.packFileName)) {
                    plugin.getResourcePackGenerator().cleanupPack(pendingSound.packFileName);
                }
            }
            plugin.clearPendingSingleUserSound(player.getUniqueId());
        } else if (packType.equals("room") && typeParts.length >= 3) {
            String packFileName = typeParts[2];
            if (plugin.getResourcePackGenerator() != null && packFileName != null) {
                plugin.getResourcePackGenerator().cleanupPack(packFileName);
            }
            plugin.getLogger().info("Player " + player.getName() + " failed/declined room resource pack: " + packFileName);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        InventoryView view = event.getView();

        String guiTitleFromConfig = plugin.getConfig().getString("gui.title", "§9音乐播放器");

        List<PresetSong> presetSongs = plugin.getPresetSongs();
        int totalPages = (int) Math.ceil((double) presetSongs.size() / (double) MusicGUI.ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        MusicGUI currentGui = MusicGUI.getPlayerOpenGUI(player);
        int currentPageForTitle = (currentGui != null) ? currentGui.getCurrentPage() : 0;

        String actualGuiTitleString = guiTitleFromConfig;
        if (totalPages > 1) {
            actualGuiTitleString += " §7(第 " + (currentPageForTitle + 1) + "/" + totalPages + " 页)";
        }
        String coloredGuiTitle = ChatColor.translateAlternateColorCodes('&', actualGuiTitleString);

        if (!view.getTitle().equals(coloredGuiTitle)) {
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

        if (currentGui != null) {
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
                    // GUI click always plays for self only
                    musicCommands.handlePlayUrl(player, selectedSong.getUrl(), false, null);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MusicPlayerPlugin.PendingOnlineSound pendingSound = plugin.getPendingSingleUserSound(player.getUniqueId());
        if (pendingSound != null && plugin.getResourcePackGenerator() != null && pendingSound.packFileName != null) {
            plugin.getResourcePackGenerator().cleanupPack(pendingSound.packFileName);
        }
        plugin.clearPendingSingleUserSound(player.getUniqueId());
        plugin.clearPlayerPendingPackType(player.getUniqueId());

        for (MusicRoom room : new HashSet<>(plugin.getActiveMusicRoomsView())) {
            if (room.isMember(player)) {
                room.removeMember(player);
                plugin.getLogger().info("Player " + player.getName() + " left music room " + room.getRoomId() + " due to disconnect.");
            }
        }
        MusicGUI.removePlayerOpenGUI(player);
    }
}