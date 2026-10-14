# Immersive Minecraft

Minecraft Java **26.2**, Fabric **0.19.5**, with a Windows launcher, two modpack profiles, custom client addons and compatibility patches.

This repository contains the launcher and preparation engine source, the exact downloadable modpack manifests and settings recipes, editable customizations, licensing notices, and tests. Release **2026.10.14.1** adds workstation UI, Interactive Stuff sound-thread and Field Guide journal repairs.

- [Download and player guide](https://immersive-minecraft-friends.fr-brainforge.chatgpt.site)
- [Browse every included mod](https://immersive-minecraft-friends.fr-brainforge.chatgpt.site/mods/)
- [Windows launcher source](tools/distribution/windows)
- [Installation/update engine](tools/distribution/launcher)
- [Full modpack manifest](tools/distribution/release/pack/manifest.json)
- [Vanilla protection manifest](tools/distribution/release/pack/manifest-vanilla.json)
- [Editable addon and patch sources](customizations)
- [Performance patch sources](performance-patches)
- [Latest compatibility repairs](tools/three-bug-fixes/README.md)
- [Expanded UI coverage, comparisons and validation](docs/compatibility-validation.md)

The full profile includes gameplay/content mods. Vanilla protection uses a separate save directory and a subset intended for vanilla worlds/servers. Discovery features such as Field Guide creature scanning need their server mod. On servers without Field Guide, the compatibility addon provides a personal journal saved on the player's computer. Neither profile guarantees a particular FPS.

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

The 26.2 Recolourful text shader now uses the existing texture corner markers, removing reproduced repeating/triangular animation strips on Ender Chest, anvil and stonecutter probes while preserving animations. Interactive Stuff's sound callbacks are dispatched on Minecraft's thread, preserving C2ME's safety checks and the binding metadata cache. Field Guide opens an editable local journal when the server does not support its journal protocol; notes are scoped per player/server and backed up locally.

The repairs pass 22 sound-dispatch/journal-persistence assertions and native UI/journal checks with Faithful, Iris, Sodium, ImmediatelyFast and Scholar. A sculk-sensor gameplay confirmation remains pending. The separately recorded shutdown-watchdog issue is not claimed resolved by the sound callback fix. The linked public download site can lag this repository until the author publishes the new snapshot.

## Licensing

Original launcher/addon/helper code is MIT where marked. Third-party code, artwork, libraries and shader modifications retain their own licenses and attribution under `tools/distribution/release/pack/notices` and the matching source folders. The root MIT license does not relicense third-party material. Recolourful assets are fetched from their author; only the original one-line correction and archive reconstruction recipe are included here. This project is unofficial and is not affiliated with Mojang, Microsoft, Fabric, Modrinth or Prism Launcher.
