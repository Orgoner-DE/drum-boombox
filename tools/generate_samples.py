#!/usr/bin/env python3
"""Synthesize original CC0 drum samples for Spaze Drum Boombox 0.1.0."""
import math, os, struct, wave, json

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "samples")

PADS = [
    ("kick", "Kick"),
    ("snare", "Snare"),
    ("clap", "Clap"),
    ("rim", "Rim"),
    ("hat_c", "HH C"),
    ("hat_o", "HH O"),
    ("shaker", "Shaker"),
    ("ride", "Ride"),
    ("tom_lo", "Tom L"),
    ("tom_mid", "Tom M"),
    ("tom_hi", "Tom H"),
    ("cowbell", "Glocke"),
    ("perc1", "Perc 1"),
    ("perc2", "Perc 2"),
    ("perc3", "Perc 3"),
    ("crash", "Crash"),
]

KITS = [
    ("street", "Street", dict(punch=1.15, noise=0.9, tone=0.85, decay=0.9, click=1.1)),
    ("steel", "Steel", dict(punch=1.0, noise=0.7, tone=1.15, decay=0.7, click=1.3)),
    ("room", "Room", dict(punch=0.9, noise=1.05, tone=1.0, decay=1.25, click=0.8)),
]


def clamp(x, a=-1.0, b=1.0):
    return a if x < a else b if x > b else x


def env_exp(t, d):
    return math.exp(-t / max(d, 1e-4))


class Noise:
    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFF

    def next(self):
        self.s = (1664525 * self.s + 1013904223) & 0xFFFFFFFF
        return (self.s / 0xFFFFFFFF) * 2.0 - 1.0


def hpf(src, coef):
    y = 0.0
    prev = 0.0
    out = []
    for x in src:
        y = coef * (y + x - prev)
        prev = x
        out.append(y)
    return out


def render(seconds, fn):
    n = int(SR * seconds)
    return [fn(i / SR, i) for i in range(n)]


def kick(kit, ngen):
    punch, decay, click = kit["punch"], kit["decay"], kit["click"]
    def f(t, i):
        freq = 52 + 90 * env_exp(t, 0.035 * decay)
        body = math.sin(2 * math.pi * freq * t) * env_exp(t, 0.22 * decay) * punch
        click_s = ngen.next() * env_exp(t, 0.008) * 0.35 * click
        sub = math.sin(2 * math.pi * 42 * t) * env_exp(t, 0.28 * decay) * 0.55
        return body + sub + click_s
    return render(0.55 * decay + 0.15, f)


def snare(kit, ngen):
    def f(t, i):
        tone = math.sin(2 * math.pi * (190 + 20 * kit["tone"]) * t) * env_exp(t, 0.08 * kit["decay"])
        noise = ngen.next() * env_exp(t, 0.12 * kit["decay"]) * kit["noise"]
        snap = ngen.next() * env_exp(t, 0.012) * 0.7
        return 0.55 * tone + 0.7 * noise + 0.25 * snap
    sig = render(0.42 * kit["decay"] + 0.12, f)
    return hpf(sig, 0.72)


def clap(kit, ngen):
    bursts = [0.0, 0.012, 0.023, 0.038]
    def f(t, i):
        s = 0.0
        for b in bursts:
            if t >= b:
                s += ngen.next() * env_exp(t - b, 0.018 * kit["decay"])
        return s * 0.7 * kit["noise"]
    sig = render(0.35, f)
    return hpf(sig, 0.8)


def rim(kit, ngen):
    def f(t, i):
        a = math.sin(2 * math.pi * 780 * t) * env_exp(t, 0.018)
        b = math.sin(2 * math.pi * 1240 * t) * env_exp(t, 0.014)
        n = ngen.next() * env_exp(t, 0.01) * 0.25
        return (a + 0.5 * b + n) * kit["click"]
    return render(0.18, f)


def hat(kit, ngen, open_=False):
    d = (0.28 if open_ else 0.045) * kit["decay"]
    def f(t, i):
        n = ngen.next()
        m = 0
        for f0 in (3800, 5200, 7400, 9100):
            m += math.sin(2 * math.pi * f0 * t) * 0.12
        return (n * 0.85 + m) * env_exp(t, d) * (0.7 if open_ else 0.55)
    sig = render((0.5 if open_ else 0.16) * kit["decay"] + 0.08, f)
    return hpf(sig, 0.88)


def shaker(kit, ngen):
    def f(t, i):
        g = env_exp(t, 0.07 * kit["decay"])
        return ngen.next() * g * (0.55 if (i % 3) else 0.9) * kit["noise"]
    return hpf(render(0.25, f), 0.9)


