# Paper Channel Compatibility

Client-only addon for the installed Minecraft 26.2 / Fabric Networking API 6.3.4.

Paper limits each connection to 128 advertised plugin channels. The current complete pack exposes 130 gameplay receivers; Field Guide added five since the previous successful connection. This addon limits public `minecraft:register` advertisements to 127 unique channels on connections whose server brand identifies Paper, Purpur or Folia. One spare slot avoids Paper's limit check before duplicate-channel detection.

No server mobs, sound quality, camera behavior, textures, incoming receiver registrations or mod payload bodies are modified. Single-player and Fabric/vanilla/unknown-brand connections retain their original packet objects and complete channel lists. The budget is per connection and spans configuration/play; disconnects cannot affect another connection. Unregistration frees slots. Channels advertised by the server receive priority, followed by common plugin integrations such as voice chat and JourneyMap. Unadvertised Field Guide backend channels are omitted first, since Paper has no Fabric Field Guide backend by default. Client book interfaces remain installed.

If a Paper server offers unusual mod-compatible plugins whose channels are advertised late, the 127-channel budget may omit a backend advertisement. This limitation is logged explicitly. Server-side `-Dpaper.disableChannelLimit=true` removes the original Paper restriction, but this client addon is intended for servers the player does not control.

Original code is MIT licensed. No third-party classes are bundled. Build with `build.py` using the installed Java 25/game/API classpaths. Sources are included in the jar. Remove this jar to undo the compatibility behavior.
