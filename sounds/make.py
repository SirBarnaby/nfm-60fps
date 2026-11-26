#!/usr/bin/env python3
"""Cuts the game's extra sounds from the recordings in sources/.

Reads clips.tsv, and for each row cuts its part of sources/<source>.opus,
filters it, samples it down to the game's rate, makes a loop seamless or
fades a shot out, brings it to one level and writes it as 16-bit mono WAV
into src/nfm/lit/features/sounds/. Needs ffmpeg and numpy.

    python3 sounds/make.py            every clip
    python3 sounds/make.py turbo fuse  only these

Every run also writes the credits from sources.tsv and clips.tsv: credits.txt
next to the clips (it goes into madness.jar with them; the extras' checks
read it) and CREDITS.md here, for people.
"""
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / 'src' / 'nfm' / 'lit' / 'features' / 'sounds'
CROSSFADE = 0.2   # seconds of a loop's end laid over its start
FADE_IN = 0.004   # a shot starts from silence over this, so a cut inside a sound does not click
FADE_OUT = 0.03
SHOT_PEAK = 0.7   # a shot's peak, of full scale: about -3 dB
LOOP_RMS = 0.14   # a loop's level, of full scale: about -17 dB
LOOP_PEAK = 0.8


def cut(source, start, seconds, rate, audio_filter):
    """The part of a source, filtered and at the game's rate, as floats."""
    filters = [] if audio_filter == '-' else [audio_filter]
    filters.append(f'aresample={rate}:resampler=soxr:precision=28')
    done = subprocess.run(
        ['ffmpeg', '-v', 'error', '-ss', f'{start}', '-t', f'{seconds}', '-i', str(HERE / 'sources' / f'{source}.opus'),
         '-af', ','.join(filters), '-ac', '1', '-f', 'f32le', '-'],
        check=True, capture_output=True)
    return np.frombuffer(done.stdout, dtype=np.float32).astype(np.float64)


def loop(x, rate):
    """x with its last CROSSFADE seconds laid over its first at equal power: it repeats with no seam."""
    n = int(rate * CROSSFADE)
    body = x[:len(x) - n].copy()
    t = np.linspace(0, np.pi / 2, n)
    body[:n] = body[:n] * np.sin(t) + x[len(x) - n:] * np.cos(t)
    return body


def shot(x, rate):
    x = x.copy()
    a, z = int(rate * FADE_IN), int(rate * FADE_OUT)
    x[:a] *= np.linspace(0, 1, a)
    x[len(x) - z:] *= np.linspace(1, 0, z) ** 2
    return x


def rows(table):
    for line in (HERE / table).read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.startswith('#'):
            yield line.split('\t')


def edits(kind, rate, audio_filter):
    """What was done to a recording to make a clip, in words (CC BY asks that changes are named)."""
    done = ['an excerpt', 'mixed to mono']
    if audio_filter != '-':
        done.append('filtered (' + audio_filter + ')')
    done.append(f'sampled down to {rate} Hz')
    done.append('its end cross-faded into its start to loop' if kind == 'loop' else 'faded in and out')
    done.append('its level changed')
    return ', '.join(done)


def credits():
    """credits.txt for the game and CREDITS.md for people: every clip, its recording, who made it, its license."""
    sources = {r[0]: r for r in rows('sources.tsv')}
    game = ['# clip\trate\tkind\ttitle\tauthor\tlicense\tpage\tchanges']
    people = ['# Sound credits', '',
              'The extras\' sounds are cut from these recordings (`sources.tsv`, `clips.tsv`, `make.py`).',
              'Thank you to everyone who recorded them and gave them away.', '',
              '| Sound in the game | Recording | By | License | Changes |', '| --- | --- | --- | --- | --- |']
    for name, source, _, _, rate, kind, audio_filter, about in (r[:8] for r in rows('clips.tsv')):
        _, _, author, title, licence, _, _, page = sources[source][:8]
        changed = edits(kind, rate, audio_filter)
        game.append('\t'.join([name, rate, kind, title, author, licence, page, changed]))
        people.append(f'| {about} (`{name}`) | [{title}]({page}) | {author} | {licence} | {changed} |')
    (OUT / 'credits.txt').write_text('\n'.join(game) + '\n', encoding='utf-8', newline='\n')
    (HERE / 'CREDITS.md').write_text('\n'.join(people) + '\n', encoding='utf-8', newline='\n')


def main():
    wanted = set(sys.argv[1:])
    OUT.mkdir(parents=True, exist_ok=True)
    credits()
    for row in rows('clips.tsv'):
        name, source, start, seconds, rate, kind, audio_filter = row[:7]
        if wanted and name not in wanted:
            continue
        rate = int(rate)
        extra = CROSSFADE if kind == 'loop' else 0
        x = cut(source, float(start), float(seconds) + extra, rate, audio_filter)
        x -= np.mean(x)
        if kind == 'loop':
            x = loop(x, rate)
            x *= LOOP_RMS / (np.sqrt(np.mean(x * x)) + 1e-9)
            x *= min(1.0, LOOP_PEAK / (np.max(np.abs(x)) + 1e-9))
        else:
            x = shot(x, rate)
            x *= SHOT_PEAK / (np.max(np.abs(x)) + 1e-9)
        pcm = np.round(x * 32767).astype('<i2')
        with wave.open(str(OUT / f'{name}.wav'), 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(rate)
            w.writeframes(pcm.tobytes())
        print(f'{name}: {len(pcm) / rate:.2f} s at {rate} Hz, {kind}')


main()
