"""Run a trained PvP brain in real Minecraft through the FlyBridge mod.

  python -m pvp.live --checkpoint brain/pvp/checkpoint.pt --duels 5 --opponent zombie

Needs the mod from ./mod (0.10.0-pvp or newer) in a Fabric 1.21.4 profile whose game
directory is ./minecraft/client, and an open singleplayer world whose name starts
with "Fly Colony School". Learning is off. Each duel:

  /pvp/reset   builds the walled arena, arms both fighters, spawns the sparring opponent
  /start       realtime clock at 20 ticks per second (normal game speed)
  loop         /pvp/senses -> the brain -> /step with held keys, a yaw turn and a click
  /pvp/evaluate the mod's own verdict, then /stop

The senses are rebuilt into exactly the vector the simulator gives the brain by
loading the live numbers into a one-duel simulator state and asking it for
senses(0). Pitch is aimed at the opponent's chest here, as in the simulator.
F8 in game hands control back to you at any time.
"""
import argparse
import http.client
import json
import math
import time
from pathlib import Path
from urllib.parse import urlparse
import torch
from .sim import Duel, HEADS, COOLDOWN, FORWARD, STRAFE, TURNS, HEIGHT, EYE, MAX_TICKS
from .model import load_circuit, one_hot_actions

ROOT = Path(__file__).resolve().parents[1]


