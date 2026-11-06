"""Derive a finite-verb replacement table from the pinned reviewed grammar."""
import collections
import csv
import hashlib
import io
import re
import tarfile

ARCHIVE_SHA256 = '2f7375d54b71b8cd95fa821e9f49fe9eb0f6c5a330bbcd3c0cb8277bfd5b5cd3'
MEMBERSHIPS_SHA256 = '6bc4e3216d94d75c9553d086baa25a4a6b37275ca89eccf80aaf5248f7567f46'
FIRST = {'verb_present_non3sg', 'aux_be_present_1sg', 'aux_have_base', 'aux_do_base'}
THIRD = {'verb_present_3sg', 'aux_be_present_3sg', 'aux_have_3sg', 'aux_do_3sg'}
OTHER = {'verb_present_non3sg', 'aux_be_present_other', 'aux_have_base', 'aux_do_base'}
PAST_SINGULAR = {'verb_past', 'aux_be_past_1sg_3sg'}
PAST_OTHER = {'verb_past', 'aux_be_past_other'}
PRESENT = FIRST | THIRD | OTHER
PAST = PAST_SINGULAR | PAST_OTHER | {'aux_have_past', 'aux_do_past'}


def build_english_verb_forms(data):
    assert hashlib.sha256(data).hexdigest() == ARCHIVE_SHA256
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
        memberships = archive.extractfile('grammar/memberships.tsv').read()
    assert hashlib.sha256(memberships).hexdigest() == MEMBERSHIPS_SHA256
    rows = csv.DictReader(io.StringIO(memberships.decode('utf-8')), delimiter='\t')
    by_surface = collections.defaultdict(lambda: collections.defaultdict(set))
    by_lexeme = collections.defaultdict(lambda: collections.defaultdict(set))
    for row in rows:
        category, lexeme, surface = row['category'], row['lexeme_id'], row['surface']
        if category in PRESENT | PAST and re.fullmatch('[a-z]+', surface):
            by_surface[surface][lexeme].add(category)
            by_lexeme[lexeme][category].add(surface)

    def replacement(source, targets, *, past):
        roles = by_surface[source]
        if source in {'was', 'were'}:
            be_target = 'aux_be_past_other' if targets in (OTHER, PAST_OTHER) else 'aux_be_past_1sg_3sg'
            be_roles = {'aux_be_past_other', 'aux_be_past_1sg_3sg'}
            if any(be_target in categories for categories in roles.values()):
                return ''
            be_choices = {
                form for lexeme, categories in roles.items() if categories & be_roles
                for form in by_lexeme[lexeme].get(be_target, ())
            }
            return next(iter(be_choices)) if len(be_choices) == 1 else ''

        # An already-valid reading or a valid past-tense homograph takes precedence.
        if any(categories & (targets | PAST) for categories in roles.values()):
            return ''
        if past and source in {'have', 'has', 'do', 'does'}:
            return ''
        choices = set()
        reviewed_choices = set()
        for lexeme, categories in roles.items():
            if not categories & PRESENT:
                continue
            forms = {form for target in targets for form in by_lexeme[lexeme].get(target, ())}
            choices.update(forms)
            if lexeme.startswith('v-'):
                reviewed_choices.update(forms)
        # The reviewed grammar's v-* seed lexemes supply the ordinary forms of
        # common verbs such as work, even when other sources list rare variants.
        if past and len(choices) > 1 and len(reviewed_choices) == 1:
            choices = reviewed_choices
        if len(choices) == 1:
            choice = next(iter(choices))
            if choice != source:
                return choice
        return ''

    result = []
    for surface in sorted(by_surface):
        fields = [
            replacement(surface, targets, past=index >= 3)
            for index, targets in enumerate((FIRST, THIRD, OTHER, PAST_SINGULAR, PAST_OTHER))
        ]
        if any(fields):
            result.append('\t'.join([surface, *(field or '-' for field in fields)]))
    assert result
    return '\n'.join(result) + '\n'
