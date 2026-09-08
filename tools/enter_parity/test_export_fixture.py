import unittest
from export_fixture import export, observed_action


class ObservationExportTest(unittest.TestCase):
    def test_partial_capture_cannot_be_exported_as_reference(self):
        with self.assertRaisesRegex(ValueError, 'Missing successful'):
            export({})

    def test_action_plus_key_is_not_a_single_action(self):
        with self.assertRaisesRegex(ValueError, 'Unclassified'):
            observed_action(dict(id='mixed', events=[dict(method='performEditorAction', value=6),
                                                     dict(method='sendKeyEvent', value='0:66')]))

    def test_action_plus_commit_is_not_a_single_action(self):
        with self.assertRaisesRegex(ValueError, 'Unclassified'):
            observed_action(dict(id='mixed', events=[dict(method='performEditorAction', value=6),
                                                     dict(method='commitText', value='\n')]))

    def test_duplicate_enter_pair_is_not_one_enter(self):
        with self.assertRaisesRegex(ValueError, 'Unclassified'):
            observed_action(dict(id='twice', events=[dict(method='sendKeyEvent', value='0:66'),
                                                     dict(method='sendKeyEvent', value='1:66')] * 2))
