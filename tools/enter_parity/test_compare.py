import copy
import unittest
from compare import compare
from generate_cases import CASES

class ComparisonIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.name = CASES[0]['id']
        self.row = dict(id=self.name, status='observed', ime='reference', actual=dict(inputType=1, imeOptions=0, actionLabel=None, actionId=0),
                        text='seed\n', focus='editor', events=[dict(method='sendKeyEvent', value='0:66'),
                                                              dict(method='sendKeyEvent', value='1:66')])

    def status(self, a, b):
        b = copy.deepcopy(b)
        b['ime'] = 'target'
        return compare({self.name: a}, {self.name: b})['cases'][0]['status']

    def test_missing_cases_are_not_passes(self):
        result = compare({}, {})
        self.assertEqual({'not-run': len(CASES)}, result['counts'])

    def test_two_blocked_observations_are_not_a_match(self):
        self.row['status'] = 'blocked'
        self.assertEqual('blocked', self.status(self.row, self.row))

    def test_same_text_with_wrong_action_is_a_failure(self):
        other = copy.deepcopy(self.row)
        other['events'] = [dict(method='performEditorAction', value=6)]
        self.assertEqual('mismatch', self.status(self.row, other))

    def test_different_editor_information_invalidates_comparison(self):
        other = copy.deepcopy(self.row)
        other['actual']['imeOptions'] = 6
        self.assertEqual('editor-info-mismatch', self.status(self.row, other))

    def test_missing_actual_editor_information_is_not_a_match(self):
        self.row.pop('actual')
        self.assertEqual('editor-info-mismatch', self.status(self.row, self.row))

    def test_extra_action_is_detected(self):
        other = copy.deepcopy(self.row)
        other['events'] += [dict(method='performEditorAction', value=6)] * 2
        self.assertEqual('mismatch', self.status(self.row, other))

    def test_same_text_with_different_transport_is_a_failure(self):
        other = copy.deepcopy(self.row)
        other['events'] = [dict(method='commitText', value='\n')]
        other['ime'] = 'target'
        result = compare({self.name:self.row}, {self.name:other})['cases'][0]
        self.assertEqual('mismatch', result['status'])
        self.assertFalse(result['transportEqual'])

    def test_using_the_same_ime_twice_cannot_prove_parity(self):
        result = compare({self.name:self.row}, {self.name:self.row})['cases'][0]
        self.assertEqual('ime-mismatch', result['status'])

    def test_both_imes_receiving_the_wrong_raw_case_does_not_pass(self):
        self.row['actual']['inputType'] = 2
        self.assertEqual('editor-info-mismatch', self.status(self.row, self.row))

if __name__ == '__main__':
    unittest.main()
