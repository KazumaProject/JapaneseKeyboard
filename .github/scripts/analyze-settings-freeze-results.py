#!/usr/bin/env python3
"""Summarize Settings Freeze Probe artifacts without third-party dependencies."""

from __future__ import annotations

import argparse
import csv
import math
import re
from collections import Counter, defaultdict
from pathlib import Path


def percentile(values: list[int], fraction: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * fraction) - 1)
    return ordered[index]


def read_instrumentation_rows(root: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for report in root.glob("cases/**/instrumentation-results.tsv"):
        with report.open(encoding="utf-8", newline="") as handle:
            rows.extend(csv.DictReader(handle, delimiter="\t"))
    return rows


def summarize_instrumentation(
    rows: list[dict[str, str]],
) -> list[dict[str, int | str]]:
    grouped: dict[tuple[str, int, str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        key = (
            row["dataset"],
            int(row["entryCount"]),
            row["homeMode"],
            row["phase"],
        )
        grouped[key].append(row)

    summaries: list[dict[str, int | str]] = []
    for key, samples in sorted(grouped.items()):
        elapsed = [int(sample["elapsedMs"]) for sample in samples]
        stalls = [int(sample["maxMainStallMs"]) for sample in samples]
        summaries.append(
            {
                "dataset": key[0],
                "entryCount": key[1],
                "homeMode": key[2],
                "phase": key[3],
                "samples": len(samples),
                "medianElapsedMs": percentile(elapsed, 0.50),
                "p95ElapsedMs": percentile(elapsed, 0.95),
                "maxElapsedMs": max(elapsed),
                "p95MainStallMs": percentile(stalls, 0.95),
                "maxMainStallMs": max(stalls),
                "stallSamples": sum(
                    sample["status"] in {"STALL", "ANR_RISK"} for sample in samples
                ),
                "anrRiskSamples": sum(
                    sample["status"] == "ANR_RISK" for sample in samples
                ),
            }
        )
    return summaries


def write_summary_tsv(root: Path, summaries: list[dict[str, int | str]]) -> None:
    columns = [
        "dataset",
        "entryCount",
        "homeMode",
        "phase",
        "samples",
        "medianElapsedMs",
        "p95ElapsedMs",
        "maxElapsedMs",
        "p95MainStallMs",
        "maxMainStallMs",
        "stallSamples",
        "anrRiskSamples",
    ]
    with (root / "analysis-summary.tsv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, delimiter="\t")
        writer.writeheader()
        writer.writerows(summaries)


def read_stack_signatures(root: Path) -> Counter[tuple[str, str, str]]:
    signatures: Counter[tuple[str, str, str]] = Counter()
    stack_files = list(root.glob("cases/**/stack-*.txt"))
    stack_files.extend(root.glob("cases/**/timeout-*.txt"))
    for stack_file in stack_files:
        text = stack_file.read_text(encoding="utf-8", errors="replace")
        phase_match = re.search(r"^phase=(\S+)", text, re.MULTILINE)
        phase = phase_match.group(1) if phase_match else "unknown"
        case_name = next(
            (part.name for part in stack_file.parents if part.parent.name == "cases"),
            "unknown-0-unknown",
        )
        dataset = case_name.split("-", 1)[0]
        main_match = re.search(
            r"^thread=main\b.*?\n(?P<frames>(?:  at .*\n?)+)",
            text,
            re.MULTILINE,
        )
        if not main_match:
            continue
        frames = [line.strip() for line in main_match.group("frames").splitlines()]
        app_frames = [frame for frame in frames if "com.kazumaproject" in frame]
        selected = app_frames[:3] if app_frames else frames[:3]
        signatures[(dataset, phase, " | ".join(selected))] += 1
    return signatures


def read_launch_times(root: Path) -> list[dict[str, int | str]]:
    samples: list[dict[str, int | str]] = []
    pattern = re.compile(
        r"^(cold-home|ime-to-settings|cache-warm-settings)-round-(\d+)\.txt$"
    )
    for output in root.glob("cases/**/*.txt"):
        match = pattern.match(output.name)
        if not match:
            continue
        text = output.read_text(encoding="utf-8", errors="replace")
        total_match = re.search(r"^TotalTime:\s*(\d+)", text, re.MULTILINE)
        if not total_match:
            continue
        samples.append(
            {
                "case": output.parent.name,
                "path": match.group(1),
                "round": int(match.group(2)),
                "totalMs": int(total_match.group(1)),
            }
        )
    return samples


def cause_matches(
    summaries: list[dict[str, int | str]],
    signatures: Counter[tuple[str, str, str]],
) -> list[dict[str, int | str]]:
    baselines: dict[tuple[str, str], int] = {}
    for row in summaries:
        if row["dataset"] == "empty":
            baselines[(str(row["homeMode"]), str(row["phase"]))] = int(
                row["maxMainStallMs"]
            )

    maximum_counts: dict[tuple[str, str, str], int] = defaultdict(int)
    for row in summaries:
        key = (str(row["dataset"]), str(row["homeMode"]), str(row["phase"]))
        maximum_counts[key] = max(maximum_counts[key], int(row["entryCount"]))

    matches: list[dict[str, int | str]] = []
    for row in summaries:
        dataset = str(row["dataset"])
        row_key = (dataset, str(row["homeMode"]), str(row["phase"]))
        if dataset == "empty" or int(row["entryCount"]) != maximum_counts[row_key]:
            continue
        phase = str(row["phase"])
        baseline = baselines.get((str(row["homeMode"]), phase))
        candidate_stall = int(row["maxMainStallMs"])
        if baseline is None or candidate_stall <= 0:
            continue
        reduction = round(100 * (candidate_stall - baseline) / candidate_stall)
        repeated = max(
            (
                count
                for (stack_dataset, stack_phase, _), count in signatures.items()
                if stack_dataset == dataset and stack_phase == phase
            ),
            default=0,
        )
        if reduction >= 50 and repeated >= 3:
            matches.append(
                {
                    "dataset": dataset,
                    "homeMode": str(row["homeMode"]),
                    "phase": phase,
                    "maxMainStallMs": candidate_stall,
                    "emptyMaxMainStallMs": baseline,
                    "reductionPercent": reduction,
                    "sameStackCount": repeated,
                }
            )
    return matches


def write_markdown(
    root: Path,
    summaries: list[dict[str, int | str]],
    signatures: Counter[tuple[str, str, str]],
    launches: list[dict[str, int | str]],
) -> None:
    matches = cause_matches(summaries, signatures)
    stalled = [row for row in summaries if int(row["stallSamples"]) > 0]
    slowest = sorted(
        summaries,
        key=lambda row: (int(row["maxMainStallMs"]), int(row["maxElapsedMs"])),
        reverse=True,
    )[:20]
    launch_groups: dict[str, list[int]] = defaultdict(list)
    for sample in launches:
        launch_groups[str(sample["path"])].append(int(sample["totalMs"]))

    scaling_groups: dict[tuple[str, str, str], list[dict[str, int | str]]] = defaultdict(list)
    for row in summaries:
        if row["dataset"] != "empty":
            scaling_groups[
                (str(row["dataset"]), str(row["homeMode"]), str(row["phase"]))
            ].append(row)
    scaling_rows: list[tuple[float, float, str, str, str, int, int]] = []
    for (dataset, home, phase), values in scaling_groups.items():
        ordered = sorted(values, key=lambda value: int(value["entryCount"]))
        if len(ordered) < 2:
            continue
        low = ordered[0]
        high = ordered[-1]
        elapsed_growth = int(high["p95ElapsedMs"]) / max(1, int(low["p95ElapsedMs"]))
        stall_growth = int(high["p95MainStallMs"]) / max(
            1, int(low["p95MainStallMs"])
        )
        scaling_rows.append(
            (
                stall_growth,
                elapsed_growth,
                dataset,
                home,
                phase,
                int(low["entryCount"]),
                int(high["entryCount"]),
            )
        )
    scaling_rows.sort(reverse=True)

    lines = [
        "# 設定画面フリーズ診断レポート",
        "",
        f"- instrumentation測定行: {sum(int(row['samples']) for row in summaries)}",
        f"- 1秒以上の停止を含む集計群: {len(stalled)}",
        f"- 同一メインスレッドスタックが3回以上の署名: "
        f"{sum(count >= 3 for count in signatures.values())}",
        f"- 原因特定基準を満たした候補: {len(matches)}",
        "",
        "## 原因特定基準",
        "",
    ]
    if matches:
        lines.extend(
            [
                "同一スタックが3回以上観測され、空データにすると最大停止時間が50%以上減る候補です。",
                "",
                "| dataset | home | phase | max stall | empty | reduction | same stack |",
                "|---|---|---|---:|---:|---:|---:|",
            ]
        )
        for match in matches:
            lines.append(
                f"| {match['dataset']} | {match['homeMode']} | {match['phase']} | "
                f"{match['maxMainStallMs']} ms | {match['emptyMaxMainStallMs']} ms | "
                f"{match['reductionPercent']}% | {match['sameStackCount']} |"
            )
    else:
        lines.extend(
            [
                "今回の採取結果だけでは基準を満たす候補はありません。再現しなかった場合も、"
                "辞書の直接関与を断定的に否定するものではありません。",
                "",
            ]
        )

    lines.extend(
        [
            "",
            "## 停止時間の大きい測定",
            "",
            "| dataset | count | home | phase | p95 elapsed | max elapsed | p95 stall | max stall | flags |",
            "|---|---:|---|---|---:|---:|---:|---:|---:|",
        ]
    )
    for row in slowest:
        lines.append(
            f"| {row['dataset']} | {row['entryCount']} | {row['homeMode']} | {row['phase']} | "
            f"{row['p95ElapsedMs']} ms | {row['maxElapsedMs']} ms | "
            f"{row['p95MainStallMs']} ms | {row['maxMainStallMs']} ms | "
                f"{row['stallSamples']} |"
        )

    lines.extend(
        [
            "",
            "## 件数増加との連動",
            "",
            "| dataset | home | phase | count range | p95 elapsed growth | p95 stall growth |",
            "|---|---|---|---:|---:|---:|",
        ]
    )
    for (
        stall_growth,
        elapsed_growth,
        dataset,
        home,
        phase,
        low_count,
        high_count,
    ) in scaling_rows[:20]:
        lines.append(
            f"| {dataset} | {home} | {phase} | {low_count}→{high_count} | "
            f"{elapsed_growth:.2f}x | {stall_growth:.2f}x |"
        )

    lines.extend(["", "## ADB起動時間", ""])
    if launch_groups:
        lines.extend(
            [
                "| path | samples | median | p95 | max |",
                "|---|---:|---:|---:|---:|",
            ]
        )
        for path, values in sorted(launch_groups.items()):
            lines.append(
                f"| {path} | {len(values)} | {percentile(values, 0.50)} ms | "
                f"{percentile(values, 0.95)} ms | {max(values)} ms |"
            )
    else:
        lines.append("`am start -W`の完了データはありません。")

    repeated_signatures = [
        (key, count) for key, count in signatures.most_common() if count >= 3
    ]
    cross_dataset_signatures: Counter[tuple[str, str]] = Counter()
    cross_dataset_names: dict[tuple[str, str], set[str]] = defaultdict(set)
    for (dataset, phase, signature), count in signatures.items():
        key = (phase, signature)
        cross_dataset_signatures[key] += count
        cross_dataset_names[key].add(dataset)
    repeated_cross_dataset = [
        (key, count)
        for key, count in cross_dataset_signatures.most_common()
        if count >= 3 and len(cross_dataset_names[key]) >= 2
    ]
    lines.extend(["", "## 反復したメインスレッドスタック", ""])
    if repeated_signatures:
        for (dataset, phase, signature), count in repeated_signatures:
            lines.append(f"- `{dataset}` / `{phase}` / {count}回: `{signature}`")
    else:
        lines.append("同一署名が3回以上観測されたスタックはありません。")

    lines.extend(["", "## データ種別をまたぐ共通スタック", ""])
    if repeated_cross_dataset:
        lines.append(
            "複数のデータ種別で反復したため、辞書内容より共通の画面初期化・描画処理との関連が強い署名です。"
        )
        lines.append("")
        for (phase, signature), count in repeated_cross_dataset:
            datasets = ", ".join(sorted(cross_dataset_names[(phase, signature)]))
            lines.append(
                f"- `{phase}` / {count}回 / datasets: `{datasets}`: `{signature}`"
            )
    else:
        lines.append("複数データ種別に共通して3回以上観測された署名はありません。")

    lines.extend(
        [
            "",
            "## 判定上の注意",
            "",
            "このレポートの50%比較は、同じhome/phaseの空データを対照にしています。"
            "最大負荷で再現しない場合は、実端末のANR traces、bugreport、"
            "端末固有のIME切替・メモリ圧迫を追加確認してください。",
            "",
        ]
    )
    (root / "analysis.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("artifact_dir", type=Path)
    args = parser.parse_args()
    root = args.artifact_dir.resolve()
    root.mkdir(parents=True, exist_ok=True)
    rows = read_instrumentation_rows(root)
    summaries = summarize_instrumentation(rows)
    signatures = read_stack_signatures(root)
    launches = read_launch_times(root)
    write_summary_tsv(root, summaries)
    write_markdown(root, summaries, signatures, launches)
    print(
        f"Analyzed {len(rows)} instrumentation rows, {len(launches)} launch samples, "
        f"and {sum(signatures.values())} thread dumps."
    )


if __name__ == "__main__":
    main()
