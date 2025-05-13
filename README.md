# EogdMusicPlayer - Spigot 音乐播放插件

EogdMusicPlayer 是一款功能丰富的 Spigot 插件，允许服务器管理员和玩家在 Minecraft 中通过自定义资源包播放外部音乐链接。它支持独立播放和音乐房间功能，为你的服务器带来独特的听觉体验。

## 目录

*   [主要功能](#-主要功能)
*   [要求与依赖](#-要求与依赖)
*   [安装步骤](#-安装步骤)
*   [配置 (`config.yml`)](#️-配置-configyml)
*   [命令与权限](#-命令与权限)
*   [工作原理简述](#️-工作原理简述)
*   [故障排除](#-故障排除)
*   [作者](#-作者)

## 主要功能

*   **URL 音乐播放**: 直接通过 URL 播放 .ogg 格式的音乐。
*   **预设歌曲列表**: 在配置文件中预设常用歌曲，方便快速播放。
*   **动态资源包生成**:
    *   **独立模式**: 为每个播放请求动态生成一个包含单首音乐的资源包。
    *   **合并模式 (可选)**: 将音乐动态添加到服务器配置的基础资源包中，减少客户端切换资源包的频率。
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
# HTTP 服务器设置
httpServer:
  enabled: true # 是否启用内置 HTTP 服务器 (必需功能)
  port: 8080 # HTTP 服务器监听的端口
  publicAddress: "auto" # 服务器的公共IP地址或域名。 "auto" 会尝试自动检测。如果检测失败或不准确，请手动设置为你的服务器IP。
  servePathPrefix: "eogdmusicplayer" # 资源包内声音事件的命名空间前缀，建议保持默认。

# 资源包设置
resourcePack:
  packFormat: 32 # 资源包格式版本。请根据你的服务器 Minecraft 版本调整。 (例如 1.20.5-1.20.6 是 32, 1.21可能是新的值)
  description: "&b音乐播放器资源包" # 资源包的描述信息
  promptMessage: "&6服务器请求您安装一个音乐资源包以获得完整体验！" # 发送给玩家的资源包提示信息
  maxDownloadSizeBytes: 10485760 # 单个音频文件最大允许下载大小 (字节单位, 默认10MB)。0表示无限制。
  # 合并模式设置 (可选)
  useMergedPackLogic: false # 是否启用合并模式。如果为 true，则需要配置 basePackFileName。
  basePackFileName: "BaseResourcePack.zip" # 基础资源包的文件名，应放置在 plugins/EogdMusicPlayer/ 目录下。

# 预设歌曲列表
presetSongs:
  - name: "&a示例歌曲 1"
    url: "https://example.com/music1.ogg"
    displayItem: "MUSIC_DISC_CAT" # GUI中显示的物品材质 (Material 名称)
  - name: "&b示例歌曲 2"
    url: "https://example.com/music2.ogg"
    displayItem: "MUSIC_DISC_13"

# 消息配置 (部分示例)
messages:
  prefix: "&7[&bEogdMusic&3Player&7] "
  general:
    noPermission: "&c你没有权限执行此命令。"
    playerOnly: "&c此命令只能由玩家执行。"
    # ... 更多消息可在此自定义
```

**重要**:
*   `resourcePack.packFormat`: 这个值非常重要，必须与你的服务器客户端版本兼容。请查阅 Minecraft Wiki 获取最新的资源包版本信息。
*   `httpServer.publicAddress`: 如果服务器在NAT网络后（例如家庭网络），`auto` 可能无法正确检测到公网IP。你需要手动配置为你的公网IP或域名，否则玩家可能无法下载资源包。确保配置的 `httpServer.port` 在防火墙和路由器上是开放的。
*   `resourcePack.useMergedPackLogic`: 如果设置为 `true`，你必须在 `plugins/EogdMusicPlayer/` 目录下放置一个名为 `basePackFileName` 指定的有效基础资源包文件。

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
    *   当玩家请求播放音乐时，插件会根据配置（独立模式或合并模式）动态生成一个临时的资源包。
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
    *   确保 `config.yml` 中 `useMergedPackLogic` 为 `true` 时，`plugins/EogdMusicPlayer/` 目录下存在 `basePackFileName` 指定的基础资源包文件。
*   **玩家无法下载资源包**:
    *   确认 `config.yml` 中的 `httpServer.publicAddress` 设置正确（对于公网服务器，应为服务器的公网IP或域名）。
    *   确认 `httpServer.port` 已在服务器防火墙和路由器（如果适用）中开放。

## 作者

EogdMusicPlayer 由 **Eogd** 开发。

## 许可证
本项目采用 [知识共享署名-非商业性使用4.0 国际许可证]（https://creativecommons.org/licenses/by-nc/4.0/） 进行许可。

## 支持我的闲鱼店铺 【闲鱼】https://m.tb.cn/h.6nA3DRE?tk=7ACpVUFVHJa MF937 「这是我的闲鱼号，快来看看吧～」 点击链接直接打开 
