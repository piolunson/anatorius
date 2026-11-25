<div align="center">

# Anatorius

**Un cliente utilitario Fabric para Minecraft 1.21.11. Ahora con código abierto.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Modules](https://img.shields.io/badge/Modules-162-8A2BE2?style=flat-square)

[English](README.md) · [Polski](README.pl.md) · [Русский](README.ru.md) · **Español** · [中文](README.zh.md)

</div>

---

> **Este es un fork no oficial** de [AnatoriusDev/Anatorius](https://github.com/AnatoriusDev/Anatorius).
> El código no ha cambiado: este fork solo añade documentación traducida y archivos JAR ya compilados
> en la sección [Releases](https://github.com/piolunson/anatorius/releases).

## Descarga

El mod listo para usar está adjunto en la [última versión](https://github.com/piolunson/anatorius/releases/latest):
**`anatorius-2.2.4.jar`**. Ignora los archivos `-dev` y `-sources`.

## ¿Por qué es público?

Hacer un cliente es fácil ahora. Las herramientas son buenas, el conocimiento está en todas partes y
cualquiera que quiera uno puede armarlo en un fin de semana. Mantener Anatorius cerrado, detrás de
inicios de sesión y comprobaciones de HWID, ya no tiene sentido.

Así que aquí está todo. El sistema de inicio de sesión, las comprobaciones de licencia y la
ofuscación desaparecieron. Lo que queda es el cliente: cada módulo, la GUI y el HUD. Compílalo con un
solo comando y es tuyo.

Puede que sigan llegando actualizaciones, pero aquí no hay nada secreto. Léelo, aprende de él,
haz un fork, crea el tuyo.

---

## Compilación

Solo necesitas **JDK 21**. Todo lo demás (Gradle, Minecraft, Fabric, Baritone) se descarga en la
primera compilación.

```bash
git clone https://github.com/piolunson/anatorius.git
cd anatorius
./gradlew build        # Windows: gradlew.bat build
```

El JAR queda en `build/libs/anatorius-<versión>.jar`. Ignora los archivos `-dev` y `-sources`.

## Instalación

1. Instala [Fabric Loader](https://fabricmc.net/use/) para **1.21.11**.
2. Coloca [Fabric API](https://modrinth.com/mod/fabric-api) y el JAR de Anatorius en `.minecraft/mods`.
3. Inicia el juego. Pulsa **Shift derecho** para abrir el ClickGUI.

Los comandos usan el prefijo `.`: `.help`, `.toggle <módulo>`, `.bind <módulo> <tecla>`, `.config`, `.friend` y más.

---

## Funciones

<details>
<summary><b>Combate</b> (26)</summary>

AntiFeetTrap · AntiPearl · AntiPiston · AutoAnchor · AutoArmor · AutoBed · AutoBowRelease · AutoCrystal ·
AutoDrop · AutoLog · AutoMace · AutoMine · AutoPot · AutoTotem · AutoTrap · AutoWeb · AutoXp · Blocker ·
CombatPlacer · Criticals · HoleFill · KillAura · NoHitDelay · SelfBow · SelfFill · SelfTrap
</details>

<details>
<summary><b>Movimiento</b> (29)</summary>

Accelerate · AntiLevitation · AntiVoid · ArrowControls · AutoWalk · ElytraFly · EntityControl · FakeFly ·
FastFall · FastWeb · Flight · HoleSnap · IceSpeed · InventoryControl · Jesus · KeepSprint · LongJump · NoFall ·
NoJumpDelay · NoSlow · PacketFly · Parkour · SafeWalk · Scaffold · Speed · Sprint · Step · Velocity · Warp
</details>

<details>
<summary><b>Jugador</b> (22)</summary>

AirPlace · AnimalBreeder · AntiHunger · AutoFirework · AutoPearl · ChestStealer · ChorusControl · FastPlace ·
InventorySorter · MultiTask · NoGlitchBlocks · NoInteract · NoRotate · Nuker · QuickCraft · Replenish ·
RotationLock · SourceFill · SpeedMine · Swing · ToolSaver · XCarry
</details>

<details>
<summary><b>Visuales</b> (35)</summary>

ArrowTracers · AspectRatio · Atmosphere · BlockHighlight · BreakHighlight · Chams · CityESP · Crosshair ·
DamageNumbers · DeathEffects · EntityModifier · ESP · Freecam · FullBright · HandProgress · HoleESP ·
MotionBlur · NameTags · NoInterpolation · NoRender · PhaseESP · PopChams · Search · Shaders · Skybox · TextESP ·
Tracers · Trails · Trajectories · ViewClip · ViewModel · VoidESP · Waypoints · Xray · Zoom
</details>

<details>
<summary><b>Exploits</b> (15)</summary>

AntiVanish · Disabler · FastLatency · HitboxDesync · MouseFix · NewChunks · NoMineAnimation · Phase ·
PingSpoof · PortalGodMode · RaytraceBypass · Reach · RocketExtender · Timer · Trident
</details>

<details>
<summary><b>Varios</b> (22)</summary>

Announcer · AntiPacketKick · AutoDupe · AutoLogin · AutoReconnect · AutoRespawn · BetterChat · CombatInfo ·
ExtraTab · FakePlayer · FOVModifier · Handshake · InventoryCleaner · NameProtect · NoSoundLag · PosLogger ·
ShulkerInfo · Sounds · Spammer · SwapDebug · UnfocusedFPS · Welcomer
</details>

<details>
<summary><b>Núcleo</b> (13)</summary>

AntiCheat · Cape · ClickGui · Color · Commands · Font · Friend · HUD · Menu · Notifications · Renders ·
Rotations · RPC
</details>

---

## Estructura del proyecto

```
src/main/java/dev/project/anatorius/
├── modules/      todos los módulos, agrupados por categoría en modules/impl/
├── commands/     comandos de chat (.help, .bind, ...)
├── gui/          ClickGUI
├── hud/          elementos del HUD
├── managers/     configuración, amigos, rotaciones, objetivos, renderizado ...
├── mixins/       hooks en Minecraft
├── events/       bus de eventos
├── settings/     ajustes de los módulos
└── utils/        renderizado, matemáticas, red y otras utilidades
```

Añadir un módulo es una sola clase: extiende `Module`, anótala con `@RegisterModule` y la compilación
la registra automáticamente.

---

## Créditos

- **ArtyCi / DesBilalz**, los autores de Anatorius
- [Baritone](https://github.com/cabaletta/baritone) por el pathfinding (LGPL-3.0)
- [Orbit](https://github.com/MeteorDevelopment/orbit) de Meteor Development, la base del bus de eventos
- [discord-ipc](https://github.com/MeteorDevelopment/discord-ipc) por Discord Rich Presence

## Aviso legal

Este proyecto es para fines educativos. Muchos servidores prohíben los clientes utilitarios, así que
todo lo que hagas con él es responsabilidad tuya.

<div align="center">

**Licencia GPL-3.0**

</div>
