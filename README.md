# Jade SkyBlock Music

Jade 是用于 Hypixel SkyBlock 的 Fabric 客户端模组。它读取计分板上的地点，在探索岛屿、地牢和裂隙时播放对应的背景音乐。支持 Minecraft 26.1.2、26.2 和 26.3，可独立使用，也可与 Sapphire 汉化模组同装。

## 安装

从 [GitHub Releases](https://github.com/ESN17/Jade-Mod-for-Hypixel-SkyBlock/releases/latest) 下载与游戏版本一致的 JAR，放入实例的 `mods` 文件夹。需要 Java 25、Fabric Loader 0.19.5 或更高版本，以及对应版本的 Fabric API。

首次启动时，Jade 会在后台下载 23 首默认音乐并校验文件。已缓存的完整曲目可以离线播放。使用 `/jade status` 查看进度，使用 `/jade reload` 重试。进入 SkyBlock 后，请在「SkyBlock 菜单 → 设置 → 音效设置 → 播放音乐」中关闭服务器配乐。

## 设置音乐

- 输入 `/jade` 打开岛屿和地点目录。可为整岛设置默认音乐，为单个地点指定曲目，或选择跟随岛屿。地牢血房可单独配乐。
- 通过系统文件选择框挑选音乐，或将文件拖进设置页。支持 WAV、FLAC、MP3、Ogg Vorbis、AIFF 和 AU。
- 不同曲目之间交叉渐变。相邻地点共用曲目时保持播放进度，计分板显示 `None` 时延续上一地点的配乐。
- 在 Minecraft 的「音乐」和「主音量」中调整音量。进入多人服务器后暂停原版背景音乐和唱片，保留服务器提示音。
- 地点绑定保存在实例的 `config/jade-music/locations.json`。默认曲库位于 `config/jade-music/music/Jade Defaults`。

## 从源码构建

主开发版本为 Minecraft 26.3。在 Windows 使用 Java 25 和 PowerShell 运行 `.\tools\build-all.ps1` 可生成三个版本。单独构建可用 `.\gradlew.bat "-Pmc=26.1.2" build`（Linux/macOS 使用 `./gradlew`）。JAR 输出到 `build/<Minecraft 版本>/libs/`。

源码附带默认曲目的文件清单、校验值和地点映射，构建模组无需下载录音。地点索引来源见 [LOCATION-CREDITS.txt](src/main/resources/LOCATION-CREDITS.txt)。

