# TYPE_NULL input behavior manual checks

## Termux + TYPE_NULL + default

- Set `TYPE_NULL 入力欄の動作` to `デフォルト（直接入力）`.
- Open Termux and use the QWERTY English keyboard.
- Type `c`, `o`, `w`, `s`, `a`, `y`.
- Confirm each character appears in Termux immediately before Enter.
- Confirm the candidate strip does not accumulate `cowsay`.
- Press Enter and confirm the command runs.
- Press Backspace and confirm Termux deletes the character in the terminal.
- Repeat with `直接入力` and confirm the same behavior.

## Termux + TYPE_NULL + ComposingText override

- Set `TYPE_NULL 入力欄の動作` to `ComposingText を使う`.
- Open Termux and type `cowsay`.
- Confirm input accumulates in the composing buffer/candidate strip as before.
- Press Enter and confirm the text is committed.

## Normal EditText

- Confirm Japanese input still uses composing text.
- Confirm conversion candidates are shown.
- Confirm live conversion still works when enabled.
- Confirm Zenzai, Gemma, and bunsetsu separation behavior is unchanged.
- Confirm Enter, Done, and Search actions keep their existing behavior.

## Password fields

- Confirm existing password-field settings are unchanged.
- Confirm `TYPE_NULL 入力欄の動作` does not affect password fields.

## Number and phone fields

- Confirm number fields keep the existing behavior.
- Confirm phone fields keep the existing behavior.
