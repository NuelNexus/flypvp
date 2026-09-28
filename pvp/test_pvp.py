import unittest
import torch
from .sim import Duel, COOLDOWN, SENSE_DIM, armor_damage, HEADS, TURNS
from .bots import Bots
from .live import LiveState, to_step

STILL = [1, 1, 0, 0, 0, TURNS.index(0.)]


def act(a, b=STILL):
    return torch.tensor([[a, b]])


def face_each_other(d, gap):
    d.pos[0] = torch.tensor([[0., 0, 0], [0, 0, gap]])
    d.vel.zero_(); d.yaw[0] = torch.tensor([0., 180.])


class Physics(unittest.TestCase):
    def speed(self, sprint):
        d = Duel(1); face_each_other(d, 50); d.pos[0, 1] = torch.tensor([11., 0, 11])
        d.pos[0, 0] = torch.tensor([0., 0, -11])
        a = [2, 1, int(sprint), 0, 0, TURNS.index(0.)]
        for _ in range(40): d.step(act(a))
        z = d.pos[0, 0, 2].item()
        for _ in range(20): d.step(act(a))
        return d.pos[0, 0, 2].item() - z

    def test_walk_and_sprint_speed_match_vanilla(self):
        self.assertAlmostEqual(self.speed(False), 4.317, delta=.02)
        self.assertAlmostEqual(self.speed(True), 5.612, delta=.02)

    def test_jump_apex_is_about_1_25_blocks(self):
        d = Duel(1); face_each_other(d, 8)
        peak = 0
        for t in range(20):
            d.step(act([1, 1, 0, int(t == 0), 0, TURNS.index(0.)]))
            peak = max(peak, d.pos[0, 0, 1].item())
        self.assertAlmostEqual(peak, 1.2522, delta=.01)
        self.assertTrue(bool(d.on_ground[0, 0]))


class Combat(unittest.TestCase):
    def hit(self, charge_ticks=20, falling=False, sprint=False, gap=2.):
        d = Duel(1); face_each_other(d, gap)
        d.swing[0, 0] = charge_ticks
        if falling:
            d.on_ground[0, 0] = False; d.vel[0, 0, 1] = -.3; d.fall[0, 0] = .5; d.pos[0, 0, 1] = .4
        d.sprinting[0, 0] = sprint
        dealt, _, _ = d.step(act([1, 1, 0, 0, 1, TURNS.index(0.)]))
        return dealt[0, 0].item(), d

    def test_full_charge_sword_through_iron(self):
        self.assertAlmostEqual(self.hit()[0], armor_damage(torch.tensor(7.)).item(), places=4)
        self.assertAlmostEqual(self.hit()[0], 3.78, places=2)

    def test_spam_click_is_weak(self):
        weak, _ = self.hit(charge_ticks=0)
        self.assertLess(weak, 1.)

    def test_crit_needs_falling_and_no_sprint(self):
        crit, _ = self.hit(falling=True)
        self.assertAlmostEqual(crit, armor_damage(torch.tensor(10.5)).item(), places=4)
        sprint_fall, _ = self.hit(falling=True, sprint=True)
        self.assertAlmostEqual(sprint_fall, 3.78, places=2)

    def test_out_of_reach_or_looking_away_misses(self):
        self.assertEqual(self.hit(gap=3.8)[0], 0)
        d = Duel(1); face_each_other(d, 2); d.yaw[0, 0] = 60
        dealt, _, _ = d.step(act([1, 1, 0, 0, 1, TURNS.index(0.)]))
        self.assertEqual(dealt[0, 0].item(), 0)

    def test_invulnerability_and_knockback(self):
        _, d = self.hit()
        self.assertGreater(d.vel[0, 1, 2].item(), .15)          # pushed away (+z)
        d.swing[0, 0] = 20
        dealt, _, _ = d.step(act([1, 1, 0, 0, 1, TURNS.index(0.)]))
        self.assertEqual(dealt[0, 0].item(), 0)                 # same damage during i-frames does nothing

    def test_sprint_hit_knocks_further_and_stops_sprint(self):
        _, plain = self.hit()
        _, sprint = self.hit(sprint=True)
        self.assertGreater(sprint.vel[0, 1, 2].item(), plain.vel[0, 1, 2].item() + .2)
        self.assertFalse(bool(sprint.sprinting[0, 0]))

    def test_duels_end(self):
        d = Duel(64, 3); b0, b1 = Bots(64, 1), Bots(64, 2)
        m = torch.ones(64, dtype=torch.bool)
        b0.set_levels(m, torch.full((64,), 4)); b1.set_levels(m, torch.full((64,), 2))
        wins = torch.zeros(3)
        while not d.done.all():
            _, fin, w = d.step(torch.stack([b0.act(d, 0), b1.act(d, 1)], 1))
            for k in range(3): wins[k] += (fin & (w == k)).sum()
        self.assertGreater(wins[0], wins[1] * 3)


class Live(unittest.TestCase):
    def test_live_packet_rebuilds_the_simulator_senses(self):
        d = Duel(1, 5); b = Bots(1, 5); m = torch.ones(1, dtype=torch.bool); b.set_levels(m, torch.tensor([3]))
        for _ in range(30): d.step(torch.stack([b.act(d, 0), b.act(d, 1)], 1))
        center = {'x': 512, 'y': 250, 'z': 0}
        def entity(i):
            p, v = d.pos[0, i], d.vel[0, i]
            return {'x': p[0].item() + 512.5, 'y': p[1].item() + 250, 'z': p[2].item() + .5, 'vx': v[0].item(), 'vy': v[1].item(), 'vz': v[2].item(),
                    'yaw': d.yaw[0, i].item(), 'onGround': bool(d.on_ground[0, i]), 'health': d.health[0, i].item(), 'sprinting': bool(d.sprinting[0, i]),
                    'timeUntilRegen': int(d.hurt[0, i].item())}
        me, them = entity(0), entity(1)
        me.update(cooldown=min(1., (d.swing[0, 0].item() + .5) / COOLDOWN), fallDistance=d.fall[0, 0].item(), pitch=0.)
        them.update(maxHealth=20.)
        live = LiveState(); live.previous_pos = torch.stack([d.pos[0, 0], d.pos[0, 1] - d.vel[0, 1]])
        live.their_swing = int(d.swing[0, 1].item()); live.since_my_hit = int(d.since_hit[0, 0].item()); live.since_their_hit = int(d.since_hit[0, 1].item())
        live.last_regen = (me['timeUntilRegen'], them['timeUntilRegen'])
        packet = {'center': center, 'tick': int(d.tick[0].item()), 'player': me, 'opponent': them}
        expected = d.senses(0)
        got = live.load(packet)
        self.assertEqual(got.shape, (1, SENSE_DIM))
        self.assertTrue(torch.allclose(got, expected, atol=1e-3), (got - expected).abs().max())
        step = to_step(torch.tensor([[2, 1, 1, 0, 1, TURNS.index(10.)]]), packet)
        self.assertEqual(set(step['keys']), {'forward', 'sprint', 'attack'})
        self.assertEqual(step['yaw'], 10.)


if __name__ == '__main__':
    unittest.main()
