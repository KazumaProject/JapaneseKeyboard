"""Regression cases for APK/source mismatches reported in issue #1293."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('release_source', Path(__file__).with_name('verify-release-source.py'))
release_source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_source)

RELEASED = 'cb4b8006d6c9055d879012df487fa969502912f7'
REBUILT = 'f9bef456174a43b660a2be6bdac943e25a73cdbb'


class ReleaseSourceTest(unittest.TestCase):
    def apk(self, directory, metadata):
        path = Path(directory) / 'app.apk'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('classes.dex', b'dex')
            if metadata is not None:
                archive.writestr('META-INF/version-control-info.textproto', metadata)
        return path

    def test_matching_apk_passes(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            path = self.apk(directory, f'repositories {{\n  revision: "{RELEASED}"\n}}\n')
            release_source.verify_apk(path, RELEASED)

    def test_original_issue_mismatch_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.apk(directory, f'revision: "{RELEASED}"\n')
            with self.assertRaisesRegex(ValueError, 'expected revision'):
                release_source.verify_apk(path, REBUILT)

    def test_missing_metadata_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'missing version-control-info'):
                release_source.verify_apk(self.apk(directory, None), RELEASED)

    def test_missing_malformed_or_ambiguous_revision_fails(self):
        for metadata in ('repositories {}', 'generate_error_reason: NO_VALID_GIT_FOUND\n',
                         'revision: "short"',
                         f'revision: "{RELEASED}"\nrevision: "{REBUILT}"\n'):
            with self.subTest(metadata=metadata), tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(ValueError, 'expected revision'):
                    release_source.verify_apk(self.apk(directory, metadata), RELEASED)

    def test_lightweight_tag_passes(self):
        output = f'{RELEASED}\trefs/tags/v1.7.149\n'
        with patch.object(release_source.subprocess, 'check_output', return_value=output), contextlib.redirect_stdout(io.StringIO()):
            release_source.verify_tag('v1.7.149', RELEASED)

    def test_annotated_tag_uses_peeled_commit(self):
        output = f'{REBUILT}\trefs/tags/v1.7.149\n{RELEASED}\trefs/tags/v1.7.149^{{}}\n'
        with patch.object(release_source.subprocess, 'check_output', return_value=output), contextlib.redirect_stdout(io.StringIO()):
            release_source.verify_tag('v1.7.149', RELEASED)

    def test_moved_tag_fails(self):
        output = f'{REBUILT}\trefs/tags/v1.7.149\n'
        with patch.object(release_source.subprocess, 'check_output', return_value=output):
            with self.assertRaisesRegex(ValueError, 'publish a new version/tag'):
                release_source.verify_tag('v1.7.149', RELEASED)

    def test_missing_tag_fails(self):
        with patch.object(release_source.subprocess, 'check_output', return_value=''):
            with self.assertRaisesRegex(ValueError, 'publish a new version/tag'):
                release_source.verify_tag('v1.7.149', RELEASED)


if __name__ == '__main__':
    unittest.main()
