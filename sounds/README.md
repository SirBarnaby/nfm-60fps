# The extras' recorded sounds

Requirement: `openspec\specs\sound`, "Sound effects come from real
recordings". Nothing here is synthesized: every sound the extras add is cut
from a recording somebody made and gave away.

| File | What it is |
| --- | --- |
| `sources.tsv` | Every recording: its page, who made it, its title and its license, and which part of it is kept |
| `sources\*.opus` | That part of each recording (24 kHz mono Opus, 1.1 MB in all) |
| `clips.tsv` | Every sound the game plays: which source, from where, how long, the game rate it is sampled down to, a shot or a loop, the filter |
| `make.py` | Cuts the clips: `python3 sounds/make.py` (all) or `python3 sounds/make.py turbo fuse` (some). Needs ffmpeg and numpy |
| `CREDITS.md` | The credits, written by `make.py` from the two tables |

`make.py` writes each clip as a 16-bit mono WAV into
`src\nfm\lit\features\sounds\`, with `credits.txt` beside them. They go into
`madness.jar` with the extras' classes and are read as class-path resources
(`Recordings.clip`, `Recordings.give`), as the car sheets are; no mod replaces
them. The WAVs and `credits.txt` are in git, so a build needs neither ffmpeg
nor Python.

## How a clip is made

- **The game's rates.** The game's own sounds are 8000, 8272, 11025 and
  22050 samples a second. A clip is sampled down to 11025, or 22050 where its
  top end is the sound (a turbo's whistle, a metal ring, a fuse), with soxr
  at high precision, so it sits among the game's sounds instead of over them.
- **A loop** is cut 0.2 s longer than it plays, and that tail is laid over
  its start at equal power, so it repeats without a seam; then it is brought
  to one level (-17 dB RMS, never over -2 dB peak).
- **A shot** starts from silence over 4 ms, fades out over its last 30 ms and
  peaks at -3 dB.
- **Filters** take out what is not the sound: the dyno run the turbo comes
  from loses everything under 2.8 kHz, so only the whistle is left.

## Licenses

Each license was read on the recording's own page, 26 Nov 2026. All are
Creative Commons 0 (public domain) except two under Creative Commons
Attribution 3.0, which ask for credit and for the changes to be named:
`wipedry` (NoiseCollector) and `whine` (jessepash). `CREDITS.md` and
`credits.txt` name every recording all the same, with what was changed, and
`test\extras-check.ps1 sounds` fails if a clip has no credit.

## Adding a sound

1. Find a recording under CC0 or CC BY (not NC, not "sampling+") and read its
   license on its page.
2. Keep the part needed as `sources\<name>.opus` and add its line to
   `sources.tsv`.
3. Add the clip's line to `clips.tsv`, run `make.py`, and give it to the
   sound with `Recordings.give`.

The cloud workspace cannot reach freesound.org; the recordings came through
the PC's browser, a preview at a time.
