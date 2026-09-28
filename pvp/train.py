"""Teach the fly circuit to sword fight.

Two lessons, in FlyBridge's order (demonstrate, then practise):

  imitate  the expert bot fights, the brain watches and copies its controls
           (DAgger: the brain increasingly drives while the expert keeps labelling)
  ppo      the brain practises against a mixed ladder of scripted fighters and
           frozen copies of its own earlier self, learning from wins and damage

  python -m pvp.train imitate --out brain/pvp/imitate.pt
  python -m pvp.train ppo --init brain/pvp/imitate.pt --out brain/pvp/checkpoint.pt

Only edge gains, leaks, biases, the sensory encoder and the readout train; the
fly's wiring and signs are fixed buffers.
"""
import argparse
import copy
import json
import random
import time
from pathlib import Path
import torch
from torch import nn
from .sim import Duel, HEADS, MAX_TICKS
from .bots import Bots
from .model import PvPFly, load_circuit, one_hot_actions
from . import evaluate

ROOT = Path(__file__).resolve().parents[1]


def log(path, row):
    print(json.dumps(row), flush=True)
    with open(path, 'a') as f:
        f.write(json.dumps(row) + '\n')


def save(model, path, meta):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    torch.save({'version': model.version, 'variant': model.variant, 'state': model.state_dict(), 'meta': meta}, path)


def load(path, circuit):
    blob = torch.load(path, map_location='cpu', weights_only=False)
    model = PvPFly(circuit, blob.get('variant', 'fly'))
    model.load_state_dict(blob['state'])
    return model, blob.get('meta', {})


def imitate(args, circuit, digest):
    torch.manual_seed(args.seed)
    model = PvPFly(circuit, args.variant)
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)
    B = args.envs
    duel = Duel(B, args.seed)
    teacher = Bots(B, args.seed)          # labels the brain's side
    rival = Bots(B, args.seed + 1)
    everyone = torch.ones(B, dtype=torch.bool)
    teacher.set_levels(everyone, torch.full((B,), 4))
    rival.set_levels(everyone, torch.randint(1, 5, (B,)))
    hidden = model.initial(B)
    previous = torch.zeros(B, sum(HEADS))
    logfile = ROOT / f'results/{args.id}.jsonl'
    started = time.time()
    for it in range(args.iterations):
        beta = max(0., 1 - it / (args.iterations * .6))     # probability the expert drives
        data = {k: [] for k in ('senses', 'previous', 'hidden', 'label')}
        with torch.no_grad():
            W = model.weight_matrix()
            for t in range(args.horizon):
                senses = duel.senses(0)
                label = teacher.act(duel, 0)
                mine, _, _, _, new_hidden, _ = model(senses, previous, hidden, W=W)
                for k, v in (('senses', senses), ('previous', previous), ('hidden', hidden), ('label', label)):
                    data[k].append(v)
                drive = torch.where((torch.rand(B) < beta)[:, None], label, mine)
                _, finished, _ = duel.step(torch.stack([drive, rival.act(duel, 1)], 1))
                hidden, previous = new_hidden, one_hot_actions(drive)
                if finished.any():
                    n = int(finished.sum())
                    hidden[finished] = 0; previous[finished] = 0
                    duel.reset(finished)
                    teacher.set_levels(finished, torch.full((n,), 4))
                    rival.set_levels(finished, torch.randint(1, 5, (n,)))
        data = {k: torch.cat(v) for k, v in data.items()}
        total = len(data['label'])
        losses, correct = [], torch.zeros(len(HEADS))
        for epoch in range(args.epochs):
            order = torch.randperm(total)
            for start in range(0, total, args.minibatch):
                mb = order[start:start + args.minibatch]
                _, _, _, _, _, logits = model(data['senses'][mb], data['previous'][mb], data['hidden'][mb])
                label = data['label'][mb]
                loss = sum(nn.functional.cross_entropy(l, label[:, k]) for k, l in enumerate(logits))
                opt.zero_grad(); loss.backward()
                nn.utils.clip_grad_norm_(model.parameters(), 1.)
                opt.step()
                losses.append(loss.item())
                correct = torch.stack([l.argmax(-1) for l in logits], -1).eq(label).float().mean(0)
        if it % 10 == 0 or it == args.iterations - 1:
            row = {'stage': 'imitate', 'it': it, 'beta': round(beta, 3), 'loss': round(sum(losses) / len(losses), 4),
                   'agree': [round(x, 3) for x in correct.tolist()], 'sec': round(time.time() - started)}
            if it % 50 == 0 or it == args.iterations - 1:
                row['eval'] = evaluate.ladder(model, duels=128, seed=10_000 + it, levels=(2, 3, 4))
            log(logfile, row)
    save(model, args.out, {'stage': 'imitate', 'circuitSha256': digest, 'args': vars(args), 'parameterHash': model.parameter_hash()})
    return model


