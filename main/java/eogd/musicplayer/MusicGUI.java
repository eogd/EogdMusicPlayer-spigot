package eogd.musicplayer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


public class MusicGUI {

    private final MusicPlayerPlugin plugin;
    public static final int ITEMS_PER_PAGE = 45;
    private static final Map<UUID, MusicGUI> playerOpenGUIs = new HashMap<>();

    private int currentPage = 0;

    public MusicGUI(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void open(Player player) {
        currentPage = 0;
        playerOpenGUIs.put(player.getUniqueId(), this);
        player.openInventory(createInventory(player));
    }

    private Inventory createInventory(Player player) {
        List<PresetSong> presetSongs = plugin.getPresetSongs();
        int totalPages = (int) Math.ceil((double) presetSongs.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        String guiTitleString = plugin.getConfig().getString("gui.title", "§9音乐播放器");
        if (totalPages > 1) {
            guiTitleString += " §7(第 " + (currentPage + 1) + "/" + totalPages + " 页)";
        }
        String coloredGuiTitle = ChatColor.translateAlternateColorCodes('&', guiTitleString);

        Inventory inv = Bukkit.createInventory(player, 54, coloredGuiTitle);

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, presetSongs.size());

        if (presetSongs.isEmpty()) {
            ItemStack noSongsItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = noSongsItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.noPresets", "§c没有可用的预设歌曲。")));
                noSongsItem.setItemMeta(meta);
            }
            inv.setItem(22, noSongsItem);
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                PresetSong song = presetSongs.get(i);
                ItemStack item = new ItemStack(song.getMaterial());
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', song.getName()));
                    List<String> coloredLore = song.getLore().stream()
                            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                            .collect(Collectors.toList());
                    meta.setLore(coloredLore);
                    item.setItemMeta(meta);
                }
                inv.addItem(item);
            }
        }

        if (currentPage > 0) {
            inv.setItem(45, createNavItem("gui.prevPageItem", "ARROW", "gui.prevPageName", "§c<- 上一页"));
        }
        if (currentPage < totalPages - 1) {
            inv.setItem(53, createNavItem("gui.nextPageItem", "ARROW", "gui.nextPageName", "§a下一页 ->"));
        }
        return inv;
    }

    private ItemStack createNavItem(String materialConfigKey, String defaultMaterial, String nameConfigKey, String defaultName) {
        Material navMaterial = Material.getMaterial(plugin.getConfig().getString(materialConfigKey, defaultMaterial).toUpperCase());
        if (navMaterial == null) navMaterial = Material.ARROW;
        ItemStack navItem = new ItemStack(navMaterial);
        ItemMeta navMeta = navItem.getItemMeta();
        if (navMeta != null) {
            navMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(nameConfigKey, defaultName)));
            navItem.setItemMeta(navMeta);
        }
        return navItem;
    }

    public void changePage(Player player, int direction) {
        List<PresetSong> presetSongs = plugin.getPresetSongs();
        int totalPages = (int) Math.ceil((double) presetSongs.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        currentPage += direction;
        if (currentPage < 0) {
            currentPage = 0;
        }
        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
        player.openInventory(createInventory(player));
    }

    public static MusicGUI getPlayerOpenGUI(Player player) {
        return playerOpenGUIs.get(player.getUniqueId());
    }
    public static void removePlayerOpenGUI(Player player) {
        playerOpenGUIs.remove(player.getUniqueId());
    }
}