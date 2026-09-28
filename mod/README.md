# FlyBridge, the mod

A client-side Fabric mod for Minecraft Java 1.21.4 that lets a local program observe the game, press the game's own keys and mouse buttons, step the client and the integrated server in lockstep, and build the practice arenas the lessons use. The top-level `README.md` explains how it fits with the learner.

## Requirements

- Minecraft Java Edition 1.21.4 (your own copy)
- Java 21
- Fabric Loader 0.19.5 and Fabric API 0.119.4+1.21.4

## Build

```
cd mod
.\gradlew build        (Windows)
./gradlew build        (macOS / Linux)
```

The first build downloads Gradle 8.12.1, Fabric Loom, the Yarn mappings and the game jar from their official sources. The result is `build\libs\flybridge-0.9.2.jar` (plus a sources jar). `.\gradlew test` runs the 22 JUnit test classes.

A prebuilt `releases\flybridge-0.9.2.jar` is included. It is the build that ran the finale; the source in `src\` is the same code.

## Install

Copy the jar and the Fabric API jar into the `mods` folder of a Fabric 1.21.4 profile, or run `..\scripts\install-mod.ps1`. The learner expects the profile's game directory to be `<repo>\minecraft\client`, because that is where it reads the session file from.

## How it behaves

- It only activates in a singleplayer world whose name starts with `Fly Colony`, and refuses a LAN session with other players.
- When such a world loads it starts an HTTP server on 127.0.0.1 on a free port and writes `config\flybridge-session.json` in the game directory: the address and a random token. Every request must carry the token. The file is per session; do not share it.
- `/start` takes control (`clockMode` `realtime` or `paired`, plus a target tick rate). `/stop` or **F8** releases it and restores your frame rate, VSync and tick settings.
- `/step` applies a set of held keys, mouse deltas and clicks for a number of ticks. `/vision` returns the rendered frame as a PNG (384 by 216). `/observe`, `/senses` and `/perception` return the player's state and the bounded world facts around the player.
- Each lesson has a reset endpoint (`/farm/reset`, `/seedfarm/reset`, `/seeds/reset`, `/wood/reset`, `/craft/reset`, `/equipment/reset`, `/site/reset`, `/pipeline/reset`, `/school/reset`, `/arena/reset`) that builds a fresh practice area from a seed. The same endpoints, asked to evaluate, count what the player did (tilled, planted, watered, harvested, stored, damaged) from the server's authoritative state.
- `/human/start` and `/human/stop` record your own inputs and the matching observations, which is how the teacher demonstrations for some lessons were captured.

The mod never places or removes blocks for the player, never gives items outside an arena reset, and never moves the player outside the game's own physics.

## Source map

- `FlyBridge.java`: the server, the session file, control lifecycle, the endpoint switch.
- `PlayerControls.java`, `HeldInputs.java`, `HumanInputs.java`, `BoundedTurn.java`: how inputs are applied and limited.
- `StepGate.java`, `StepRequest.java`, `TickCommand.java` and the mixins: paired client/server stepping.
- `ClientObservation.java`, `WorldObservation.java`, `VisualObservation.java`, `WorldScene.java`: what the learner sees.
- `*Arena.java`, `*Lesson.java`, `FarmBed.java`, `FarmLedger.java`, `Homestead*.java`, `SiteLedger.java`: the practice areas and their evaluators.
- `src\test\java`: the JUnit tests.
