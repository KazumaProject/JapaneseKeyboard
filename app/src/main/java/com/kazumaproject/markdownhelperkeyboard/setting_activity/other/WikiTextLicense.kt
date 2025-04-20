package com.kazumaproject.markdownhelperkeyboard.setting_activity.other

import android.content.Context
import de.psdev.licensesdialog.licenses.License

class WikiTextLicense : License() {

    override fun getName(): String {
        return "Salesforce/wikitext"
    }

    override fun readSummaryTextFromResources(context: Context?): String {
        return "@misc{merity2016pointer,\n" +
                "      title={Pointer Sentinel Mixture Models},\n" +
                "      author={Stephen Merity and Caiming Xiong and James Bradbury and Richard Socher},\n" +
                "      year={2016},\n" +
                "      eprint={1609.07843},\n" +
                "      archivePrefix={arXiv},\n" +
                "      primaryClass={cs.CL}\n" +
                "}\n"
    }

    override fun readFullTextFromResources(context: Context?): String {
        return ""
    }

    override fun getVersion(): String {
        return ""
    }

    override fun getUrl(): String {
        return "https://huggingface.co/datasets/Salesforce/wikitext"
    }
}
