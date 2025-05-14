package eogd.musicplayer;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;


public class MusicCommands implements CommandExecutor, TabCompleter {

    private final MusicPlayerPlugin plugin;

    public enum PlaybackContextType {
        PRESET,
        DIRECT_URL,
        ROOM
    }

    public MusicCommands(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canExecute(CommandSender sender, String permissionKey, boolean playerOnly) {
        if (playerOnly && !(sender instanceof Player)) {
            plugin.sendConfigMsg(sender, "messages.general.playerOnly");
            return false;
        }
        if (!sender.hasPermission(permissionKey) && !(sender instanceof org.bukkit.command.ConsoleCommandSender &&
                (permissionKey.equals("eogdmusicplayer.reload") || permissionKey.equals("eogdmusicplayer.info")))) {
            plugin.sendConfigMsg(sender, "messages.general.noPermission");
            return false;
        }
        return true;
    }

    private void handleLeavePreviousRoom(Player player, @Nullable MusicRoom newRoomToJoin) {
        plugin.getActiveMusicRoomsView().stream()
                .filter(r -> r.isMember(player) && (newRoomToJoin == null || !r.equals(newRoomToJoin)))
                .findFirst()
                .ifPresent(otherRoom -> {
                    otherRoom.removeMember(player);
                    plugin.sendConfigMsg(player, "messages.bf.join.leftOtherRoom", "other_room_description", otherRoom.getDescription());
                    String playerCurrentTempPack = plugin.getPlayerCurrentMusicPackFile(player.getUniqueId());
                    if (otherRoom.getPackFileName() != null && otherRoom.getPackFileName().equals(playerCurrentTempPack) && !plugin.isPrewarmedPackFile(playerCurrentTempPack)) {
                        plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
                        if (plugin.shouldUseMergedPackLogic()) {
                            plugin.sendOriginalBasePackToPlayer(player);
                        }
                    }
                });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("bf")) {
            if (args.length == 0) {
                plugin.sendConfigMsg(sender, "messages.bf.usage");
                return true;
            }
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "play":
                    if (!canExecute(sender, "eogdmusicplayer.play", true)) return true;
                    if (!(sender instanceof Player playerForPlay)) return true;
                    if (args.length < 2) {
                        plugin.sendConfigMsg(playerForPlay, "messages.bf.play.usage");
                        return true;
                    }
                    String songIdentifier = args[1];
                    PresetSong preset = plugin.getPresetSongs().stream()
                            .filter(s -> {
                                String nameWithSections = ChatColor.translateAlternateColorCodes('&', s.getName());
                                String strippedName = ChatColor.stripColor(nameWithSections);
                                return strippedName.equalsIgnoreCase(songIdentifier) ||
                                        songIdentifier.equalsIgnoreCase(String.valueOf(plugin.getPresetSongs().indexOf(s) + 1));
                            })
                            .findFirst().orElse(null);

                    if (preset != null) {
                        handlePlay(playerForPlay, preset.getUrl(), PlaybackContextType.PRESET, null, preset);
                    } else {
                        plugin.sendConfigMsg(playerForPlay, "messages.bf.play.notFound", "song", songIdentifier);
                    }
                    return true;

                case "playurl":
                    if (!canExecute(sender, "eogdmusicplayer.playurl", true)) return true;
                    return handlePlayUrlCommandLogic(sender, args, true);

                case "stop":
                    if (!canExecute(sender, "eogdmusicplayer.stop", true)) return true;
                    if (!(sender instanceof Player playerForStop)) return true;

                    MusicRoom roomPlayerIsIn = plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.isMember(playerForStop))
                            .findFirst().orElse(null);
                    boolean stoppedSomething = false;

                    if (roomPlayerIsIn != null) {
                        String roomSoundEventBase = plugin.getHttpFileServer().getServePathPrefix() + ".room." + roomPlayerIsIn.getRoomId();
                        if (roomPlayerIsIn.getCreator().equals(playerForStop)) {
                            roomPlayerIsIn.stopPlaybackForAll();
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.roomStopped", "room_description", roomPlayerIsIn.getDescription());

                            String roomTempPack = roomPlayerIsIn.getPackFileName();
                            if (roomTempPack != null && !plugin.isPrewarmedPackFile(roomTempPack)) {
                                for (Player member : new HashSet<>(roomPlayerIsIn.getMembers())) {
                                    if (member.isOnline()) {
                                        String memberCurrentPack = plugin.getPlayerCurrentMusicPackFile(member.getUniqueId());
                                        if (roomTempPack.equals(memberCurrentPack)) {
                                            plugin.clearPlayerCurrentMusicPack(member.getUniqueId());
                                            if (plugin.shouldUseMergedPackLogic()) {
                                                plugin.sendOriginalBasePackToPlayer(member);
                                            }
                                        }
                                    }
                                }
                            }
                            stoppedSomething = true;
                        } else {
                            playerForStop.stopSound(roomSoundEventBase, SoundCategory.MUSIC);
                            String memberCurrentPack = plugin.getPlayerCurrentMusicPackFile(playerForStop.getUniqueId());
                            if (roomPlayerIsIn.getPackFileName() != null && roomPlayerIsIn.getPackFileName().equals(memberCurrentPack) && !plugin.isPrewarmedPackFile(memberCurrentPack)) {
                                plugin.clearPlayerCurrentMusicPack(playerForStop.getUniqueId());
                                if (plugin.shouldUseMergedPackLogic()) {
                                    plugin.sendOriginalBasePackToPlayer(playerForStop);
                                }
                            }
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.stoppedForSelfInRoom", "room_description", roomPlayerIsIn.getDescription());
                            stoppedSomething = true;
                        }
                    } else {
                        MusicPlayerPlugin.PendingOnlineSound pendingSoundInfo = plugin.getPendingSingleUserSound(playerForStop.getUniqueId());
                        String currentTempPack = plugin.getPlayerCurrentMusicPackFile(playerForStop.getUniqueId());

                        if (pendingSoundInfo != null) {
                            playerForStop.stopSound(pendingSoundInfo.soundEventName(), SoundCategory.MUSIC);
                            plugin.getLogger().info("停止独立音乐: " + pendingSoundInfo.soundEventName() + " for " + playerForStop.getName());
                            if (pendingSoundInfo.packFileName() != null && plugin.getResourcePackGenerator() != null && !plugin.isPrewarmedPackFile(pendingSoundInfo.packFileName())) {
                                plugin.getResourcePackGenerator().cleanupPack(pendingSoundInfo.packFileName());
                            }
                            plugin.clearPendingSingleUserSound(playerForStop.getUniqueId());
                            stoppedSomething = true;
                        }
                        if (currentTempPack != null && !plugin.isPrewarmedPackFile(currentTempPack)) {
                            boolean isRoomPack = plugin.getActiveMusicRoomsView().stream().anyMatch(r -> currentTempPack.equals(r.getPackFileName()));
                            if (!isRoomPack) {
                                if (plugin.getResourcePackGenerator() != null) {
                                    plugin.getResourcePackGenerator().cleanupPack(currentTempPack);
                                }
                                plugin.getLogger().info("清理当前玩家独立音乐包: " + currentTempPack + " for " + playerForStop.getName());
                                if (!stoppedSomething) stoppedSomething = true;
                            }
                        }

                        if (stoppedSomething) {
                            plugin.clearPlayerCurrentMusicPack(playerForStop.getUniqueId());
                            plugin.clearPlayerPendingPackType(playerForStop.getUniqueId());
                            if (plugin.shouldUseMergedPackLogic()) {
                                plugin.sendOriginalBasePackToPlayer(playerForStop);
                            }
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.stoppedForSelf");
                        }
                    }

                    if (!stoppedSomething) {
                        plugin.sendConfigMsg(playerForStop, "messages.bf.stop.notPlaying");
                    }
                    return true;

                case "gui":
                    if (!canExecute(sender, "eogdmusicplayer.gui", true)) return true;
                    if (!(sender instanceof Player playerForGui)) return true;
                    new MusicGUI(plugin).open(playerForGui);
                    return true;

                case "createroom":
                    if (!canExecute(sender, "eogdmusicplayer.createroom", true)) return true;
                    if (!(sender instanceof Player creator)) return true;
                    if (args.length < 3) {
                        plugin.sendConfigMsg(creator, "messages.bf.createroom.usage");
                        return true;
                    }
                    String musicUrl = args[1];
                    StringBuilder descriptionBuilder = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        descriptionBuilder.append(args[i]).append(" ");
                    }
                    String descriptionInput = descriptionBuilder.toString().trim();
                    if (descriptionInput.isEmpty()) {
                        plugin.sendConfigMsg(creator, "messages.bf.createroom.noDescription");
                        return true;
                    }

