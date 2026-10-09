#!/usr/bin/env python3
"""Summarize saved raw device samples without third-party Python dependencies."""
import argparse
import gzip
import json
import math
import pathlib
import re
from collections import defaultdict

parser=argparse.ArgumentParser()
parser.add_argument("directory",type=pathlib.Path)
parser.add_argument("--output",required=True,type=pathlib.Path)
args=parser.parse_args()
pools=defaultdict(list)
runs={}

def stats(samples):
    values=sorted(samples)
    def p(fraction): return values[max(0,math.ceil(len(values)*fraction)-1)] / 1_000_000
    return {"count":len(values),"p50Ms":p(.50),"p95Ms":p(.95),"p99Ms":p(.99),"maxMs":values[-1]/1_000_000}

for path in sorted(args.directory.glob("*.txt.gz")):
    label=path.name.removesuffix(".txt.gz")
    group=label.rsplit("-",1)[0]
    lines=gzip.decompress(path.read_bytes()).decode().splitlines()
    runs[label]={"metadata":[],"latency":{}}
    for line in lines:
        if line.startswith("raw "):
            _,mode,reading,data=line.split(" ",3)
            samples=[int(v) for v in data.split(",")]
            pools[(group,mode,reading)].extend(samples)
            runs[label]["latency"][mode+"/"+reading]=stats(samples)
        elif not line.startswith("latency "):
            runs[label]["metadata"].append(line)

summary={"percentile":"nearest rank; three equally sized fresh-process runs pooled; timings in ms", "runs":runs,"pooled":{}}
for (group,mode,reading),samples in sorted(pools.items()):
    summary["pooled"].setdefault(group,{}).setdefault(mode,{})[reading]=stats(samples)
args.output.write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n")
print("Wrote",args.output,"from",len(runs),"runs")
