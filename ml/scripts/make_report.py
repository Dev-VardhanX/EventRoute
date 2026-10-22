"""Turn models/buffer_v1.metadata.json into pitch-ready PNG charts and a Markdown table.

    python scripts/make_report.py --meta models/buffer_v1.metadata.json --out report
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--meta", default="models/buffer_v1.metadata.json")
    ap.add_argument("--out", default="report")
    a = ap.parse_args()
    meta = json.loads(Path(a.meta).read_text(encoding="utf-8"))
    rep = meta["report"]
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    synthetic_note = "SYNTHETIC data" if meta.get("contains_synthetic_data") else "real data"

    cal = rep["calibration_by_distance"]
    labels = list(cal)
    x = range(len(labels))
    fig, ax = plt.subplots(figsize=(7, 4))
    ax.bar([i - 0.2 for i in x], [cal[k]["model_coverage"] for k in labels], 0.4, label="ML model", color="#3B82F6")
    ax.bar([i + 0.2 for i in x], [cal[k]["rule_scaled_coverage"] for k in labels], 0.4, label="Rule table (scaled)", color="#F9BCD4")
    ax.axhline(0.9, color="#334155", ls="--", lw=1, label="Target 90%")
    ax.set_xticks(list(x)); ax.set_xticklabels(labels); ax.set_ylim(0.7, 1.02)
    ax.set_ylabel("Share of trips on time"); ax.set_title(f"On-time coverage by trip length ({synthetic_note})")
    ax.legend(); fig.tight_layout(); fig.savefig(out / "coverage_by_distance.png", dpi=160); plt.close(fig)

    fig, ax = plt.subplots(figsize=(7, 4))
    ax.bar([i - 0.2 for i in x], [cal[k]["model_mean_buffer"] for k in labels], 0.4, label="ML model", color="#3B82F6")
    ax.bar([i + 0.2 for i in x], [cal[k]["rule_scaled_mean_buffer"] for k in labels], 0.4, label="Rule table (scaled)", color="#F9BCD4")
    ax.set_xticks(list(x)); ax.set_xticklabels(labels); ax.set_ylabel("Mean buffer (minutes)")
    ax.set_title(f"Buffer minutes by trip length ({synthetic_note})"); ax.legend()
    fig.tight_layout(); fig.savefig(out / "buffer_by_distance.png", dpi=160); plt.close(fig)

    h = rep["holdout"]
    rows = [
        ("ML model (clipped 0-20 min)", h["model_clipped_0_20"]),
        ("Rule table (as shipped)", h["rule_table_baseline"]),
    ]
    md = [f"# Delay-risk buffer results ({synthetic_note})", "",
          f"Model `{meta['version']}`, quantile {meta['quantile']}, {meta['rows']} rows / {meta['routes']} routes. "
          "Holdout is grouped by route (unseen routes).", "",
          "| Method | Coverage | Mean buffer (min) | Pinball loss |", "| --- | --- | --- | --- |"]
    for name, m in rows:
        md.append(f"| {name} | {m['coverage']:.3f} | {m['mean_buffer_min']:.2f} | {m['pinball_q']:.3f} |")
    s = h["rule_table_scaled_to_same_coverage"]
    md.append(f"| Rule table scaled x{s['scale']} to reach the same coverage | {s['coverage']:.3f} | {s['mean_buffer_min']:.2f} | {h['pinball_q']['rule_scaled']:.3f} |")
    md += ["", f"5-fold route-grouped coverage: {rep['group_5fold_coverage_clipped']['mean']:.3f} +/- {rep['group_5fold_coverage_clipped']['std']:.3f}", ""]
    (out / "RESULTS.md").write_text("\n".join(md), encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    main()
