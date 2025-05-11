# EogdMusicPlayer - Spigot 音乐插件

![Plugin Icon Placeholder](https://via.placeholder.com/128x128.png?text=EogdMusicPlayer) <!-- 你可以将这个链接替换为你的插件图标 -->

EogdMusicPlayer 是一款为 Spigot 1.21 Minecraft 服务器设计的音乐播放插件，旨在为玩家提供沉浸式的背景音乐和便捷的音乐控制体验。

## 目录
- [特性](#特性)
- [要求](#要求)
- [安装](#安装)
- [指令](#指令)
- [权限](#权限)
- [配置文件 (config.yml)](#配置文件-configyml)
- [常见问题 (FAQ)](#常见问题-faq)
- [贡献](#贡献)
- [许可证](#许可证)

## 特性
*   **背景音乐**: 为服务器的不同世界或区域配置背景音乐。
*   **玩家指令**: 允许玩家控制音乐播放（如播放、停止、选择歌曲等）。
*   **易于配置**: 通过简单的配置文件进行个性化设置。
*   **轻量高效**: 旨在尽可能减少对服务器性能的影响。
*   *(在此处添加更多你的插件特有的功能)*

## 要求
*   **Minecraft 服务器**: Spigot (或兼容 Spigot 的服务端，如 Paper) 1.21 或更高版本。
*   **Java**: Java 17 或更高版本。

## 安装
1.  从 [GitHub Releases](https://github.com/eogd/EogdMusicPlayer-spigot/releases) (你需要先创建 Release) 或其他分发渠道下载最新的 `EogdMusicPlayer-spigot-版本号.jar` 文件。
2.  将下载的 `.jar` 文件放入你服务器的 `plugins` 文件夹中。
3.  重启或重载你的 Minecraft 服务器。
    *   **重启**: `stop` 服务器，然后重新启动。
    *   **重载 (不推荐用于生产环境)**: 使用 `/reload` (可能会导致问题，建议完全重启)。
4.  插件首次加载时，会在 `plugins/EogdMusicPlayer/` 目录下生成默认的配置文件 (`config.yml` 等)。

## 指令
以下是 EogdMusicPlayer 插件提供的指令：

| 指令                 | 描述                                     | 权限节点 (示例)                  |
| -------------------- | ---------------------------------------- | -------------------------------- |
| `/mp help`           | 显示插件的帮助信息。                       | `eogdmusicplayer.command.help`   |
| `/mp play [歌曲名]`  | 播放指定的歌曲 (如果玩家有权)。            | `eogdmusicplayer.command.play`   |
| `/mp stop`           | 停止当前播放的音乐。                       | `eogdmusicplayer.command.stop`   |
| `/mp volume <0-100>` | 设置玩家自己的音乐音量。                 | `eogdmusicplayer.command.volume` |
| `/mp list`           | 列出可播放的歌曲。                       | `eogdmusicplayer.command.list`   |
| `/mp reload`         | (管理员) 重载插件配置文件。              | `eogdmusicplayer.admin.reload` |
| `/bf info`           | 显示插件的作者、版本和描述信息。         | `eogdmusicplayer.command.info`   |
| *(在此处添加更多指令)* |                                          |                                  |

**注意**: `/bf` 是 `/eogdmusicplayer` 的别名 (如果已配置)。请根据实际指令和别名进行调整。

## 权限
以下是 EogdMusicPlayer 插件使用的权限节点：

*   `eogdmusicplayer.command.help`: 允许玩家使用 `/mp help` 指令。
*   `eogdmusicplayer.command.play`: 允许玩家使用 `/mp play` 指令。
*   `eogdmusicplayer.command.stop`: 允许玩家使用 `/mp stop` 指令。
*   `eogdmusicplayer.command.volume`: 允许玩家使用 `/mp volume` 指令。
*   `eogdmusicplayer.command.list`: 允许玩家使用 `/mp list` 指令。
*   `eogdmusicplayer.command.info`: 允许玩家使用 `/bf info` 指令。
*   `eogdmusicplayer.admin.reload`: 允许管理员使用 `/mp reload` 指令。
*   `eogdmusicplayer.admin.*`: 赋予所有管理员权限。
*   `eogdmusicplayer.user.*`: 赋予所有普通用户权限。
*   *(在此处添加更多权限节点)*

默认情况下，OP 玩家拥有所有权限。你可以使用如 LuckPerms 这样的权限管理插件来管理这些权限。

## 配置文件 (config.yml)
插件的主要配置文件位于 `plugins/EogdMusicPlayer/config.yml`。你可以在此调整插件的各项设置。

```yaml
# EogdMusicPlayer 配置示例
# config.yml

# 插件消息前缀
plugin-prefix: "&7[&bEogdMusic&3Player&7] &r"

# 默认音量 (0-100)
default-volume: 70

# 是否启用世界背景音乐
enable-world-music: true

# 世界背景音乐列表 (示例)
# world_music:
#   world: "song1.nbs, song2.nbs" # 主世界播放的歌曲，逗号分隔
#   world_nether: "nether_ambient.nbs" # 下界播放的歌曲

# (在此处添加更多配置选项和说明)
# 例如：歌曲文件夹路径、消息自定义等

# ... 其他配置项 ...
```
**重要**: 修改配置文件后，请使用 `/mp reload` (需要相应权限) 或重启服务器以使更改生效。

## 常见问题 (FAQ)
*   **Q: 插件不工作怎么办？**
    A: 请检查服务器控制台是否有 EogdMusicPlayer相关的错误信息。确保你使用的是兼容的 Spigot/Paper 版本和 Java 版本。你也可以在 [GitHub Issues](https://github.com/eogd/EogdMusicPlayer-spigot/issues) 页面报告问题。

*   **Q: 如何添加我自己的音乐？**
    A: (你需要在这里说明音乐文件应该放在哪里，支持什么格式，以及如何在配置中引用它们。例如：请将 `.nbs` 文件放入 `plugins/EogdMusicPlayer/songs/` 目录下，然后在 `config.yml` 中引用它们的文件名。)

*   *(在此处添加更多常见问题与解答)*

## 贡献
欢迎对 EogdMusicPlayer 项目做出贡献！
1.  Fork 本仓库 (`https://github.com/eogd/EogdMusicPlayer-spigot`)
2.  创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3.  提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4.  推送到分支 (`git push origin feature/AmazingFeature`)
5.  打开一个 Pull Request

## 许可证 
本项目采用 [知识共享署名-非商业性使用4.0 国际许可证]（https://creativecommons.org/licenses/by-nc/4.0/） 进行许可。

---
*由 eogd 开发和维护*
*感谢使用 EogdMusicPlayer！*

## 支持我的闲鱼店铺 【闲鱼】https://m.tb.cn/h.6nA3DRE?tk=7ACpVUFVHJa MF937 「这是我的闲鱼号，快来看看吧～」 点击链接直接打开 
