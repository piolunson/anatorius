<div align="center">

# Anatorius

**A Fabric utility client for Minecraft 1.21.11. Now open source.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Modules](https://img.shields.io/badge/Modules-162-8A2BE2?style=flat-square)

</div>

---

## Why is this public?

Making a client is easy now. The tools are good, the knowledge is everywhere, and anyone who wants
one can put it together in a weekend. Keeping Anatorius closed, behind logins and HWID checks,
doesn't make sense anymore.

So here's the whole thing. The login system, the license checks and the obfuscation are gone. What's
left is the client: every module, the GUI and the HUD. Build it with one command and it's yours.

Updates may still come, but there's nothing secret in here. Read it, learn from it, fork it, make
your own.

---

## Building

You only need **JDK 21**. Everything else (Gradle, Minecraft, Fabric, Baritone) is downloaded on the
first build.

```bash
git clone <this repo>
cd Anatorius
./gradlew build        # Windows: gradlew.bat build
```

The jar ends up in `build/libs/anatorius-<version>.jar`. Ignore the `-dev` and `-sources` jars.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for **1.21.11**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the Anatorius jar in `.minecraft/mods`.
3. Start the game. Press **Right Shift** to open the ClickGUI.

Commands use the `.` prefix: `.help`, `.toggle <module>`, `.bind <module> <key>`, `.config`, `.friend`, and more.

---

## Features

<details>
<summary><b>Combat</b> (26)</summary>

AntiFeetTrap · AntiPearl · AntiPiston · AutoAnchor · AutoArmor · AutoBed · AutoBowRelease · AutoCrystal ·
AutoDrop · AutoLog · AutoMace · AutoMine · AutoPot · AutoTotem · AutoTrap · AutoWeb · AutoXp · Blocker ·
CombatPlacer · Criticals · HoleFill · KillAura · NoHitDelay · SelfBow · SelfFill · SelfTrap
</details>

<details>
<summary><b>Movement</b> (29)</summary>

Accelerate · AntiLevitation · AntiVoid · ArrowControls · AutoWalk · ElytraFly · EntityControl · FakeFly ·
FastFall · FastWeb · Flight · HoleSnap · IceSpeed · InventoryControl · Jesus · KeepSprint · LongJump · NoFall ·
NoJumpDelay · NoSlow · PacketFly · Parkour · SafeWalk · Scaffold · Speed · Sprint · Step · Velocity · Warp
</details>

<details>
<summary><b>Player</b> (22)</summary>

AirPlace · AnimalBreeder · AntiHunger · AutoFirework · AutoPearl · ChestStealer · ChorusControl · FastPlace ·
InventorySorter · MultiTask · NoGlitchBlocks · NoInteract · NoRotate · Nuker · QuickCraft · Replenish ·
RotationLock · SourceFill · SpeedMine · Swing · ToolSaver · XCarry
</details>

<details>
<summary><b>Visuals</b> (35)</summary>

ArrowTracers · AspectRatio · Atmosphere · BlockHighlight · BreakHighlight · Chams · CityESP · Crosshair ·
DamageNumbers · DeathEffects · EntityModifier · ESP · Freecam · FullBright · HandProgress · HoleESP ·
MotionBlur · NameTags · NoInterpolation · NoRender · PhaseESP · PopChams · Search · Shaders · Skybox · TextESP ·
Tracers · Trails · Trajectories · ViewClip · ViewModel · VoidESP · Waypoints · Xray · Zoom
</details>

<details>
<summary><b>Exploit</b> (15)</summary>

AntiVanish · Disabler · FastLatency · HitboxDesync · MouseFix · NewChunks · NoMineAnimation · Phase ·
PingSpoof · PortalGodMode · RaytraceBypass · Reach · RocketExtender · Timer · Trident
</details>

<details>
<summary><b>Miscellaneous</b> (22)</summary>

Announcer · AntiPacketKick · AutoDupe · AutoLogin · AutoReconnect · AutoRespawn · BetterChat · CombatInfo ·
ExtraTab · FakePlayer · FOVModifier · Handshake · InventoryCleaner · NameProtect · NoSoundLag · PosLogger ·
ShulkerInfo · Sounds · Spammer · SwapDebug · UnfocusedFPS · Welcomer
</details>

<details>
<summary><b>Core</b> (13)</summary>

AntiCheat · Cape · ClickGui · Color · Commands · Font · Friend · HUD · Menu · Notifications · Renders ·
Rotations · RPC
</details>

---

## Project layout

```
src/main/java/dev/project/anatorius/
├── modules/      every module, grouped by category in modules/impl/
├── commands/     chat commands (.help, .bind, ...)
├── gui/          ClickGUI
├── hud/          HUD elements
├── managers/     config, friends, rotations, targets, rendering ...
├── mixins/       hooks into Minecraft
├── events/       event bus
├── settings/     module settings
└── utils/        rendering, math, networking and other helpers
```

Adding a module is one class: extend `Module`, annotate it with `@RegisterModule`, and the build
registers it automatically.

---

## Credits

- **ArtyCi / DesBilalz**, the authors of Anatorius
- [Baritone](https://github.com/cabaletta/baritone) for pathfinding (LGPL-3.0)
- [Orbit](https://github.com/MeteorDevelopment/orbit) by Meteor Development, the base of the event bus
- [discord-ipc](https://github.com/MeteorDevelopment/discord-ipc) for Discord Rich Presence

## Disclaimer

This project is for educational purposes. Many servers forbid utility clients, so whatever you do
with it is on you.

<div align="center">

**Licensed under GPL-3.0**

</div>
