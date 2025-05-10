# EogdMusicPlayer (Spigot 1.21)

## 概述

EogdMusicPlayer 是一款 Spigot Minecraft 插件，旨在为服务器提供灵活的音乐播放功能。它允许管理员配置服务器预设的背景音乐或主题曲，并提供一个指令让玩家可以播放在线的 `.ogg` 音乐文件。

该插件的主要功能包括：
*   **服务器预设音乐**：管理员可以配置一个包含自定义音乐的资源包，玩家下载后即可通过指令播放。
*   **在线音乐播放 (`/playurl`)**：拥有权限的玩家可以播放在线托管的 `.ogg` 音乐文件。插件会动态生成一个临时的、包含该音乐的资源包发送给玩家。
*   **基于 SQLite 的配置存储**：用于存储预设资源包的 URL 和 SHA-1 哈希值。
*   **内置 HTTP 服务器**：用于向玩家提供动态生成的临时资源包 (用于 `/playurl` 功能)。

## 功能特性

*   通过指令下载和播放服务器预设的音乐资源包。
*   通过指令播放在线 `.ogg` 音乐 URL。
*   为在线音乐动态生成临时资源包。
*   使用 SHA-1 哈希验证资源包，确保客户端下载的是正确的版本。
*   通过内置 HTTP 服务器提供临时资源包，需要服务器管理员正确配置网络和端口。
*   详细的权限管理。
*   通过 MiniMessage 提供格式化的消息提示。

## 安装

1.  下载最新的 `EogdMusicPlayer-版本号.jar` 文件。
2.  将其放入你的 Spigot 服务器的 `plugins` 文件夹。
3.  重启或加载你的 Spigot 服务器。
4.  插件会自动生成默认的 `config.yml` 文件和 SQLite 数据库文件 (`EogdMusicPlayer.db`)。

## 指令与权限

### 主要指令

*   `/bf` 或 `/music` 或 `/eogdmusic`
    *   **描述**: EogdMusicPlayer 插件的主指令。
    *   **权限**: `musicplayer.use` (默认所有玩家拥有)

*   `/playurl <URL> [玩家名]` 或 `/pu <URL> [玩家名]`
    *   **描述**: 播放在线 URL 的音乐。如果未指定玩家名，则为指令执行者播放。
    *   **权限**:
        *   为自己播放: `musicplayer.playurl` (默认 OP 拥有)
        *   为他人播放: `musicplayer.playurl.others` (默认 OP 拥有)

### `/bf` 子指令

*   `/bf d` 或 `/bf download`
    *   **描述**: 提示玩家下载服务器预设的音乐资源包。
    *   **权限**: `musicplayer.use`

*   `/bf start` 或 `/bf play`
    *   **描述**: 播放服务器预设音乐 (在 `config.yml` 中通过 `soundEventName` 配置的声音事件)。
    *   **权限**: `musicplayer.use`

*   `/bf setconfig <键> <值>`
    *   **描述**: (管理员指令) 设置插件存储在数据库中的配置项。
    *   **权限**: `musicplayer.admin` (默认 OP 拥有)
    *   **可用键**:
        *   `musicurl <URL>`: 设置预设音乐的源文件下载 URL (供 `/bf downloadlatestmusic` 指令使用，目前该指令主要用于下载示例文件，不直接用于播放)。
        *   `rpackurl <URL>`: 设置服务器预设资源包 (`.zip` 文件) 的**直接下载链接**。
        *   `rpacksha1 <SHA1>`: 设置服务器预设资源包的 **SHA-1 哈希值** (40位小写十六进制字符)。
    *   **示例**:
        *   `/bf setconfig rpackurl https://example.com/packs/MyServerMusic.zip`
        *   `/bf setconfig rpacksha1 a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2`

*   `/bf downloadlatestmusic`
    *   **描述**: (管理员指令) 从数据库中 `musicurl` 配置的 URL 下载音乐文件到插件的 `downloads` 目录。主要用于测试或获取管理员指定的通用音乐文件。
    *   **权限**: `musicplayer.admin`

*   `/bf reload`
    *   **描述**: (管理员指令) 重载插件的 `config.yml` 文件。
    *   **权限**: `musicplayer.admin`

*   `/bf help`
    *   **描述**: 显示帮助信息。
    *   **权限**: `musicplayer.use`

## 配置文件 (`config.yml`)

插件会在首次启动时在 `plugins/EogdMusicPlayer/config.yml` 生成默认配置文件。

