import copy
import unittest
from compare import differences


def fixture():
    before = dict(text="abc", selectionStart=3, selectionEnd=3, composingStart=-1,
                  composingEnd=-1, actions=[], calls=[])
    after = dict(before, text="abc\n", selectionStart=4, selectionEnd=4)
    return dict(status="reviewed", ime="com.google.android.inputmethod.latin/IME", caseId="test",
                editorInfo={}, operation="tap", androidSdk=37, before=before, after=[after])


class ComparisonTest(unittest.TestCase):
    def test_equivalent_results_ignore_internal_call_sequences(self):
        expected = fixture()
        actual = copy.deepcopy(expected)
        actual["after"][0]["calls"] = ["sendKeyEvent:0:66", "sendKeyEvent:1:66"]
        self.assertEqual([], differences(expected, actual))

    def test_missing_newline_is_detected(self):
        actual = fixture()
        actual["after"][0]["text"] = "abc"
        self.assertTrue(differences(fixture(), actual))

    def test_wrong_action_and_double_dispatch_are_detected(self):
        for actions in ([3], [6], [3, 3]):
            actual = fixture()
            actual["after"][0]["actions"] = actions
            self.assertTrue(differences(fixture(), actual))

    def test_commit_must_not_also_send(self):
        expected = fixture()
        expected["before"]["composingStart"] = 0
        expected["before"]["composingEnd"] = 3
        expected["after"][0].update(text="abc", selectionStart=3, selectionEnd=3)
        actual = copy.deepcopy(expected)
        actual["after"][0]["actions"] = [4]
        self.assertTrue(differences(expected, actual))

    def test_each_press_is_checked_not_just_final_state(self):
        expected = fixture()
        expected["after"].append(copy.deepcopy(expected["after"][0]))
        actual = copy.deepcopy(expected)
        actual["after"][0]["text"] = "wrong"
        self.assertTrue(differences(expected, actual))

    def test_unreviewed_and_different_initial_conditions_are_not_passes(self):
        expected = fixture()
        expected["status"] = "observed-unreviewed"
        self.assertTrue(differences(expected, fixture()))
        actual = fixture()
        actual["before"]["selectionStart"] = 0
        self.assertTrue(differences(fixture(), actual))

    def test_failed_recording_is_not_a_pass(self):
        actual = fixture()
        actual["operation"] = "FAILED:tap"
        self.assertTrue(differences(fixture(), actual))


if __name__ == "__main__":
    unittest.main()
