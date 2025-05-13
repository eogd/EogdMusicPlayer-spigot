package eogd.musicplayer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MusicRoom {
    private final String roomId;
    private final Player creator;
    private String musicUrl;
    private String description;
    private final Set<Player> members = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private long lastActivityTime;
    private RoomStatus status;
    private String packFileName;
    private boolean playRequestActive = false;

    public enum RoomStatus {
        ACTIVE,
        PLAYING,
        CLOSING,
        CLOSED
    }

    public MusicRoom(String roomId, Player creator, String musicUrl, String description) {
        this.roomId = roomId;
        this.creator = creator;
        this.musicUrl = musicUrl;
        this.description = description;
        this.lastActivityTime = System.currentTimeMillis();
        this.status = RoomStatus.ACTIVE;
        addMember(creator);
    }

    public String getRoomId() { return roomId; }
    public Player getCreator() { return creator; }
    public String getMusicUrl() { return musicUrl; }

    public void setMusicUrl(String musicUrl) {
        this.musicUrl = musicUrl;
        this.lastActivityTime = System.currentTimeMillis();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<Player> getMembers() { return Collections.unmodifiableSet(members); }

    public void addMember(Player player) {
        members.add(player);
        this.lastActivityTime = System.currentTimeMillis();
    }

    public void removeMember(Player player) {
        boolean removed = members.remove(player);
        if (removed) {
            this.lastActivityTime = System.currentTimeMillis();
            if (player.equals(creator) && status != RoomStatus.CLOSING && status != RoomStatus.CLOSED) {
                MusicPlayerPlugin plugin = JavaPlugin.getPlugin(MusicPlayerPlugin.class);
                plugin.getLogger().info("音乐室发起者 " + creator.getName() + " 离开了音乐室 " + roomId + "。准备关闭...");
                setStatus(RoomStatus.CLOSING);
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.removeMusicRoom(roomId), 20L * 1);
            }
        }
    }

    public boolean isMember(Player player) { return members.contains(player); }
    public boolean isEmpty() { return members.isEmpty(); }
    public long getLastActivityTime() { return lastActivityTime; }
    public void updateLastActivityTime() { this.lastActivityTime = System.currentTimeMillis(); }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
    public @Nullable String getPackFileName() { return packFileName; }
    public void setPackFileName(@Nullable String packFileName) { this.packFileName = packFileName; }

    public boolean isPlayRequestActive() { return playRequestActive; }
    public void setPlayRequestActive(boolean playRequestActive) { this.playRequestActive = playRequestActive; }


    public void stopPlaybackForPlayer(@NotNull Player player) {
        MusicPlayerPlugin plugin = JavaPlugin.getPlugin(MusicPlayerPlugin.class);
        if (plugin.getHttpFileServer() != null && plugin.getHttpFileServer().isRunning()) {
            String soundEventName = plugin.getHttpFileServer().getServePathPrefix() + ".room." + this.roomId;
            player.stopSound(soundEventName);
        }
        updateLastActivityTime();
    }

    public void stopPlaybackForAll() {
        MusicPlayerPlugin plugin = JavaPlugin.getPlugin(MusicPlayerPlugin.class);
        if (plugin.getHttpFileServer() != null && plugin.getHttpFileServer().isRunning()) {
            String soundEventName = plugin.getHttpFileServer().getServePathPrefix() + ".room." + this.roomId;
            for (Player member : new HashSet<>(members)) {
                if (member.isOnline()) {
                    member.stopSound(soundEventName);
                }
            }
        }
        this.setPlayRequestActive(false);
        this.setStatus(RoomStatus.ACTIVE);
        updateLastActivityTime();
    }
}