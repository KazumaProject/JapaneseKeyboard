#!/usr/bin/env python3
"""Checks that corrupt captures and missing/out-of-budget trials cannot pass."""

import importlib.util
import struct
import tempfile
import unittest
from pathlib import Path


def module(name):
    spec = importlib.util.spec_from_file_location(
        name, Path(__file__).with_name(name + ".py")
    )
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


verify = module("verify-direct-motion")
comparison = module("compare-direct-motion")


class DirectMotionTest(unittest.TestCase):
    def parse(self, data):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "recording.mp4"
            path.write_bytes(data)
            return verify.winscope_times(path)

    def test_source_clock_is_read_without_video_pts(self):
        data = (
            b"arbitrary-mp4-boxes"
            + b"#VV1NSC0PET1ME2#"
            + struct.pack("<IQIQQ", 2, 900, 2, 1000000000, 1020000000)
        )
        self.assertEqual([1.0, 1.02], self.parse(data).tolist())

    def test_duplicate_missing_truncated_and_nonmonotonic_metadata_fail(self):
        magic = b"#VV1NSC0PET1ME2#"
        valid = magic + struct.pack("<IQIQQ", 2, 0, 2, 100, 200)
        for data in (
            b"no-metadata",
            valid + valid,
            valid[:-3],
            magic + struct.pack("<IQIQQ", 2, 0, 2, 200, 100),
        ):
            with self.subTest(data=data), self.assertRaises((ValueError, struct.error)):
                self.parse(data)

    def sample(self, timestamp):
        return {
            "kind": "qwerty",
            "mode": "light",
            "label": "q",
            "trial": 0,
            "dismissal": {"bracket_ms": [timestamp - 10, timestamp]},
        }

    def test_does_not_hide_failure_by_averaging(self):
        a = self.sample(80)
        b = self.sample(100)
        report = comparison.compare({"gestures": [a]}, {"gestures": [b]})
        self.assertFalse(report["passed"])

    def test_missing_and_duplicate_trials_fail(self):
        sample = self.sample(80)
        for rows in ([], [sample, sample]):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                comparison.compare({"gestures": [sample]}, {"gestures": rows})

    def test_empty_reports_cannot_claim_success(self):
        with self.assertRaises(ValueError):
            comparison.compare({"gestures": []}, {"gestures": []})

    def test_identical_transitions_pass(self):
        report = {"gestures": [self.sample(80)]}
        self.assertTrue(comparison.compare(report, report)["passed"])


if __name__ == "__main__":
    unittest.main()
