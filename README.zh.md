<div align="center">

# Anatorius

**适用于 Minecraft 1.21.11 的 Fabric 实用客户端，现已开源。**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Modules](https://img.shields.io/badge/Modules-162-8A2BE2?style=flat-square)

[English](README.md) · [Polski](README.pl.md) · [Русский](README.ru.md) · [Español](README.es.md) · **中文**

</div>

---

> **本项目是 [AnatoriusDev/Anatorius](https://github.com/AnatoriusDev/Anatorius) 的非官方分支（fork）。**
> 代码没有改动：本分支只添加了翻译后的文档，并在
> [Releases](https://github.com/piolunson/anatorius/releases) 中提供已编译好的 JAR 文件。

## 下载

可直接使用的模组附在[最新版本](https://github.com/piolunson/anatorius/releases/latest)中：
**`anatorius-2.2.4.jar`**。请忽略 `-dev` 和 `-sources` 文件。

## 为什么公开？

现在做客户端很容易。工具很成熟，知识随处可得，想做的人一个周末就能拼出来。再把 Anatorius
关起来、加上登录和 HWID 校验，已经没有意义。

所以这里就是全部内容。登录系统、许可证校验和混淆都去掉了，剩下的就是客户端本身：所有模块、
GUI 和 HUD。一条命令就能编译，编译出来就是你的。

以后可能还会有更新，但这里没有任何秘密。阅读、学习、fork，做你自己的客户端。

---

## 编译

只需要 **JDK 21**。其他一切（Gradle、Minecraft、Fabric、Baritone）都会在第一次编译时下载。

```bash
git clone https://github.com/piolunson/anatorius.git
cd anatorius
./gradlew build        # Windows: gradlew.bat build
```

JAR 文件位于 `build/libs/anatorius-<版本>.jar`。请忽略 `-dev` 和 `-sources` 文件。

## 安装

1. 为 **1.21.11** 安装 [Fabric Loader](https://fabricmc.net/use/)。
2. 将 [Fabric API](https://modrinth.com/mod/fabric-api) 和 Anatorius 的 JAR 放入 `.minecraft/mods`。
3. 启动游戏，按 **右 Shift** 打开 ClickGUI。

命令以 `.` 开头：`.help`、`.toggle <模块>`、`.bind <模块> <按键>`、`.config`、`.friend` 等。

---

## 功能

<details>
<summary><b>战斗</b> (26)</summary>

AntiFeetTrap · AntiPearl · AntiPiston · AutoAnchor · AutoArmor · AutoBed · AutoBowRelease · AutoCrystal ·
AutoDrop · AutoLog · AutoMace · AutoMine · AutoPot · AutoTotem · AutoTrap · AutoWeb · AutoXp · Blocker ·
CombatPlacer · Criticals · HoleFill · KillAura · NoHitDelay · SelfBow · SelfFill · SelfTrap
</details>

<details>
<summary><b>移动</b> (29)</summary>

Accelerate · AntiLevitation · AntiVoid · ArrowControls · AutoWalk · ElytraFly · EntityControl · FakeFly ·
FastFall · FastWeb · Flight · HoleSnap · IceSpeed · InventoryControl · Jesus · KeepSprint · LongJump · NoFall ·
NoJumpDelay · NoSlow · PacketFly · Parkour · SafeWalk · Scaffold · Speed · Sprint · Step · Velocity · Warp
</details>

<details>
<summary><b>玩家</b> (22)</summary>

AirPlace · AnimalBreeder · AntiHunger · AutoFirework · AutoPearl · ChestStealer · ChorusControl · FastPlace ·
InventorySorter · MultiTask · NoGlitchBlocks · NoInteract · NoRotate · Nuker · QuickCraft · Replenish ·
RotationLock · SourceFill · SpeedMine · Swing · ToolSaver · XCarry
</details>

<details>
<summary><b>视觉</b> (35)</summary>

ArrowTracers · AspectRatio · Atmosphere · BlockHighlight · BreakHighlight · Chams · CityESP · Crosshair ·
DamageNumbers · DeathEffects · EntityModifier · ESP · Freecam · FullBright · HandProgress · HoleESP ·
MotionBlur · NameTags · NoInterpolation · NoRender · PhaseESP · PopChams · Search · Shaders · Skybox · TextESP ·
Tracers · Trails · Trajectories · ViewClip · ViewModel · VoidESP · Waypoints · Xray · Zoom
</details>

<details>
<summary><b>漏洞利用</b> (15)</summary>

AntiVanish · Disabler · FastLatency · HitboxDesync · MouseFix · NewChunks · NoMineAnimation · Phase ·
PingSpoof · PortalGodMode · RaytraceBypass · Reach · RocketExtender · Timer · Trident
</details>

<details>
<summary><b>杂项</b> (22)</summary>

Announcer · AntiPacketKick · AutoDupe · AutoLogin · AutoReconnect · AutoRespawn · BetterChat · CombatInfo ·
ExtraTab · FakePlayer · FOVModifier · Handshake · InventoryCleaner · NameProtect · NoSoundLag · PosLogger ·
ShulkerInfo · Sounds · Spammer · SwapDebug · UnfocusedFPS · Welcomer
</details>

<details>
<summary><b>核心</b> (13)</summary>

AntiCheat · Cape · ClickGui · Color · Commands · Font · Friend · HUD · Menu · Notifications · Renders ·
Rotations · RPC
</details>

---

## 项目结构

```
src/main/java/dev/project/anatorius/
├── modules/      所有模块，按分类放在 modules/impl/
├── commands/     聊天命令（.help、.bind 等）
├── gui/          ClickGUI
├── hud/          HUD 元素
├── managers/     配置、好友、旋转、目标、渲染等
├── mixins/       注入 Minecraft 的 hook
├── events/       事件总线
├── settings/     模块设置
└── utils/        渲染、数学、网络及其他辅助工具
```

添加模块只需要一个类：继承 `Module`，加上 `@RegisterModule` 注解，编译时会自动注册。

---

## 致谢

- **ArtyCi / DesBilalz**，Anatorius 的作者
- [Baritone](https://github.com/cabaletta/baritone)，提供寻路功能（LGPL-3.0）
- [Orbit](https://github.com/MeteorDevelopment/orbit)（Meteor Development），事件总线的基础
- [discord-ipc](https://github.com/MeteorDevelopment/discord-ipc)，提供 Discord Rich Presence

## 免责声明

本项目仅供学习交流。许多服务器禁止使用实用客户端，因此你使用它造成的一切后果由你自己承担。

<div align="center">

**采用 GPL-3.0 许可证**

</div>