                    MusicRoom existingRoomByCreator = plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.getCreator().equals(creator))
                            .findFirst().orElse(null);
                    if (existingRoomByCreator != null) {
                        plugin.sendConfigMsg(creator, "messages.bf.createroom.alreadyCreated", "room_description", existingRoomByCreator.getDescription());
                        return true;
                    }

                    MusicRoom newRoom = plugin.createMusicRoom(creator, musicUrl, descriptionInput);
                    plugin.sendConfigMsg(creator, "messages.bf.createroom.successWithStartHint",
                            "description", newRoom.getDescription(),
                            "url", newRoom.getMusicUrl()
                    );
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (!onlinePlayer.equals(creator)) {
                            plugin.sendConfigMsg(onlinePlayer, "messages.bf.createroom.broadcast",
                                    "creator_name", creator.getName(),
                                    "description", newRoom.getDescription());
                        }
                    }
                    return true;

                case "join":
                    if (!canExecute(sender, "eogdmusicplayer.joinroom", true)) return true;
                    if (!(sender instanceof Player joiner)) return true;
                    if (args.length < 2) {
                        plugin.sendConfigMsg(joiner, "messages.bf.join.usage");
                        return true;
                    }
                    String creatorName = args[1];
                    MusicRoom targetRoom = plugin.findMusicRoomByCreatorName(creatorName);

                    if (targetRoom == null) {
                        plugin.sendConfigMsg(joiner, "messages.bf.join.roomNotFound", "creator", creatorName);
                        return true;
                    }
                    if (targetRoom.getCreator().equals(joiner)) {
                        plugin.sendConfigMsg(joiner, "messages.bf.join.alreadyCreator", "room_description", targetRoom.getDescription());
                        return true;
                    }
                    if (targetRoom.isMember(joiner)) {
                        plugin.sendConfigMsg(joiner, "messages.bf.join.alreadyMember", "room_description", targetRoom.getDescription());
                        return true;
                    }

                    handleLeavePreviousRoom(joiner, targetRoom);

                    targetRoom.addMember(joiner);
                    plugin.sendConfigMsg(joiner, "messages.bf.join.successNoAutoPlay", "room_description", targetRoom.getDescription());

                    if (targetRoom.getStatus() == MusicRoom.RoomStatus.PLAYING && targetRoom.getPackFileName() != null) {
                        handlePlay(joiner, targetRoom.getMusicUrl(), PlaybackContextType.ROOM, targetRoom, null);
                    }
                    return true;

                case "start":
                    if (!canExecute(sender, "eogdmusicplayer.room.start", true)) return true;
                    if (!(sender instanceof Player roomStarter)) return true;
                    MusicRoom roomToStart = plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.getCreator().equals(roomStarter))
                            .findFirst().orElse(null);
                    if (roomToStart == null) {
                        plugin.sendConfigMsg(roomStarter, "messages.bf.room.start.notRoomCreator");
                        return true;
                    }
                    if (roomToStart.getMusicUrl() == null || roomToStart.getMusicUrl().isEmpty()) {
                        plugin.sendConfigMsg(roomStarter, "messages.bf.room.start.noMusicUrl", "room_description", roomToStart.getDescription());
                        return true;
                    }

                    roomToStart.setPlayRequestActive(true);
                    String memberNotification = plugin.getConfig().getString("messages.bf.room.start.memberStartNotification");

                    for (Player member : new ArrayList<>(roomToStart.getMembers())) {
                        if (member.isOnline()) {
                            handlePlay(member, roomToStart.getMusicUrl(), PlaybackContextType.ROOM, roomToStart, null);
                            if (!member.equals(roomStarter) && memberNotification != null && !memberNotification.isEmpty()) {
                                plugin.sendLegacyMsg(member, memberNotification, "room_description", roomToStart.getDescription(), "creator_name", roomToStart.getCreator().getName());
                            }
                        }
                    }
                    plugin.sendConfigMsg(roomStarter, "messages.bf.room.start.started", "room_description", roomToStart.getDescription());
                    return true;

                case "roomplay":
                    if (!canExecute(sender, "eogdmusicplayer.room.roomplay", true)) return true;
                    if (!(sender instanceof Player roomPlayRequester)) return true;
                    MusicRoom ownRoom = plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.getCreator().equals(roomPlayRequester))
                            .findFirst().orElse(null);
                    if (ownRoom == null) {
                        plugin.sendConfigMsg(roomPlayRequester, "messages.bf.room.play.notRoomCreator");
                        return true;
                    }
                    if (args.length < 2) {
                        plugin.sendConfigMsg(roomPlayRequester, "messages.bf.room.play.usage");
                        return true;
                    }
                    String newMusicUrl = args[1];
                    if (newMusicUrl.equalsIgnoreCase(ownRoom.getMusicUrl())) {
                        plugin.sendConfigMsg(roomPlayRequester, "messages.bf.room.play.urlSame", "room_description", ownRoom.getDescription());
                        return true;
                    }

                    if (ownRoom.getStatus() == MusicRoom.RoomStatus.PLAYING || ownRoom.isPlayRequestActive()) {
                        ownRoom.stopPlaybackForAll();
                        String oldRoomTempPack = ownRoom.getPackFileName();
                        if (oldRoomTempPack != null && !plugin.isPrewarmedPackFile(oldRoomTempPack)) {
                            for(Player member : new HashSet<>(ownRoom.getMembers())) {
                                if(member.isOnline()) {
                                    String memberCurrentPack = plugin.getPlayerCurrentMusicPackFile(member.getUniqueId());
                                    if (oldRoomTempPack.equals(memberCurrentPack)) {
                                        plugin.clearPlayerCurrentMusicPack(member.getUniqueId());
                                        if (plugin.shouldUseMergedPackLogic()) {
                                            plugin.sendOriginalBasePackToPlayer(member);
                                        }
                                    }
                                }
                            }
                            if (plugin.getResourcePackGenerator() != null) {
                                plugin.getResourcePackGenerator().cleanupPack(oldRoomTempPack);
                            }
                        }
                        ownRoom.setPackFileName(null);
                    }
                    ownRoom.setMusicUrl(newMusicUrl);
                    plugin.sendConfigMsg(roomPlayRequester, "messages.bf.room.play.urlSet",
                            "room_description", ownRoom.getDescription(), "url", newMusicUrl);
                    plugin.sendConfigMsg(roomPlayRequester, "messages.bf.room.play.startHint");
                    return true;

                case "disbandroom":
                    if (!canExecute(sender, "eogdmusicplayer.disbandroom", true)) return true;
                    if (!(sender instanceof Player disbandRequester)) return true;
                    MusicRoom roomToDisband = plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.getCreator().equals(disbandRequester))
                            .findFirst().orElse(null);
                    if (roomToDisband == null) {
                        plugin.sendConfigMsg(disbandRequester, "messages.bf.disbandroom.notCreatorOrNoRoom");
                        return true;
                    }
                    String disbandedRoomDesc = roomToDisband.getDescription();
                    String roomSoundEventToStop = plugin.getHttpFileServer().getServePathPrefix() + ".room." + roomToDisband.getRoomId();
                    for(Player member : new HashSet<>(roomToDisband.getMembers())) {
                        if(member.isOnline()){
                            member.stopSound(roomSoundEventToStop, SoundCategory.MUSIC);
                        }
                    }
                    plugin.removeMusicRoom(roomToDisband.getRoomId());
                    plugin.sendConfigMsg(disbandRequester, "messages.bf.disbandroom.success", "room_description", disbandedRoomDesc);
                    return true;

                case "reload":
                    if (!canExecute(sender, "eogdmusicplayer.reload", false)) return true;
                    plugin.reloadPluginConfiguration();
                    plugin.sendConfigMsg(sender, "messages.bf.reload.success");
                    return true;

                case "info":
                    if (!canExecute(sender, "eogdmusicplayer.info", false)) return true;
                    PluginDescriptionFile pdf = plugin.getDescription();
                    sender.sendMessage(ChatColor.GOLD + "--- [" + ChatColor.YELLOW + "EogdMusicPlayer 信息" + ChatColor.GOLD + "] ---");
                    sender.sendMessage(ChatColor.AQUA + "作者: " + ChatColor.WHITE + String.join(", ", pdf.getAuthors()));
                    sender.sendMessage(ChatColor.AQUA + "版本: " + ChatColor.WHITE + pdf.getVersion());
                    sender.sendMessage(ChatColor.AQUA + "描述: " + ChatColor.WHITE + (pdf.getDescription() != null ? pdf.getDescription() : "N/A"));
                    sender.sendMessage(ChatColor.AQUA + "预设预热: " + ChatColor.WHITE + (plugin.isPresetPrewarmingEnabled() ? "开启" : "关闭"));
                    sender.sendMessage(ChatColor.GOLD + "-----------------------------");
                    return true;

                default:
                    plugin.sendConfigMsg(sender, "messages.bf.unknownCommand");
                    return true;
            }
        } else if (command.getName().equalsIgnoreCase("playurl")) {
            if (!canExecute(sender, "eogdmusicplayer.playurl", true)) return true;
            return handlePlayUrlCommandLogic(sender, args, false);
        } else if (command.getName().equalsIgnoreCase("internal_join_room")) {
            if (!(sender instanceof Player playerToJoin)) return true;
            if (args.length < 1) return true;
            String roomId = args[0];
            MusicRoom roomToJoin = plugin.getMusicRoom(roomId);
            if (roomToJoin != null) {
                if (!roomToJoin.isMember(playerToJoin) && !roomToJoin.getCreator().equals(playerToJoin)) {
                    handleLeavePreviousRoom(playerToJoin, roomToJoin);
                    roomToJoin.addMember(playerToJoin);
                    plugin.sendConfigMsg(playerToJoin, "messages.bf.join.successNoAutoPlay", "room_description", roomToJoin.getDescription());
                    if (roomToJoin.getStatus() == MusicRoom.RoomStatus.PLAYING && roomToJoin.getPackFileName() != null) {
                        handlePlay(playerToJoin, roomToJoin.getMusicUrl(), PlaybackContextType.ROOM, roomToJoin, null);
                    }
                } else {
                    plugin.sendConfigMsg(playerToJoin, "messages.bf.join.alreadyMember", "room_description", roomToJoin.getDescription());
                }
            } else {
                plugin.sendConfigMsg(playerToJoin, "messages.bf.join.internalRoomNotFound");
            }
            return true;
        }
        return false;
    }

    private boolean handlePlayUrlCommandLogic(CommandSender sender, String[] args, boolean isFromBfCommand) {
        if (!(sender instanceof Player player)) return true;
        String urlToPlay;
        if (isFromBfCommand) {
            if (args.length < 2) {
                plugin.sendConfigMsg(player, "messages.bf.playurl.usage"); return true;
            }
            urlToPlay = args[1];
        } else {
            if (args.length < 1) {
                plugin.sendConfigMsg(player, "messages.playurl.usage"); return true;
            }
            urlToPlay = args[0];
        }
        handlePlay(player, urlToPlay, PlaybackContextType.DIRECT_URL, null, null);
        return true;
    }

    public void handlePlay(Player player, String url, PlaybackContextType contextType, @Nullable MusicRoom roomContext, @Nullable PresetSong presetContext) {
        if (plugin.getResourcePackGenerator() == null || plugin.getHttpFileServer() == null || !plugin.getHttpFileServer().isRunning()) {
            plugin.sendConfigMsg(player, "messages.general.httpDisabled");
            return;
        }
        if (url == null || url.isEmpty()) {
            plugin.sendConfigMsg(player, "messages.general.invalidUrl");
            return;
        }
        if (plugin.shouldUseMergedPackLogic() && (plugin.getBasePackFile() == null || !plugin.getBasePackFile().exists())) {
            plugin.sendConfigMsg(player, "messages.general.basePackMissing");
            return;
        }

        MusicRoom currentRoomPlayerIsIn = plugin.getActiveMusicRoomsView().stream().filter(r -> r.isMember(player)).findFirst().orElse(null);
        if (contextType != PlaybackContextType.ROOM || roomContext == null || !roomContext.equals(currentRoomPlayerIsIn)) {
            MusicPlayerPlugin.PendingOnlineSound existingSingleSound = plugin.getPendingSingleUserSound(player.getUniqueId());
            if (existingSingleSound != null) {
                player.stopSound(existingSingleSound.soundEventName(), SoundCategory.MUSIC);
                if (existingSingleSound.packFileName() != null && plugin.getResourcePackGenerator() != null && !plugin.isPrewarmedPackFile(existingSingleSound.packFileName())) {
                    plugin.getResourcePackGenerator().cleanupPack(existingSingleSound.packFileName());
                }
                plugin.clearPendingSingleUserSound(player.getUniqueId());
                plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
            } else {
                String currentPack = plugin.getPlayerCurrentMusicPackFile(player.getUniqueId());
                if (currentPack != null && !plugin.isPrewarmedPackFile(currentPack) &&
                        (currentRoomPlayerIsIn == null || !currentPack.equals(currentRoomPlayerIsIn.getPackFileName())) ) {
                    player.stopSound(SoundCategory.MUSIC);
                    if (plugin.getResourcePackGenerator() != null) {
                        plugin.getResourcePackGenerator().cleanupPack(currentPack);
                    }
                    plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
                }
            }
        }


        String soundEventName;

        if (contextType == PlaybackContextType.PRESET && presetContext != null) {
            soundEventName = plugin.getHttpFileServer().getServePathPrefix() + ".preset." + plugin.createStableIdentifier(presetContext.getUrl());
            plugin.sendConfigMsg(player, "messages.bf.play.preparing", "song_name", ChatColor.translateAlternateColorCodes('&', presetContext.getName()));
        } else if (contextType == PlaybackContextType.ROOM && roomContext != null) {
            soundEventName = plugin.getHttpFileServer().getServePathPrefix() + ".room." + roomContext.getRoomId();
            plugin.sendConfigMsg(player, "messages.bf.room.start.startingMusic", "room_description", roomContext.getDescription());
        } else {
            String randomId = UUID.randomUUID().toString().substring(0, 4);
            String uniquePlayerIdPart = player.getUniqueId().toString().substring(0, 8);
            soundEventName = plugin.getHttpFileServer().getServePathPrefix() + ".single." + uniquePlayerIdPart + "." + randomId;
            plugin.sendConfigMsg(player, "messages.playurl.preparing");
        }

        plugin.getResourcePackGenerator().generateAndServePack(player, url, soundEventName, contextType == PlaybackContextType.ROOM, roomContext)
                .thenAccept(packInfo -> {
                    if (packInfo != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            String promptMessage = plugin.getMusicPackPromptMessage();
                            byte[] sha1Bytes;
                            try {
                                sha1Bytes = Hex.decodeHex(packInfo.sha1());
                            } catch (DecoderException e) {
                                plugin.getLogger().log(Level.SEVERE, "无效的SHA-1哈希值: " + packInfo.sha1(), e);
                                plugin.sendConfigMsg(player, "messages.playurl.error");
                                if (roomContext != null) roomContext.setPlayRequestActive(false);
                                if (!plugin.isPrewarmedPackFile(packInfo.packFileName())) {
                                    plugin.getResourcePackGenerator().cleanupPack(packInfo.packFileName());
                                }
                                if (plugin.shouldUseMergedPackLogic()) plugin.sendOriginalBasePackToPlayer(player);
                                return;
                            }
                            player.setResourcePack(packInfo.packUrl(), sha1Bytes, promptMessage, true);

                            if (contextType == PlaybackContextType.ROOM && roomContext != null) {
                                plugin.markPlayerPendingRoomPack(player.getUniqueId(), roomContext.getRoomId(), packInfo.packFileName());
                            } else {
                                plugin.addPendingSingleUserSound(player.getUniqueId(),
                                        new MusicPlayerPlugin.PendingOnlineSound(packInfo.packFileName(), soundEventName, packInfo.sha1(), player, packInfo.packUrl()));
                            }
                        });
                    } else {
                        plugin.sendConfigMsg(player, "messages.playurl.packCreationFailed");
                        if (roomContext != null) roomContext.setPlayRequestActive(false);
                    }
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "处理播放请求时出错 for " + player.getName() + " (URL: " + url + "): " + ex.getMessage(), ex);
                    plugin.sendConfigMsg(player, "messages.playurl.error");
                    if (roomContext != null) roomContext.setPlayRequestActive(false);
                    return null;
                });
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("bf")) {
            if (args.length == 1) {
                String input = args[0].toLowerCase();
                List<String> subCommands = new ArrayList<>(List.of("play", "stop", "gui", "playurl", "createroom", "join", "start", "roomplay", "disbandroom", "reload", "info"));
                if (sender instanceof Player p) {
                    subCommands.removeIf(cmd -> {
                        String perm = "eogdmusicplayer." + cmd;
                        if (cmd.equals("start") || cmd.equals("roomplay")) perm = "eogdmusicplayer.room." + cmd;
                        return !p.hasPermission(perm);
                    });
                } else {
                    subCommands.removeIf(cmd -> !cmd.equals("reload") && !cmd.equals("info"));
                }
                addMatchingCompletions(completions, input, subCommands.toArray(new String[0]));

            } else if (args.length == 2) {
                String subCommand = args[0].toLowerCase();
                String input = args[1].toLowerCase();
                if (subCommand.equals("play") && sender.hasPermission("eogdmusicplayer.play")) {
                    plugin.getPresetSongs().forEach(song -> {
                        String songIndexStr = String.valueOf(plugin.getPresetSongs().indexOf(song) + 1);
                        if (songIndexStr.startsWith(input)) {
                            completions.add(songIndexStr);
                        }
                        String cleanName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', song.getName())).replace(" ", "_");
                        if (cleanName.toLowerCase().startsWith(input)) {
                            completions.add(cleanName);
                        }
                    });
                } else if (subCommand.equals("join") && sender.hasPermission("eogdmusicplayer.joinroom")) {
                    plugin.getActiveMusicRoomsView().stream()
                            .map(room -> room.getCreator().getName())
                            .filter(name -> name.toLowerCase().startsWith(input))
                            .distinct()
                            .forEach(completions::add);
                } else if ((subCommand.equals("playurl") && sender.hasPermission("eogdmusicplayer.playurl")) ||
                        (subCommand.equals("roomplay") && sender.hasPermission("eogdmusicplayer.room.roomplay")) ||
                        (subCommand.equals("createroom") && sender.hasPermission("eogdmusicplayer.createroom") && args.length == 2 ) ) {
                    if (input.isEmpty() || input.startsWith("http")) {
                        completions.add("http://");
                        completions.add("https://");
                    }
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("createroom") && sender.hasPermission("eogdmusicplayer.createroom")) {
                completions.add("<房间描述>");
            }
        } else if (command.getName().equalsIgnoreCase("playurl") && args.length == 1) {
            if (sender.hasPermission("eogdmusicplayer.playurl")) {
                String input = args[0].toLowerCase();
                if (input.isEmpty() || input.startsWith("http")) {
                    completions.add("http://");
                    completions.add("https://");
                }
            }
        }
        return completions.stream().distinct().collect(Collectors.toList());
    }

    private void addMatchingCompletions(List<String> completions, String input, String... options) {
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                completions.add(option);
            }
        }
    }
}