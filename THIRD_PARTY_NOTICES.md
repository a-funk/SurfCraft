# Third-party notices and acknowledgements

SurfCraft is free and open source under the MIT License (`LICENSE`). It is, and must stay, free of charge.

## Valve's Source 1 SDK License (acknowledged)
`dev.afunk.surfcraft.physics` reimplements Counter-Strike: Source movement in Java. It ports the CS:S movement of
the author's browser surf project (OpenSurf, https://github.com/a-funk/OpenSurf), which follows the publicly
documented structure of [Valve's Source SDK 2013 game movement](https://github.com/ValveSoftware/source-sdk-2013/blob/b8cfb12c0e083a2ef5b2f9f9b50f3902fa034474/src/game/shared/gamemovement.cpp)
and the SDK's brush trace. Its behaviour is checked against recordings of a CS:S dedicated server (build
11003710), and a few details (the quadrant ground check, the box-trace tie rule, velocity clipping) were matched to
that server build. The SDK was consulted: this is not clean-room work.

To the extent any part of SurfCraft is a modification of the Source 1 SDK, that part is distributed free of charge
under Valve's **Source 1 SDK License**, Copyright (c) Valve Corporation, whose full text and companion notices are
included verbatim: `LICENSES/Valve-Source-SDK-2013.txt` and `LICENSES/Valve-thirdpartylegalnotices.txt` (also in the
mod jar under `META-INF/licenses/`). Valve's license requires such distributions to be free of charge and to carry
those files and its copyright notice. Counter-Strike, Source and Valve are trademarks of Valve Corporation;
SurfCraft is not affiliated with or endorsed by Valve.

## Other material
- **CS:S recordings** (`src/test/resources/css-reference/`): measured positions and velocities from a CS:S
  dedicated server, captured by the author's surf project. Measurements only; no game files.
- **Minecraft:** SurfCraft is a mod for Minecraft, not an official Minecraft product, and is not approved by or
  associated with Mojang or Microsoft. Nothing from Minecraft or Fabric is redistributed in the mod jar. The
  screenshots and videos in `devlog/` show Minecraft and are shared under Mojang's usage guidelines; the MIT
  License does not cover Mojang's game content in them.
- **universal-modder skills** (`.claude/skills/`, except `obstacle-protocol/`): from
  [universal-modder](https://github.com/rehan-remade/universal-modder) at commit
  `15d6f9d5fbd32de9b1884f29ddec3be9133bd912`, MIT License, copyright (c) 2026 Rehan and universal-modder
  contributors; the license text is in `.claude/skills/UNIVERSAL-MODDER-LICENSE`.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: from the Fabric example mod (CC0) / Gradle (Apache-2.0).
- Textures, the Karambit sprite and the mod icon are drawn by scripts in `tools/` (MIT, like the rest).
