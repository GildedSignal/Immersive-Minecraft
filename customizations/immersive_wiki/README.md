# Immersive Wiki Fieldbook

Client-only Minecraft 26.2 / Fabric / Java 25 addon. It requires the installed Fabric API. No server plugin, account or browser is required.

Press **F8**, or use **/fieldbook**. **/fieldbook Elytra** opens a subject directly. The key is rebindable in Minecraft Controls and does not replace existing bindings.

The native two-page book offers an encyclopedia search, chapter index, green internal article/section links, ribbon bookmarks, Back/history, last-reading-page continuation and a targeted block/entity/held-item lookup. Arrow keys, Page Up/Down, the mouse wheel and page buttons turn leaves. Escape closes the book. It pauses a local singleplayer world normally; multiplayer continues while reading.

Minecraft Wiki's MediaWiki API supplies actual articles, not generated summaries. jsoup parses headings, emphasis, lists, infobox values and labelled table rows, including rowspan/colspan. Header item illustrations use Minecraft's current resource-pack item models when a matching item exists. This first edition does not display web gallery pictures, diagrams or videos: captions and image-only ingredient names remain readable. Table captions are formatted distinctly but do not clutter the chapter index.

Downloads, HTML parsing and cache/settings writes run on a single bounded background worker. Searches run only on submission. Native font advances are snapshotted on article/size changes; a separate bounded worker paginates longer articles and prepares immutable text runs. No network, disk I/O or paragraph wrapping occurs every frame. Native testing of the Elytra article at a small GUI size measured 3.2–5.1 ms on the main thread across two runs versus 12.1 ms before offloading; this is a book-opening measurement, not a gameplay FPS improvement. A bounded cache keeps up to 64 article/alias files and 32 MB. Previously read articles open offline; Update refreshes an edition explicitly. Errors appear as native book text, and stale requests/layout jobs cannot replace a newly selected view.

`config/immersive-wiki.json` contains language (`en` or `fr`), volume, four sound-event names, bookmarks and the last reading position. `cache/immersive-wiki` contains article copies. Default sounds are quiet resource-pack-aware book/chiseled-bookshelf events at 35% volume. The dedicated events below can be replaced by an audio resource pack later:

- `immersive_wiki:book_open`
- `immersive_wiki:page_turn`
- `immersive_wiki:book_close`
- `immersive_wiki:bookmark`

ElevenLabs generation is not integrated yet. A later authoring step can create OGG assets without embedding an API key in a mod, pack or friend download. No speech/narration generation has been added.

Interface code is original MIT-licensed code; its sources are embedded in the jar and available separately. jsoup 1.23.2 is MIT, with its license and upstream source link inside the nested library. Download hash is verified against the immutable Maven artifact checksum. Wiki text remains Minecraft Wiki contributors' CC BY-NC-SA 3.0 content; source/revision/attribution/license are included in each article's final chapter. Cached wiki text is separate from the interface's license and is not included in the shareable mod jar.

Validation: parser/pagination tests, four real articles, live search, memory/disk cache checks and an isolated hidden native Minecraft render test. The runtime harness uses a dummy offline account, its own game folder, and no player saves or keyboard/mouse input. See `validation.json`, `live-validation.txt`, and `runtime-test/runtime-validation.txt` for current results. Testing this exact addon with the complete modpack in an actual world remains separate from the isolated checks.

Build with `build.py` using the exact installed 26.2 classes, Fabric modules and Java 25. Development reference classes, traces, game directories and test-only mods are excluded from the deliverable jar.
