package eogd.musicplayer;

import org.bukkit.Material;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PresetSong {
    private final String name;
    private final String url;
    private final Material displayItemMaterial;
    private final List<String> lore;

    public PresetSong(String name, String url, Material displayItemMaterial, List<String> lore) {
        this.name = name;
        this.url = url;
        this.displayItemMaterial = displayItemMaterial;
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }

    public String getName() { return name; }
    public String getUrl() { return url; }
    public Material getDisplayItemMaterial() { return displayItemMaterial; }
    public List<String> getLore() { return Collections.unmodifiableList(lore); }
}