```yaml
# EogdMusicPlayer 配置文件

# 用于 /bf start 播放的预设音乐的声音事件名称。
# 这个名称必须与你制作的服务器预设资源包中 sounds.json 里定义的事件名称一致。
# 例如： "custom.music.background" 或 "mypack:music.theme"
soundEventName: "custom.music.song1" # 示例值，请修改为你自己的

# 当玩家使用 /bf d 下载预设资源包时，客户端收到的提示消息。
# 可以使用 MiniMessage 格式化: https://docs.adventure.kyori.net/minimessage/format.html
resourcePackPromptMessage: "<gold>服务器音乐包可用！请接受以收听自定义音乐。</gold>"

# --- 在线播放功能 (/playurl) 配置 ---
# 当玩家使用 /playurl 播放音乐时，客户端收到的临时资源包提示消息。
onlinePlayPromptMessage: "<gold>正在准备播放在线音乐，请接受临时资源包...</gold>"

httpServer:
  # 是否启用 /playurl 功能的内置 HTTP 服务器。如果禁用，/playurl 指令将无法使用。
  enabled: true
  
  # 重要: 服务器的公共可访问域名或IP地址 (不含端口号)。
  # 客户端将使用这个地址来下载由 /playurl 功能动态生成的临时资源包。
  # 例如: "play.example.com" 或 "123.45.67.89"
  # 对于面板服务器 (如 简幻欢 JHH)，这里填写你的服务器域名。
  publicAddress: "play.simpfun.cn" # 请根据你的服务器域名修改
  
  # 插件内置 HTTP 服务器将监听的独立 TCP 端口。
  # 必须与你的 Minecraft 游戏端口不同。
  # 对于面板服务器，你必须使用面板提供商为你分配并正确配置了端口转发的额外端口。
  # 示例中为 25199，请根据你从服务商处确认的端口进行修改。
  port: 25199 # 请根据你的实际可用端口修改
  
  # 客户端访问资源包的 URL 路径部分。
  # 最终 URL 结构: http://<publicAddress>:<port>/<servePath>/<pack_name>.zip
  # 通常保持默认即可。
  servePath: "musicpacks"
  
  # 插件用于存储临时下载的 OGG 文件和为 /playurl 生成的 ZIP 资源包的目录。
  # 这是相对于插件数据文件夹 (plugins/EogdMusicPlayer/) 的路径。
  tempDirectory: "temp_packs"
  
  # /playurl 下载的音乐文件最大允许大小 (字节)。0 表示无限制 (不推荐)。
  # 20971520 字节 = 20 MB
  maxDownloadSizeBytes: 20971520
  
  # 临时资源包在播放或失败后，在服务器上保留的时间 (秒)，之后会被自动清理。
  # 300 秒 = 5 分钟
  cleanupDelaySeconds: 300
```

**配置说明:**

*   `soundEventName`: 这是通过 `/bf start` 播放的预设音乐在资源包 `sounds.json` 中定义的声音事件的名称。管理员必须确保这个名称与他们自己制作的资源包中的定义完全一致。
*   `resourcePackPromptMessage`: 当玩家使用 `/bf d` 下载预设资源包时，客户端看到的提示信息。支持 MiniMessage 格式。
*   `onlinePlayPromptMessage`: 当玩家使用 `/playurl` 下载临时资源包时，客户端看到的提示信息。支持 MiniMessage 格式。
*   `httpServer.enabled`: 控制是否启用 `/playurl` 功能。如果为 `false`，内置 HTTP 服务器不会启动，`/playurl` 指令也无法使用。
*   `httpServer.publicAddress`: **极其重要**。这是玩家客户端用来下载临时资源包的地址。**必须是你的服务器的公共域名或 IP 地址，且不包含端口号。** 如果留空或配置错误，`/playurl` 功能将无法对外部玩家正常工作。
*   `httpServer.port`: **极其重要**。这是插件内置 HTTP 服务器监听的端口。**必须与你的 Minecraft 游戏端口不同。** 对于独立服务器，你需要确保此端口在防火墙中开放。对于面板服务器（如 "简幻欢" JHH），你**必须**使用面板提供商为你分配的、已正确设置端口转发的额外 TCP 端口。例如，如果你确认 JHH 为你分配了 `25199` 用于此插件，则在此处填写 `25199`。
*   `httpServer.servePath`: 通常保持默认的 `"musicpacks"` 即可。
*   `httpServer.tempDirectory`: 临时文件存储位置，通常保持默认。
*   `httpServer.maxDownloadSizeBytes`: 限制 `/playurl` 下载的 `.ogg` 文件大小，防止滥用。
*   `httpServer.cleanupDelaySeconds`: 临时生成的资源包在服务器上保留的时长，之后会自动删除以节省空间。

## 设置服务器预设音乐

