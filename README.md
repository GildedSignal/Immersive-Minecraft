# Immersive Minecraft

Minecraft Java **26.2**, Fabric **0.19.5**, with a Windows launcher, two modpack profiles, custom client addons and compatibility patches.

This repository contains the launcher and preparation engine source, the exact downloadable modpack manifests and settings recipes, editable customizations, licensing notices, and tests. Release **2026.10.14.2** adds local Field Guide discovery, chest audio and shutdown fixes, Enderman atlas repair and a Streams Reflowing update, alongside the workstation UI and Interactive Stuff callback repairs.

- [Download and player guide](https://immersive-minecraft-friends.fr-brainforge.chatgpt.site)
- [Browse every included mod](https://immersive-minecraft-friends.fr-brainforge.chatgpt.site/mods/)
- [Windows launcher source](tools/distribution/windows)
- [Installation/update engine](tools/distribution/launcher)
- [Full modpack manifest](tools/distribution/release/pack/manifest.json)
- [Vanilla protection manifest](tools/distribution/release/pack/manifest-vanilla.json)
- [Editable addon and patch sources](customizations)
- [Performance patch sources](performance-patches)
- [Latest compatibility repairs and validation details](tools/three-bug-fixes/README.md)

The full profile includes gameplay/content mods. Vanilla protection uses a separate save directory and a subset intended for vanilla worlds/servers. Field Guide creature scanning and personal journals now work locally when Field Guide is absent from the server. Local discoveries and notes are private and persist per player/server; compatible Field Guide servers keep upstream synchronization. Custom server loot tables, datapacks and rewards still depend on server-provided information. Neither profile guarantees a particular FPS.

The launcher downloads Prism Launcher unchanged, then downloads third-party mods and packs from their recorded author URLs with hash verification. Local archive recipes reconstruct the reviewed customizations. Microsoft/Minecraft sign-in requires each player's own Java Edition account. Worlds, account data, saved servers, credentials and private gameplay logs are excluded from this repository.

## Build the Windows launcher

Install Python 3 and the .NET 8 SDK on Windows. From the repository root:

```powershell
python tools/distribution/bundle-release.py
dotnet publish tools/distribution/windows/FriendLauncher.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:DebugType=None -p:DebugSymbols=false -o artifacts/windows
```

This builds `artifacts/windows/ImmersiveMinecraftLauncher.exe` with the reviewed pack embedded. The author publishing private key is not included. Source builds can install the embedded snapshot; public automatic updates use the public trust information already present in the release. To change a release version, review/build/validate its source and package before publishing.

Engine checks can be run with:

```powershell
dotnet run --project tools/distribution/launcher/FriendPack.csproj -- --self-test
```

The Windows source also includes native tests for launch detection, window suppression, menu reopening, updates, world copy/export/removal and secrets storage. Addon builds require Java 25 plus the installed Minecraft/Fabric/mod APIs; see the corresponding source README and build helpers. The copied investigation helpers retain their original author-workstation defaults, so adjust input paths to your local installation. They are not needed to build the launcher.

## Latest repairs

The 26.2 Recolourful text shader uses existing texture corner markers, removing reproduced repeating/triangular animation strips while preserving animations. All 68 marker textures were audited with 136 native batching/tooltip captures. Interactive Stuff callbacks execute on Minecraft's thread; the actual held-sculk script now plays its clicking sound from a worker callback without the unsafe-random exception. Field Guide's local fallback loads 177 entries and supports native scans, entry notes and journals with private persistence.

The repairs pass 36 dispatch/storage assertions. Native single/double/Ender chest tests pass 21 clear paths and 21 wall controls, retaining obstruction muffling and the 320-ray/12-bounce reverb settings. Sound Physics worker pools shut down on client exit. Enderman body UVs now match the active Fresh Extensions atlas; native before/after corpse captures confirm the missing body surfaces are opaque. A 25-pig death test reaches the existing 16-corpse cap with vanilla overflow and zero budget-helper failures. Streams Reflowing 2.14.4 includes its author's unsafe-terrain-read fixes. Standalone launcher checks verify 212 full / 180 protected artifacts and preserved player preferences. Natural audible comparisons, live launcher feedback, server tick evidence and the 180 FPS target remain pending. The linked public download site can lag this repository until the author publishes the snapshot.

## Staged improvements

Source now includes arrow-key elytra steering with smooth Leawind follow, camera/player banking and collision-limited pullback. The native equipped-flight test and 174 policy/motion checks pass. Launcher preparation skips unchanged settings/options replacements and repeated source-mod scanning; installer regression suites pass. These improvements are **not yet installed or included in the 2026.10.14.2 payload**. Startup-to-world timing and a new standalone release remain pending. See [validation and limits](docs/flight-and-startup-validation.md) and [TODO](TODO.md).

## Licensing

Original launcher/addon/helper code is MIT where marked. Third-party code, artwork, libraries and shader modifications retain their own licenses and attribution under `tools/distribution/release/pack/notices` and the matching source folders. The root MIT license does not relicense third-party material. Recolourful assets are fetched from their author; only the original one-line correction and archive reconstruction recipe are included here. This project is unofficial and is not affiliated with Mojang, Microsoft, Fabric, Modrinth or Prism Launcher.
