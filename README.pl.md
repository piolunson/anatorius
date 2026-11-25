<div align="center">

# Anatorius

**Klient użytkowy Fabric dla Minecrafta 1.21.11. Teraz open source.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Modules](https://img.shields.io/badge/Modules-162-8A2BE2?style=flat-square)

[English](README.md) · **Polski** · [Русский](README.ru.md) · [Español](README.es.md) · [中文](README.zh.md)

</div>

---

> **To nieoficjalny fork** repozytorium [AnatoriusDev/Anatorius](https://github.com/AnatoriusDev/Anatorius).
> Kod pozostał bez zmian: ten fork dodaje wyłącznie przetłumaczoną dokumentację oraz gotowe pliki JAR
> udostępniane w zakładce [Releases](https://github.com/piolunson/anatorius/releases).

## Pobieranie

Gotowy do użycia mod znajdziesz w [najnowszym wydaniu](https://github.com/piolunson/anatorius/releases/latest):
**`anatorius-2.2.4.jar`**. Pliki `-dev` i `-sources` możesz pominąć.

## Dlaczego to jest publiczne?

Zrobienie klienta jest teraz łatwe. Narzędzia są dobre, wiedza jest wszędzie, a każdy, kto chce
mieć taki klient, może go złożyć w weekend. Trzymanie Anatoriusa zamkniętego, za logowaniem i
kontrolą HWID, nie ma już sensu.

Więc proszę bardzo — całość. System logowania, sprawdzanie licencji i obfuskacja zniknęły. Został
sam klient: każdy moduł, GUI i HUD. Zbuduj go jedną komendą i jest twój.

Aktualizacje mogą się jeszcze pojawiać, ale nie ma tu nic tajnego. Czytaj, ucz się, forkuj, twórz
własne wersje.

---

## Budowanie

Potrzebujesz tylko **JDK 21**. Wszystko inne (Gradle, Minecraft, Fabric, Baritone) jest pobierane
przy pierwszej kompilacji.

```bash
git clone https://github.com/piolunson/anatorius.git
cd anatorius
./gradlew build        # Windows: gradlew.bat build
```

Plik JAR znajdziesz w `build/libs/anatorius-<wersja>.jar`. Pliki `-dev` i `-sources` możesz pominąć.

## Instalacja

1. Zainstaluj [Fabric Loader](https://fabricmc.net/use/) dla wersji **1.21.11**.
2. Umieść [Fabric API](https://modrinth.com/mod/fabric-api) oraz plik JAR Anatoriusa w `.minecraft/mods`.
3. Uruchom grę. Naciśnij **prawy Shift**, aby otworzyć ClickGUI.

Komendy zaczynają się od kropki: `.help`, `.toggle <moduł>`, `.bind <moduł> <klawisz>`, `.config`, `.friend` i inne.

---

## Funkcje

<details>
<summary><b>Walka</b> (26)</summary>

AntiFeetTrap · AntiPearl · AntiPiston · AutoAnchor · AutoArmor · AutoBed · AutoBowRelease · AutoCrystal ·
AutoDrop · AutoLog · AutoMace · AutoMine · AutoPot · AutoTotem · AutoTrap · AutoWeb · AutoXp · Blocker ·
CombatPlacer · Criticals · HoleFill · KillAura · NoHitDelay · SelfBow · SelfFill · SelfTrap
</details>

<details>
<summary><b>Ruch</b> (29)</summary>

Accelerate · AntiLevitation · AntiVoid · ArrowControls · AutoWalk · ElytraFly · EntityControl · FakeFly ·
FastFall · FastWeb · Flight · HoleSnap · IceSpeed · InventoryControl · Jesus · KeepSprint · LongJump · NoFall ·
NoJumpDelay · NoSlow · PacketFly · Parkour · SafeWalk · Scaffold · Speed · Sprint · Step · Velocity · Warp
</details>

<details>
<summary><b>Gracz</b> (22)</summary>

AirPlace · AnimalBreeder · AntiHunger · AutoFirework · AutoPearl · ChestStealer · ChorusControl · FastPlace ·
InventorySorter · MultiTask · NoGlitchBlocks · NoInteract · NoRotate · Nuker · QuickCraft · Replenish ·
RotationLock · SourceFill · SpeedMine · Swing · ToolSaver · XCarry
</details>

<details>
<summary><b>Wizualia</b> (35)</summary>

ArrowTracers · AspectRatio · Atmosphere · BlockHighlight · BreakHighlight · Chams · CityESP · Crosshair ·
DamageNumbers · DeathEffects · EntityModifier · ESP · Freecam · FullBright · HandProgress · HoleESP ·
MotionBlur · NameTags · NoInterpolation · NoRender · PhaseESP · PopChams · Search · Shaders · Skybox · TextESP ·
Tracers · Trails · Trajectories · ViewClip · ViewModel · VoidESP · Waypoints · Xray · Zoom
</details>

<details>
<summary><b>Exploity</b> (15)</summary>

AntiVanish · Disabler · FastLatency · HitboxDesync · MouseFix · NewChunks · NoMineAnimation · Phase ·
PingSpoof · PortalGodMode · RaytraceBypass · Reach · RocketExtender · Timer · Trident
</details>

<details>
<summary><b>Różne</b> (22)</summary>

Announcer · AntiPacketKick · AutoDupe · AutoLogin · AutoReconnect · AutoRespawn · BetterChat · CombatInfo ·
ExtraTab · FakePlayer · FOVModifier · Handshake · InventoryCleaner · NameProtect · NoSoundLag · PosLogger ·
ShulkerInfo · Sounds · Spammer · SwapDebug · UnfocusedFPS · Welcomer
</details>

<details>
<summary><b>Rdzeń</b> (13)</summary>

AntiCheat · Cape · ClickGui · Color · Commands · Font · Friend · HUD · Menu · Notifications · Renders ·
Rotations · RPC
</details>

---

## Struktura projektu

```
src/main/java/dev/project/anatorius/
├── modules/      wszystkie moduły, pogrupowane według kategorii w modules/impl/
├── commands/     komendy czatu (.help, .bind, ...)
├── gui/          ClickGUI
├── hud/          elementy HUD
├── managers/     konfiguracja, znajomi, rotacje, cele, renderowanie ...
├── mixins/       hooki do Minecrafta
├── events/       event bus
├── settings/     ustawienia modułów
└── utils/        renderowanie, matematyka, sieć i inne pomocniki
```

Dodanie modułu to jedna klasa: rozszerz `Module`, oznacz ją adnotacją `@RegisterModule`, a build
zarejestruje ją automatycznie.

---

## Podziękowania

- **ArtyCi / DesBilalz**, autorzy Anatoriusa
- [Baritone](https://github.com/cabaletta/baritone) za pathfinding (LGPL-3.0)
- [Orbit](https://github.com/MeteorDevelopment/orbit) od Meteor Development, podstawa event busa
- [discord-ipc](https://github.com/MeteorDevelopment/discord-ipc) za Discord Rich Presence

## Zastrzeżenie

Ten projekt służy celom edukacyjnym. Wiele serwerów zabrania używania klientów użytkowych, więc
wszystko, co z nim zrobisz, jest na twoją odpowiedzialność.

<div align="center">

**Licencja GPL-3.0**

</div>