def ppo(args, circuit, digest):
    torch.manual_seed(args.seed); random.seed(args.seed)
    if args.init:
        model, meta = load(args.init, circuit)
    else:
        model, meta = PvPFly(circuit, args.variant), {}
    opt = torch.optim.Adam(model.parameters(), lr=args.lr, eps=1e-5)
    B, T = args.envs, args.horizon
    duel = Duel(B, args.seed)
    bots = Bots(B, args.seed + 1)
    # Opponent slots: level 1-4 bots, or -1 = a frozen snapshot of the brain.
    league = [copy.deepcopy(model).eval()]
    def pick(n):
        weights = torch.tensor([.05, .15, .25, .35, args.selfplay])   # rusher, timer, strafer, expert, self
        choice = torch.multinomial(weights, n, replacement=True)
        return torch.where(choice == 4, -1, choice + 1)
    everyone = torch.ones(B, dtype=torch.bool)
    slot = pick(B)
    bots.set_levels(everyone, slot.clamp_min(1))
    snap_id = torch.zeros(B, dtype=torch.long)
    hidden, rival_hidden = model.initial(B), model.initial(B)
    previous, rival_previous = torch.zeros(B, sum(HEADS)), torch.zeros(B, sum(HEADS))
    logfile = ROOT / f'results/{args.id}.jsonl'
    started, steps = time.time(), 0
    best = -1.
    wins = losses = draws = 0
    for update in range(args.updates):
        frac = 1 - update / args.updates
        for g in opt.param_groups:
            g['lr'] = args.lr * frac
        buf = {k: [] for k in ('senses', 'previous', 'hidden', 'action', 'logprob', 'value', 'reward', 'done')}
        with torch.no_grad():
            W = model.weight_matrix()
            rival_W = [m.weight_matrix() for m in league]
            for t in range(T):
                senses = duel.senses(0)
                action, logprob, _, value, new_hidden, _ = model(senses, previous, hidden, W=W)
                rival_action = bots.act(duel, 1)
                selfplay = slot == -1
                if selfplay.any():
                    idx = selfplay.nonzero().squeeze(-1)
                    for sid in snap_id[idx].unique().tolist():
                        sub = idx[snap_id[idx] == sid]
                        ra, _, _, _, rh, _ = league[sid](duel.senses(1)[sub], rival_previous[sub], rival_hidden[sub], W=rival_W[sid])
                        rival_action[sub] = ra; rival_hidden[sub] = rh
                        rival_previous[sub] = one_hot_actions(ra)
                dealt, finished, winner = duel.step(torch.stack([action, rival_action], 1))
                reward = (dealt[:, 0] - dealt[:, 1]) / 20 * args.damage_weight
                reward += torch.where(finished & (winner == 0), 1., 0.) - torch.where(finished & (winner == 1), 1., 0.)
                for k, v in (('senses', senses), ('previous', previous), ('hidden', hidden), ('action', action),
                             ('logprob', logprob), ('value', value), ('reward', reward), ('done', finished.float())):
                    buf[k].append(v)
                hidden, previous = new_hidden, one_hot_actions(action)
                wins += int((finished & (winner == 0)).sum()); losses += int((finished & (winner == 1)).sum()); draws += int((finished & (winner == 2)).sum())
                if finished.any():
                    n = int(finished.sum())
                    hidden[finished] = 0; previous[finished] = 0; rival_hidden[finished] = 0; rival_previous[finished] = 0
                    duel.reset(finished)
                    slot[finished] = pick(n)
                    bots.set_levels(finished, slot[finished].clamp_min(1))
                    # Prefer recent snapshots but keep old ones in the pool.
                    snap_id[finished] = torch.tensor([min(len(league) - 1, int(len(league) * random.random() ** .5)) for _ in range(n)])
            _, _, _, next_value, _, _ = model(duel.senses(0), previous, hidden, W=W)
        steps += B * T
        # GAE.
        rewards, values, dones = torch.stack(buf['reward']), torch.stack(buf['value']), torch.stack(buf['done'])
        advantages = torch.zeros_like(rewards); last = torch.zeros(B)
        for t in reversed(range(T)):
            nv = next_value if t == T - 1 else values[t + 1]
            nonterminal = 1 - dones[t]
            delta = rewards[t] + args.gamma * nv * nonterminal - values[t]
            last = delta + args.gamma * args.lam * nonterminal * last
            advantages[t] = last
        returns = advantages + values
        flat = lambda k: torch.stack(buf[k]).reshape(B * T, *buf[k][0].shape[1:])
        data = {k: flat(k) for k in ('senses', 'previous', 'hidden', 'action', 'logprob')}
        data['adv'], data['ret'], data['value'] = advantages.reshape(-1), returns.reshape(-1), values.reshape(-1)
        stats = []
        for epoch in range(args.epochs):
            order = torch.randperm(B * T)
            for start in range(0, B * T, args.minibatch):
                mb = order[start:start + args.minibatch]
                _, logprob, entropy, value, _, _ = model(data['senses'][mb], data['previous'][mb], data['hidden'][mb], action=data['action'][mb])
                adv = data['adv'][mb]; adv = (adv - adv.mean()) / (adv.std() + 1e-8)
                ratio = (logprob - data['logprob'][mb]).exp()
                pg = torch.max(-adv * ratio, -adv * ratio.clamp(1 - args.clip, 1 + args.clip)).mean()
                v_clipped = data['value'][mb] + (value - data['value'][mb]).clamp(-args.clip, args.clip)
                vloss = .5 * torch.max((value - data['ret'][mb]) ** 2, (v_clipped - data['ret'][mb]) ** 2).mean()
                ent = entropy.mean()
                loss = pg + args.vf * vloss - args.ent * ent
                opt.zero_grad(); loss.backward()
                nn.utils.clip_grad_norm_(model.parameters(), .5)
                opt.step()
                stats.append((pg.item(), vloss.item(), ent.item(), ((ratio - 1).abs() > args.clip).float().mean().item()))
        if (update + 1) % args.snapshot_every == 0:
            league.append(copy.deepcopy(model).eval())
            league = league[-args.league_size:]
        if update % args.log_every == 0 or update == args.updates - 1:
            s = torch.tensor(stats).mean(0).tolist()
            total = max(1, wins + losses + draws)
            row = {'stage': 'ppo', 'update': update, 'steps': steps, 'sps': round(steps / (time.time() - started)),
                   'train_win': round(wins / total, 3), 'train_loss': round(losses / total, 3), 'duels': total,
                   'pg': round(s[0], 4), 'vloss': round(s[1], 4), 'entropy': round(s[2], 3), 'clipfrac': round(s[3], 3),
                   'league': len(league), 'sec': round(time.time() - started)}
            wins = losses = draws = 0
            if update % args.eval_every == 0 or update == args.updates - 1:
                row['eval'] = evaluate.ladder(model, duels=200, seed=20_000 + update, levels=(1, 2, 3, 4))
                score = row['eval']['4']['win']
                save(model, Path(args.out).with_name(Path(args.out).stem + '-latest.pt'), {'stage': 'ppo', 'update': update, 'circuitSha256': digest, 'eval': row['eval']})
                if score >= best:
                    best = score
                    save(model, args.out, {'stage': 'ppo', 'update': update, 'steps': steps, 'circuitSha256': digest, 'eval': row['eval'],
                                           'args': vars(args), 'parameterHash': model.parameter_hash()})
            log(logfile, row)
    return model


