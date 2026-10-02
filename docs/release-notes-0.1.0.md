First release of SurfCraft: Counter-Strike: Source surf in Minecraft 26.3. Craft surf ramps, build courses with a Karambit, and surf them with CS:S movement checked against a real CS:S server.

- **Players:** install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or newer for Minecraft 26.3. Put `surfcraft-0.1.0.jar` and [Fabric API](https://modrinth.com/mod/fabric-api) 0.161.0+26.3 or newer in your `mods` folder and play the Fabric profile.
- **Servers:** put the same two jars in the Fabric server's `mods` folder. Players need SurfCraft installed to join.

Requirements: Minecraft Java Edition 26.3, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3, Java 25 (the launcher includes it; servers need it installed).

What's in it:
- **Surf Ramp** (51°) and **Steep Surf Ramp** (63°), crafted from smooth stone. Blocks join into one smooth slope of any size.
- **Karambit**, the surf builder: copy a piece of ramp, extend a ramp past its end, or place the piece anywhere. A fresh knife places a classic two-sided 51° ramp.
- **CS:S surf physics** near ramps: air strafing (sv_airaccelerate 150), ramp clipping, auto bunny hop, 66.67 Hz movement ticks. Away from ramps, movement stays vanilla.
- **Speedometer** in units/s while you surf.
- **Servers** keep vanilla's movement checks, adapted for players surfing near ramps (a per-packet speed budget, CS:S airtime not counted as flying, exact ramp collision), so packet bursts from hitches or stalls don't rubber-band surfers.

Recipes, controls and surfing tips are in the [README](https://github.com/a-funk/SurfCraft#readme), changes in [CHANGELOG.md](https://github.com/a-funk/SurfCraft/blob/v0.1.0/CHANGELOG.md). Tested in survival, in single player and on an in-process dedicated server over a local connection (up to 2200 u/s, no server corrections; real-network latency untested). The movement replays 23 recordings from a real CS:S server to within 0.007 units.

SHA-256
- surfcraft-0.1.0.jar `dae3aa0653e4eef176a1f9e8f8cc356e77a4f1e548e3880658d02a6fb3fcf177`

Free and open source under the MIT License, except the routines derived from Valve's Source SDK 2013 game movement (SourceMovement, SourceMove, TickDriver's CheckStuck), which are redistributed free of charge under Valve's Source 1 SDK License. All license texts and notices are in NOTICES.txt (and inside the jar).

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT. Not affiliated with or endorsed by Valve.

Dev log: https://devlog-production-6292.up.railway.app · Montage: https://devlog-production-6292.up.railway.app/montage.mp4
