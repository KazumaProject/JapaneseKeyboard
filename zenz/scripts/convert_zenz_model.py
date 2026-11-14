#!/usr/bin/env python3
"""Use the upstream converter with the legacy Zenz tokenizer fingerprint."""

from hashlib import sha256
from pathlib import Path
import sys


def main():
    llama_dir = Path(sys.argv.pop(1)).resolve()
    sys.path[:0] = [str(llama_dir), str(llama_dir / "gguf-py")]
    import convert_hf_to_gguf
    from conversion.base import ModelBase
    from conversion.gpt2 import GPT2Model

    @ModelBase.register("GPT2LMHeadModel")
    class ZenzGPT2Model(GPT2Model):
        model_arch = GPT2Model.model_arch

        def get_vocab_base_pre(self, tokenizer):
            # Same fingerprint probe as upstream and the original azooKey fork.
            chktxt = '\n \n\n \n\n\n \t \t\t \t\n  \n   \n    \n     \n🚀 (normal) 😶\u200d🌫️ (multiple emojis concatenated) ✅ 🦙🦙 3 33 333 3333 33333 333333 3333333 33333333 3.3 3..3 3...3 កាន់តែពិសេសអាច😁 ?我想在apple工作1314151天～ ------======= нещо на Български \'\'\'\'\'\'```````""""......!!!!!!?????? I\'ve been \'told he\'s there, \'RE you sure? \'M not sure I\'ll make it, \'D you like some tea? We\'Ve a\'lL'
            fingerprint = sha256(str(tokenizer.encode(chktxt)).encode()).hexdigest()
            self.legacy_zenz_vocab = fingerprint == "acded0362f57dcca1fbdada8d4c87dd782c798cb4c510a70f12a9de6aea57857"
            if self.legacy_zenz_vocab:
                self.zenz_special_ids = [tokenizer.get_vocab()[piece] for piece in ("[UNK]", "[PAD]", "<s>", "</s>")]
                return "gpt-2"
            return super().get_vocab_base_pre(tokenizer)

        def set_vocab(self):
            super().set_vocab()
            if getattr(self, "legacy_zenz_vocab", False):
                unk, pad, bos, eos = self.zenz_special_ids
                self.gguf_writer.add_unk_token_id(unk)
                self.gguf_writer.add_pad_token_id(pad)
                self.gguf_writer.add_bos_token_id(bos)
                self.gguf_writer.add_eos_token_id(eos)

    convert_hf_to_gguf.main()


if __name__ == "__main__":
    main()
