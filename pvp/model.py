"""The FlyWire circuit as a sword fighter.

Same construction as FlyBridge's FarmFly / VisualFly:

* 1,536 neurons and 42,921 signed connections from FlyWire FAFB v783
  (dist/data/circuit.json). No connection is added, removed or flipped.
* each connection carries initial_weight * sign * 2 * sigmoid(gain); only the
  gain is learned, so every synapse stays between 0x and 2x its fly strength
  and keeps its fly sign;
* every neuron has a learned leak and bias and a tanh rate;
* combat facts are written onto the olfactory receptor neurons (group 6),
  activity propagates through the real wiring four times per tick, and the
  readout sees only mushroom body output (19) and lateral horn (23) neurons.

The circuit state carries over from tick to tick (like VisualFly), so the brain
has a short memory of the fight. The weight matrix is built dense from the
edge list and applied with a matmul; the maths is identical to FlyBridge's
index_add, just much faster on a CPU.
"""
import hashlib
import json
from pathlib import Path
import torch
from torch import nn
from torch.distributions import Categorical
from .sim import HEADS, SENSE_DIM

VERSION = 'fly-pvp-circuit-1'
ROOT = Path(__file__).resolve().parents[1]
CIRCUIT = ROOT / 'dist/data/circuit.json'


def load_circuit(path=CIRCUIT):
    text = Path(path).read_text()
    return json.loads(text), hashlib.sha256(text.encode()).hexdigest()


class PvPFly(nn.Module):
    version = VERSION

    def __init__(self, circuit, variant='fly'):
        super().__init__()
        if variant not in ('fly', 'shuffled', 'disconnected'):
            raise ValueError('Unknown connectivity condition')
        self.variant = variant
        self.n = len(circuit['neurons'])
        edges = torch.tensor(circuit['edges'], dtype=torch.float32)
        source, target = edges[:, 0].long(), edges[:, 1].long()
        if variant == 'shuffled':
            target = target[torch.randperm(len(target), generator=torch.Generator().manual_seed(8001))]
        magnitude = edges[:, 2].abs().log1p()
        incoming = torch.zeros(self.n).index_add_(0, target, magnitude).clamp_min(1)
        self.register_buffer('source', source)
        self.register_buffer('target', target)
        self.register_buffer('edge_sign', edges[:, 2].sign())
        self.register_buffer('initial_weight', magnitude / incoming[target])
        groups = [n['group'] for n in circuit['neurons']]
        self.register_buffer('sensory', torch.tensor([i for i, g in enumerate(groups) if g == 6]))
        self.register_buffer('outputs', torch.tensor([i for i, g in enumerate(groups) if g in (19, 23)]))
        self.edge_gain = nn.Parameter(torch.zeros(len(source)))
        self.leak = nn.Parameter(torch.zeros(self.n))
        self.bias = nn.Parameter(torch.zeros(self.n))
        # Sensory interface: facts plus the previous action onto the 256 receptor neurons.
        self.encoder = nn.Sequential(nn.Linear(SENSE_DIM + sum(HEADS), 128), nn.ELU(), nn.LayerNorm(128))
        self.drive = nn.Linear(128, len(self.sensory))
        self.readout = nn.Sequential(nn.LayerNorm(len(self.outputs), eps=1e-6), nn.Linear(len(self.outputs), 128), nn.Tanh())
        self.actor = nn.ModuleList([nn.Linear(128, h) for h in HEADS])
        self.critic = nn.Linear(128, 1)
        for head in self.actor:
            nn.init.orthogonal_(head.weight, .01); nn.init.zeros_(head.bias)

    def initial(self, batch=1):
        return self.bias.new_zeros((batch, self.n))

    def weight_matrix(self):
        weight = self.initial_weight * self.edge_sign * (2 * torch.sigmoid(self.edge_gain))
        if self.variant == 'disconnected':
            weight = weight * 0
        return torch.zeros(self.n, self.n, device=weight.device).index_put((self.source, self.target), weight, accumulate=True)

    def features(self, senses, previous, hidden, W=None):
        W = self.weight_matrix() if W is None else W
        drive_values = self.drive(self.encoder(torch.cat([senses, previous], -1)))
        drive = hidden.new_zeros(hidden.shape).index_copy(1, self.sensory, drive_values)
        leak = torch.sigmoid(self.leak) * .9 + .05
        for _ in range(4):
            hidden = leak * hidden + (1 - leak) * torch.tanh(hidden @ W + drive + self.bias)
        return self.readout(hidden[:, self.outputs]), hidden

    def forward(self, senses, previous, hidden, action=None, greedy=False, W=None):
        features, hidden = self.features(senses, previous, hidden, W)
        logits = [head(features) for head in self.actor]
        dists = [Categorical(logits=l) for l in logits]
        if action is None:
            action = torch.stack([l.argmax(-1) if greedy else d.sample() for l, d in zip(logits, dists)], -1)
        logprob = sum(d.log_prob(action[:, k]) for k, d in enumerate(dists))
        entropy = sum(d.entropy() for d in dists)
        return action, logprob, entropy, self.critic(features).squeeze(-1), hidden, logits

    def parameter_hash(self):
        sha = hashlib.sha256()
        for name, value in self.state_dict().items():
            sha.update(name.encode()); sha.update(value.detach().cpu().numpy().tobytes())
        return sha.hexdigest()


def one_hot_actions(action):
    return torch.cat([nn.functional.one_hot(action[:, k], h).float() for k, h in enumerate(HEADS)], -1)
