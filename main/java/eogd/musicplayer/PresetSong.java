package eogd.musicplayer;

import org.bukkit.Material;
import java.util.List;

public class PresetSong {
    private final String name;
    private final String url;
    private final Material material;
    private final List<String> lore;

    public PresetSong(String name, String url, Material material, List<String> lore) {
        this.name = name;
        this.url = url;
        this.material = material;
        this.lore = lore;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public Material getMaterial() {
        return material;
    }

    public List<String> getLore() {
        return lore;
    }
}