def ride(kit, ngen):
    def f(t, i):
        s = 0
        for f0, a in ((820, 0.2), (1240, 0.15), (2460, 0.12), (3180, 0.1), (4520, 0.08)):
            s += math.sin(2 * math.pi * f0 * (1 + 0.002 * math.sin(2 * math.pi * 6 * t)) * t) * a
        s += ngen.next() * 0.08 * env_exp(t, 0.2)
        return s * env_exp(t, 0.55 * kit["decay"])
    return render(1.1 * kit["decay"], f)


def tom(kit, ngen, freq):
    def f(t, i):
        fr = freq * (1 + 0.18 * env_exp(t, 0.03))
        body = math.sin(2 * math.pi * fr * t) * env_exp(t, 0.22 * kit["decay"])
        body += 0.25 * math.sin(2 * math.pi * fr * 1.5 * t) * env_exp(t, 0.1 * kit["decay"])
        att = ngen.next() * env_exp(t, 0.008) * 0.2 * kit["click"]
        return body * kit["tone"] + att
    return render(0.45 * kit["decay"] + 0.1, f)


def cowbell(kit, ngen):
    def f(t, i):
        a = math.sin(2 * math.pi * 540 * t)
        b = math.sin(2 * math.pi * 800 * t)
        return (a * 0.65 + b * 0.5) * env_exp(t, 0.18 * kit["decay"]) * kit["tone"]
    return render(0.4, f)


def perc(kit, ngen, kind):
    if kind == 1:
        def f(t, i):
            return (math.sin(2 * math.pi * 210 * t) + 0.4 * ngen.next()) * env_exp(t, 0.07 * kit["decay"])
        return render(0.22, f)
    if kind == 2:
        def f(t, i):
            return ngen.next() * env_exp(t, 0.04) * 0.8 + math.sin(2 * math.pi * 980 * t) * env_exp(t, 0.03)
        return hpf(render(0.18, f), 0.7)
    def f(t, i):
        fr = 140 + 420 * env_exp(t, 0.04)
        return math.sin(2 * math.pi * fr * t) * env_exp(t, 0.09 * kit["decay"])
    return render(0.28, f)


def crash(kit, ngen):
    def f(t, i):
        s = ngen.next() * 0.7
        for f0 in (420, 680, 1100, 1900, 2700, 4100):
            s += 0.08 * math.sin(2 * math.pi * f0 * t)
        return s * env_exp(t, 0.7 * kit["decay"])
    return hpf(render(1.3 * kit["decay"], f), 0.65)


def normalize(sig, peak=0.89):
    m = max(abs(x) for x in sig) or 1.0
    g = peak / m
    n = len(sig)
    out = []
    for i, x in enumerate(sig):
        fade = 1.0
        if i < 4:
            fade = i / 4
        if i > n - 16:
            fade = max(0.0, (n - i - 1) / 16)
        out.append(clamp(x * g * fade))
    return out


def write_wav(path, sig):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = b"".join(struct.pack("<h", int(clamp(x) * 32767)) for x in sig)
        w.writeframes(frames)


def build_sound(key, kit, ngen):
    fn = {
        "kick": lambda: kick(kit, ngen),
        "snare": lambda: snare(kit, ngen),
        "clap": lambda: clap(kit, ngen),
        "rim": lambda: rim(kit, ngen),
        "hat_c": lambda: hat(kit, ngen, False),
        "hat_o": lambda: hat(kit, ngen, True),
        "shaker": lambda: shaker(kit, ngen),
        "ride": lambda: ride(kit, ngen),
        "tom_lo": lambda: tom(kit, ngen, 98),
        "tom_mid": lambda: tom(kit, ngen, 132),
        "tom_hi": lambda: tom(kit, ngen, 176),
        "cowbell": lambda: cowbell(kit, ngen),
        "perc1": lambda: perc(kit, ngen, 1),
        "perc2": lambda: perc(kit, ngen, 2),
        "perc3": lambda: perc(kit, ngen, 3),
        "crash": lambda: crash(kit, ngen),
    }[key]
    return fn()


def main():
    root = os.path.abspath(ROOT)
    os.makedirs(root, exist_ok=True)
    manifest = {"version": "0.1.0", "sampleRate": SR, "license": "CC0-1.0", "kits": []}
    for i, (kid, label, kit) in enumerate(KITS):
        ngen = Noise(0xC0FFEE00 + i * 997)
        voices = []
        for p, (key, plabel) in enumerate(PADS):
            sig = normalize(build_sound(key, kit, ngen))
            rel = f"{kid}/{key}.wav"
            write_wav(os.path.join(root, rel), sig)
            voices.append({"pad": p, "key": key, "label": plabel, "path": f"samples/{rel}", "frames": len(sig)})
        manifest["kits"].append({"id": kid, "label": label, "pads": voices})
    with open(os.path.join(root, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print("wrote", root)


if __name__ == "__main__":
    main()
