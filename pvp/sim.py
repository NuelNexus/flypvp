"""Batched, headless 1v1 sword duel that follows Minecraft Java 1.21.4 combat rules.

Everything runs as torch tensors so thousands of duels step together on a CPU.
Each env holds two fighters (index 0 and 1). One tick is 1/20 s. The numbers
below are the game's own constants (LivingEntity.travel, PlayerEntity.attack,
LivingEntity.damage/takeKnockback, DamageUtil.getDamageLeft):

* ground accel 0.1 * 0.98 (x1.3 sprinting), air accel 0.02 (0.026 sprinting),
  ground drag 0.6*0.91, air drag 0.91, gravity 0.08 with 0.98 vertical drag,
  jump 0.42 plus a 0.2 sprint-jump boost along the facing direction;
* diamond sword: 7 damage, attack speed 1.6 -> 12.5 tick cooldown,
  damage * (0.2 + progress^2 * 0.8), crit x1.5 when falling, not sprinting and
  charged over 0.9, sprint hit adds 0.5 knockback and slows the attacker to 0.6;
* 20 tick hurt timer, a hit during the second half only deals the excess over
  the last hit and carries no knockback, base knockback 0.4 away from the attacker;
* full iron armour on both fighters (15 armour, 0 toughness);
* reach 3.0 from the eyes to the target's 0.6 x 1.8 box, along the look ray.

Not modelled: ping, shields, bows, potions, hunger, regeneration, blocks other
than a flat floor and four walls. Pitch is aimed at the opponent's chest by the
motor layer (the same way FlyBridge's controller aims); yaw is the brain's.
"""
import math
import torch

TICKS_PER_SECOND = 20
MAX_TICKS = 1200            # 60 s duel, then the healthier fighter wins
ARENA = 12.0                # walls at +-ARENA blocks
EYE = 1.62
HALF_WIDTH = 0.3
HEIGHT = 1.8
REACH = 3.0
COOLDOWN = 20 / 1.6         # ticks for a full diamond sword swing
SWORD = 7.0
ARMOR = 15.0
TOUGHNESS = 0.0
HURT_TICKS = 20
MAX_HEALTH = 20.0

# Discrete action space shared by the brain and the scripted fighters.
FORWARD = (-1, 0, 1)
STRAFE = (-1, 0, 1)          # +1 is left, as in the game's sideways input
TURNS = (-40., -20., -10., -5., -2., 0., 2., 5., 10., 20., 40.)
HEADS = (len(FORWARD), len(STRAFE), 2, 2, 2, len(TURNS))   # forward, strafe, sprint, jump, attack, turn
HEAD_NAMES = ('forward', 'strafe', 'sprint', 'jump', 'attack', 'turn')


def armor_damage(damage):
    f = 2 + TOUGHNESS / 4
    g = torch.clamp(ARMOR - damage / f, min=ARMOR * .2, max=20.)
    return damage * (1 - g / 25)


def wrap(angle):
    return (angle + 180) % 360 - 180


