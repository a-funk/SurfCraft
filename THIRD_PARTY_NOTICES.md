# Third-party notices and provenance

## Movement physics
`dev.afunk.surfcraft.physics` is a Java port of the CS:S movement implementation in the author's surf project
(the browser CS:S surf project distributed as OpenSurf, https://github.com/a-funk/OpenSurf). That implementation follows the publicly
documented movement structure in [Valve's Source SDK game movement](https://github.com/ValveSoftware/source-sdk-2013/blob/b8cfb12c0e083a2ef5b2f9f9b50f3902fa034474/src/game/shared/gamemovement.cpp)
and the SDK's brush trace, and checks CS:S-specific behaviour against recordings from a CS:S dedicated server
(build 11003710). SurfCraft replays some of those recordings in its tests (`src/test/resources/css-reference/`,
measured output only). A few behaviours (the quadrant ground check, the box-trace tie rule, velocity clipping) were
also compared with the behaviour of that server build. The SDK was consulted: this is **not** clean-room work.
The official Source 1 SDK license and its companion notices are retained verbatim in `LICENSES/` (and in the jar
under `META-INF/licenses/`); their inclusion is not a blanket license for this mod. As in the surf project, public
release of the code is gated on a provenance review of these routines. No game binaries, maps, models, textures or
sounds are included; map geometry used by local tests stays in the gitignored `local-content/`.

## Other material
- `.claude/skills/` (except `obstacle-protocol/`): skills from
  [universal-modder](https://github.com/rehan-remade/universal-modder) at commit
  `15d6f9d5fbd32de9b1884f29ddec3be9133bd912`, MIT License, copyright (c) 2026 Rehan and universal-modder
  contributors. The license text is in `.claude/skills/UNIVERSAL-MODDER-LICENSE`.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: from the Fabric example mod (CC0) / Gradle (Apache-2.0).
- Textures, the Karambit sprite and the mod icon are drawn by scripts in `tools/`.
- Minecraft and Fabric are used as the platform; nothing from them is redistributed in the mod jar.
