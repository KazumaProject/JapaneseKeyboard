package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.opensource

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.BaseExpandableListAdapter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentOpenSourceBinding
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

class OpenSourceFragment : Fragment() {
    private var _binding: FragmentOpenSourceBinding? = null
    private val binding get() = _binding!!
    private var licenseDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentOpenSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarAndMenu()
        val catalog = OpenSourceLicenses(requireContext())
        val sections = catalog.sections()
        val header = layoutInflater.inflate(R.layout.header_licenses, binding.openSourceLicenseList, false)
        header.findViewById<TextView>(R.id.license_intro).setText(
            if (AppVariantConfig.hasZenz) R.string.license_intro else R.string.license_intro_lite)
        header.findViewById<View>(R.id.license_app).setOnClickListener { showDocument(catalog.application()) }
        binding.openSourceLicenseList.apply {
            addHeaderView(header, null, false)
            setAdapter(object : BaseExpandableListAdapter() {
                override fun getGroupCount() = sections.size
                override fun getChildrenCount(group: Int) = sections[group].items.size
                override fun getGroup(group: Int) = sections[group]
                override fun getChild(group: Int, child: Int) = sections[group].items[child]
                override fun getGroupId(group: Int) = group.toLong()
                override fun getChildId(group: Int, child: Int) = child.toLong()
                override fun hasStableIds() = false
                override fun isChildSelectable(group: Int, child: Int) = true
                override fun getGroupView(group: Int, expanded: Boolean, recycled: View?, parent: ViewGroup): View =
                    row(recycled, parent, sections[group].title,
                        getString(R.string.license_section_count, sections[group].items.size), if (expanded) "−" else "+", true)
                override fun getChildView(group: Int, child: Int, last: Boolean, recycled: View?, parent: ViewGroup): View {
                    val item = getChild(group, child)
                    return row(recycled, parent, item.title, item.summary, "›", false)
                }
            })
            setOnChildClickListener { _, _, group, child, _ ->
                showDocument(sections[group].items[child])
                true
            }
        }
    }

    private fun row(recycled: View?, parent: ViewGroup, title: String, summary: String, arrow: String, heading: Boolean): View =
        (recycled ?: layoutInflater.inflate(R.layout.item_license, parent, false)).apply {
            findViewById<TextView>(R.id.license_title).apply {
                text = title
                setTypeface(null, if (heading) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                androidx.core.view.ViewCompat.setAccessibilityHeading(this, heading)
            }
            findViewById<TextView>(R.id.license_summary).text = summary
            findViewById<TextView>(R.id.license_arrow).text = arrow
        }

    private fun showDocument(item: LicenseItem) {
        licenseDialog?.dismiss()
        val webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.scheme in listOf("https", "http")) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        catch (_: ActivityNotFoundException) { /* The offline document remains available. */ }
                    }
                    return true
                }
            }
            setBackgroundColor(Color.TRANSPARENT)
        }
        val ink = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface)
        val link = MaterialColors.getColor(requireView(), androidx.appcompat.R.attr.colorPrimary)
        val style = """
            <style>
            body{font:16px sans-serif;line-height:1.6;padding:8px 16px;overflow-wrap:anywhere;color:#%06x}
            h1{font-size:20px;line-height:1.4}h2{font-size:17px;margin-top:24px}h3{font-size:15px}
            html[lang=ja] .translation[lang=en],html:not([lang=ja]) .translation[lang=ja]{display:none}
            a{color:#%06x}pre{white-space:pre-wrap;font:13px monospace;line-height:1.5}
            </style>
        """.trimIndent().format(ink and 0xffffff, link and 0xffffff)
        val content = "<!doctype html><html lang=\"${resources.configuration.locales[0].language}\"><head>" +
            "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">$style</head><body>" +
            item.document().replace(Regex("<style>.*?</style>|<!doctype[^>]*>|<meta[^>]*>|<title>.*?</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "") + "</body></html>"
        webView.loadDataWithBaseURL("https://licenses.invalid/", content, "text/html", "UTF-8", null)
        val viewportHeight = (resources.displayMetrics.heightPixels * 0.60f).toInt()
        val viewport = FrameLayout(requireContext()).apply {
            addView(webView, FrameLayout.LayoutParams(-1, viewportHeight))
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setView(viewport)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener {
                webView.destroy()
                if (licenseDialog === dialog) licenseDialog = null
            }
            .create()
        licenseDialog = dialog
        dialog.show()
    }

    override fun onDestroyView() {
        licenseDialog?.dismiss()
        licenseDialog = null
        binding.openSourceLicenseList.setOnChildClickListener(null)
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbarAndMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.open_sorce_title)
            setDisplayHomeAsUpEnabled(true)
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}
