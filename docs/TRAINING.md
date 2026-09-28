# How the PvP brain was trained, and what it scored

This document covers every training run, including the ones that went wrong. The numbers are in `results/`, and each log line there is one JSON object.

The trained brain is `brain/pvp/checkpoint.pt` (phase 4, update 100, parameter hash `714de01be5637bf9…`). All scores below come from `results/final-evaluation.json`. They were measured with learning off, on seeds the brain never trained on, using its most likely action at every tick.

## Result

**The brain passes the gate.** It won 12 out of 12 first-to-3 sets against the expert bot (36 duels won, 1 lost). It also won 12 out of 12 against the "ace" bot, which never appeared in training.

Here is how it did in 500 fresh duels per opponent:

| Opponent | Fly brain win | Fly brain loss | Brain HP left (of 20) | Stage-1 brain win (for comparison) |
|---|---|---|---|---|
| dummy | 100% | 0% | 20.0 | – |
| rusher (spam clicks) | 100% | 0% | 17.4 | – |
| timer (waits for a charged sword) | 99.4% | 0.6% | 8.8 | 95.8% |
| strafer (strafes and W-taps) | 98.8% | 1.2% | 9.3 | 44.2% |
| **expert** (adds spacing, crits, tight aim) | **99.6%** | **0.4%** | 8.5 | 46.8% |
| ace (near-perfect aim, not used in training) | 100% | 0% | 9.3 | 74.8% |
| the stage-1 brain, playing its best moves (1,000 duels) | 51.0% | 49.0% | – | 50% by symmetry |

The last column is the stage-1 brain, which copied the expert. It ended up about as good as the expert bot. Practice took the fly circuit from about 47% to 99.6% against the expert.

**Limitation:** against its own stage-1 copy, the final brain only breaks even (51%). The scripted bots have weaknesses it learned to exploit. A neural opponent at expert level does not share those weaknesses. The trained brain is clearly better than every scripted fighter, but it is not clearly better than an equally strong copy of its own circuit.

## How it fights

These are from 200 duels against the expert, with learning off (`python -m pvp.inspect_play`):

- **Sprints and circles.** It sprints 95% of the time and holds forward 95% of the time. It strafes right 63% of the time and never left. The result is a circle-strafe that the bots' aim cannot track.
- **Waits for a charged sword.** It clicks on only 5% of ticks. Its hits average 3.33 damage, where a full-charge hit through iron armour does 3.78. It never jumps, so it doesn't go for crits.
- **Lands more hits than it takes.** It lands 6.0 hits per duel against the expert's 3.1. It usually lands the first hit, and it wins most trades because its sprint knockback pushes the opponent out of reach.

## The runs

| Run | Log | What happened |
|---|---|---|
| Stage 1: imitation | `imitate-1.jsonl` | DAgger from the expert over 300 iterations of 16,384 ticks (27 minutes). The brain matches the expert's controls closely, apart from turning, where the expert adds random aim noise that can't be learned. It finished at 41% win and 40% loss against the expert. |
| PPO 1 | `ppo-1-old-sim.jsonl` | Practice against the bot ladder plus 20% self-play. Within 25 updates it went from 40% to 98% against the expert by learning circle-strafing. I stopped the run after finding a simulator bug: both fighters could kill each other on the same tick, which caused 43% draws against the stage-1 brain. The fix is below. This brain was kept as `phase1.pt`. |
| PPO 2 | `ppo-2-sampled-rivals.jsonl` | Corrected simulator, and the stage-1 brain added as a permanent self-play opponent. It stuck around 50% against the stage-1 brain. The cause: frozen opponents were sampling their actions, and a sampling copy of a brain is much weaker than the same brain playing its best moves. It beat the sampled stage-1 brain 94% of the time but the best-move one only about 40%. |
| PPO 3 | `ppo-3-few-anchor-duels.jsonl` | Frozen opponents now play their best move 75% of the time. It still showed no progress, because league sampling favoured recent snapshots and the stage-1 brain got only about 5% of duels. |
| PPO 4 | `ppo-4.jsonl` | A fixed 60% of self-play duels go to the stage-1 brain. Against it, the brain moved from 50% up to 57% by update 75–100, then drifted between 48% and 56%. The saved brain is update 100 (4.1M ticks of practice in this run). I stopped the run at update 250 once that matchup had plateaued. |

Total training was about 3.5 hours on a 4-core CPU with no GPU: about 5M ticks of imitation and 10M ticks of practice (PPO, trial-and-error learning), roughly 5 hours of simulated sword fighting.

## Simulator fix: same-tick trades

The first simulator resolved both fighters' attacks against the same state, so two lethal swings on the same tick killed both fighters. That can't happen in Minecraft, because the server handles one attack packet before the other and a dead player's swing never lands. Now, when both swings in a tick would kill, a random one lands first and the other is cancelled. It's in `pvp/sim.py`, and the unit tests still pass.

## Not yet verified

- **The real game.** The mod's `/pvp/*` endpoints and `pvp/live.py` are written, and the conversion from game state to the brain's inputs is unit-tested to reproduce the simulator's senses exactly. However, the modified mod has not been compiled or played; the build machine could not reach Mojang's or Fabric's servers. In real Minecraft the sparring partner is an armed mob, not a player, and mobs fight differently. Expect some drop in performance compared with the simulator.
- **Human players.** The brain has never fought a person.
