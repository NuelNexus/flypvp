"""Render simulated duels to an MP4: top-down arena, HUD, and the fly circuit's live activity.

  python -m pvp.render --out media/fly-pvp-duels.mp4

Every frame is the simulator's state at that tick, and every control the fly brain
presses comes from brain/pvp/checkpoint.pt with learning off. This is the training
simulator (Minecraft 1.21.4 combat rules), not footage of the real game.
"""
import argparse
import math
import subprocess
from pathlib import Path
import numpy as np
import torch
from PIL import Image, ImageDraw, ImageFont
from .sim import Duel, HEADS, ARENA, REACH, COOLDOWN, FORWARD, STRAFE, TURNS
from .bots import Bots, NAMES
from .model import load_circuit, one_hot_actions

W, H = 1280, 720
FPS = 20
SCALE = 680 / (2 * ARENA + 1)        # pixels per block
OX, OY = 20 + 340, 20 + 340           # arena centre on screen
BG = (24, 26, 31)
STONE, STONE_LINE = (122, 124, 128), (106, 108, 112)
GLASS = (170, 215, 235)
BRAIN, RIVAL = (110, 220, 120), (235, 95, 85)
TEXT, DIM = (235, 235, 235), (150, 155, 165)


def font(size, bold=False):
    for path in (f"/usr/share/fonts/truetype/dejavu/DejaVuSans{'-Bold' if bold else ''}.ttf",
                 '/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf'):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size=size)


F_BIG, F_MED, F_SMALL, F_TINY = font(40, True), font(22, True), font(17), font(13)


def screen(x, z):
    return OX + x * SCALE, OY + z * SCALE


def arena_layer():
    img = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(img)
    r = ARENA + .5
    x0, y0 = screen(-r, -r); x1, y1 = screen(r, r)
    d.rectangle([x0 - 8, y0 - 8, x1 + 8, y1 + 8], fill=GLASS)
    d.rectangle([x0, y0, x1, y1], fill=STONE)
    for k in range(-int(ARENA), int(ARENA) + 2):
        a = screen(k - .5, -r); b = screen(k - .5, r)
        d.line([a, b], fill=STONE_LINE, width=1)
        a = screen(-r, k - .5); b = screen(r, k - .5)
        d.line([a, b], fill=STONE_LINE, width=1)
    return img


class Recorder:
    """Plays one duel and keeps what each frame needs."""

    def __init__(self, model, opponent, seed, opponent_model=None):
        self.frames = []
        d = Duel(1, seed)
        bots = Bots(1, seed + 1)
        level = opponent if isinstance(opponent, int) else 4
        bots.set_levels(torch.ones(1, dtype=torch.bool), torch.tensor([level]))
        Wm = model.weight_matrix()
        h, prev = model.initial(1), torch.zeros(1, sum(HEADS))
        if opponent_model is not None:
            rW, rh, rp = opponent_model.weight_matrix(), opponent_model.initial(1), torch.zeros(1, sum(HEADS))
        with torch.no_grad():
            while not d.done.all():
                a, _, _, _, h, _ = model(d.senses(0), prev, h, greedy=True, W=Wm)
                prev = one_hot_actions(a)
                if opponent_model is not None:
                    ra, _, _, _, rh, _ = opponent_model(d.senses(1), rp, rh, greedy=True, W=rW); rp = one_hot_actions(ra)
                else:
                    ra = bots.act(d, 1)
                state = dict(pos=d.pos[0].clone(), yaw=d.yaw[0].clone(), health=d.health[0].clone(),
                             cooldown=torch.clamp((d.swing[0] + .5) / COOLDOWN, 0, 1), hurt=d.hurt[0].clone(),
                             sprint=d.sprinting[0].clone(), action=a[0].clone(), rival_action=ra[0].clone(),
                             sensory=h[0, model.sensory].clone(), output=h[0, model.outputs].clone(), tick=int(d.tick[0]))
                dealt, finished, winner = d.step(torch.stack([a, ra], 1))
                state['dealt'] = dealt[0].clone()
                self.frames.append(state)
                if finished.any():
                    self.winner = int(winner[0])
        self.final_health = d.health[0].clamp_min(0).clone()


def neuron_grid(values, cols, cell, x, y, d, label):
    v = values.numpy()
    rows = math.ceil(len(v) / cols)
    d.text((x, y - 20), label, font=F_TINY, fill=DIM)
    for i, a in enumerate(v):
        r, c = divmod(i, cols)
        t = float(np.tanh(a * 3))
        col = (int(40 + 200 * max(t, 0)), int(40 + 170 * max(t, 0) + 60 * max(-t, 0)), int(40 + 210 * max(-t, 0)))
        d.rectangle([x + c * cell, y + r * cell, x + (c + 1) * cell - 2, y + (r + 1) * cell - 2], fill=col)
    return y + rows * cell


