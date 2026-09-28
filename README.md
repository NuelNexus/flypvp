# FlyPvP

A fruit fly's brain wiring, taught to sword fight in Minecraft.

This is the PvP version of [FlyBridge](https://github.com/swotstudio/FlyBridge), the project behind *I Put a Fly's Brain in Minecraft and Made It Farm*. It uses the same brain: 1,536 neurons and 42,921 signed connections taken from the FlyWire connectome of an adult fruit fly. FlyBridge taught that circuit to farm wheat. FlyPvP teaches it to win 1v1 sword duels with 1.9+ combat: attack cooldowns, crits, sprint knockback, W-taps, strafing and spacing.

## Result

The trained brain is `brain/pvp/checkpoint.pt`. With learning off, on fresh seeds, it won **99.6% of 500 duels against the expert bot** and passed the **12/12 gate**. It also won 100% against a held-out near-perfect-aim bot. Against the stage-1 brain that copied the expert, it breaks even (51%), which is the main limitation. Every run, including the failed ones, is in [`docs/TRAINING.md`](docs/TRAINING.md).

## What is real, and what is not

Here is what the project claims, and what it does not.

**Real:**

- **The wiring.** `dist/data/circuit.json` is FlyBridge's circuit, copied byte for byte. It holds 1,536 FlyWire FAFB v783 neurons from the smell pathway: receptor, local, projection, Kenyon, mushroom body output and lateral horn neurons. Training adds no connection, removes none and flips no sign.
- **The learning rule.** Only a gain on each existing connection is learned: `initial_weight * sign * 2 * sigmoid(gain)`, so each synapse stays between 0× and 2× its fly strength. A neuron-level leak and bias are learned too, along with the sensory encoder and the readout. This is the same rule FlyBridge uses.
- **Where signals go in and come out.** Combat facts are written onto the 256 olfactory receptor neurons. Activity passes through the fly's wiring four times per game tick. The controls are read only from the 96 mushroom body output neurons and the 288 lateral horn neurons. The brain keeps its activity from one tick to the next, which gives it a short memory of the fight.
- **The brain presses the controls.** It chooses forward, back or neither; left, right or neither; sprint; jump; when to click; and how far to turn (one of 11 yaw steps per tick). No script tells it when to swing or where to walk.

**Simplified:**

- **Most training ran in a simulator, not in Minecraft.** `pvp/sim.py` is a batched, headless 1v1 arena that follows vanilla 1.21.4's movement and combat constants: accelerations, drag, jump, the 12.5-tick diamond sword cooldown, charge scaling, crit and sprint-hit rules, 20-tick hurt immunity, knockback, iron armour reduction, and 3.0-block reach along the look ray. Unit tests hold it to vanilla values: walking 4.317 m/s, sprinting 5.612 m/s, a 1.252-block jump apex, and 3.78 damage for a charged sword hit on iron armour. It leaves out ping, shields, rods, bows, potions, hunger and blocks other than a flat floor and walls.
- **Pitch is aimed by a motor layer at the opponent's chest.** FlyBridge's motor controller does the same kind of job. Yaw belongs to the brain.
- **The opponents during training were scripted bots**, plus frozen copies of the brain itself.
- **It has not been run in the real game yet.** The mod endpoints and the live runner are written, and the runner's sense conversion is unit-tested against the simulator. However, the modified mod could not be compiled or played in the environment where this was built: it had no access to Mojang's or Fabric's servers. Test it with `./gradlew build` and `python -m pvp.live`, and expect the sim-to-game gap to cost some performance.

## Layout

```
pvp/sim.py        the 1.21.4 duel simulator (torch, thousands of duels at once)
pvp/bots.py       scripted fighters: dummy, rusher, timer, strafer, expert
pvp/model.py      PvPFly, the FlyWire circuit as a fighter
pvp/train.py      lesson 1 imitate (DAgger from the expert), lesson 2 ppo (practice vs bots + self-play)
pvp/evaluate.py   frozen ladder and the 12/12 check gate
pvp/live.py       run the brain in real Minecraft through the mod
pvp/test_pvp.py   physics, combat and live-conversion tests
mod/              FlyBridge 0.9.2 source + PvpArena/PvpLesson and /pvp/* endpoints (0.10.0-pvp)
brain/pvp/        trained checkpoints
results/          training logs (JSON lines) and the final evaluation
docs/TRAINING.md  how it was trained and what it scored
```

## The lessons

| # | Lesson | Command | What it teaches |
|---|---|---|---|
| 1 | Watch the expert | `python -m pvp.train imitate --out brain/pvp/imitate.pt` | copy the expert bot's controls (DAgger: the brain drives more and more, and the expert keeps labelling) |
| 2 | Practice | `python -m pvp.train ppo --init brain/pvp/imitate.pt --out brain/pvp/checkpoint.pt` | PPO against a mix of rusher, timer, strafer and expert bots and frozen earlier copies of itself; the reward is damage dealt minus damage taken, plus win or loss |

The gate follows FlyBridge's rule: a skill counts only when it passes **12 out of 12 frozen checks** on seeds it has never seen, with learning switched off. In FlyPvP, one check is a first-to-3 set against the expert bot.

```
python -m pvp.evaluate --checkpoint brain/pvp/checkpoint.pt          # win rates vs every bot level
python -m pvp.evaluate --checkpoint brain/pvp/checkpoint.pt --check  # the 12/12 gate
python -m unittest pvp.test_pvp
```

Training runs on a CPU; no GPU is needed. The fly's weights are applied as one dense matrix multiply per step. That is the same maths as FlyBridge's per-edge `index_add`, and about 10× faster.

## Running it in Minecraft

1. Build the mod: `cd mod && ./gradlew build`. This needs Java 21; the jar lands in `mod/build/libs`. Install it with Fabric Loader 0.19.5 and Fabric API 0.119.4+1.21.4 in a 1.21.4 profile whose game directory is `<repo>/minecraft/client`.
2. Create a world whose name starts with `Fly Colony School`. The arena is built at x=512, y=249, well away from spawn.
3. Install the learner with `pip install -r requirements.txt`, then run:

```
python -m pvp.live --checkpoint brain/pvp/checkpoint.pt --duels 5 --opponent zombie
```

Opponents are `zombie`, `husk`, `vindicator`, `piglin_brute` and `wither_skeleton`. Each is spawned in full iron armour with a diamond sword (the vindicator gets an iron axe). Singleplayer has no second human, so the sparring partner is an armed mob; mobs fight differently from players. Each duel writes to `minecraft/runs/pvp/<id>/`. F8 hands control back to you at any time.

New mod endpoints, all POST and loopback-only with the session token, like the rest of FlyBridge:

- `/pvp/reset {seed, opponent}` builds the arena, gears up both fighters and spawns the opponent.
- `/pvp/senses` returns positions, velocities, health, hurt timers, attack cooldown, and swing and crosshair state.
- `/pvp/evaluate` returns the mod's verdict: `win`, `loss`, `draw` or `running`.

A PvP reset also switches off natural regeneration and, if the test world is on Peaceful, sets it to Normal.

## Credits

This is a derivative of FlyBridge (MIT). FlyWire connectome: Dorkenwald et al., *Nature* 634 (2024), CC BY 4.0. See `CREDITS.md` and `LICENSE`. Minecraft is Mojang's; nothing from the game is included.
