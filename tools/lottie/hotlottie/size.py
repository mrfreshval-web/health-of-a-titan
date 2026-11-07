"""Bytes per layer and per property of a Lottie file: python3 -m hotlottie.size file.json"""
import json
import sys


def main(path):
    d = json.load(open(path))
    rows = []
    for l in d["layers"]:
        ks = {k: len(json.dumps(v, separators=(",", ":"))) for k, v in l["ks"].items()}
        sh = len(json.dumps(l.get("shapes", []), separators=(",", ":")))
        rows.append((l["nm"], sh, ks))
    for nm, sh, ks in sorted(rows, key=lambda r: -(r[1] + sum(r[2].values()))):
        print(f"{nm:20s} shapes {sh:6d}  " + " ".join(f"{k}={v}" for k, v in ks.items() if v > 60))


if __name__ == "__main__":
    main(sys.argv[1])
