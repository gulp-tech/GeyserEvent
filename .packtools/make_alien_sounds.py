#!/usr/bin/env python3
"""
Процедурный генератор «страшных» звуков пришельцев (v1.20.3).

Все звуки генерируются синтезом — никаких внешних источников, 100% royalty-free.
Файлы сохраняются в формате WAV (16-bit, 44100 Hz, mono) в
src/main/resources/resourcepack-vanilla/sounds/alien/

Создаёт 4 звука:
  1. alien_growl.wav   — низкий рычящий звук (атмосфера/появление босса)
  2. alien_screech.wav — высокий скрипучий крик (способности босса)
  3. alien_ambient.wav — атмосферный дрон (фоновая атмосфера ивента)
  4. alien_transmit.wav — странная «радиопередача» пришельцев (тиклы/сообщения)

Звуки запускаются через SoundCategory.HOSTIL для громкости.
"""

import math
import os
import struct
import random
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "resourcepack-vanilla", "sounds", "alien")

SAMPLE_RATE = 44100


def _ensure_dir():
    os.makedirs(OUT_DIR, exist_ok=True)


def _write_wav(path, samples):
    """Save float samples (-1..1) as 16-bit WAV."""
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        data = b""
        for s in samples:
            val = max(-1.0, min(1.0, s))
            data += struct.pack("<h", int(val * 32767))
        w.writeframes(data)
    return len(data)


def _lerp(a, b, t):
    return a + (b - a) * t


# ------------------------------------------------------------------ звук 1: рык
def gen_growl(duration_sec=3.0):
    """Низкий рычящий звук с нарастанием и спадом."""
    n = int(duration_sec * SAMPLE_RATE)
    samples = []
    rng = random.Random(42)

    for i in range(n):
        t = i / SAMPLE_RATE
        # Фаза: нарастание (0-1.0с), удержание (1.0-2.0с), спад (2.0-3.0с)
        if t < 0.8:
            env = t / 0.8  # нарастание
        elif t < 1.5:
            env = 1.0  # удержание
        else:
            env = 1.0 - (t - 1.5) / 1.5  # спад

        env = max(0.0, env)
        # Восмущения для «рыка»
        base_freq = 55  # низкий бас
        # Добавляем гармоники для «нечеловеческого» звука
        s = 0.0
        s += math.sin(2 * math.pi * base_freq * t) * 0.6
        s += math.sin(2 * math.pi * base_freq * 1.5 * t) * 0.25
        s += math.sin(2 * math.pi * base_freq * 0.5 * t) * 0.15
        # Вибрато для «переменного» рыка
        vib = 1.0 + 0.15 * math.sin(2 * math.pi * 8 * t)
        s *= vib
        # Низкий шум для «грохота»
        noise = (rng.random() - 0.5) * 0.15
        s += noise
        # Сжимаем динамику
        s = math.tanh(s * 1.5) * env * 0.7
        samples.append(s)

    return samples


# ------------------------------------------------------------------ звук 2: скрип
def gen_screech(duration_sec=1.5):
    """Высокий скрипучий крик пришельца — Digital Distortion."""
    n = int(duration_sec * SAMPLE_RATE)
    samples = []
    rng = random.Random(137)

    for i in range(n):
        t = i / SAMPLE_RATE
        # Фаза: быстрый подъём (0-0.15с), крики (0.15-1.0с), спад (1.0-1.5с)
        if t < 0.15:
            env = t / 0.15
        elif t < 1.0:
            # Случайные «крики» — прерывисты
            burst = 0.5 + 0.5 * math.sin(2 * math.pi * 20 * t)
            env = burst * (0.7 + 0.3 * math.sin(2 * math.pi * 5 * t))
        else:
            env = 1.0 - (t - 1.0) / 0.5

        env = max(0.0, env)

        # Высокаяfrequency с synth-скрип
        base = 1800  # высокий
        # Несколько деструктивных гармоник
        s = 0.0
        s += math.sin(2 * math.pi * base * t) * 0.4
        s += math.sin(2 * math.pi * base * 1.3 * t) * 0.3
        s += math.sin(2 * math.pi * base * 0.7 * t) * 0.2
        # Низкая обратная фидback для «digital» звука
        s += math.sin(2 * math.pi * (base + 300) * t) * 0.15
        # Distortion
        s = math.tanh(s * 3.0)
        # Добавляем резкий шум для «скрипа»
        noise_env = 0.3 * env * (0.5 + 0.5 * math.sin(2 * math.pi * 30 * t))
        s += (rng.random() - 0.5) * noise_env
        # Сжимаем
        s = math.tanh(s * 1.2) * env * 0.5
        samples.append(s)

    return samples


