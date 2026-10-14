# Rain audio and connected chest source fix

Particle Rain's persisted rainVolume was 0.0. Its weather hook explicitly cancels the rain-sound path at that value. `AudioLiveAgent` restored the mod's default 0.2 on Minecraft's thread, saved through ConfigManager and retained an exact backup. Weather slider volume was already 1.0. The shared settings recipe also uses 0.2. Audible confirmation during natural rain remains pending.

Sound traces captured chest starts around 0.21 instead of 0.50 while their perceived source shifted 2–3 blocks, with few competing channels. The corrected V2 path probe recovered original sources through both getOriginal and getWrapped; the first probe's original-position fields were unreliable and are not used as source evidence.

A double chest emitted at `(26.5, 66.5, 54.0)`, on its two halves' boundary. Sound Physics counted the adjacent half as material between listener and sound. `ContainerSource` retains ordinary source-block equality and additionally excludes only reciprocal, facing-compatible LEFT/RIGHT parts of the same chest block. Walls, floors, unconnected and single containers remain occluding. Unsupported APIs fail back to ordinary occlusion. Reflection metadata is cached per class; immutable geometry metadata only.

`ContainerSourceTest` checks 146 pair-policy/equality combinations. A live loaded-world check verified both connected halves and refusal to exclude their grass-block floor. Sound Physics keeps its existing ray-hit storage fix, 320 rays / 12 bounces, reverb and attenuation settings. Subjective chest confirmation and the separate reported single/Ender-chest cases remain open.

One RaycastingHelper source-block comparison is changed, four original helper classes are added, and unrelated entries stay identical. The friend's archive is reconstructed from an author download, with source and upstream notices retained. All own helper/patch/probe code uses the accompanying MIT license. Decompiled inspection files and private traces are not distributed.

The bounded `Install-Reviewed-When-Closed.ps1` helper checks exact source/original hashes, backs up originals, rejects directory/file links and waits for the named Minecraft instance to close. It never stops the game. It replaces only the two reviewed JARs; the rain setting is already saved by its own mod. Requests and logs stay private. The first watcher encountered an unavailable Windows PowerShell hash cmdlet and made no changes; the corrected watcher uses native SHA256 and the working PowerShell runtime.
