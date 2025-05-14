# EogdMusicPlayer - Spigot 音乐播放插件

EogdMusicPlayer 是一款功能丰富的 Spigot 插件，允许服务器管理员和玩家在 Minecraft 中通过自定义资源包播放外部音乐链接。它支持独立播放和音乐房间功能，为你的服务器带来独特的听觉体验。

## 目录

*   [主要功能](#主要功能)
*   [要求与依赖](#要求与依赖)
*   [安装步骤](#安装步骤)
*   [配置 (`config.yml`)](#配置-configyml)
*   [命令与权限](#命令与权限)
*   [工作原理简述](#工作原理简述)
*   [故障排除](#故障排除)
*   [作者](#作者)

## 主要功能

*   **URL 音乐播放**: 直接通过 URL 播放 .ogg 格式的音乐。
*   **预设歌曲列表**: 在配置文件中预设常用歌曲，方便快速播放。
*   **动态资源包生成**:
    *   **独立模式**: 为每个播放请求动态生成一个包含单首音乐的资源包。
    *   **合并模式 (可选)**: 将音乐动态添加到服务器配置的基础资源包中，减少客户端切换资源包的频率。
    *   **资源包预热 (可选)**: 为预设歌曲提前生成资源包，加快播放加载速度。
*   **内置 HTTP 服务器**: 用于托管动态生成的资源包，无需外部Web服务器。
*   **音乐房间**:
    *   玩家可以创建音乐房间，并邀请其他玩家加入。
    *   房间创建者可以控制音乐的播放、切换和停止。
    *   房间内的所有成员将同步收听相同的音乐。
*   **图形用户界面 (GUI)**: 通过 `/bf gui` 命令打开一个简单的界面，方便选择预设歌曲。
*   **权限管理**: 精细的权限节点控制各项功能的使用。
*   **高度可配置**: 通过 `config.yml` 自定义消息、资源包参数、HTTP 服务器等。
*   **音量控制**: 播放的音乐遵循客户端的“音乐”音量设置。

## 要求与依赖

*   **Minecraft 服务器**: Spigot 1.21 或兼容的 PaperMC 等服务端。
*   **Java**: Java 17 或更高版本。
*   **依赖 (通常由插件内部处理或服务器提供)**:
    *   Gson (用于 JSON 处理)
    *   Apache Commons Codec (用于 SHA-1 哈希计算)

## 安装步骤

1.  下载最新的 `EogdMusicPlayer.jar` 文件。
2.  将 `EogdMusicPlayer.jar` 文件放入你服务器的 `plugins` 文件夹中。
3.  启动或重启你的 Minecraft 服务器。
4.  插件将自动生成默认的配置文件 `plugins/EogdMusicPlayer/config.yml`。

## 配置 (`config.yml`)

插件首次加载时会自动生成 `config.yml`。以下是主要配置项的说明：

```yaml
# EogdMusicPlayer 配置文件

# HTTP 文件服务器设置
httpServer:
  enabled: true # 是否启用内置 HTTP 服务器以提供资源包 (true/false)
  port: 8123 # HTTP 服务器监听的端口
  publicAddress: "" # 服务器的公共IP地址或域名。如果留空，插件会尝试自动检测，但可能不准确。
                    # 对于外部访问，强烈建议手动设置此项，例如："your.server.ip" 或 "your.domain.com"
  servePath: "musicpacks" # 资源包在URL中的路径前缀，例如 http://<publicAddress>:<port>/musicpacks/pack.zip
  tempDirectory: "temp_packs" # 存储临时生成的资源包的子目录 (在插件数据文件夹内)
  maxDownloadSizeBytes: 0 # 单个音频文件下载的最大允许大小（字节）。0 表示无限制。例如: 10485760 (10MB)
  downloadConnectTimeoutMillis: 5000 # 下载音频文件时的连接超时时间（毫秒）
  downloadReadTimeoutMillis: 10000   # 下载音频文件时的读取超时时间（毫秒）
  musicRoomInactiveCleanupDelaySeconds: 600 # 音乐室在无人且无活动后自动关闭的延迟时间（秒）

# 资源包相关设置
resourcePack:
  packFormat: 34 # 资源包格式版本。Minecraft 1.21.x 对应 34。请根据你的服务器版本调整。
                 # 1.20.5-1.20.6 -> 32, 1.20.3-1.20.4 -> 26, 1.20.2 -> 18, 1.20-1.20.1 -> 15
  description: "§bEogdMusicPlayer §7音乐资源包" # 资源包的描述文本
  enablePresetPrewarming: false # 是否在插件启动/重载时为预设歌曲提前生成资源包 (true/false)
                               # 开启后，预设歌曲加载更快，但会占用少量启动时间和磁盘空间。

# 基础资源包合并设置 (如果启用，插件会将音乐添加到此基础包中，而不是创建独立的音乐包)
baseResourcePack:
  enableMerging: false # 是否启用与基础资源包的合并模式 (true/false)
  fileName: "base_pack.zip" # 基础资源包的文件名，应放置在插件的数据文件夹内。
  promptMessage: "§6加载音乐资源包..." # 发送合并后的音乐资源包给玩家时显示的提示信息
  originalPackPromptMessage: "§6恢复默认资源包..." # 当停止音乐并恢复到原始基础包时显示的提示信息

# GUI 相关设置
gui:
  title: "§9音乐播放器" # GUI 的标题
  noPresets: "§c没有可用的预设歌曲。" # 当没有预设歌曲时在GUI中显示的消息
  prevPageName: "§c<- 上一页" # 上一页按钮的名称
  nextPageName: "§a下一页 ->" # 下一页按钮的名称
  prevPageItem: "ARROW" # 上一页按钮的物品材质 (区分大小写, 参考 Spigot Material 枚举)
  nextPageItem: "ARROW" # 下一页按钮的物品材质

# 预设歌曲列表
# 每首歌曲是一个独立的配置节 (例如 'song1', 'song2')
# name: 歌曲在GUI和命令中显示的名称 (支持颜色代码 '&')
# url: 歌曲的 .ogg 文件直链 URL
# item: 在GUI中代表这首歌的物品材质 (区分大小写, 参考 Spigot Material 枚举)
# lore: 物品的描述文本列表 (支持颜色代码 '&')
presetSongs:
  song1:
    name: "§e示例歌曲 1"
    url: "https://example.com/music/song1.ogg" # 请替换为有效的 .ogg 文件链接
    item: "MUSIC_DISC_CAT"
    lore:
      - "§7点击播放这首美妙的歌曲！"
      - "§7流派: 流行"
  song2:
    name: "§b自定义音乐 §7(演示)"
    url: "https://example.com/music/another_song.ogg" # 请替换为有效的 .ogg 文件链接
    item: "MUSIC_DISC_BLOCKS"
    lore:
      - "§a这是一首通过配置添加的歌曲。"
      - "§7URL: <url>" # 你可以在lore中使用 <url> 占位符，它不会被自动替换，仅作展示
  # 你可以在这里添加更多歌曲...
  # song3:
  #   name: "§d史诗音乐"
  #   url: "YOUR_OGG_FILE_DIRECT_LINK_HERE"
  #   item: "MUSIC_DISC_MELLOHI"
  #   lore:
  #     - "§7感受这史诗般的旋律！"

# 消息配置 (所有消息都支持颜色代码 '&')
# <placeholder> 会被替换为实际值
messages:
  general:
    playerOnly: "§c此命令只能由玩家执行。"
    noPermission: "§c你没有权限执行此命令。"
    invalidUrl: "§c提供的URL无效或无法访问。"
    httpDisabled: "§cHTTP服务器未启用，无法播放在线音乐。"
    basePackMissing: "§c错误：基础资源包 'base_pack.zip' 在插件文件夹中缺失，但合并模式已启用。请添加基础包或禁用合并模式。"
    basePackReapplyFailed: "§c尝试恢复基础资源包失败。"
    downloadFailedResponse: "§c下载音频文件失败，服务器响应无效。URL: <url>"
    fileTooLarge: "§c音频文件过大 (<size>MB)，无法下载。URL: <url>" # <size> 会被替换
    downloadException: "§c下载音频文件时发生网络错误。URL: <url>"

  bf: # /bf 命令相关消息
    usage: |-
      §e用法: /bf <子命令> [参数...]
      §7可用子命令: play, playurl, stop, gui, createroom, join, start, roomplay, disbandroom, reload, info
    unknownCommand: "§c未知子命令。输入 /bf 获取帮助。"
    play:
      usage: "§e用法: /bf play <歌曲名称或编号>"
      notFound: "§c未找到名为 '<song>' 的预设歌曲。"
      preparing: "§7正在准备播放预设歌曲: <song_name>§7..."
    playurl:
      usage: "§e用法: /bf playurl <URL>"
    stop:
      notPlaying: "§e你当前没有在播放任何音乐。"
      stoppedForSelf: "§a已为你停止播放音乐。"
      stoppedForSelfInRoom: "§a已为你停止在房间 '<room_description>§a' 中的音乐。"
      roomStopped: "§a音乐室 '<room_description>§a' 的音乐已停止。"
    createroom:
      usage: "§e用法: /bf createroom <音乐URL> <房间描述>"
      noDescription: "§c请输入房间描述。"
      alreadyCreated: "§c你已经创建了一个音乐室: <room_description>§c。请先解散它。"
      successWithStartHint: "§a音乐室 '<description>§a' 已创建！音乐URL: <url>§a。使用 §e/bf start §a来开始播放。"
      broadcast: "§e玩家 <creator_name> §e创建了音乐室: <description>"
    join:
      usage: "§e用法: /bf join <房主名称>"
      roomNotFound: "§c未找到由 '<creator>' 创建的音乐室。"
      alreadyCreator: "§e你已经是音乐室 '<room_description>§e' 的创建者了。"
      alreadyMember: "§e你已经是音乐室 '<room_description>§e' 的成员了。"
      successNoAutoPlay: "§a已加入音乐室: <room_description>§a。等待房主开始播放。"
      leftOtherRoom: "§7已自动离开你之前所在的音乐室: <other_room_description>"
      internalRoomNotFound: "§c内部错误：尝试加入的房间未找到。"
    room:
      start:
        notRoomCreator: "§c你不是任何音乐室的创建者，无法启动音乐。"
        noMusicUrl: "§c你的音乐室 '<room_description>§c' 没有设置音乐URL。"
        startingMusic: "§7房间 '<room_description>§7' 正在准备播放音乐..."
        memberStartNotification: "§e房主 <creator_name> §e在房间 '<room_description>§e' 中开始了音乐播放！"
        started: "§a已在你的音乐室 '<room_description>§a' 中开始播放音乐。"
      play:
        notRoomCreator: "§c你不是任何音乐室的创建者，无法设置音乐。"
        usage: "§e用法: /bf roomplay <新音乐URL>"
        urlSet: "§a音乐室 '<room_description>§a' 的音乐URL已更新为: <url>"
        urlSame: "§e新URL与当前音乐室 '<room_description>§e' 的URL相同。"
        startHint: "§7使用 §e/bf start §7来播放新音乐。"
    disbandroom:
      notCreatorOrNoRoom: "§c你没有创建音乐室，或你的音乐室已被解散。"
      success: "§a音乐室 '<room_description>§a' 已成功解散。"
      notifyMemberRoomDisbanded: "§e你所在的音乐室 '<room_description>§e' 已被房主解散。"
    reload:
      success: "§aEogdMusicPlayer 配置已重载。"
    info: {} # /bf info 使用硬编码格式

  playurl: # /playurl 命令相关消息 (如果与 /bf playurl 不同)
    usage: "§e用法: /playurl <URL>"
    preparing: "§7正在准备播放URL音乐..."
    packCreationFailed: "§c无法创建音乐资源包。"
    error: "§c播放音乐时发生错误。"

  resourcePack:
    status:
      accepted: "§a资源包请求已接受，正在下载..."
      successfully_loaded: "§a音乐资源包加载成功！"
      declined: "§c你拒绝了音乐资源包。将无法播放音乐。"
      failed: "§c音乐资源包下载失败或加载出错。"

  musicRoomClosedMessage: "§7音乐室 '<description>§7' 因长时间不活动已被自动关闭。"
```

**重要**:
*   `resourcePack.packFormat`: 这个值非常重要，必须与你的服务器客户端版本兼容。请查阅 Minecraft Wiki 获取最新的资源包版本信息 (例如，1.21.x 通常是 34)。
*   `httpServer.publicAddress`: 如果服务器在NAT网络后（例如家庭网络），留空或使用 `auto` 可能无法正确检测到公网IP。你需要手动配置为你的公网IP或域名，否则玩家可能无法下载资源包。确保配置的 `httpServer.port` 在防火墙和路由器上是开放的。
*   `baseResourcePack.enableMerging`: 如果设置为 `true`，你必须在 `plugins/EogdMusicPlayer/` 目录下放置一个名为 `baseResourcePack.fileName` 指定的有效基础资源包文件。
*   `resourcePack.enablePresetPrewarming`: 设置为 `true` 以在插件启动时为预设歌曲生成资源包，加快后续播放速度。

## 命令与权限

以下是插件的主要命令及其对应的权限节点：

| 命令                                            | 描述                                           | 权限节点                             |
| :---------------------------------------------- | :--------------------------------------------- | :----------------------------------- |
| `/bf play <歌曲名 或 序号>`                       | 播放预设列表中的歌曲。                             | `eogdmusicplayer.play`               |
| `/bf playurl <URL>`                             | 直接播放指定 URL 的音乐。                        | `eogdmusicplayer.playurl`            |
| `/playurl <URL>`                                | `/bf playurl` 的别名。                         | `eogdmusicplayer.playurl`            |
| `/bf stop`                                      | 停止当前为自己播放的音乐，或停止自己创建的房间音乐。 | `eogdmusicplayer.stop`               |
| `/bf gui`                                       | 打开预设歌曲选择GUI。                            | `eogdmusicplayer.gui`                |
| `/bf createroom <URL> <房间描述>`                 | 创建一个音乐房间。                               | `eogdmusicplayer.createroom`         |
| `/bf join <房间创建者名称>`                       | 加入一个已存在的音乐房间。                         | `eogdmusicplayer.joinroom`           |
| `/bf start`                                     | (房间创建者) 开始播放当前房间设置的音乐。          | `eogdmusicplayer.room.start`         |
| `/bf roomplay <新URL>`                          | (房间创建者) 更改当前房间的音乐URL。             | `eogdmusicplayer.roomplay`           |
| `/bf disbandroom`                               | (房间创建者) 解散自己创建的音乐房间。              | `eogdmusicplayer.disbandroom`        |
| `/bf reload`                                    | 重载插件配置文件。                               | `eogdmusicplayer.reload`             |
| `/bf info`                                      | 显示插件信息。                                   | `eogdmusicplayer.info`               |

## 工作原理简述

1.  **资源包生成**:
    *   当玩家请求播放音乐时（或在预热功能开启时），插件会根据配置（独立模式或合并模式）动态生成一个临时的资源包。
    *   这个资源包包含一个 `sounds.json` 文件，用于定义新的声音事件，并将该事件指向要播放的音乐文件。
    *   音乐文件（.ogg 格式）会从提供的 URL 下载到服务器的临时存储中，然后打包进资源包。
2.  **HTTP 服务器**:
    *   插件内置的 HTTP 服务器会托管这个动态生成的资源包。
    *   服务器向玩家发送资源包的下载链接和 SHA-1 哈希值。
3.  **客户端应用**:
    *   玩家的 Minecraft 客户端下载并应用资源包。
    *   一旦资源包加载成功，服务器会指令客户端播放定义好的声音事件，从而播放音乐。
4.  **音乐房间**:
    *   音乐房间允许多个玩家同步收听。当房间创建者启动或更改音乐时，所有房间成员都会收到相应的资源包和播放指令。

## 故障排除

*   **音乐不播放/音量小**:
    *   检查服务器控制台是否有错误信息。
    *   确认客户端已成功接受并加载了资源包（通常会有提示）。
    *   检查游戏内的“音乐”音量滑块和“主音量”滑块是否已调高。
    *   确保音乐 URL 指向的是有效的 `.ogg` 文件，并且服务器可以访问该 URL。
*   **提示 "HTTP Server Disabled"**:
    *   检查 `config.yml` 中的 `httpServer.enabled` 是否为 `true`。
    *   检查 `httpServer.port` 是否被其他程序占用。
*   **提示 "Base Pack Missing" (合并模式下)**:
    *   确保 `config.yml` 中 `baseResourcePack.enableMerging` 为 `true` 时，`plugins/EogdMusicPlayer/` 目录下存在 `baseResourcePack.fileName` 指定的基础资源包文件。
*   **玩家无法下载资源包**:
    *   确认 `config.yml` 中的 `httpServer.publicAddress` 设置正确（对于公网服务器，应为服务器的公网IP或域名）。
    *   确认 `httpServer.port` 已在服务器防火墙和路由器（如果适用）中开放。
*   **预热功能未按预期工作**:
    *   确保 `config.yml` 中 `resourcePack.enablePresetPrewarming` 设置为 `true`。
    *   检查服务器启动日志中是否有关于预热成功或失败的消息。
    *   确认预设歌曲的 URL 是有效的，并且服务器可以访问它们。

## 作者

EogdMusicPlayer 由 **Eogd** 开发。

---

## 许可证
本项目采用 [知识共享署名-非商业性使用4.0 国际许可证]（https://creativecommons.org/licenses/by-nc/4.0/） 进行许可。

## 支持我的闲鱼店铺 【闲鱼】https://m.tb.cn/h.6nA3DRE?tk=7ACpVUFVHJa MF937 「这是我的闲鱼号，快来看看吧～」 点击链接直接打开
