package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

class MozcCompatibleConverter(
    private val dictionary: MozcDictionary,
    private val unknownNodeGenerator: MozcUnknownNodeGenerator,
    private val prefixSuffixPenalty: MozcPrefixSuffixPenalty,
    private val resegmenter: MozcResegmenter,
    private val viterbi: MozcViterbi,
    private val nBestGenerator: MozcNBestGenerator,
    private val candidateConverter: MozcCandidateConverter = MozcCandidateConverter(),
    private val candidateFilter: MozcCandidateFilter = MozcCandidateFilter(),
    private val trace: MozcConverterTrace? = null,
) : MozcCandidateProvider {

    override fun getCandidates(
        input: String,
        options: MozcConversionOptions,
    ): List<Candidate> {
        if (input.isEmpty()) return emptyList()

        val lattice = MozcLattice(input)
        for (position in input.indices) {
            val builder = MozcNodeListBuilder()
            dictionary.lookupPrefix(
                key = input,
                beginPos = position,
                options = options,
                builder = builder,
            )
            unknownNodeGenerator.addCharacterTypeBasedNodes(
                key = input,
                beginPos = position,
                builder = builder,
            )
            builder.insertInto(lattice)
        }

        prefixSuffixPenalty.apply(lattice, options)
        resegmenter.resegment(lattice, options)

        check(viterbi.calculate(lattice)) {
            "MozcCompatibleConverter failed to connect BOS to EOS for '$input'"
        }

        val paths = nBestGenerator.generate(lattice, options)
        return candidateFilter.filter(candidateConverter.convert(paths))
    }

    fun trace(): MozcConverterTrace? = trace
}