class Duel:
    """B independent duels. State tensors are (B, 2, ...)."""

    def __init__(self, batch, seed=0, device='cpu'):
        self.B = batch
        self.device = device
        self.gen = torch.Generator(device='cpu').manual_seed(seed)
        z = lambda *s: torch.zeros(batch, 2, *s, device=device)
        self.pos, self.vel = z(3), z(3)
        self.yaw, self.health = z(), z()
        self.on_ground = torch.ones(batch, 2, dtype=torch.bool, device=device)
        self.sprinting = torch.zeros(batch, 2, dtype=torch.bool, device=device)
        self.swing, self.hurt, self.last_damage, self.fall = z(), z(), z(), z()
        self.since_hit = z()       # ticks since this fighter last landed a hit
        self.tick = torch.zeros(batch, device=device)
        self.done = torch.zeros(batch, dtype=torch.bool, device=device)
        self.reset(torch.ones(batch, dtype=torch.bool, device=device))

    def rand(self, *shape):
        return torch.rand(*shape, generator=self.gen).to(self.device)

    def reset(self, mask):
        n = int(mask.sum())
        if n == 0:
            return
        # Spawn facing roughly towards each other, 5-10 blocks apart, anywhere in the arena.
        centre = (self.rand(n, 2) - .5) * (ARENA * .8)
        heading = self.rand(n) * 2 * math.pi
        gap = 5 + self.rand(n) * 5
        off = torch.stack([torch.cos(heading), torch.sin(heading)], -1) * gap[:, None] / 2
        a, b = centre - off, centre + off
        pos = torch.zeros(n, 2, 3, device=self.device)
        pos[:, 0, 0], pos[:, 0, 2] = a[:, 0], a[:, 1]
        pos[:, 1, 0], pos[:, 1, 2] = b[:, 0], b[:, 1]
        pos[..., 0].clamp_(-ARENA + 1, ARENA - 1); pos[..., 2].clamp_(-ARENA + 1, ARENA - 1)
        self.pos[mask] = pos
        self.vel[mask] = 0
        yaw = torch.stack([self.bearing(pos[:, 0], pos[:, 1]), self.bearing(pos[:, 1], pos[:, 0])], 1)
        self.yaw[mask] = wrap(yaw + (self.rand(n, 2) - .5) * 120)
        self.health[mask] = MAX_HEALTH
        self.on_ground[mask] = True
        self.sprinting[mask] = False
        self.swing[mask] = COOLDOWN     # spawn with a charged sword
        self.hurt[mask] = 0
        self.last_damage[mask] = 0
        self.fall[mask] = 0
        self.since_hit[mask] = 100
        self.tick[mask] = 0
        self.done[mask] = False

    @staticmethod
    def bearing(src, dst):
        """Yaw (degrees, game convention) that looks from src to dst."""
        dx, dz = dst[..., 0] - src[..., 0], dst[..., 2] - src[..., 2]
        return torch.rad2deg(torch.atan2(-dx, dz))

    def look(self, i):
        """Look vector with yaw from the fighter and pitch aimed at the opponent's chest."""
        me, other = self.pos[:, i], self.pos[:, 1 - i]
        eye = me.clone(); eye[:, 1] += EYE
        dy = other[:, 1] + HEIGHT / 2 - eye[:, 1]
        flat = torch.hypot(other[:, 0] - me[:, 0], other[:, 2] - me[:, 2]).clamp_min(1e-3)
        pitch = torch.atan2(dy, flat)
        yaw = torch.deg2rad(self.yaw[:, i])
        d = torch.stack([-torch.sin(yaw) * torch.cos(pitch), torch.sin(pitch), torch.cos(yaw) * torch.cos(pitch)], -1)
        return eye, d

    def ray_hits(self, i):
        """Does fighter i's crosshair land on the opponent within reach? (slab test)"""
        eye, d = self.look(i)
        other = self.pos[:, 1 - i]
        lo = torch.stack([other[:, 0] - HALF_WIDTH, other[:, 1], other[:, 2] - HALF_WIDTH], -1)
        hi = torch.stack([other[:, 0] + HALF_WIDTH, other[:, 1] + HEIGHT, other[:, 2] + HALF_WIDTH], -1)
        inv = 1 / torch.where(d.abs() < 1e-8, torch.full_like(d, 1e-8), d)
        t1, t2 = (lo - eye) * inv, (hi - eye) * inv
        near = torch.minimum(t1, t2).amax(-1)
        far = torch.maximum(t1, t2).amin(-1)
        return (far >= near.clamp_min(0)) & (near <= REACH)

    def step(self, actions):
        """actions: (B, 2, 6) long tensor of head indices. Returns (damage_dealt (B,2), done (B,), winner (B,))."""
        fwd = torch.tensor(FORWARD, device=self.device, dtype=torch.float32)[actions[..., 0]]
        strafe = torch.tensor(STRAFE, device=self.device, dtype=torch.float32)[actions[..., 1]]
        sprint_key = actions[..., 2] == 1
        jump_key = actions[..., 3] == 1
        attack = actions[..., 4] == 1
        turn = torch.tensor(TURNS, device=self.device)[actions[..., 5]]
        live = ~self.done
        self.yaw = torch.where(live[:, None], wrap(self.yaw + turn), self.yaw)

        # 1. Attacks resolve at the start of the tick, both sides against the same positions.
        dealt = torch.zeros(self.B, 2, device=self.device)
        hits = [self.ray_hits(0), self.ray_hits(1)]
        progress = torch.clamp((self.swing + .5) / COOLDOWN, 0, 1)
        falling = (~self.on_ground) & (self.vel[..., 1] < 0) & (self.fall > 0)
        pending_kb = []
        for i in (0, 1):
            j = 1 - i
            swung = attack[:, i] & live
            landed = swung & hits[i]
            p = progress[:, i]
            charged = p > .9
            crit = charged & falling[:, i] & ~self.sprinting[:, i]
            raw = SWORD * (.2 + p * p * .8) * torch.where(crit, 1.5, 1.)
            dmg = armor_damage(raw)
            immune = self.hurt[:, j] > HURT_TICKS / 2
            excess = torch.where(immune, (dmg - self.last_damage[:, j]).clamp_min(0), dmg)
            applies = landed & (excess > 0)
            full = applies & ~immune
            dealt[:, i] = torch.where(applies, torch.minimum(excess, self.health[:, j]), 0.)
            sprint_hit = full & charged & self.sprinting[:, i]
            pending_kb.append((j, full, sprint_hit, dmg, applies, i))
            self.swing[:, i] = torch.where(swung, 0., self.swing[:, i])
        for j, full, sprint_hit, dmg, applies, i in pending_kb:
            self.health[:, j] -= dealt[:, i]
            self.last_damage[:, j] = torch.where(applies, dmg, self.last_damage[:, j])
            self.hurt[:, j] = torch.where(full, float(HURT_TICKS), self.hurt[:, j])
            self.since_hit[:, i] = torch.where(applies, 0., self.since_hit[:, i])
            # Base knockback 0.4 away from the attacker's position.
            away = self.pos[:, i, [0, 2]] - self.pos[:, j, [0, 2]]
            self._knockback(j, full, .4, away)
            # Sprint knockback along the attacker's facing, then the attacker slows and stops sprinting.
            yaw = torch.deg2rad(self.yaw[:, i])
            facing = torch.stack([torch.sin(yaw), -torch.cos(yaw)], -1)
            self._knockback(j, sprint_hit, .5, facing)
            self.vel[:, i, 0] = torch.where(sprint_hit, self.vel[:, i, 0] * .6, self.vel[:, i, 0])
            self.vel[:, i, 2] = torch.where(sprint_hit, self.vel[:, i, 2] * .6, self.vel[:, i, 2])
            self.sprinting[:, i] &= ~sprint_hit

        # 2. Movement, per fighter.
        forward_ok = fwd > 0
        can_sprint = sprint_key & forward_ok
        self.sprinting = (self.sprinting | can_sprint) & forward_ok
        jump = jump_key & self.on_ground & live[:, None]
        yaw = torch.deg2rad(self.yaw)
        self.vel[..., 1] = torch.where(jump, .42, self.vel[..., 1])
        boost = jump & self.sprinting
        self.vel[..., 0] -= torch.where(boost, torch.sin(yaw) * .2, 0.)
        self.vel[..., 2] += torch.where(boost, torch.cos(yaw) * .2, 0.)
        f, s = fwd * .98, strafe * .98
        norm = torch.sqrt(f * f + s * s).clamp_min(1.)
        f, s = f / norm, s / norm
        speed = torch.where(self.on_ground, .1 * torch.where(self.sprinting, 1.3, 1.), torch.where(self.sprinting, .026, .02))
        ax = (s * torch.cos(yaw) - f * torch.sin(yaw)) * speed
        az = (f * torch.cos(yaw) + s * torch.sin(yaw)) * speed
        grounded_before = self.on_ground.clone()
        self.vel[..., 0] += ax; self.vel[..., 2] += az
        self.vel = torch.where(live[:, None, None], self.vel, torch.zeros_like(self.vel))
        self.pos = self.pos + self.vel
        # Walls and floor.
        hit_wall = torch.zeros_like(self.on_ground)
        for axis in (0, 2):
            limit = ARENA - HALF_WIDTH
            over = self.pos[..., axis].abs() > limit
            hit_wall |= over
            self.pos[..., axis] = self.pos[..., axis].clamp(-limit, limit)
            self.vel[..., axis] = torch.where(over, 0., self.vel[..., axis])
        self.sprinting &= ~hit_wall
        drop = (-self.vel[..., 1]).clamp_min(0)
        self.fall = torch.where(self.vel[..., 1] < 0, self.fall + drop, self.fall)
        landed = self.pos[..., 1] <= 0
        self.pos[..., 1] = self.pos[..., 1].clamp_min(0)
        self.vel[..., 1] = torch.where(landed, 0., self.vel[..., 1])
        self.fall = torch.where(landed, 0., self.fall)
        self.on_ground = landed
        drag = torch.where(grounded_before, .6 * .91, .91)
        self.vel[..., 0] *= drag; self.vel[..., 2] *= drag
        self.vel[..., 1] = torch.where(landed, 0., (self.vel[..., 1] - .08) * .98)

        # 3. Timers and the result.
        self.swing += 1
        self.hurt = (self.hurt - 1).clamp_min(0)
        self.since_hit += 1
        self.tick += 1
        dead = self.health <= 0
        timeout = self.tick >= MAX_TICKS
        finished = live & (dead.any(-1) | timeout)
        winner = torch.full((self.B,), -1, dtype=torch.long, device=self.device)   # -1 none, 2 draw
        h0, h1 = self.health[:, 0], self.health[:, 1]
        winner = torch.where(finished & (h0 > h1), 0, winner)
        winner = torch.where(finished & (h1 > h0), 1, winner)
        winner = torch.where(finished & (h0 == h1), 2, winner)
        self.done |= finished
        return dealt, finished, winner

    def _knockback(self, j, mask, strength, direction):
        # LivingEntity.takeKnockback: halve velocity, push along -direction, pop up if grounded.
        n = direction / direction.norm(dim=-1, keepdim=True).clamp_min(1e-5)
        v = self.vel[:, j]
        vx = torch.where(mask, v[:, 0] / 2 - n[:, 0] * strength, v[:, 0])
        vz = torch.where(mask, v[:, 2] / 2 - n[:, 1] * strength, v[:, 2])
        vy = torch.where(mask & self.on_ground[:, j], torch.clamp(v[:, 1] / 2 + strength, max=.4), v[:, 1])
        self.vel[:, j] = torch.stack([vx, vy, vz], -1)
        self.on_ground[:, j] &= ~(mask & (vy > 0))

    # ---- senses -------------------------------------------------------------
    def senses(self, i):
        """Combat facts for fighter i, in its own frame. All are things a player can see or feel."""
        me, other = self.pos[:, i], self.pos[:, 1 - i]
        yaw = torch.deg2rad(self.yaw[:, i])
        fx, fz = -torch.sin(yaw), torch.cos(yaw)          # forward
        lx, lz = torch.cos(yaw), torch.sin(yaw)           # left
        dx, dz, dy = other[:, 0] - me[:, 0], other[:, 2] - me[:, 2], other[:, 1] - me[:, 1]
        dist = torch.hypot(dx, dz)
        ahead, left = dx * fx + dz * fz, dx * lx + dz * lz
        bearing = wrap(self.bearing(me, other) - self.yaw[:, i])           # how far to turn to face them
        their_bearing = wrap(self.bearing(other, me) - self.yaw[:, 1 - i])  # are they facing me
        mv, ov = self.vel[:, i], self.vel[:, 1 - i]
        b = torch.deg2rad(bearing)
        tb = torch.deg2rad(their_bearing)
        wall = lambda px, sign: (ARENA - sign * px) / ARENA
        features = [
            ahead / 6, left / 6, dy / 2, dist / 6, (dist - REACH).clamp(-2, 4) / 2,
            torch.sin(b), torch.cos(b), bearing / 180, torch.sin(tb), torch.cos(tb),
            (mv[:, 0] * fx + mv[:, 2] * fz) * 3, (mv[:, 0] * lx + mv[:, 2] * lz) * 3, mv[:, 1] * 3,
            (ov[:, 0] * fx + ov[:, 2] * fz) * 3, (ov[:, 0] * lx + ov[:, 2] * lz) * 3, ov[:, 1] * 3,
            self.health[:, i] / 20, self.health[:, 1 - i] / 20, (self.health[:, i] - self.health[:, 1 - i]) / 20,
            torch.clamp((self.swing[:, i] + .5) / COOLDOWN, 0, 1), torch.clamp(self.swing[:, 1 - i] / COOLDOWN, 0, 1.5),
            self.hurt[:, i] / 20, self.hurt[:, 1 - i] / 20,
            self.on_ground[:, i].float(), self.on_ground[:, 1 - i].float(), self.sprinting[:, i].float(), self.sprinting[:, 1 - i].float(),
            ((~self.on_ground[:, i]) & (mv[:, 1] < 0)).float(),
            self.ray_hits(i).float(), self.ray_hits(1 - i).float(),
            wall(me[:, 0], 1), wall(me[:, 0], -1), wall(me[:, 2], 1), wall(me[:, 2], -1),
            (fx * torch.sign(me[:, 0]) + fz * torch.sign(me[:, 2])) / 2,   # facing outwards
            torch.clamp(self.since_hit[:, i] / 40, 0, 1), torch.clamp(self.since_hit[:, 1 - i] / 40, 0, 1),
            self.tick / MAX_TICKS,
        ]
        return torch.stack(features, -1).float()


SENSE_NAMES = ('ahead', 'left', 'dy', 'dist', 'reach_gap', 'bearing_sin', 'bearing_cos', 'bearing', 'their_bearing_sin',
               'their_bearing_cos', 'my_vel_fwd', 'my_vel_left', 'my_vel_up', 'their_vel_fwd', 'their_vel_left', 'their_vel_up',
               'my_health', 'their_health', 'health_lead', 'my_cooldown', 'their_swing_age', 'my_hurt', 'their_hurt',
               'my_ground', 'their_ground', 'my_sprint', 'their_sprint', 'my_falling', 'on_target', 'they_on_target',
               'wall_px', 'wall_nx', 'wall_pz', 'wall_nz', 'facing_out', 'since_my_hit', 'since_their_hit', 'clock')
SENSE_DIM = len(SENSE_NAMES)
