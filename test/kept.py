"""How closely the scene kept for a tick follows the scene drawn the whole way.

With -Dnfm.motiontest the game writes, for a few race ticks, the scene drawn
the whole way at 49 moments between two ticks (motion-<tick>.bin) and the
scene recorded once for the tick and moved to the same moments on the
graphics card's behalf (kept-<tick>.bin, see RetainedScene). For every
polygon in both, this measures how far the kept corners are from the ones
drawn the whole way, by kind (scenery, objects), and how smoothly the kept
corners move (as motion.py does).

    python test/kept.py <dir>
"""
import glob
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from motion import read, polygons  # noqa: E402


def main(d):
    for path in sorted(glob.glob(os.path.join(d, 'kept-*.bin'))):
        tick = os.path.basename(path)[5:-4]
        whole_path = os.path.join(d, 'motion-' + tick + '.bin')
        if not os.path.exists(whole_path):
            continue
        kept = [{p[0]: p for p in polygons(l)} for l in read(path)]
        whole = [{p[0]: p for p in polygons(l)} for l in read(whole_path)]
        diffs = {'scenery': [], 'objects': []}
        worst = {'scenery': (0, None, 0), 'objects': (0, None, 0)}
        shared = 0
        for k, (kf, wf) in enumerate(zip(kept, whole)):
            for key, wp in wf.items():
                kp = kf.get(key)
                if kp is None or kp[3] is None or wp[3] is None or kp[1] != wp[1]:
                    continue
                shared += 1
                group = 'scenery' if isinstance(key[0], tuple) else 'objects'
                ex = np.array(wp[3])
                kx = np.array(kp[3])
                # corners the game keeps on screen; far off ones are clamped
                on = (ex[0] > -50) & (ex[0] < 1330) & (ex[1] > -50) & (ex[1] < 770)
                if not on.any():
                    continue
                dev = np.abs(kx - ex)[:, on].max()
                diffs[group].append(dev)
                if dev > worst[group][0]:
                    worst[group] = (dev, key, k)
        print(f"tick {tick}: {len(kept[0])} kept polygons, {len(whole[0])} drawn the whole way, {shared // len(kept)} matched per moment")
        for group, values in diffs.items():
            if not values:
                continue
            v = np.array(values)
            print(f"   {group:8s} off by: mean {v.mean():.4f} px, 99% {np.percentile(v, 99):.3f}, max {v.max():.2f}"
                  f" (worst {worst[group][1]} at moment {worst[group][2]})")
        # smoothness of the kept corners over the 49 moments
        keys = set(kept[0])
        for f in kept[1:]:
            keys &= set(f)
        jumps = []
        for key in keys:
            if kept[0][key][3] is None:
                continue
            for axis in (0, 1):
                e = np.array([f[key][3][axis] for f in kept])
                limit = 1280 if axis == 0 else 720
                on = np.all((e > -50) & (e < limit + 50), axis=0)
                jumps.append(np.diff(e[:, on], 2, axis=0).ravel())
        if jumps:
            j = np.abs(np.concatenate(jumps))
            print(f"   kept corners, change of step: mean {j.mean():.4f} px, 99% {np.percentile(j, 99):.3f}, max {j.max():.2f}")


if __name__ == '__main__':
    main(sys.argv[1])
