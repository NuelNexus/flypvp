"""Scripted sword fighters, from a training dummy to a strong 1.9+ PvP player.

They press the same discrete controls the brain does (sim.HEADS), so the
expert doubles as the imitation teacher. Every level is a set of numbers, not
a different program, so a batch can mix levels per duel.

  0 dummy    stands, turns slowly, never swings
  1 rusher   sprints in and spam-clicks with sloppy aim
  2 timer    waits for a charged sword, decent aim, no footwork
  3 strafer  timed hits, strafing, W-tap sprint resets
  4 expert   all of 3, plus spacing while recharging, jump crits, tight aim
"""
import torch
from .sim import TURNS, REACH, COOLDOWN, Duel

LEVELS = {
    #          noise  click  thresh strafe wtap  space  crit  approach
    0: dict(noise=30., click=0., thresh=2., strafe=0., wtap=0., space=0., crit=0., approach=0.),
    1: dict(noise=9., click=.45, thresh=0., strafe=0., wtap=0., space=0., crit=0., approach=1.),
    2: dict(noise=4., click=1., thresh=.92, strafe=0., wtap=0., space=0., crit=0., approach=1.),
    3: dict(noise=2.5, click=1., thresh=.92, strafe=1., wtap=1., space=0., crit=0., approach=1.),
    4: dict(noise=1.5, click=1., thresh=.95, strafe=1., wtap=1., space=1., crit=.35, approach=1.),
}
NAMES = {0: 'dummy', 1: 'rusher', 2: 'timer', 3: 'strafer', 4: 'expert'}


class Bots:
    def __init__(self, batch, seed=0, device='cpu'):
        self.B, self.device = batch, device
        self.gen = torch.Generator().manual_seed(seed + 7919)
        self.params = {k: torch.zeros(batch, device=device) for k in LEVELS[0]}
        self.level = torch.zeros(batch, dtype=torch.long, device=device)
        self.strafe_dir = torch.ones(batch, device=device)
        self.strafe_timer = torch.zeros(batch, device=device)
        self.wtap_timer = torch.zeros(batch, device=device)
        self.crit_plan = torch.zeros(batch, dtype=torch.bool, device=device)
        self.turns = torch.tensor(TURNS, device=device)

    def rand(self, *shape):
        return torch.rand(*shape, generator=self.gen).to(self.device)

    def set_levels(self, mask, levels):
        """levels: long tensor of size mask.sum()."""
        self.level[mask] = levels
        for k in self.params:
            table = torch.tensor([LEVELS[l][k] for l in range(len(LEVELS))], device=self.device)
            self.params[k][mask] = table[levels]
        n = int(mask.sum())
        self.strafe_dir[mask] = torch.where(self.rand(n) < .5, -1., 1.)
        self.strafe_timer[mask] = 10 + self.rand(n) * 30
        self.wtap_timer[mask] = 0
        self.crit_plan[mask] = False

    def act(self, duel: Duel, i):
        """Actions (B, 6) for fighter i of every duel."""
        P = self.params
        B = self.B
        s = duel.senses(i)
        bearing = s[:, 7] * 180
        dist = s[:, 3] * 6
        cooldown = s[:, 19]
        on_target = s[:, 28] > 0
        on_ground = s[:, 23] > 0
        falling = s[:, 27] > 0
        sprinting = s[:, 25] > 0

        # Aim: pick the turn bin closest to the (noisy) bearing, leading a strafing target a little.
        lead = s[:, 14] * 2.0 * (dist < 5).float()
        want = bearing + lead + torch.randn(B, generator=self.gen).to(self.device) * P['noise']
        turn = (self.turns[None] - want[:, None]).abs().argmin(-1)
        dummy = self.level == 0
        turn = torch.where(dummy, torch.where(self.rand(B) < .1, (want > 0).long() * 2 + 4, 5), turn)

        # Footwork.
        forward = torch.where(P['approach'] > 0, 2, 1)   # index 2 is forward
        close = dist < REACH - .1
        self.wtap_timer = (self.wtap_timer - 1).clamp_min(0)
        just_hit = s[:, 35] * 40 < 1.5
        self.wtap_timer = torch.where(just_hit & (P['wtap'] > 0), 2., self.wtap_timer)
        forward = torch.where(self.wtap_timer > 0, 1, forward)
        recharging = cooldown < .75
        spacing = (P['space'] > 0) & recharging & (dist < 3.4)
        forward = torch.where(spacing, 0, forward)                                    # back off while recharging
        forward = torch.where((P['approach'] > 0) & close & ~spacing & (P['strafe'] == 0) & (dist < 1.2), 1, forward)
        self.strafe_timer -= 1
        flip = self.strafe_timer <= 0
        self.strafe_dir = torch.where(flip, -self.strafe_dir, self.strafe_dir)
        self.strafe_timer = torch.where(flip, 12 + self.rand(B) * 28, self.strafe_timer)
        # Walls: strafe away from the nearer side wall.
        strafe = torch.where((P['strafe'] > 0) & (dist < 6), self.strafe_dir, torch.zeros(B, device=self.device))
        strafe_idx = (strafe + 1).long()
        sprint = torch.where(forward == 2, 1, 0)

        # Jump crits: hop when about to be in range with a charging sword, swing on the way down.
        plan = (P['crit'] > 0) & on_ground & (dist < 3.8) & (dist > 2.) & (cooldown > .55) & (cooldown < .85)
        plan &= self.rand(B) < P['crit']
        jump = plan.long()
        self.crit_plan = torch.where(plan, True, torch.where(on_ground & ~plan, False, self.crit_plan))
        sprint = torch.where(self.crit_plan, 0, sprint)   # a crit needs the sprint released
        forward = torch.where(self.crit_plan & (forward == 2), 2, forward)

        # Clicking.
        ready = cooldown >= P['thresh']
        want_crit = self.crit_plan & ~on_ground
        ready = torch.where(want_crit, ready & falling, ready)
        in_reach = on_target & (dist < REACH + .6)
        click = (self.rand(B) < P['click']) & ready & (in_reach | ((P['thresh'] == 0) & (dist < 4.)))
        attack = click.long()
        return torch.stack([forward.long(), strafe_idx, sprint.long(), jump, attack, turn], -1)