def main():
    p = argparse.ArgumentParser()
    p.add_argument('stage', choices=('imitate', 'ppo'))
    p.add_argument('--id', default=None)
    p.add_argument('--out', required=True)
    p.add_argument('--init')
    p.add_argument('--variant', default='fly', choices=('fly', 'shuffled', 'disconnected'))
    p.add_argument('--seed', type=int, default=1)
    p.add_argument('--envs', type=int, default=256)
    p.add_argument('--horizon', type=int, default=64)
    p.add_argument('--iterations', type=int, default=400)
    p.add_argument('--updates', type=int, default=600)
    p.add_argument('--lr', type=float, default=3e-4)
    p.add_argument('--gamma', type=float, default=.995)
    p.add_argument('--lam', type=float, default=.95)
    p.add_argument('--clip', type=float, default=.2)
    p.add_argument('--epochs', type=int, default=3)
    p.add_argument('--minibatch', type=int, default=4096)
    p.add_argument('--vf', type=float, default=.5)
    p.add_argument('--ent', type=float, default=.003)
    p.add_argument('--damage-weight', type=float, default=1.)
    p.add_argument('--selfplay', type=float, default=.2)
    p.add_argument('--snapshot-every', type=int, default=25)
    p.add_argument('--league-size', type=int, default=8)
    p.add_argument('--log-every', type=int, default=5)
    p.add_argument('--eval-every', type=int, default=25)
    p.add_argument('--threads', type=int, default=4)
    args = p.parse_args()
    args.id = args.id or f'{args.stage}-{int(time.time())}'
    torch.set_num_threads(args.threads)
    (ROOT / 'results').mkdir(exist_ok=True)
    circuit, digest = load_circuit()
    (imitate if args.stage == 'imitate' else ppo)(args, circuit, digest)


if __name__ == '__main__':
    main()
