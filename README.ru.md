<div align="center">

# Anatorius

**Утилитарный клиент Fabric для Minecraft 1.21.11. Теперь с открытым исходным кодом.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Modules](https://img.shields.io/badge/Modules-162-8A2BE2?style=flat-square)

[English](README.md) · [Polski](README.pl.md) · **Русский** · [Español](README.es.md) · [中文](README.zh.md)

</div>

---

> **Это неофициальный форк** репозитория [AnatoriusDev/Anatorius](https://github.com/AnatoriusDev/Anatorius).
> Код не изменён: форк добавляет только переведённую документацию и готовые JAR-файлы в разделе
> [Releases](https://github.com/piolunson/anatorius/releases).

## Скачать

Готовый мод прикреплён к [последнему релизу](https://github.com/piolunson/anatorius/releases/latest):
**`anatorius-2.2.4.jar`**. Файлы `-dev` и `-sources` можно игнорировать.

## Почему код открыт?

Сделать клиент теперь легко. Инструменты хорошие, знаний хватает везде, и любой желающий может
собрать свой за выходные. Держать Anatorius закрытым, за логином и проверками HWID, больше не
имеет смысла.

Так что вот он целиком. Система входа, проверки лицензии и обфускация убраны. Остался сам клиент:
все модули, GUI и HUD. Соберите его одной командой — и он ваш.

Обновления всё ещё могут выходить, но секретов здесь нет. Читайте, учитесь, форкайте, делайте
свой клиент.

---

## Сборка

Нужен только **JDK 21**. Всё остальное (Gradle, Minecraft, Fabric, Baritone) скачивается при первой
сборке.

```bash
git clone https://github.com/piolunson/anatorius.git
cd anatorius
./gradlew build        # Windows: gradlew.bat build
```

JAR-файл появится в `build/libs/anatorius-<версия>.jar`. Файлы `-dev` и `-sources` можно игнорировать.

## Установка

1. Установите [Fabric Loader](https://fabricmc.net/use/) для версии **1.21.11**.
2. Положите [Fabric API](https://modrinth.com/mod/fabric-api) и JAR-файл Anatorius в `.minecraft/mods`.
3. Запустите игру. Нажмите **правый Shift**, чтобы открыть ClickGUI.

Команды начинаются с точки: `.help`, `.toggle <модуль>`, `.bind <модуль> <клавиша>`, `.config`, `.friend` и другие.

---

## Возможности

<details>
<summary><b>Бой</b> (26)</summary>

AntiFeetTrap · AntiPearl · AntiPiston · AutoAnchor · AutoArmor · AutoBed · AutoBowRelease · AutoCrystal ·
AutoDrop · AutoLog · AutoMace · AutoMine · AutoPot · AutoTotem · AutoTrap · AutoWeb · AutoXp · Blocker ·
CombatPlacer · Criticals · HoleFill · KillAura · NoHitDelay · SelfBow · SelfFill · SelfTrap
</details>

<details>
<summary><b>Движение</b> (29)</summary>

Accelerate · AntiLevitation · AntiVoid · ArrowControls · AutoWalk · ElytraFly · EntityControl · FakeFly ·
FastFall · FastWeb · Flight · HoleSnap · IceSpeed · InventoryControl · Jesus · KeepSprint · LongJump · NoFall ·
NoJumpDelay · NoSlow · PacketFly · Parkour · SafeWalk · Scaffold · Speed · Sprint · Step · Velocity · Warp
</details>

<details>
<summary><b>Игрок</b> (22)</summary>

AirPlace · AnimalBreeder · AntiHunger · AutoFirework · AutoPearl · ChestStealer · ChorusControl · FastPlace ·
InventorySorter · MultiTask · NoGlitchBlocks · NoInteract · NoRotate · Nuker · QuickCraft · Replenish ·
RotationLock · SourceFill · SpeedMine · Swing · ToolSaver · XCarry
</details>

<details>
<summary><b>Визуал</b> (35)</summary>

ArrowTracers · AspectRatio · Atmosphere · BlockHighlight · BreakHighlight · Chams · CityESP · Crosshair ·
DamageNumbers · DeathEffects · EntityModifier · ESP · Freecam · FullBright · HandProgress · HoleESP ·
MotionBlur · NameTags · NoInterpolation · NoRender · PhaseESP · PopChams · Search · Shaders · Skybox · TextESP ·
Tracers · Trails · Trajectories · ViewClip · ViewModel · VoidESP · Waypoints · Xray · Zoom
</details>

<details>
<summary><b>Эксплойты</b> (15)</summary>

AntiVanish · Disabler · FastLatency · HitboxDesync · MouseFix · NewChunks · NoMineAnimation · Phase ·
PingSpoof · PortalGodMode · RaytraceBypass · Reach · RocketExtender · Timer · Trident
</details>

<details>
<summary><b>Разное</b> (22)</summary>

Announcer · AntiPacketKick · AutoDupe · AutoLogin · AutoReconnect · AutoRespawn · BetterChat · CombatInfo ·
ExtraTab · FakePlayer · FOVModifier · Handshake · InventoryCleaner · NameProtect · NoSoundLag · PosLogger ·
ShulkerInfo · Sounds · Spammer · SwapDebug · UnfocusedFPS · Welcomer
</details>

<details>
<summary><b>Ядро</b> (13)</summary>

AntiCheat · Cape · ClickGui · Color · Commands · Font · Friend · HUD · Menu · Notifications · Renders ·
Rotations · RPC
</details>

---

## Структура проекта

```
src/main/java/dev/project/anatorius/
├── modules/      все модули, сгруппированные по категориям в modules/impl/
├── commands/     чат-команды (.help, .bind, ...)
├── gui/          ClickGUI
├── hud/          элементы HUD
├── managers/     конфиг, друзья, повороты, цели, рендеринг ...
├── mixins/       хуки в Minecraft
├── events/       шина событий
├── settings/     настройки модулей
└── utils/        рендеринг, математика, сеть и другие помощники
```

Добавить модуль — это один класс: наследуйтесь от `Module`, поставьте аннотацию `@RegisterModule`,
и сборка зарегистрирует его автоматически.

---

## Благодарности

- **ArtyCi / DesBilalz**, авторы Anatorius
- [Baritone](https://github.com/cabaletta/baritone) за поиск пути (LGPL-3.0)
- [Orbit](https://github.com/MeteorDevelopment/orbit) от Meteor Development, основа шины событий
- [discord-ipc](https://github.com/MeteorDevelopment/discord-ipc) за Discord Rich Presence

## Отказ от ответственности

Проект создан в образовательных целях. Многие серверы запрещают утилитарные клиенты, поэтому всё,
что вы с ним сделаете, — ваша ответственность.

<div align="center">

**Лицензия GPL-3.0**

</div>
