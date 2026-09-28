"""How does a checkpoint fight? Control usage, spacing and hits against one bot level, learning off.

  python -m pvp.inspect_play --checkpoint brain/pvp/checkpoint.pt --level 4
"""
import argparse
import json
import torch
from .model import load_circuit, one_hot_actions
from .sim import Duel, HEADS, HEAD_NAMES
from .bots import Bots


@torch.no_grad()
def inspect(model, level=4, duels=200, seed=4242):
    model.eval()
    d = Duel(duels, seed); b = Bots(duels, seed + 1)
    b.set_levels(torch.ones(duels, dtype=torch.bool), torch.full((duels,), level))
    h, prev, W = model.initial(duels), torch.zeros(duels, sum(HEADS)), model.weight_matrix()
    counts = [torch.zeros(k) for k in HEADS]
    dist, dealt, hits, res = [], torch.zeros(2), torch.zeros(2), torch.full((duels,), -1)
    while not d.done.all():
        live = ~d.done
        a, _, _, _, h, _ = model(d.senses(0), prev, h, greedy=True, W=W); prev = one_hot_actions(a)
        for k in range(len(HEADS)):
            counts[k] += torch.bincount(a[live, k], minlength=HEADS[k]).float()
        dist.append(d.senses(0)[live, 3] * 6)
        dl, f, w = d.step(torch.stack([a, b.act(d, 1)], 1))
        dealt += dl.sum(0); hits += (dl > 0).float().sum(0); res = torch.where(f, w, res)
    dd = torch.cat(dist)
    return {'controls': {n: (c / c.sum()).round(decimals=3).tolist() for n, c in zip(HEAD_NAMES, counts)},
            'distance': {'mean': round(dd.mean().item(), 2), 'p10/p50/p90': [round(x, 2) for x in dd.quantile(torch.tensor([.1, .5, .9])).tolist()]},
            'damagePerHit': [round(x, 2) for x in (dealt / hits.clamp_min(1)).tolist()], 'hitsPerDuel': [round(x, 2) for x in (hits / duels).tolist()],
            'win': (res == 0).float().mean().item(), 'loss': (res == 1).float().mean().item()}


def main():
    from .train import load
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', required=True)
    p.add_argument('--level', type=int, default=4)
    p.add_argument('--threads', type=int, default=2)
    args = p.parse_args()
    torch.set_num_threads(args.threads)
    model, _ = load(args.checkpoint, load_circuit()[0])
    print(json.dumps(inspect(model, args.level), indent=1))


if __name__ == '__main__':
    main()