1.  **创建资源包**:
    *   **音乐文件**: 将你的 `.ogg` 音乐文件（例如 `my_song.ogg`）放入资源包的 `assets/minecraft/sounds/custom/` 目录下 (或者你选择的其他子目录)。
    *   **`sounds.json`**: 在 `assets/minecraft/sounds.json` 中定义声音事件。
        ```json
        {
          "server.music.theme": { 
            "sounds": [
              {
                "name": "custom/my_song", 
                "stream": true 
              }
            ]
          }
        }
        ```
        这里的 `"server.music.theme"` 就是你的声音事件名称，`"custom/my_song"` 指向 `assets/minecraft/sounds/custom/my_song.ogg`。
    *   **`pack.mcmeta`**: 在资源包根目录创建，内容示例：
        ```json
        {
          "pack": {
            "pack_format": 32, // Minecraft 1.21
            "description": "My Server's Awesome Music Pack"
          }
        }
        ```
    *   将 `assets` 文件夹和 `pack.mcmeta` 文件打包成一个 `.zip` 文件 (例如 `ServerMusic.zip`)。

2.  **托管资源包**: 将 `ServerMusic.zip` 上传到一个公开的、可直接下载的 HTTP/HTTPS URL (例如 `https://your-file-host.com/ServerMusic.zip`)。

3.  **计算 SHA-1 哈希**: 计算 `ServerMusic.zip` 的 SHA-1 哈希值 (一个40位的十六进制字符串)。

4.  **配置插件**:
    *   以OP身份或在服务器控制台执行以下指令：
        *   `/bf setconfig rpackurl https://your-file-host.com/ServerMusic.zip`
        *   `/bf setconfig rpacksha1 <你的40位SHA1哈希值>`
    *   编辑 `plugins/EogdMusicPlayer/config.yml`，将 `soundEventName` 设置为你定义的事件名：
        `soundEventName: "server.music.theme"`
    *   执行 `/bf reload` 或重启服务器。

5.  **玩家使用**:
    *   玩家执行 `/bf d` 来下载和应用预设资源包。
    *   玩家执行 `/bf start` 来播放主题曲。

## 使用在线音乐播放 (`/playurl`)

1.  **确保配置正确**:
    *   `config.yml` 中的 `httpServer.enabled` 必须为 `true`。
    *   `httpServer.publicAddress` 必须是你的服务器公共域名 (不含端口)。
    *   `httpServer.port` 必须是你从面板提供商 (如 JHH) 处确认可用的、已正确转发的独立 TCP 端口 (例如 `25199`)。

2.  **获取 `.ogg` 音乐 URL**: 找到一个公开的、可直接下载的 `.ogg` 音乐文件的 URL。

3.  **执行指令**:
    *   拥有 `musicplayer.playurl` 权限的玩家可以执行：`/playurl <OGG音乐的URL>`
    *   例如: `/playurl https://example.com/music/cool_song.ogg`
    *   插件会下载该 `.ogg` 文件，动态创建一个包含此音乐的临时资源包，并通过内置 HTTP 服务器将其提供给玩家。玩家会收到加载临时资源包的提示。

4.  **播放**: 玩家接受并成功加载临时资源包后，音乐会自动播放。

**故障排除 (`/playurl`):**
*   **"INVALID_URL" 状态**:
    *   检查服务器日志中插件生成的资源包 URL。它应该类似于 `http://<publicAddress>:<port>/musicpacks/temp_music_pack_xxxx.zip`。
    *   确认 `config.yml` 中的 `publicAddress` 没有包含端口号，并且 `port` 设置正确。
*   **"FAILED_DOWNLOAD" 状态**:
    *   确认 `config.yml` 中的 `httpServer.port` (例如 `25199`) 已经在你的服务器防火墙中开放，并且你的面板提供商 (如 JHH) 已经为你的服务器实例正确设置了该端口的 TCP 转发。
    *   尝试在浏览器中直接访问插件日志中生成的资源包 URL，看是否能下载。
*   **资源包加载成功但无声音**:
    *   检查客户端的 Minecraft 日志 (`.minecraft/logs/latest.log`) 是否有关于声音事件或 OGG 解码的错误。
    *   确保客户端的“主音量”和“音乐/唱片机”音量已调高。
    *   尝试使用不同的 `.ogg` 文件，确保文件本身没有问题。

## 注意事项

*   对于 `/playurl` 功能，服务器的网络配置 (特别是 `httpServer.publicAddress` 和 `httpServer.port` 的正确性以及端口转发) 至关重要。
*   确保所有 `.ogg` 文件都是有效的 Ogg Vorbis 格式。
*   GeyserMC (基岩版跨平台联机) 对自定义声音和动态资源包的支持可能有限。此插件主要为 Java 版玩家设计。

## 支持我的闲鱼店铺 【闲鱼】https://m.tb.cn/h.6nA3DRE?tk=7ACpVUFVHJa MF937 「这是我的闲鱼号，快来看看吧～」 点击链接直接打开 
## 许可证 
本项目采用 [知识共享署名-非商业性使用4.0 国际许可证]（https://creativecommons.org/licenses/by-nc/4.0/） 进行许可。

---
作者: eogd
```
