package eogd.musicplayer;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class MusicCommands implements CommandExecutor, TabCompleter {

    private final MusicPlayerPlugin plugin;

    public MusicCommands(MusicPlayerPlugin plugin) {
        this.plugin = plugin;
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
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player playerForPlay = (Player) sender;
                    if (!playerForPlay.hasPermission("eogdmusicplayer.play")) {
                        plugin.sendConfigMsg(playerForPlay, "messages.general.noPermission");
                        return true;
                    }
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
                    if (!sender.hasPermission("eogdmusicplayer.playurl")) {
                        plugin.sendConfigMsg(sender, "messages.general.noPermission");
                        return true;
                    }
                    return handlePlayUrlCommandLogic(sender, args, true);

                case "stop":
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player playerForStop = (Player) sender;
                    if (!playerForStop.hasPermission("eogdmusicplayer.stop")) {
                        plugin.sendConfigMsg(playerForStop, "messages.general.noPermission");
                        return true;
                    }
                    MusicRoom roomPlayerIsIn = plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.isMember(playerForStop) || r.getCreator().equals(playerForStop))
                            .findFirst().orElse(null);

                    if (roomPlayerIsIn != null) {
                        if (roomPlayerIsIn.getCreator().equals(playerForStop)) {
                            roomPlayerIsIn.stopPlaybackForAll();
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.roomStopped", "room_description", roomPlayerIsIn.getDescription());
                        } else {
                            roomPlayerIsIn.stopPlaybackForPlayer(playerForStop);
                        }
                    } else {
                        MusicPlayerPlugin.PendingOnlineSound pendingSound = plugin.getPendingSingleUserSound(playerForStop.getUniqueId());
                        if (pendingSound != null && pendingSound.soundEventName != null) {
                            playerForStop.stopSound(pendingSound.soundEventName);
                            plugin.clearPendingSingleUserSound(playerForStop.getUniqueId());
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.stoppedForSelf");
                        } else {
                            plugin.sendConfigMsg(playerForStop, "messages.bf.stop.notPlaying");
                        }
                    }
                    return true;

                case "gui":
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player playerForGui = (Player) sender;
                    if (!playerForGui.hasPermission("eogdmusicplayer.gui")) {
                        plugin.sendConfigMsg(playerForGui, "messages.general.noPermission");
                        return true;
                    }
                    new MusicGUI(plugin).open(playerForGui);
                    return true;

                case "createroom":
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player creator = (Player) sender;
                    if (!creator.hasPermission("eogdmusicplayer.createroom")) {
                        plugin.sendConfigMsg(creator, "messages.general.noPermission");
                        return true;
                    }
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

                    String broadcastTemplate = plugin.getConfig().getString("messages.bf.createroom.broadcast",
                            "§b<creator_name> §a创建了音乐室 描述:§f <description> §a输入§e /bf join <creator_name> §a来加入吧！");
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        plugin.sendLegacyMsg(onlinePlayer, broadcastTemplate,
                                "creator_name", creator.getName(),
                                "description", newRoom.getDescription());
                    }
                    return true;

                case "join":
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player joiner = (Player) sender;
                    if (!joiner.hasPermission("eogdmusicplayer.joinroom")) {
                        plugin.sendConfigMsg(joiner, "messages.general.noPermission");
                        return true;
                    }
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

                    plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.isMember(joiner) && !r.equals(targetRoom))
                            .findFirst()
                            .ifPresent(otherRoom -> {
                                otherRoom.removeMember(joiner);
                                plugin.sendConfigMsg(joiner, "messages.bf.join.leftOtherRoom", "other_room_description", otherRoom.getDescription());
                            });

                    targetRoom.addMember(joiner);
                    plugin.sendConfigMsg(joiner, "messages.bf.join.successNoAutoPlay", "room_description", targetRoom.getDescription());
                    return true;

                case "start":
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player roomStarter = (Player) sender;
                    if (!roomStarter.hasPermission("eogdmusicplayer.room.start")) {
                        plugin.sendConfigMsg(roomStarter, "messages.general.noPermission");
                        return true;
                    }
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
                    plugin.sendConfigMsg(roomStarter, "messages.bf.room.start.startingMusic", "room_description", roomToStart.getDescription());

                    String memberNotification = plugin.getConfig().getString("messages.bf.room.start.memberStartNotification", "§6房主已开始播放音乐！请接受资源包。");

                    for (Player member : new ArrayList<>(roomToStart.getMembers())) {
                        if (member.isOnline()) {
                            handlePlayUrl(member, roomToStart.getMusicUrl(), true, roomToStart);
                            if (!member.equals(roomStarter)) {
                                plugin.sendLegacyMsg(member, memberNotification);
                            }
                        }
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> roomToStart.setPlayRequestActive(false), 20L * 30);
                    return true;

                case "roomplay":
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player roomPlayRequester = (Player) sender;
                    if (!roomPlayRequester.hasPermission("eogdmusicplayer.roomplay")) {
                        plugin.sendConfigMsg(roomPlayRequester, "messages.general.noPermission");
                        return true;
                    }
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

                    ownRoom.setMusicUrl(newMusicUrl);
                    if (ownRoom.getPackFileName() != null && plugin.getResourcePackGenerator() != null) {
                        plugin.getResourcePackGenerator().cleanupPack(ownRoom.getPackFileName());
                        ownRoom.setPackFileName(null);
                    }
                    plugin.sendConfigMsg(roomPlayRequester, "messages.bf.room.play.urlSet",
                            "room_description", ownRoom.getDescription(),
                            "url", newMusicUrl);
                    return true;

                case "disbandroom":
                    if (!(sender instanceof Player)) {
                        plugin.sendConfigMsg(sender, "messages.general.playerOnly");
                        return true;
                    }
                    Player disbandRequester = (Player) sender;
                    if (!disbandRequester.hasPermission("eogdmusicplayer.disbandroom")) {
                        plugin.sendConfigMsg(disbandRequester, "messages.general.noPermission");
                        return true;
                    }
                    MusicRoom roomToDisband = plugin.getActiveMusicRoomsView().stream()
                            .filter(r -> r.getCreator().equals(disbandRequester))
                            .findFirst().orElse(null);
                    if (roomToDisband == null) {
                        plugin.sendConfigMsg(disbandRequester, "messages.bf.disbandroom.notCreatorOrNoRoom");
                        return true;
                    }
                    String disbandedRoomDesc = roomToDisband.getDescription();
                    plugin.removeMusicRoom(roomToDisband.getRoomId());
                    plugin.sendConfigMsg(disbandRequester, "messages.bf.disbandroom.success", "room_description", disbandedRoomDesc);
                    return true;

                case "reload":
                    if (!sender.hasPermission("eogdmusicplayer.reload")) {
                        plugin.sendConfigMsg(sender, "messages.general.noPermission");
                        return true;
                    }
                    plugin.reloadPluginConfiguration();
                    plugin.sendConfigMsg(sender, "messages.bf.reload.success");
                    return true;

                case "info":
                    if (!sender.hasPermission("eogdmusicplayer.info")) {
                        plugin.sendConfigMsg(sender, "messages.general.noPermission"); // Use existing noPermission message
                        return true;
                    }
                    PluginDescriptionFile pdf = plugin.getDescription();
                    sender.sendMessage(ChatColor.GOLD + "--- [" + ChatColor.YELLOW + "EogdMusicPlayer 信息" + ChatColor.GOLD + "] ---");
                    sender.sendMessage(ChatColor.AQUA + "作者: " + ChatColor.WHITE + String.join(", ", pdf.getAuthors()));
                    sender.sendMessage(ChatColor.AQUA + "版本: " + ChatColor.WHITE + pdf.getVersion());
                    String description = pdf.getDescription();
                    sender.sendMessage(ChatColor.AQUA + "描述: " + ChatColor.WHITE + (description != null ? description : "N/A"));
                    sender.sendMessage(ChatColor.GOLD + "-----------------------------");
                    return true;


                default:
                    plugin.sendConfigMsg(sender, "messages.bf.unknownCommand");
                    return true;
            }
        } else if (command.getName().equalsIgnoreCase("playurl")) {
            if (!sender.hasPermission("eogdmusicplayer.playurl")) {
                plugin.sendConfigMsg(sender, "messages.general.noPermission");
                return true;
            }
            return handlePlayUrlCommandLogic(sender, args, false);
        } else if (command.getName().equalsIgnoreCase("internal_join_room")) {
            if (!(sender instanceof Player)) return true;
            Player playerToJoin = (Player) sender;
            if (args.length < 1) return true;
            String roomId = args[0];
            MusicRoom roomToJoin = plugin.getMusicRoom(roomId);
            if (roomToJoin != null) {
                if (!roomToJoin.isMember(playerToJoin) && !roomToJoin.getCreator().equals(playerToJoin)) {
                    roomToJoin.addMember(playerToJoin);
                    plugin.sendConfigMsg(playerToJoin, "messages.bf.join.successNoAutoPlay", "room_description", roomToJoin.getDescription());
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
        if (!(sender instanceof Player)) {
            plugin.sendConfigMsg(sender, "messages.general.playerOnly");
            return true;
        }
        Player player = (Player) sender;
        String urlToPlay;

        if (args.length < (isFromBfCommand ? 2 : 1)) {
            plugin.sendConfigMsg(player, isFromBfCommand ? "messages.bf.playurl.usage" : "messages.playurl.usage");
            return true;
        }
        urlToPlay = isFromBfCommand ? args[1] : args[0];

        handlePlayUrl(player, urlToPlay, false, null);
        return true;
    }

    public void handlePlayUrl(Player player, String url, boolean isRoomPlayback, MusicRoom roomContext) {
        if (plugin.getResourcePackGenerator() == null || plugin.getHttpFileServer() == null) {
            plugin.sendConfigMsg(player, "messages.general.httpDisabled");
            return;
        }
        if (url == null || url.isEmpty()) {
            plugin.sendConfigMsg(player, "messages.general.invalidUrl");
            return;
        }

        String packTypeIdentifier;
        String soundEventName;

        if (isRoomPlayback && roomContext != null) {
            packTypeIdentifier = "room." + roomContext.getRoomId();
            soundEventName = plugin.getHttpFileServer().getServePath() + "." + packTypeIdentifier;
        } else {
            packTypeIdentifier = "single." + player.getUniqueId().toString().substring(0,8) + "." + UUID.randomUUID().toString().substring(0,4);
            soundEventName = plugin.getHttpFileServer().getServePath() + "." + packTypeIdentifier;
        }

        plugin.sendConfigMsg(player, "messages.playurl.preparing");

        plugin.getResourcePackGenerator().generateAndServePack(player, url, soundEventName, isRoomPlayback, roomContext)
                .thenAccept(packInfo -> {
                    if (packInfo != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            String promptString = ChatColor.translateAlternateColorCodes('&',
                                    plugin.getConfig().getString("resourcePack.promptMessage", "§6请接受音乐资源包！"));
                            byte[] sha1Bytes;
                            try {
                                sha1Bytes = Hex.decodeHex(packInfo.getSha1());
                            } catch (DecoderException e) {
                                plugin.getLogger().log(Level.SEVERE, "Invalid SHA-1 hex string: " + packInfo.getSha1(), e);
                                plugin.sendConfigMsg(player, "messages.playurl.error");
                                return;
                            }
                            player.setResourcePack(packInfo.getPackUrl(), sha1Bytes, promptString, true);

                            if (isRoomPlayback && roomContext != null) {
                                plugin.markPlayerPendingRoomPack(player.getUniqueId(), roomContext.getRoomId(), packInfo.getPackFileName());
                            } else {
                                plugin.addPendingSingleUserSound(player.getUniqueId(),
                                        new MusicPlayerPlugin.PendingOnlineSound(packInfo.getPackFileName(), soundEventName, packInfo.getSha1(), player, packInfo.getPackUrl()));
                            }
                        });
                    } else {
                        plugin.sendConfigMsg(player, "messages.playurl.packCreationFailed");
                    }
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Error generating/serving pack for " + player.getName() + ": " + ex.getMessage(), ex);
                    plugin.sendConfigMsg(player, "messages.playurl.error");
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
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    subCommands.removeIf(cmd -> {
                        String perm = "eogdmusicplayer." + cmd;
                        if (cmd.equals("start")) perm = "eogdmusicplayer.room.start";
                        if (cmd.equals("info")) perm = "eogdmusicplayer.info";
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
                            completions.add(cleanName);
                        }
                    });
                } else if (subCommand.equals("join") && sender.hasPermission("eogdmusicplayer.joinroom")) {
                    plugin.getActiveMusicRoomsView().stream()
                            .map(room -> room.getCreator().getName())
                            .filter(name -> name != null && name.toLowerCase().startsWith(input))
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