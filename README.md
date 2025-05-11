# EogdMusicPlayer - Spigot 音乐插件

*为你的 Spigot 1.21 服务器带来音乐体验，由 Eogd 开发。*

EogdMusicPlayer 是一款功能丰富的音乐播放插件，允许玩家和管理员控制音乐播放、创建共享音乐室，并通过动态生成的资源包播放在线音乐。

## 目录
- [核心特性](#核心特性)
- [系统要求](#系统要求)
- [安装与设置](#安装与设置)
  - [首次安装](#首次安装)
  - [HTTP 服务器配置](#http-服务器配置)
- [配置文件 (config.yml)](#配置文件-configyml)
  - [HttpServer 设置](#httpserver-设置)
  - [ResourcePack 设置](#resourcepack-设置)
  - [PresetSongs (预设歌曲)](#presetsongs-预设歌曲)
  - [GUI 设置](#gui-设置)
  - [Messages (消息自定义)](#messages-消息自定义)
- [指令列表](#指令列表)
  - [主要指令 (/bf)](#主要指令-bf)
  - [快捷指令 (/playurl)](#快捷指令-playurl)
- [权限节点](#权限节点)
- [常见问题 (FAQ)](#常见问题-faq)
- [如何贡献](#如何贡献)
- [许可证](#许可证)

## 核心特性
*   **动态音乐资源包**: 自动为在线音乐 (MP3, 直播流等) 生成临时的资源包，玩家接受后即可收听。
*   **音乐室系统**:
    *   玩家可以创建自己的音乐室，邀请朋友加入。
    *   房主可以控制音乐室播放的音乐 URL。
    *   音乐室有不活动自动清理机制。
*   **预设歌曲**: 管理员可以在 `config.yml` 中配置预设歌曲列表，方便玩家通过 GUI 或指令快速播放。
*   **GUI 界面**: 提供图形用户界面 (`/bf gui`)，方便玩家浏览和播放预设歌曲。
*   **HTTP 服务器集成**: 内置 HTTP 服务器，用于托管动态生成的音乐资源包。
*   **高度可配置**: 大部分消息、GUI 文本、服务器行为等都可以通过 `config.yml` 进行定制。
*   **权限管理**: 详细的权限节点控制各项功能的使用。

## 系统要求
*   **Minecraft 服务器**: Spigot (或兼容 Spigot 的服务端，如 Paper, Purpur) **1.21 或更高版本**。
*   **Java 版本**: Java 17 或更高版本。
*   **网络端口**: 需要一个**未被占用**的 TCP 端口供内置 HTTP 服务器使用 (默认为 `8123`)。如果你的服务器有防火墙，请确保此端口已开放。如果你使用的是面板服务器，你可能需要联系服务商为你额外开放一个 TCP 端口。

## 安装与设置

### 首次安装
1.  **下载插件**:
    *   从 GitHub Releases 页面 (你需要提供链接) 下载最新的 `EogdMusicPlayer-X.Y.Z.jar` (X.Y.Z 代表版本号)。
2.  **安装插件**:
    *   将下载的 `.jar` 文件放入你的 Minecraft 服务器的 `plugins` 文件夹内。
3.  **启动服务器**:
    *   **完全重启**你的服务器。插件首次加载时，会在 `plugins/EogdMusicPlayer/` 目录下生成默认的 `config.yml` 文件。
4.  **配置 HTTP 服务器**:
    *   打开 `plugins/EogdMusicPlayer/config.yml` 文件。
    *   找到 `httpServer` 部分 (详见下方“配置文件”部分)。
    *   **关键**: 设置 `publicAddress` 为你的服务器的**公共 IP 地址或域名 (不带端口号)**。这是玩家客户端下载资源包时需要访问的地址。
    *   确保 `port` 设置为一个未被占用的 TCP 端口，并且此端口已在服务器防火墙中开放。
5.  **重启或重载插件**:
    *   修改配置后，使用 `/bf reload` (需要 `eogdmusicplayer.reload` 权限) 或重启服务器使更改生效。

### HTTP 服务器配置
正确配置内置 HTTP 服务器对于插件的核心功能至关重要。
*   `publicAddress`: **必须**设置为你的服务器对外的 IP 地址或域名。如果留空或设置错误，玩家将无法下载音乐资源包。
*   `port`: 确保此端口未被其他程序占用，并且服务器的防火墙允许外部 TCP 连接到此端口。

## 配置文件 (config.yml)
配置文件位于 `plugins/EogdMusicPlayer/config.yml`。

### HttpServer 设置
```yaml
httpServer:
  enabled: true # 是否启用内置 HTTP 服务器 (核心功能，建议 true)
  publicAddress: "" # 【重要】服务器的公共IP地址或域名 (不带端口号), 例如 "play.yourserver.com" 或 "123.45.67.89"
  port: 8123 # HTTP 服务器监听的TCP端口。确保此端口未被占用且已在防火墙开放。
  servePath: "musicpacks" # 动态资源包的URL路径部分, 例如 http://<publicAddress>:<port>/musicpacks/<pack_id>.zip
  tempDirectory: "temp_packs" # 存储临时生成资源包的文件夹 (相对于插件目录)
  maxDownloadSizeBytes: 104857600 # 允许从URL下载的音乐文件的最大大小 (字节单位, 默认为100MB)
  musicRoomInactiveCleanupDelaySeconds: 600 # 音乐室无成员且无活动后自动清理的延迟时间 (秒, 默认为10分钟)
```

### ResourcePack 设置
```yaml
resourcePack:
  packFormat: 32 # 资源包格式版本 (Minecraft 1.21 使用 32)。请根据你的服务器版本调整。
  description: "§b音乐播放器资源包" # 资源包的描述文本
  promptMessage: "§6请接受音乐资源包！" # 提示玩家接受资源包的消息
```

### PresetSongs (预设歌曲)
定义可以在 GUI 中显示或通过 `/bf play <名称>` 播放的预设歌曲列表。
```yaml
presetSongs:
  song1: # 这是一个歌曲的唯一ID，你可以任意命名 (例如 popular_song, radio_stream)
    name: "§a示例歌曲 1 §7(一首好听的歌)" # 显示在GUI中的歌曲名称
    url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3" # 歌曲的直接播放URL
    item: "MUSIC_DISC_13" # 在GUI中代表此歌曲的物品类型 (Minecraft Item ID)
    lore: # 物品的描述文本 (可选)
      - "§7这是一首示例歌曲"
      - "§9点击播放！"
  song2:
    name: "§b在线电台示例"
    url: "http://stream.laut.fm/BesteMusikVonHeute"
    item: "MUSIC_DISC_CAT"
    lore:
      - "§6一个在线音乐电台"
      - "§e全天候播放"
  # 你可以添加更多预设歌曲...
```

### GUI 设置
```yaml
gui:
  title: "§9音乐播放器" # GUI界面的标题
  nextPageItem: "ARROW" # GUI中“下一页”按钮的物品类型
  nextPageName: "§a下一页 ->" # “下一页”按钮的显示名称
  prevPageItem: "ARROW" # GUI中“上一页”按钮的物品类型
  prevPageName: "§c<- 上一页" # “上一页”按钮的显示名称
  noPresets: "§c没有可用的预设歌曲。" # 当没有预设歌曲时在GUI中显示的消息
```

### Messages (消息自定义)
插件的大部分用户提示信息都可以在这里自定义。支持 `&` 或 `§` 颜色代码。
```yaml
messages:
  general:
    playerOnly: "§c此命令只能由玩家执行。"
    noPermission: "§c你没有权限执行此命令。"
    httpDisabled: "§cHTTP服务器未启用，此功能不可用。"
    invalidUrl: "§c提供的URL无效或为空。"
  bf:
    usage: |-
      §6EogdMusicPlayer 命令:
      §e/bf play <歌曲名称/编号>§7 - 播放预设歌曲
      §e/bf playurl <URL>§7 - 播放指定URL的音乐 (仅自己听)
      §e/bf stop§7 - 停止当前为你播放的音乐
      §e/bf gui§7 - 打开音乐播放GUI
      §e/bf createroom <URL> <描述>§7 - 创建一个音乐室
      §e/bf join <发起者名称>§7 - 加入一个音乐室
      §e/bf start§7 - 为你的音乐室开始播放音乐 (仅房主)
      §e/bf roomplay <新URL>§7 - 更新你音乐室的音乐URL (仅房主)
      §e/bf disbandroom§7 - 解散你创建的音乐室 (仅房主)
      §e/bf reload§7 - 重载插件配置
      §e/bf info§7 - 查看插件信息
    unknownCommand: "§c未知子命令。使用 /bf 获取帮助。"
    play:
      usage: "§6用法: /bf play <歌曲名称或编号>"
      notFound: "§c未找到名为或编号为 '§e<song>§c' 的预设歌曲。"
    playurl:
      usage: "§6用法: /bf playurl <URL>"
      preparing: "§e正在准备音乐资源包..."
      packCreationFailed: "§c创建资源包失败。"
      error: "§c播放音乐时发生错误。"
    stop:
      stoppedForSelf: "§a已为你停止播放音乐。"
      roomStopped: "§a已停止音乐室 '§e<room_description>§a' 的音乐播放。"
      notPlaying: "§e当前没有为你播放音乐。"
    createroom:
      usage: "§6用法: /bf createroom <URL> <描述>"
      noDescription: "§c请输入音乐室的描述。"
      alreadyCreated: "§e你已经创建了一个音乐室: §f<room_description>§e。请先解散它。"
      successWithStartHint: "§a音乐室 '§e<description>§a' 已创建！URL: §f<url>§a。使用 §e/bf start §a开始播放。"
      broadcast: "§b<creator_name> §a创建了音乐室 描述:§f <description> §a输入§e /bf join <creator_name> §a来加入吧！"
    join:
      usage: "§6用法: /bf join <发起者名称>"
      roomNotFound: "§c未找到由 '§e<creator>§c' 发起的音乐室。"
      alreadyCreator: "§e你已经是这个音乐室的发起者了！"
      alreadyMember: "§e你已经是音乐室 '§f<room_description>§e' 的成员了。"
      leftOtherRoom: "§8你已离开之前的音乐室: §7<other_room_description>"
      successNoAutoPlay: "§a成功加入音乐室: §f<room_description>§a！等待房主开始播放音乐。"
      internalRoomNotFound: "§c内部错误：找不到指定的音乐室ID。"
    reload:
      success: "§aEogdMusicPlayer 配置已重载。"
    room:
      start:
        notRoomCreator: "§c你不是任何音乐室的创建者。"
        noMusicUrl: "§c你的音乐室 '§e<room_description>§c' 当前没有设置音乐URL。请使用 §e/bf roomplay <URL> §c设置。"
        startingMusic: "§a正在为音乐室 '§e<room_description>§a' 的所有成员准备并发送音乐资源包..."
        memberStartNotification: "§6房主已开始播放音乐！请接受资源包。"
      play:
        usage: "§6用法: /bf roomplay <新音乐URL>"
        notRoomCreator: "§c你不是任何音乐室的创建者。"
        urlSet: "§a音乐室 '§e<room_description>§a' 的音乐URL已更新为: §f<url>§a。使用 §e/bf start §a开始播放。"
        urlSame: "§e音乐室 '§f<room_description>§e' 的URL未改变。使用 §b/bf start §e重新播放。"
    disbandroom:
      notCreatorOrNoRoom: "§c你没有创建音乐室，或无法解散它。"
      success: "§a音乐室 '§e<room_description>§a' 已成功解散。"
      notifyMemberRoomDisbanded: "§e你所在的音乐室 '§f<room_description>§e' 已被创建者解散。"
  playurl:
    usage: "§6用法: /playurl <URL>"
    preparing: "§e正在准备音乐资源包..."
    packCreationFailed: "§c创建资源包失败。"
    error: "§c播放音乐时发生错误。"
  resourcePack:
    status:
      accepted: "§a资源包已接受！"
      declined: "§c资源包被拒绝。无法播放音乐。"
      failed: "§6资源包下载失败。请检查你的网络或服务器控制台日志。"
      successfully_loaded: "§9资源包加载成功！"
  musicRoomClosedMessage: "§c音乐室 '§e<description>§c' 因长时间不活动或发起者离开已关闭。"
```

## 指令列表

### 主要指令 (/bf)
| 子命令                               | 描述                                     | 用法示例                               |
| ------------------------------------ | ---------------------------------------- | -------------------------------------- |
| `play <歌曲名称/编号>`               | 播放预设歌曲列表中的歌曲 (仅自己听)。    | `/bf play song1`                       |
| `playurl <URL>`                      | 播放指定 URL 的音乐 (仅自己听)。         | `/bf playurl <URL>`                    |
| `stop`                               | 停止当前为你播放的音乐。                 | `/bf stop`                             |
| `gui`                                | 打开预设歌曲的 GUI 界面。                | `/bf gui`                              |
| `createroom <URL> <描述>`            | 创建一个音乐室，并设置初始音乐URL和描述。 | `/bf createroom <URL> "我的派对"`      |
| `join <发起者名称>`                  | 加入由指定玩家创建的音乐室。             | `/bf join Notch`                       |
| `start`                              | (仅房主) 为你的音乐室开始/重新开始播放音乐。| `/bf start`                            |
| `roomplay <新URL>`                   | (仅房主) 更新你音乐室播放的音乐 URL。    | `/bf roomplay <新URL>`                 |
| `disbandroom`                        | (仅房主) 解散你创建的音乐室。            | `/bf disbandroom`                      |
| `reload`                             | [管理员] 重载插件的配置文件。            | `/bf reload`                           |
| `info`                               | 查看插件的作者、版本和描述信息。         | `/bf info`                             |

### 快捷指令 (/playurl)
| 指令             | 描述                               | 用法示例            |
| ---------------- | ---------------------------------- | ------------------- |
| `/playurl <URL>` | 快速播放指定 URL 的音乐 (仅自己听)。 | `/playurl <URL>`    |

### 内部指令
| 指令                          | 描述                                       | 权限 (默认OP)                |
| ----------------------------- | ------------------------------------------ | ---------------------------- |
| `/internal_join_room <roomId>`| 内部指令，用于通过特定机制加入音乐室。     | `eogdmusicplayer.internal.join` |

## 权限节点

| 权限节点                             | 描述                                       | 默认设置 |
| ------------------------------------ | ------------------------------------------ | -------- |
| `eogdmusicplayer.reload`             | 允许使用 `/bf reload` 重载插件配置。         | `op`     |
| `eogdmusicplayer.play`               | 允许使用 `/bf play` 播放预设歌曲。         | `true`   |
| `eogdmusicplayer.playurl`            | 允许使用 `/bf playurl` 和 `/playurl`。     | `true`   |
| `eogdmusicplayer.stop`               | 允许使用 `/bf stop` 停止音乐。             | `true`   |
| `eogdmusicplayer.gui`                | 允许使用 `/bf gui` 打开GUI。               | `true`   |
| `eogdmusicplayer.createroom`         | 允许使用 `/bf createroom` 创建音乐室。     | `true`   |
| `eogdmusicplayer.joinroom`           | 允许使用 `/bf join` 加入音乐室。           | `true`   |
| `eogdmusicplayer.room.start`         | 允许音乐室创建者使用 `/bf start` 播放音乐。  | `true`   |
| `eogdmusicplayer.roomplay`           | 允许音乐室创建者使用 `/bf roomplay` 更新URL。| `true`   |
| `eogdmusicplayer.disbandroom`        | 允许音乐室创建者使用 `/bf disbandroom`。   | `true`   |
| `eogdmusicplayer.info`               | 允许使用 `/bf info` 查看插件信息。         | `true`   |
| `eogdmusicplayer.internal.join`      | 允许内部机制加入房间。                     | `op`     |

## 常见问题 (FAQ)
*   **问: 为什么玩家听不到音乐/资源包提示不出现或失败？**
    答: 请检查以下几点：
    1.  `config.yml` 中 `httpServer.publicAddress` 是否已正确设置为你服务器的公共 IP 或域名 (不带端口)？ 这是最常见的问题。
    2.  `httpServer.port` (默认为 `8123`) 是否未被其他程序占用，并且已在服务器防火墙/端口转发规则中开放 TCP 访问？
    3.  玩家是否在服务器提示时点击“是”或接受了资源包？
    4.  服务器控制台是否有与 EogdMusicPlayer 或资源包相关的错误信息？
    5.  音乐 URL 是否是有效的、可直接播放的音频链接 (通常是 `.mp3` 或某些直播流格式)？

*   **问: 如何添加我自己的预设歌曲？**
    答: 编辑 `plugins/EogdMusicPlayer/config.yml` 文件中的 `presetSongs` 部分。按照已有歌曲的格式添加新的条目，指定 `name` (显示名称), `url` (音乐链接), 和 `item` (GUI中的图标)。修改后使用 `/bf reload` 重载配置。

*   **问: `resourcePack.packFormat` 应该设置成多少？**
    答: 这取决于你的服务器 Minecraft 版本。对于 Minecraft 1.21，这个值应该是 `32`。如果设置错误，玩家可能无法正确加载资源包。

*   **问: 音乐室的 URL 可以是直播电台吗？**
    答: 不，URL只支持.ogg文件。

## 如何贡献
如果你对 EogdMusicPlayer 项目感兴趣并希望做出贡献，我们非常欢迎！
1.  Fork 本仓库 (你需要提供仓库链接)。
2.  创建一个新的特性分支 (`git checkout -b feature/YourAmazingFeature`)。
3.  进行修改并提交你的更改 (`git commit -am 'Add some AmazingFeature'`)。
4.  将你的分支推送到 GitHub (`git push origin feature/YourAmazingFeature`)。
5.  创建一个 Pull Request，详细描述你的更改。

## 许可证
本项目采用 [知识共享署名-非商业性使用4.0 国际许可证]（https://creativecommons.org/licenses/by-nc/4.0/） 进行许可。

---
*由 eogd 开发和维护*
*感谢使用 EogdMusicPlayer！*

## 支持我的闲鱼店铺 【闲鱼】https://m.tb.cn/h.6nA3DRE?tk=7ACpVUFVHJa MF937 「这是我的闲鱼号，快来看看吧～」 点击链接直接打开 