# ------------------------------------------------------------------ звук 3: атмосфера
def gen_ambient(duration_sec=8.0):
    """Атмосферный дрон — низкочастотное пульсирующее гудение."""
    n = int(duration_sec * SAMPLE_RATE)
    samples = []
    rng = random.Random(999)

    for i in range(n):
        t = i / SAMPLE_RATE
        # Медленное нарастание и спад по seluruhу длительности
        env = 0.3 + 0.7 * (0.5 + 0.5 * math.sin(2 * math.pi * 0.125 * t))

        # Низкие частоты — дрон
        base = 40  # очень низко
        s = 0.0
        s += math.sin(2 * math.pi * base * t) * 0.4
        s += math.sin(2 * math.pi * base * 2 * t) * 0.15
        s += math.sin(2 * math.pi * base * 0.5 * t) * 0.25
        # Добавляем «пульсацию» — медленные изменения громкости
        pulse = 1.0 + 0.2 * math.sin(2 * math.pi * 0.08 * t)
        s *= pulse
        # Медленный вибрато
        vib = 1.0 + 0.08 * math.sin(2 * math.pi * 0.3 * t)
        s *= vib
        # Легкий шум для «живого» ощущения
        noise = (rng.random() - 0.5) * 0.05 * env
        s += noise
        # Сжимаем
        s = math.tanh(s * 0.8) * env * 0.3
        samples.append(s)

    return samples


# ------------------------------------------------------------------ звук 4: радиопередача
def gen_transmit(duration_sec=2.0):
    """Странная «радиопередача» пришельцев — прерывистые сигналы."""
    n = int(duration_sec * SAMPLE_RATE)
    samples = []
    rng = random.Random(7)

    for i in range(n):
        t = i / SAMPLE_RATE
        # Прерывистые «импульсы»
        pulse_period = 0.15  # 6.67 импульсов в секунду
        phase = (t % pulse_period) / pulse_period

        if phase < 0.3:
            env = phase / 0.3  # быстрый подъём
        elif phase < 0.7:
            env = 1.0  # удержание
        else:
            env = 1.0 - (phase - 0.7) / 0.3  # спад

        env = max(0.0, env)

        # «Digital» тон — высокая неприятная частота
        freq = 2400 + 200 * math.sin(2 * math.pi * 3 * t)
        s = math.sin(2 * math.pi * freq * t) * 0.4
        # Добавляем квадратную волну для «robot» звука
        s += (1.0 if math.sin(2 * math.pi * freq * 0.5 * t) >= 0 else -1.0) * 0.15
        # Distortion
        s = math.tanh(s * 2.0)
        # Резкий шум для «static»
        noise = (rng.random() - 0.5) * 0.2 * env
        s += noise
        # Сжимаем
        s = math.tanh(s * 1.5) * env * 0.35
        samples.append(s)

    return samples


# ------------------------------------------------------------------ звук 5: момент смерти
def gen_death(duration_sec=4.0):
    """Звук смерти/исчезновения босса — драматичный спад."""
    n = int(duration_sec * SAMPLE_RATE)
    samples = []
    rng = random.Random(555)

    for i in range(n):
        t = i / SAMPLE_RATE
        # Драматичный спад: от сильного к нулю
        env = 1.0 - (t / duration_sec)
        env = max(0.0, env) ** 1.5  # более резкий спад

        # Комбинация низкого рыка + высокого крика
        low = math.sin(2 * math.pi * 60 * t) * 0.4
        high = math.sin(2 * math.pi * 1200 * t) * 0.2 * math.sin(2 * math.pi * 2 * t)
        # Explosion noise
        noise = (rng.random() - 0.5) * 0.3 * env
        s = low + high + noise
        s = math.tanh(s * 1.5) * env * 0.6
        samples.append(s)

    return samples


def main():
    _ensure_dir()

    sounds = [
        ("alien_growl.wav", gen_growl(3.0), "рык"),
        ("alien_screech.wav", gen_screech(1.5), "скрип"),
        ("alien_ambient.wav", gen_ambient(8.0), "атмосферный дрон"),
        ("alien_transmit.wav", gen_transmit(2.0), "радиопередача"),
        ("alien_death.wav", gen_death(4.0), "момент смерти"),
    ]

    for name, samples, desc in sounds:
        path = os.path.join(OUT_DIR, name)
        size = _write_wav(path, samples)
        print(f"  {name:24s} {len(samples)/SAMPLE_RATE:.1f}s  {size:>8d} bytes  ({desc})")

    print(f"\nSounds written to {OUT_DIR}")


if __name__ == "__main__":
    main()