def bar(d, x, y, w, h, frac, color, back=(55, 58, 66)):
    d.rounded_rectangle([x, y, x + w, y + h], 4, fill=back)
    if frac > 0:
        d.rounded_rectangle([x, y, x + max(h, w * frac), y + h], 4, fill=color)


def key(d, x, y, label, on, w=42):
    d.rounded_rectangle([x, y, x + w, y + 34], 6, fill=(230, 230, 230) if on else (48, 51, 58), outline=(90, 95, 105))
    tw = d.textlength(label, font=F_TINY)
    d.text((x + (w - tw) / 2, y + 9), label, font=F_TINY, fill=(20, 20, 20) if on else DIM)


def draw_fighter(d, pos, yaw, color, hurt, sprint, label):
    x, y = screen(pos[0].item(), pos[2].item())
    height = pos[1].item()
    rad = .3 * SCALE
    # Shadow on the floor, body lifted by jump height.
    d.ellipse([x - rad, y - rad * .6 + 4, x + rad, y + rad * .6 + 4], fill=(90, 92, 96))
    by = y - height * SCALE * .6
    body = (255, 255, 255) if hurt > 17 else color
    d.ellipse([x - rad, by - rad, x + rad, by + rad], fill=body, outline=(20, 20, 20), width=2)
    yr = math.radians(yaw)
    fx, fz = -math.sin(yr), math.cos(yr)
    d.line([x, by, x + fx * rad * 2.2, by + fz * rad * 2.2], fill=(20, 20, 20), width=4)
    if sprint:
        for k in (1, 2):
            d.line([x - fx * rad * (1 + k * .6) - fz * 5, by - fz * rad * (1 + k * .6) + fx * 5,
                    x - fx * rad * (1.4 + k * .6) - fz * 5, by - fz * rad * (1.4 + k * .6) + fx * 5], fill=color, width=2)
    tw = d.textlength(label, font=F_TINY)
    d.text((x - tw / 2, by - rad - 20), label, font=F_TINY, fill=TEXT)
    return x, by


def render_duel(rec, title, rival_name, base, pipe, score):
    popups = []
    n = len(rec.frames)
    for idx, s in enumerate(rec.frames):
        img = base.copy()
        d = ImageDraw.Draw(img, 'RGBA')
        # Reach ring for the brain.
        bx, bz = s['pos'][0, 0].item(), s['pos'][0, 2].item()
        x, y = screen(bx, bz)
        d.ellipse([x - REACH * SCALE, y - REACH * SCALE, x + REACH * SCALE, y + REACH * SCALE], outline=(110, 220, 120, 90), width=2)
        p0 = draw_fighter(d, s['pos'][0], s['yaw'][0].item(), BRAIN, s['hurt'][0].item(), bool(s['sprint'][0]), 'FLY BRAIN')
        p1 = draw_fighter(d, s['pos'][1], s['yaw'][1].item(), RIVAL, s['hurt'][1].item(), bool(s['sprint'][1]), rival_name.upper())
        for i, target in ((0, p1), (1, p0)):
            if s['dealt'][i] > 0:
                popups.append([target[0], target[1] - 30, f"-{s['dealt'][i].item():.1f}", BRAIN if i == 0 else RIVAL, 16])
                src = p0 if i == 0 else p1
                d.line([src, target], fill=(255, 255, 255, 180), width=3)
        for pop in popups:
            alpha = int(255 * pop[4] / 16)
            d.text((pop[0] - 18, pop[1] - (16 - pop[4]) * 2), pop[2], font=F_MED, fill=pop[3] + (alpha,))
            pop[4] -= 1
        popups = [p for p in popups if p[4] > 0]
        # Right panel.
        px = 740
        d.text((px, 22), title, font=F_MED, fill=TEXT)
        d.text((px, 52), f"t = {s['tick'] / 20:4.1f} s     score {score}", font=F_SMALL, fill=DIM)
        for row, (name, col, i) in enumerate((('Fly brain', BRAIN, 0), (rival_name, RIVAL, 1))):
            yy = 88 + row * 62
            d.text((px, yy), name, font=F_SMALL, fill=col)
            hp = max(0., s['health'][i].item())
            d.text((px + 450, yy), f"{hp:4.1f} / 20", font=F_SMALL, fill=TEXT)
            bar(d, px, yy + 24, 520, 12, hp / 20, col)
            bar(d, px, yy + 40, 520, 5, s['cooldown'][i].item(), (240, 200, 80))
        d.text((px, 214), 'sword charge shown as the thin yellow bar', font=F_TINY, fill=DIM)
        a = s['action'].tolist()
        f, st = FORWARD[a[0]], STRAFE[a[1]]
        d.text((px, 244), "Fly brain's controls this tick", font=F_SMALL, fill=TEXT)
        key(d, px + 48, 270, 'W', f > 0); key(d, px, 308, 'A', st > 0); key(d, px + 48, 308, 'S', f < 0); key(d, px + 96, 308, 'D', st < 0)
        key(d, px + 150, 308, 'SPRINT', bool(a[2]), 70); key(d, px + 226, 308, 'JUMP', bool(a[3]), 60); key(d, px + 292, 308, 'CLICK', bool(a[4]), 60)
        d.text((px + 362, 316), f"turn {TURNS[a[5]]:+.0f}°", font=F_SMALL, fill=TEXT)
        yb = neuron_grid(s['sensory'], 32, 12, px, 380, d, 'receptor neurons (inputs, 256)')
        neuron_grid(s['output'], 32, 12, px, yb + 30, d, 'mushroom body output + lateral horn neurons (read out, 384)')
        d.text((20, H - 22), 'Simulated Minecraft 1.21.4 sword combat (training simulator, not game footage). Top-down view, 1 tile = 1 block.',
               font=F_TINY, fill=DIM)
        pipe.write(img.tobytes())
    return rec.frames[-1]


