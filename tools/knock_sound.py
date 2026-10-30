"""Синтез звука уведомления (стук дятла). Использование: python -I knock_sound.py app/src/main/res/raw/woody_knock.wav"""
import math
import random
import struct
import sys
import wave

RATE = 22050
random.seed(7)


def knock(amp, pitch):
    """Один удар: щелчок + затухающие резонансы дерева."""
    n = int(RATE * 0.06)
    out = []
    modes = [(420 * pitch, 0.55, 0.018), (1150 * pitch, 1.0, 0.010), (2350 * pitch, 0.5, 0.006), (3900 * pitch, 0.22, 0.003)]
    phases = [random.random() * 6.28 for _ in modes]
    for i in range(n):
        t = i / RATE
        s = 0.0
        for (f, a, tau), ph in zip(modes, phases):
            s += a * math.exp(-t / tau) * math.sin(2 * math.pi * f * t + ph)
        # Щелчок клюва — короткий шумовой импульс в первые 1.5 мс.
        if t < 0.0015:
            s += (random.random() * 2 - 1) * 0.9 * (1 - t / 0.0015)
        # Мягкая атака 0.3 мс, чтобы не было «цифрового» щелчка.
        s *= min(1.0, t / 0.0003)
        out.append(s * amp)
    return out


def drum(start, count, interval, peak):
    events = []
    for k in range(count):
        # Дятел бьёт чуть быстрее к концу дроби, сила плавно спадает.
        t = start + sum(interval * (1 - 0.025 * j) for j in range(k))
        amp = peak * (0.75 + 0.25 * math.sin(math.pi * min(1, (k + 1) / count))) * (1 - 0.35 * k / count)
        events.append((t, amp, 1 + random.uniform(-0.03, 0.03)))
    return events


events = drum(0.02, 11, 1 / 17, 1.0)
length = int(RATE * (events[-1][0] + 0.12))
buf = [0.0] * length
for t, amp, pitch in events:
    s = knock(amp, pitch)
    o = int(t * RATE)
    for i, v in enumerate(s):
        if o + i < length:
            buf[o + i] += v

peak = max(abs(v) for v in buf)
gain = 0.7 / peak  # ~ -3 dBFS
fade = int(RATE * 0.02)
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    frames = bytearray()
    for i, v in enumerate(buf):
        if i > length - fade:
            v *= (length - i) / fade
        frames += struct.pack("<h", int(max(-1, min(1, v * gain)) * 32767))
    w.writeframes(bytes(frames))
print(f"{length / RATE:.2f}s, {len(events)} knocks")