class Bridge:
    def __init__(self, session):
        self.descriptor = json.loads(Path(session).read_text())
        base = urlparse(self.descriptor['baseUrl'])
        if base.scheme != 'http' or base.hostname != '127.0.0.1':
            raise ValueError('Expected the mod\'s loopback endpoint')
        self.conn = http.client.HTTPConnection(base.hostname, base.port, timeout=25)
        self.sequence = 0
        self.session = None

    def call(self, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        self.conn.request('GET' if body is None else 'POST', path, body=data,
                          headers={'Authorization': 'Bearer ' + self.descriptor['token'], 'Content-Type': 'application/json'})
        response = self.conn.getresponse(); raw = response.read()
        value = json.loads(raw)
        if response.status >= 400:
            raise RuntimeError(value.get('error', f'HTTP {response.status}'))
        return value

    def start(self):
        status = self.call('/start', {'clockMode': 'realtime', 'targetTps': 20})
        self.sequence, self.session = 0, status['sessionId']

    def step(self, action):
        self.sequence += 1
        result = self.call('/step', {**action, 'sequence': self.sequence})
        if result.get('sessionId') != self.session or result.get('sequence') != self.sequence or not result.get('ok'):
            raise RuntimeError(result.get('error', 'Native input rejected'))
        return result

    def stop(self):
        try:
            self.call('/stop', {})
        except Exception:
            pass


class LiveState:
    """Keeps the few timers the screen implies but the mod does not hand over directly."""

    def __init__(self):
        self.duel = Duel(1, 0)
        self.previous_pos = None
        self.their_swing = 100
        self.since_my_hit = self.since_their_hit = 100
        self.last_regen = (0, 0)
        self.was_swinging = False

    def load(self, s):
        d, c = self.duel, s['center']
        me, them = s['player'], s['opponent']
        rel = lambda e: torch.tensor([e['x'] - c['x'] - .5, e['y'] - c['y'], e['z'] - c['z'] - .5])
        pos = torch.stack([rel(me), rel(them)])
        vel_me = torch.tensor([me['vx'], me['vy'], me['vz']])
        vel_them = pos[1] - self.previous_pos[1] if self.previous_pos is not None else torch.zeros(3)
        self.previous_pos = pos
        d.pos[0] = pos
        d.vel[0] = torch.stack([vel_me, vel_them])
        d.yaw[0] = torch.tensor([me['yaw'], them['yaw']]).add(180).remainder(360).sub(180)
        d.health[0] = torch.tensor([me['health'], them['health'] / them.get('maxHealth', 20) * 20])
        d.on_ground[0] = torch.tensor([me['onGround'], them['onGround']])
        d.sprinting[0] = torch.tensor([me['sprinting'], them.get('sprinting', False)])
        swinging = them.get('handSwinging', False)
        self.their_swing = 0 if swinging and not self.was_swinging else self.their_swing + 1
        self.was_swinging = swinging
        d.swing[0] = torch.tensor([me['cooldown'] * COOLDOWN - .5, float(self.their_swing)])
        regen = (me['timeUntilRegen'], them['timeUntilRegen'])
        if regen[1] > self.last_regen[1]: self.since_my_hit = 0
        if regen[0] > self.last_regen[0]: self.since_their_hit = 0
        self.last_regen = regen
        d.hurt[0] = torch.tensor([float(regen[0]), float(regen[1])])
        d.fall[0] = torch.tensor([me.get('fallDistance', 0.), 0.])
        d.since_hit[0] = torch.tensor([float(self.since_my_hit), float(self.since_their_hit)])
        d.tick[0] = float(s['tick'])
        self.since_my_hit += 1; self.since_their_hit += 1
        return d.senses(0)


def to_step(action, s):
    a = action[0].tolist()
    keys = []
    f, st = FORWARD[a[0]], STRAFE[a[1]]
    if f > 0: keys.append('forward')
    if f < 0: keys.append('back')
    if st > 0: keys.append('left')
    if st < 0: keys.append('right')
    if a[2]: keys.append('sprint')
    if a[3]: keys.append('jump')
    if a[4]: keys.append('attack')
    me, them = s['player'], s['opponent']
    # Motor layer: aim the pitch at the opponent's chest (game pitch is positive downwards).
    flat = math.hypot(them['x'] - me['x'], them['z'] - me['z'])
    want = -math.degrees(math.atan2(them['y'] + HEIGHT / 2 - (me['y'] + EYE), max(flat, 1e-3)))
    pitch = max(-90., min(90., want - me.get('pitch', 0.)))
    return {'action': 'control', 'ticks': 1, 'keys': keys, 'yaw': TURNS[a[5]], 'pitch': pitch}


@torch.no_grad()
def duel(bridge, model, seed, opponent, greedy=True, log=None):
    fixture = bridge.call('/pvp/reset', {'seed': seed, 'opponent': opponent})
    live = LiveState()
    W = model.weight_matrix()
    hidden, previous = model.initial(1), torch.zeros(1, sum(HEADS))
    bridge.start()
    verdict = None
    try:
        for tick in range(MAX_TICKS):
            s = bridge.call('/pvp/senses', {'seed': seed, 'opponent': opponent})
            if not s['opponent']['alive'] or not s['player']['alive'] or s['player']['health'] <= 0:
                break
            senses = live.load(s)
            action, _, _, _, hidden, _ = model(senses, previous, hidden, greedy=greedy, W=W)
            previous = one_hot_actions(action)
            step = to_step(action, s)
            if log is not None:
                log.write(json.dumps({'tick': tick, 'senses': senses[0].tolist(), 'step': step}) + '\n')
            bridge.step(step)
    finally:
        bridge.stop()
        verdict = bridge.call('/pvp/evaluate', {'seed': seed, 'opponent': opponent})
    return fixture, verdict


def main():
    from .train import load
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', default=str(ROOT / 'brain/pvp/checkpoint.pt'))
    p.add_argument('--session', default=str(ROOT / 'minecraft/client/config/flybridge-session.json'))
    p.add_argument('--opponent', default='zombie')
    p.add_argument('--duels', type=int, default=5)
    p.add_argument('--seed', type=int, default=700_000)
    p.add_argument('--sample', action='store_true')
    p.add_argument('--id', default=f'pvp-live-{int(time.time())}')
    args = p.parse_args()
    circuit, digest = load_circuit()
    model, meta = load(args.checkpoint, circuit)
    model.eval()
    bridge = Bridge(args.session)
    run = ROOT / 'minecraft/runs/pvp' / args.id; run.mkdir(parents=True, exist_ok=True)
    rows = []
    with open(run / 'events.jsonl', 'w') as log:
        for k in range(args.duels):
            fixture, verdict = duel(bridge, model, args.seed + k, args.opponent, greedy=not args.sample, log=log)
            rows.append(verdict)
            print(json.dumps(verdict), flush=True)
    summary = {'schema': 'fly-pvp-live-1', 'checkpoint': args.checkpoint, 'circuitSha256': digest,
               'parameterHash': model.parameter_hash(), 'opponent': args.opponent, 'learning': 'off',
               'wins': sum(r['result'] == 'win' for r in rows), 'duels': len(rows), 'results': rows}
    (run / 'summary.json').write_text(json.dumps(summary, indent=1))
    print(json.dumps({k: summary[k] for k in ('opponent', 'wins', 'duels')}))


if __name__ == '__main__':
    main()
