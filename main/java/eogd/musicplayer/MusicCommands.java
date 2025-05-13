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

public class MusicCommands implements CommandExecutor, TabCompleter {

    private final MusicPlayerPlugin plugin;

    public MusicCommands(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canExecute(CommandSender sender, String permissionKey, boolean playerOnly) {
        if (playerOnly && !(sender instanceof Player)) {
            plugin.sendConfigMsg(sender, "messages.general.playerOnly");
            return false;
        }
        if (!sender.hasPermission(permissionKey)) {
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
                    if (otherRoom.getPackFileName() != null && otherRoom.getPackFileName().equals(playerCurrentTempPack)) {
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
                        handlePlayUrl(playerForPlay, preset.getUrl(), false, null);
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
                        if (roomPlayerIsIn.getCreator().equals(playerForStop)) {
                            roomPlayerIsIn.stopPlaybackForAll();
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.roomStopped", "room_description", roomPlayerIsIn.getDescription());
                            String roomTempPack = roomPlayerIsIn.getPackFileName();
                            String roomSoundEvent = plugin.getHttpFileServer().getServePathPrefix() + ".room." + roomPlayerIsIn.getRoomId();

                            for (Player member : new HashSet<>(roomPlayerIsIn.getMembers())) {
                                if (member.isOnline()) {
                                    member.stopSound(roomSoundEvent, SoundCategory.MUSIC);
                                    String memberCurrentPack = plugin.getPlayerCurrentMusicPackFile(member.getUniqueId());
                                    if (roomTempPack != null && roomTempPack.equals(memberCurrentPack)) {
                                        plugin.clearPlayerCurrentMusicPack(member.getUniqueId());
                                        if (plugin.shouldUseMergedPackLogic()) {
                                            plugin.sendOriginalBasePackToPlayer(member);
                                        }
                                    }
                                }
                            }
                            stoppedSomething = true;
                        } else {
                            String roomSoundEvent = plugin.getHttpFileServer().getServePathPrefix() + ".room." + roomPlayerIsIn.getRoomId();
                            playerForStop.stopSound(roomSoundEvent, SoundCategory.MUSIC);

                            String memberCurrentPack = plugin.getPlayerCurrentMusicPackFile(playerForStop.getUniqueId());
                            if (roomPlayerIsIn.getPackFileName() != null && roomPlayerIsIn.getPackFileName().equals(memberCurrentPack)) {
                                plugin.clearPlayerCurrentMusicPack(playerForStop.getUniqueId());
                                if (plugin.shouldUseMergedPackLogic()) {
                                    plugin.sendOriginalBasePackToPlayer(playerForStop);
                                }
                            }
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.stoppedForSelfInRoom", "room_description", roomPlayerIsIn.getDescription());
                            stoppedSomething = true;
                        }
                    } else {
                        String currentTempPack = plugin.getPlayerCurrentMusicPackFile(playerForStop.getUniqueId());
                        MusicPlayerPlugin.PendingOnlineSound pendingSoundInfo = plugin.getPendingSingleUserSound(playerForStop.getUniqueId());

                        if (currentTempPack != null && pendingSoundInfo != null && currentTempPack.equals(pendingSoundInfo.packFileName())) {
                            playerForStop.stopSound(pendingSoundInfo.soundEventName(), SoundCategory.MUSIC);
                            plugin.getLogger().info("停止独立音乐: " + pendingSoundInfo.soundEventName() + " for " + playerForStop.getName());

                            if (plugin.getResourcePackGenerator() != null) {
                                plugin.getResourcePackGenerator().cleanupPack(currentTempPack);
                            }
                            plugin.clearPlayerCurrentMusicPack(playerForStop.getUniqueId());
                            plugin.clearPendingSingleUserSound(playerForStop.getUniqueId());

                            if (plugin.shouldUseMergedPackLogic()) {
                                plugin.sendOriginalBasePackToPlayer(playerForStop);
                            }
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.stoppedForSelf");
                            stoppedSomething = true;
                        } else if (pendingSoundInfo != null && pendingSoundInfo.packFileName() != null) {
                            playerForStop.stopSound(pendingSoundInfo.soundEventName(), SoundCategory.MUSIC);
                            plugin.getLogger().info("尝试停止待处理/失败的独立音乐: " + pendingSoundInfo.soundEventName() + " for " + playerForStop.getName());

                            if (plugin.getResourcePackGenerator() != null) {
                                plugin.getResourcePackGenerator().cleanupPack(pendingSoundInfo.packFileName());
                            }
                            plugin.clearPendingSingleUserSound(playerForStop.getUniqueId());
                            plugin.clearPlayerPendingPackType(playerForStop.getUniqueId());
                            plugin.clearPlayerCurrentMusicPack(playerForStop.getUniqueId());

                            if (plugin.shouldUseMergedPackLogic()) {
                                plugin.sendOriginalBasePackToPlayer(playerForStop);
                            }
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.stoppedForSelf");
                            stoppedSomething = true;
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
                        plugin.sendConfigMsg(onlinePlayer, "messages.bf.createroom.broadcast",
                                "creator_name", creator.getName(),
                                "description", newRoom.getDescription());
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
                    if (targetRoom.getStatus() == MusicRoom.RoomStatus.PLAYING && targetRoom.getPackFileName() != null) {
                        plugin.sendConfigMsg(joiner, "messages.bf.join.successNoAutoPlay", "room_description", targetRoom.getDescription());
                        handlePlayUrl(joiner, targetRoom.getMusicUrl(), true, targetRoom);
                    } else {
                        plugin.sendConfigMsg(joiner, "messages.bf.join.successNoAutoPlay", "room_description", targetRoom.getDescription());
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
                            handlePlayUrl(member, roomToStart.getMusicUrl(), true, roomToStart);
                            if (!member.equals(roomStarter) && memberNotification != null) {
                                plugin.sendLegacyMsg(member, memberNotification);
                            }
                        }
                    }
                    return true;

                case "roomplay":
                    if (!canExecute(sender, "eogdmusicplayer.roomplay", true)) return true;
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
                    String oldRoomSoundEvent = plugin.getHttpFileServer().getServePathPrefix() + ".room." + ownRoom.getRoomId();
                    if (ownRoom.getStatus() == MusicRoom.RoomStatus.PLAYING || ownRoom.isPlayRequestActive()) {
                        ownRoom.stopPlaybackForAll();
                        String oldRoomTempPack = ownRoom.getPackFileName();
                        for(Player member : new HashSet<>(ownRoom.getMembers())) {
                            if(member.isOnline()) {
                                member.stopSound(oldRoomSoundEvent, SoundCategory.MUSIC);
                                String memberCurrentPack = plugin.getPlayerCurrentMusicPackFile(member.getUniqueId());
                                if (oldRoomTempPack != null && oldRoomTempPack.equals(memberCurrentPack)) {
                                    plugin.clearPlayerCurrentMusicPack(member.getUniqueId());
                                    if (plugin.shouldUseMergedPackLogic()) {
                                        plugin.sendOriginalBasePackToPlayer(member);
                                    }
                                }
                            }
                        }
                        if (oldRoomTempPack != null && plugin.getResourcePackGenerator() != null) {
                            plugin.getResourcePackGenerator().cleanupPack(oldRoomTempPack);
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
                    if (roomToJoin.getStatus() == MusicRoom.RoomStatus.PLAYING && roomToJoin.getPackFileName() != null) {
                        plugin.sendConfigMsg(playerToJoin, "messages.bf.join.successNoAutoPlay", "room_description", roomToJoin.getDescription());
                        handlePlayUrl(playerToJoin, roomToJoin.getMusicUrl(), true, roomToJoin);
                    } else {
                        plugin.sendConfigMsg(playerToJoin, "messages.bf.join.successNoAutoPlay", "room_description", roomToJoin.getDescription());
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
        String urlToPlay = isFromBfCommand ? args[1] : args[0];
        handlePlayUrl(player, urlToPlay, false, null);
        return true;
    }

    public void handlePlayUrl(Player player, String url, boolean isRoomPlayback, @Nullable MusicRoom roomContext) {
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

        if (!isRoomPlayback) {
            MusicPlayerPlugin.PendingOnlineSound existingSingleSound = plugin.getPendingSingleUserSound(player.getUniqueId());
            if (existingSingleSound != null && plugin.getPlayerCurrentMusicPackFile(player.getUniqueId()) != null) {
                player.stopSound(existingSingleSound.soundEventName(), SoundCategory.MUSIC);
                plugin.getLogger().info("播放新独立音乐前停止旧的: " + existingSingleSound.soundEventName() + " for " + player.getName());
                if (plugin.getResourcePackGenerator() != null && existingSingleSound.packFileName() != null) {
                    plugin.getResourcePackGenerator().cleanupPack(existingSingleSound.packFileName());
                }
                plugin.clearPendingSingleUserSound(player.getUniqueId());
                plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
            } else if (existingSingleSound != null && existingSingleSound.packFileName() != null) {
                if (plugin.getResourcePackGenerator() != null) {
                    plugin.getResourcePackGenerator().cleanupPack(existingSingleSound.packFileName());
                }
                plugin.clearPendingSingleUserSound(player.getUniqueId());
            }
        }

        String existingPlayerMusicPack = plugin.getPlayerCurrentMusicPackFile(player.getUniqueId());
        if (existingPlayerMusicPack != null) {
            if (!isRoomPlayback) {
                boolean isAnActiveRoomPack = plugin.getActiveMusicRoomsView().stream().anyMatch(r -> existingPlayerMusicPack.equals(r.getPackFileName()));
                if (!isAnActiveRoomPack) {
                    plugin.getResourcePackGenerator().cleanupPack(existingPlayerMusicPack);
                    plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
                }
            } else if (roomContext != null && !existingPlayerMusicPack.equals(roomContext.getPackFileName())) {
                boolean isAnotherActiveRoomPack = plugin.getActiveMusicRoomsView().stream()
                        .filter(r -> !r.equals(roomContext))
                        .anyMatch(r -> existingPlayerMusicPack.equals(r.getPackFileName()));
                if (!isAnotherActiveRoomPack) {
                    plugin.getResourcePackGenerator().cleanupPack(existingPlayerMusicPack);
                    plugin.clearPlayerCurrentMusicPack(player.getUniqueId());
                }
            }
        }

        String packTypeIdentifier;
        String soundEventName;
        if (isRoomPlayback && roomContext != null) {
            packTypeIdentifier = "room." + roomContext.getRoomId();
        } else {
            packTypeIdentifier = "single." + player.getUniqueId().toString().substring(0,8) + "." + UUID.randomUUID().toString().substring(0,4);
        }
        soundEventName = plugin.getHttpFileServer().getServePathPrefix() + "." + packTypeIdentifier;

        plugin.sendConfigMsg(player, isRoomPlayback ? "messages.bf.room.start.startingMusic" : "messages.playurl.preparing",
                roomContext != null ? "room_description" : null,
                roomContext != null ? roomContext.getDescription() : null);

        plugin.getResourcePackGenerator().generateAndServePack(player, url, soundEventName, isRoomPlayback, roomContext)
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
                                plugin.getResourcePackGenerator().cleanupPack(packInfo.packFileName());
                                if (plugin.shouldUseMergedPackLogic()) plugin.sendOriginalBasePackToPlayer(player);
                                return;
                            }
                            player.setResourcePack(packInfo.packUrl(), sha1Bytes, promptMessage, true);

                            if (isRoomPlayback && roomContext != null) {
                                plugin.markPlayerPendingRoomPack(player.getUniqueId(), roomContext.getRoomId(), packInfo.packFileName());
                            } else {
                                plugin.addPendingSingleUserSound(player.getUniqueId(),
                                        new MusicPlayerPlugin.PendingOnlineSound(packInfo.packFileName(), soundEventName, packInfo.sha1(), player, packInfo.packUrl()));
                            }
                        });
                    } else {
                        plugin.sendConfigMsg(player, "messages.playurl.packCreationFailed");
                        if (roomContext != null) roomContext.setPlayRequestActive(false);
                        if (plugin.shouldUseMergedPackLogic()) plugin.sendOriginalBasePackToPlayer(player);
                    }
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "生成/服务包时出错 for " + player.getName() + ": " + ex.getMessage(), ex);
                    plugin.sendConfigMsg(player, "messages.playurl.error");
                    if (roomContext != null) roomContext.setPlayRequestActive(false);
                    if (plugin.shouldUseMergedPackLogic()) plugin.sendOriginalBasePackToPlayer(player);
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
                        if (cmd.equals("start")) perm = "eogdmusicplayer.room.start";
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
                        completions.add(String.valueOf(plugin.getPresetSongs().indexOf(song) + 1));
                        String cleanName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', song.getName()));
                        if (cleanName.toLowerCase().startsWith(input)) {
                            completions.add(cleanName.replace(" ", "_"));
                        }
                    });
                } else if (subCommand.equals("join") && sender.hasPermission("eogdmusicplayer.joinroom")) {
                    plugin.getActiveMusicRoomsView().stream()
                            .map(room -> room.getCreator().getName())
                            .filter(name -> name.toLowerCase().startsWith(input))
                            .distinct()
                            .forEach(completions::add);
                }
            }
        }
        return completions;
    }

    private void addMatchingCompletions(List<String> completions, String input, String... options) {
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                completions.add(option);
            }
        }
    }
}