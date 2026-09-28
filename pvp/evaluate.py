"""Frozen checks: learning off, fresh seeds, the brain against each scripted level.

  python -m pvp.evaluate --checkpoint brain/pvp/checkpoint.pt            # ladder table
  python -m pvp.evaluate --checkpoint brain/pvp/checkpoint.pt --check    # the 12/12 gate

The gate follows FlyBridge's rule: a skill counts as learned only when it passes
12 out of 12 frozen checks on seeds it has never seen. Here one check is a
first-to-three duel set against the expert bot, fresh spawn each duel.
"""
import argparse
import json
import torch
from .sim import Duel, HEADS
from .bots import Bots, NAMES
from .model import one_hot_actions


@torch.no_grad()
def play(model, level, duels, seed, greedy=True, opponent_model=None, opponent_greedy=None):
    """Returns per-duel winner (0 brain, 1 opponent, 2 draw), ticks, brain hp left, opponent hp left."""
    model.eval()
    d = Duel(duels, seed)
    bots = Bots(duels, seed + 1)
    bots.set_levels(torch.ones(duels, dtype=torch.bool), torch.full((duels,), level))
    W = model.weight_matrix()
    hidden, previous = model.initial(duels), torch.zeros(duels, sum(HEADS))
    if opponent_model is not None:
        rW = opponent_model.weight_matrix(); rh, rp = opponent_model.initial(duels), torch.zeros(duels, sum(HEADS))
    result = torch.full((duels,), -1)
    ticks = torch.zeros(duels)
    hp = torch.zeros(duels, 2)
    while not d.done.all():
        action, _, _, _, hidden, _ = model(d.senses(0), previous, hidden, greedy=greedy, W=W)
        previous = one_hot_actions(action)
        if opponent_model is not None:
            rival, _, _, _, rh, _ = opponent_model(d.senses(1), rp, rh, greedy=greedy if opponent_greedy is None else opponent_greedy, W=rW); rp = one_hot_actions(rival)
        else:
            rival = bots.act(d, 1)
        _, finished, winner = d.step(torch.stack([action, rival], 1))
        result = torch.where(finished, winner, result)
        ticks = torch.where(finished, d.tick, ticks)
        hp = torch.where(finished[:, None], d.health.clamp_min(0), hp)
    model.train()
    return result, ticks, hp


def ladder(model, duels=200, seed=0, levels=(1, 2, 3, 4), greedy=True):
    out = {}
    for level in levels:
        r, t, hp = play(model, level, duels, seed + level * 1000, greedy)
        out[str(level)] = {'name': NAMES[level], 'win': round((r == 0).float().mean().item(), 3),
                           'loss': round((r == 1).float().mean().item(), 3), 'draw': round((r == 2).float().mean().item(), 3),
                           'hpLeft': round(hp[:, 0].mean().item(), 2), 'opponentHpLeft': round(hp[:, 1].mean().item(), 2),
                           'seconds': round(t.mean().item() / 20, 1)}
    return out


def check(model, checks=12, first_to=3, seed=900_000, level=4, greedy=True):
    """12 frozen checks, each a first-to-3 set on fresh seeds. All 12 must be won."""
    rows = []
    for c in range(checks):
        won = lost = 0
        n = 0
        while won < first_to and lost < first_to:
            r, t, hp = play(model, level, 1, seed + c * 101 + n, greedy)
            n += 1
            won += int(r[0] == 0); lost += int(r[0] == 1)
        rows.append({'check': c + 1, 'won': won, 'lost': lost, 'passed': won == first_to, 'seeds': [seed + c * 101 + k for k in range(n)]})
    return {'level': NAMES[level], 'firstTo': first_to, 'passed': sum(r['passed'] for r in rows), 'of': checks, 'checks': rows}


def main():
    from .train import load
    from .model import load_circuit
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', required=True)
    p.add_argument('--duels', type=int, default=500)
    p.add_argument('--seed', type=int, default=500_000)
    p.add_argument('--sample', action='store_true', help='sample actions instead of taking the most likely')
    p.add_argument('--check', action='store_true')
    args = p.parse_args()
    torch.set_num_threads(4)
    circuit, _ = load_circuit()
    model, meta = load(args.checkpoint, circuit)
    if args.check:
        print(json.dumps(check(model, greedy=not args.sample), indent=1))
    else:
        print(json.dumps(ladder(model, args.duels, args.seed, (0, 1, 2, 3, 4), greedy=not args.sample), indent=1))


if __name__ == '__main__':
    main()
