# Compatibility validation — 2026.10.14.2

The Recolourful correction is in the shared 26.2 text shader. All 68 bitmap UI textures using encoded corner markers were audited: each has four valid red-channel markers. Native probes rendered all 68 cases with and without preceding text/tooltip batching (136 captures). This covers the 11 workstation animation elements and barrel, Ender Chest, horse, minecart, boat, shulker and villager metadata/overlay variants. Metadata glyphs intentionally hidden outside their corresponding screen are covered by the marker audit; their full gameplay menus were not individually opened. The same shader correction applies to all these providers. GUI sprites using the separate position texture shader already enable the red-channel path and are unchanged.

The three reported screens reproduced repeating/triangular strips before the correction and removed them afterward. Enchanting, cartography, dropper/dispenser and smithing animation probes also render without those strips. Artwork, animation timing and every other pack archive entry are unchanged. This validates the identified corner-selection bug, not every possible UI problem.

| Anvil before | Anvil after |
| --- | --- |
| ![Before](compatibility/before/anvil-tooltip.png) | ![After](compatibility/after/anvil-tooltip.png) |

| Stonecutter before | Stonecutter after |
| --- | --- |
| ![Before](compatibility/before/stonecutter-tooltip.png) | ![After](compatibility/after/stonecutter-tooltip.png) |

Field Guide's native Scholar journal accepts keyboard input, saves locally on unsupported servers and retains notes after world unload/load and reopening. Notes are scoped to the player and server and excluded from sharing. Server-supported discovery and journal synchronization retain the original code path.

![Persisted local Field Guide notes](compatibility/field-guide-notes.png)

There are 36 dispatch/journal/discovery storage assertions. The local fallback loads six categories and 177 entries, completes a native pig scan, stores an entry description and seen status, and retains them after unload/load with zero outbound Field Guide packets. This is tested by forcing unsupported-server capability in an isolated client; a compatible server's original integration is selected when its scan channel is advertised. Custom server datapacks, loot tables and rewards cannot be inferred locally.

An actual held-sculk script receives a worker-thread sound callback and plays clicking on Minecraft's thread without the unsafe-random exception. Sound Physics' worker pools now use its existing shutdown method on exit; copied-world test processes terminate without the previous lingering-worker watchdog.

Native `playMuffled` checks cover seven listener positions for each single, double and Ender chest: 21 clear-path results use the actual source distance and unoccluded gain; 21 stone-wall controls retain the original obstructed path. The prior reciprocal double-chest source exclusion remains intact. Reverb and 320 rays / 12 bounces remain configured. Natural audible and overlapping-playback confirmation is still pending.

Enderman native before/after captures reproduce missing body surfaces with the old atlas and show an opaque body after 168 guarded UV corner remaps, with zero helper failures. The test freezes the corpse through the physics world's queue for inspection; production corpse motion is unchanged. Eyes and nonmatching models/textures are excluded. A 25-pig death test peaks at the configured 16 ordinary corpses (15 new admissions alongside the existing test corpse), with ten vanilla overflow deaths and zero budget-helper failures.

[Streams Reflowing 2.14.4](https://modrinth.com/mod/streams-reflowing/version/2AFr1CZW) includes the author's fixes for unsafe terrain reads, generation stalls and C2ME startup. The update loads in the copied world without those warnings. Existing terrain is retained; these functional runs are not a controlled terrain-generation or FPS benchmark.

The standalone release verifies 212 full / 180 protected artifact hashes, package extraction and repeated-update preference preservation. Protected native startup, resource order and wiki navigation/audio pass with all 31 chosen packs; the earlier UI release also passed full startup with all 33 chosen packs. Latest world tests load the full test pack with voice chat omitted. Installation in both closed local profiles preserves options, all 425/395 existing configuration files and the original world's file hashes. Private copied-world screenshots, saves and logs are excluded from this repository. Live launcher feedback, natural rain/chest listening, server tick evidence and 180 FPS remain pending.