def card(pipe, lines, seconds):
    img = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(img)
    y = H / 2 - len(lines) * 30
    for text, f, col in lines:
        tw = d.textlength(text, font=f)
        d.text(((W - tw) / 2, y), text, font=f, fill=col)
        y += f.size + 18
    for _ in range(int(seconds * FPS)):
        pipe.write(img.tobytes())


def main():
    from .train import load
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', default='brain/pvp/checkpoint.pt')
    p.add_argument('--out', default='media/fly-pvp-duels.mp4')
    p.add_argument('--seed', type=int, default=810_000)
    args = p.parse_args()
    torch.set_num_threads(4)
    circuit, _ = load_circuit()
    model, meta = load(args.checkpoint, circuit); model.eval()
    imitation, _ = load('brain/pvp/imitate.pt', circuit); imitation.eval()
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    try:
        import imageio_ffmpeg
        exe = imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        exe = 'ffmpeg'
    ff = subprocess.Popen([exe, '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                           '-vf', 'fps=30', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', '-movflags', '+faststart', args.out], stdin=subprocess.PIPE)
    pipe = ff.stdin
    base = arena_layer()
    card(pipe, [('FlyPvP', F_BIG, TEXT), ("A fruit fly's brain wiring, trained to sword fight", F_MED, TEXT),
                ('1,536 FlyWire neurons  ·  42,921 real connections  ·  learning off', F_SMALL, DIM),
                ('Minecraft 1.21.4 combat rules, in the training simulator', F_SMALL, DIM)], 3.5)
    matches = [('expert', 4, None, 3), ('ace', 5, None, 2), ('its own imitation copy', 4, imitation, 2)]
    seed = args.seed
    for name, level, rival_model, count in matches:
        wins = losses = 0
        rival_label = 'copy' if rival_model is not None else NAMES[level]
        subtitle = {'expert': 'strafes, W-taps, spaces, jump-crits, tight aim',
                    'ace': 'expert footwork with near-perfect aim (never seen in training)',
                    'its own imitation copy': 'the same circuit after lesson 1 only: the hardest opponent, a near coin flip'}[name]
        card(pipe, [(f'Fly brain  vs  {name}', F_BIG, TEXT), (subtitle, F_SMALL, DIM)], 2.5)
        for k in range(count):
            rec = Recorder(model, level, seed, rival_model)
            seed += 1
            render_duel(rec, f'Duel {k + 1} of {count}: fly brain vs {rival_label}', rival_label.capitalize(), base, pipe, f'{wins}-{losses}')
            result = {0: 'FLY BRAIN WINS', 1: f'{rival_label.upper()} WINS', 2: 'DRAW'}[rec.winner]
            wins += rec.winner == 0; losses += rec.winner == 1
            hp = rec.final_health
            card(pipe, [(result, F_BIG, BRAIN if rec.winner == 0 else RIVAL if rec.winner == 1 else TEXT),
                        (f'{len(rec.frames) / 20:.1f} s  ·  fly brain {hp[0]:.1f} HP left  ·  {rival_label} {hp[1]:.1f} HP left', F_SMALL, DIM),
                        (f'set score {wins}-{losses}', F_SMALL, TEXT)], 1.6)
            print(name, k, result, flush=True)
    card(pipe, [('Frozen checks, 500 fresh duels each', F_MED, TEXT),
                ('vs expert 99.6%   ·   vs ace 100%   ·   12/12 first-to-3 sets passed', F_SMALL, TEXT),
                ('vs its own imitation copy: 51% (a coin flip)', F_SMALL, DIM),
                ('github.com/nuelnexus/flypvp  ·  docs/TRAINING.md', F_SMALL, DIM)], 4)
    pipe.close(); ff.wait()
    print('wrote', args.out)


if __name__ == '__main__':
    main()
