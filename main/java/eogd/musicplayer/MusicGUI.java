package eogd.musicplayer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MusicGUI {

    private final MusicPlayerPlugin plugin;
    private int currentPage = 0;
    public static final int ITEMS_PER_PAGE = 45;

    private static final Map<UUID, MusicGUI> openGUIs = new HashMap<>();

    public MusicGUI(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        String guiTitleFromConfig = plugin.getConfig().getString("gui.title", "§9音乐播放器");
        List<PresetSong> presetSongs = plugin.getPresetSongs();
        int totalPages = (int) Math.ceil((double) presetSongs.size() / (double) ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        String finalTitle = guiTitleFromConfig;
        if (totalPages > 1) {
            finalTitle += " §7(第 " + (currentPage + 1) + "/" + totalPages + " 页)";
        }

        Inventory dynamicInv = Bukkit.createInventory(null, 54, ChatColor.translateAlternateColorCodes('&', finalTitle));
        populateItems(dynamicInv, presetSongs, totalPages);

        player.openInventory(dynamicInv);
        openGUIs.put(player.getUniqueId(), this);
    }

    private void populateItems(Inventory currentInventory, List<PresetSong> presetSongs, int totalPages) {
        currentInventory.clear();

        if (presetSongs.isEmpty()) {
            String noPresetsMessage = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.noPresets", "§c没有可用的预设歌曲。"));
            ItemStack noSongsItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = noSongsItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(noPresetsMessage);
                noSongsItem.setItemMeta(meta);
            }
            currentInventory.setItem(22, noSongsItem);
        } else {
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, presetSongs.size());

            for (int i = startIndex; i < endIndex; i++) {
                PresetSong song = presetSongs.get(i);
                ItemStack songItem = new ItemStack(song.getDisplayItemMaterial());
                ItemMeta songMeta = songItem.getItemMeta();
                if (songMeta != null) {
                    songMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', song.getName()));
                    List<String> lore = song.getLore().stream()
                            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                            .collect(Collectors.toList());
                    songMeta.setLore(lore);
                    songItem.setItemMeta(songMeta);
                }
                currentInventory.setItem(i - startIndex, songItem);
            }
        }

        if (currentPage > 0) {
            String prevName = plugin.getConfig().getString("gui.prevPageName", "§c<- 上一页");
            currentInventory.setItem(45, createNavItem(prevName, plugin.getConfig().getString("gui.prevPageItem", "ARROW")));
        }
        if (currentPage < totalPages - 1) {
            String nextName = plugin.getConfig().getString("gui.nextPageName", "§a下一页 ->");
            currentInventory.setItem(53, createNavItem(nextName, plugin.getConfig().getString("gui.nextPageItem", "ARROW")));
        }
    }

    private ItemStack createNavItem(String name, String materialName) {
        Material itemMaterial;
        try {
            itemMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            itemMaterial = Material.ARROW;
            plugin.getLogger().warning("GUI导航物品材质 '" + materialName + "' 无效，将使用默认的 ARROW。");
        }
        ItemStack item = new ItemStack(itemMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void changePage(Player player, int direction) {
        List<PresetSong> presetSongs = plugin.getPresetSongs();
        int totalPages = (int) Math.ceil((double) presetSongs.size() / (double) ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        int newPage = currentPage + direction;
        if (newPage >= 0 && newPage < totalPages) {
            currentPage = newPage;
            open(player);
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public static MusicGUI getPlayerOpenGUI(Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    public static void removePlayerOpenGUI(Player player) {
        openGUIs.remove(player.getUniqueId());
    }
}