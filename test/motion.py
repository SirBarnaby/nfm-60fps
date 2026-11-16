"""Smoothness of the recorded scene between two ticks (-Dnfm.motiontest).

For every polygon corner present at all 49 points, measures the second
difference of its screen position from point to point: smooth motion has
almost none, rounding shows as jumps back and forth. Done for the whole
pixels the software path uses and for the exact corners when recorded.
"""
import struct
import sys
import glob
import os
import numpy as np

FILL, DRAW, FILL_X, DRAW_X = 2, 3, 6, 7


def read(path):
    raw = open(path, 'rb').read()
    lists = []
    at = 0
    while at < len(raw):
        (n,) = struct.unpack_from('>i', raw, at)
        at += 4
        lists.append(np.frombuffer(raw, dtype='>i4', count=n, offset=at).astype(np.int64))
        at += 4 * n
    return lists


def polygons(data):
    """[(op, n, ints xy, exact xy or None)] in order."""
    out = []
    i = 0
    tag = None
    seen = {}
    while i < len(data):
        op = int(data[i])
        if op == 8:
            tag = int(data[i + 1])
            i += 2
        elif op in (1, 5, 9):
            i += 2
        elif op == 4:
            i += 5
        elif op in (FILL, DRAW, FILL_X, DRAW_X):
            n = int(data[i + 1])
            xs = data[i + 2:i + 2 + n]
            ys = data[i + 2 + n:i + 2 + 2 * n]
            ex = None
            if op in (FILL_X, DRAW_X):
                bits = data[i + 2 + 2 * n:i + 2 + 4 * n].astype(np.uint32)
                f = bits.view(np.float32)
                ex = (f[:n].astype(float), f[n:].astype(float))
                i += 2 + 4 * n
            else:
                i += 2 + 2 * n
            base = (('scenery', tag) if tag is None or tag < 0 else tag, op in (FILL, FILL_X), n)
            seen[base] = seen.get(base, 0) + 1
            key = base + (seen[base],)
            out.append((key, n, (xs.astype(float), ys.astype(float)), ex))
        else:
            raise ValueError(op)
    return out


def main(d):
    for path in sorted(glob.glob(os.path.join(d, 'motion-*.bin'))):
        frames = [{p[0]: p for p in polygons(l)} for l in read(path)]
        keys = set(frames[0])
        for f in frames[1:]:
            keys &= set(f)
        count = len(frames[0])
        same = len(keys)
        groups = {'scenery': ([], []), 'objects': ([], [])}
        exact_polys = 0
        for key in keys:
            g = groups['scenery' if isinstance(key[0], tuple) else 'objects']
            has_exact = all(f[key][3] is not None for f in frames)
            exact_polys += has_exact
            for axis in (0, 1):
                w = np.array([f[key][2][axis] for f in frames])  # (49, n)
                # only corners that stay on screen: off-screen ones are clamped by the game
                limit = 1280 if axis == 0 else 720
                on = np.all((w > -50) & (w < limit + 50), axis=0)
                g[0].append(np.diff(w[:, on], 2, axis=0).ravel())
                if has_exact:
                    e = np.array([f[key][3][axis] for f in frames])
                    g[1].append(np.diff(e[:, on], 2, axis=0).ravel())
        print(f"{os.path.basename(path)}: {same} of {count} polygons in every frame, {exact_polys} with exact corners")
        for name, (whole, exact) in groups.items():
            if not whole:
                continue
            w = np.abs(np.concatenate(whole))
            if w.size == 0:
                continue
            print(f"   {name:8s} whole pixels: mean {w.mean():.3f} px, 99% {np.percentile(w, 99):.2f}, max {w.max():.1f}, jumps>0.5px {(w > 0.5).mean()*100:.1f}%")
            if exact:
                ex = np.abs(np.concatenate(exact))
                print(f"   {name:8s} exact:        mean {ex.mean():.3f} px, 99% {np.percentile(ex, 99):.2f}, max {ex.max():.1f}, jumps>0.5px {(ex > 0.5).mean()*100:.1f}%")


if __name__ == '__main__':
    main(sys.argv[1])
