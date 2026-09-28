# Credits

## The fly brain

- **FlyWire connectome.** Dorkenwald, S., Matsliah, A., Sterling, A.R. et al. Neuronal wiring diagram of an adult brain. *Nature* 634, 124-138 (2024). https://www.nature.com/articles/s41586-024-07558-y
  Data: FlyWire Codex, FAFB v783. https://codex.flywire.ai . License CC BY 4.0. FlyWire is a project of Princeton University and the FlyWire Consortium. https://flywire.ai
- **Cell type annotations.** Schlegel, P., Yin, Y., Bates, A.S. et al. Whole-brain annotation and multi-connectome cell typing of Drosophila. *Nature* 634, 139-152 (2024). Supplemental file 1 from `flyconnectome/flywire_annotations`, release v2.1.0, commit `ebd66db2596fcc39c6950fb54ea3efa00f7fe8a0`. License CC BY 4.0. The exact hash of the copy used is in `research/flywire-annotations-2.1.0.provenance.json`.
- **Full-brain tables.** `research/neurons.csv.gz`, `research/coordinates.csv.gz` and `research/connectome.bin.gz` were mirrored from https://github.com/snedea/flybrain (MIT), which packaged the FlyWire Codex export. Hashes are in `dist/data/provenance.json`.

## Code from others

- `minecraft/vision/upstream/cleanrl/ppo_atari_lstm.py`: CleanRL, https://github.com/vwxyzjn/cleanrl , MIT. The PPO update in `minecraft/vision/ppo.py` follows it.
- Fabric Loader, Fabric API and the Yarn mappings: FabricMC, https://fabricmc.net , Apache 2.0.
- JUnit 5: Eclipse Public License 2.0.

## Minecraft

Minecraft is a trademark of Mojang Studios. This repository contains no game code, textures or assets. The mod links against the game at build time through Fabric Loom, which downloads the game from Mojang's servers under Mojang's terms. To run any of this you need your own copy of Minecraft Java Edition.

## Everything else

The mod, the learner, the lessons, the circuit selection and the documentation were written for the video and are released under the MIT license in `LICENSE`.
