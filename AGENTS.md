# SurfCraft

A Fabric mod for Minecraft Java 26.3: CS:S surf ramps and CS:S surf movement. `MODLOG.md` is the journal
(versions, decisions, evidence, gotchas); read it before working and add to it when you learn something.

## Build, test, run
- JDK 25: `export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`
  (`/usr/bin/java` is the macOS stub and fails).
- `./gradlew build` builds and runs the JUnit tests and the server game tests; `./gradlew test` runs only the JUnit
  tests, `./gradlew runGameTest` only the game tests (report: `build/gametest/server-tests.xml`).
- `./gradlew runClientGameTest` opens a client window, builds a ramp showcase in a superflat world and saves
  screenshots to `build/gametest/screenshots/`. Game tests live in `src/gametest` (not in the mod jar).
  It also runs the surf tests in survival, single player (`surf`) and against an in-process dedicated server
  (`server`), which fail on any server correction; pick some with `-PclientTests=surf,server,showcase`. The
  window must be able to draw: with the display asleep macOS blocks the first frame (`caffeinate -u` wakes it).
- `./gradlew runClient` starts the dev client (offline account, `run/` folder).
- Physics reference replays need the user's own map geometry in `local-content/` (gitignored):
  `node tools/extract-kitsune-brushes.mjs`. Without it those cases skip; never commit map content.

## Source of truth
Minecraft 26.3 is unobfuscated (Mojang names, no mappings). Read the real code instead of recalling older
versions; APIs changed a lot in 1.21.x and 26.x. Decompiled sources live in the gitignored
`local-content/decomp/` (keep every file inside this folder, never in `~/`):
- `common/`, `client/`: Minecraft sources; `jar-common/`, `jar-client/`: vanilla data and asset JSON;
- `fabric-api/<module>/`: Fabric API 0.161.0+26.3 sources; `fabric-repo/`: Fabric's test mods.
Never copy decompiled code into this repo; describe behaviour in your own words.

## Layout
- `dev.afunk.surfcraft.physics`: the CS:S movement core, pure Java in Source units and axes (x, y, z-up),
  no Minecraft imports. `RampCell` is the shared ramp-cell geometry.
- Other packages: blocks, registration, Minecraft integration (`src/main`), client code (`src/client`).
- Mixins: `surfcraft.mixins.json` (common), `surfcraft.client.mixins.json` (client).

## Dev log (screenshots of the work)
The user wants screenshots at relevant moments (first render, fixes, test runs, charts of results) for a
montage. Add each with `.tools/venv/bin/python tools/devlog.py add <png> "Title" "One-line caption"`; it lands
in `devlog/shots/` and `devlog/index.html` (published at https://devlog-production-6292.up.railway.app by the
orchestrator with `tools/devlog.py deploy`). Look at a screenshot before adding it. Project-local tools live in
`.tools/` (gitignored): `.tools/venv/bin/um` (universal-modder CLI); set `MPLCONFIGDIR=$PWD/.tools/mpl` for
matplotlib. Keep every file inside this folder, never in `~/`.

## Rules
- Physics fidelity is the top priority. The surf repo (`/Users/funk/code/sandbox/surf`) and its CS:S
  recordings are the reference; do not loosen tolerances to make a regression pass.
- Skills for this repo are in `.claude/skills/` (universal-modder, MIT, pinned; plus obstacle-protocol).
