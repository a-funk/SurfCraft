# Third-party notices and acknowledgements

SurfCraft is free and open source under the MIT License (`LICENSE`). It is, and must stay, free of charge.

## Valve's Source 1 SDK License (acknowledged)
`dev.afunk.surfcraft.physics` reimplements Counter-Strike: Source movement in Java. It ports the CS:S movement of the
author's own browser surf project (OpenSurf, https://github.com/a-funk/OpenSurf, by the same author; its source is not
public). The movement follows the publicly documented structure of Valve's Source SDK 2013 game movement
([gamemovement.cpp](https://github.com/ValveSoftware/source-sdk-2013/blob/b8cfb12c0e083a2ef5b2f9f9b50f3902fa034474/src/game/shared/gamemovement.cpp)).
The box trace (`SourceHull`) reimplements the behaviour of the Source engine's CM_ClipBoxToBrush, which is not part of
the SDK. Behaviour is checked against recordings of a CS:S dedicated server (build 11003710); ClipVelocity's float
order, the quadrant ground check's call conditions and the box trace's tie rule were read from that build's server
binaries. No Valve binary code is included. The SDK was consulted: this is not clean-room work.

The SDK-derived parts are `src/main/java/dev/afunk/surfcraft/physics/SourceMovement.java`, `SourceMove.java` and the
CheckStuck step in `TickDriver.java`. They are redistributed under Valve's **Source 1 SDK License**, Copyright (c)
Valve Corporation: redistributions of them, changed or not, must be free of charge and include
`LICENSES/Valve-Source-SDK-2013.txt`, `LICENSES/Valve-thirdpartylegalnotices.txt` and Valve's copyright notice (all
included verbatim, and in the mod jar under `META-INF/licenses/`). The MIT License's permission to sell does not
extend to them. Everything else in SurfCraft is MIT. Counter-Strike, Source and Valve are trademarks of Valve
Corporation; SurfCraft is not affiliated with or endorsed by Valve.

## Other material
- **CS:S recordings** (`src/test/resources/css-reference/`): measured positions and velocities from a CS:S
  dedicated server, captured by the author's surf project. Measurements only; no game files.
- **Minecraft:** NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT. Nothing from Minecraft or Fabric is redistributed in the mod jar. The
  screenshots and videos in `devlog/` show Minecraft and are shared under Mojang's usage guidelines; the MIT
  License does not cover Mojang's game content in them.
- **universal-modder skills** (`.claude/skills/`, except `obstacle-protocol/`): from
  [universal-modder](https://github.com/rehan-remade/universal-modder) at commit
  `15d6f9d5fbd32de9b1884f29ddec3be9133bd912`, MIT License, copyright (c) 2026 Rehan and universal-modder
  contributors; the license text is in `.claude/skills/UNIVERSAL-MODDER-LICENSE`.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: from the Fabric example mod (CC0) / Gradle (Apache-2.0).
- Textures, the Karambit sprite and the mod icon are drawn by scripts in `tools/` (MIT, like the rest).
