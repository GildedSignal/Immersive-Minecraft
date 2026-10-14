# Local customization sources

The five directories contain maintainable source for this pack's existing EMF JIT, Iris translucent order, ETF, Immersive Storms and Litematica customizations. The earlier developer's complete preferred sources were unavailable: these files were recovered with CFR 0.152 and then adopted and repaired to compile against the actual Minecraft 26.2 API. They are explicitly recovered/adopted source, rather than a claim to be the original author's source files. Unchanged upstream projects are included as pinned source ZIPs with their licenses and provenance. The release keeps the installed binary behavior; these rebuilds are available for editing.

All five patch sets compiled with Java 25. For the rewritten EMF splitter, 96 differential cases compare the existing binary and adopted source, including small/large methods, branches and fallback behavior: transformed bytes and result metadata match. The full release also passed actual Minecraft startup with the original installed patches.

To rebuild, install Python 3 and a Java 25 JDK, then use your installed friend pack's mods and Prism libraries:

    python build.py --jdk "C:/path/to/jdk-25" --minecraft-jar "C:/path/to/minecraft-26.2.jar" --mods "C:/path/to/instance/.minecraft/mods" --libraries "C:/path/to/Prism/data/libraries" --output build

The compiler classpath includes nested Fabric dependency jars. The script downloads three public compile-only libraries from Maven Central and Modrinth. It creates a compile-only equivalent of ETF's declared access widener for LivingEntity.lastEquipmentItems; that jar must never be installed as a game mod. Optional --classpath-file supplies additional launcher dependencies. Output jars start from your installed addon and replace compiled patch classes, retaining metadata and unchanged upstream files.

For full upstream rebuilds, unpack the corresponding pinned upstream-source-pinned.zip, overlay the relevant edited source files, and use that project's included Gradle wrapper/build instructions. Local addon metadata and mixin JSON files accompany their source. Retained LGPL/GPL licenses apply; original names, authors and license notices remain in the original jars and the release's notices folder.

Source/build archives contain no Minecraft client jar, player worlds, article caches, login files or API credentials.
