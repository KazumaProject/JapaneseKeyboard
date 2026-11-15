package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.KeyHitTestMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.editor.EditorEnterPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.editor.EditorEnterAction
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.ShortcutAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.morse.MorsePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.min
import kotlin.math.roundToInt

/** Fresh native keyboard views. The preview intercepts touches without attaching IME input callbacks. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class KeyboardPreviewView(
    context: Context,
    var target: KeyboardPreviewTarget,
    private val skinOverride: KeyboardSkinId? = null,
) : ViewGroup(context) {
    var landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var sizeOverride: IndependentKeyboardSize? = null
    var widthOverride: Int? = null
    var alignEndOverride: Boolean? = null
    var geometryOverride: KeyboardGeometryPreference? = null
    var customOverride: KeyboardLayout? = null
    var includeCandidates = true
    var respectOrientationOverride = true
    var respectSavedInputMode = true
    var visualType: KeyboardType = target.type; private set
    var previewInputMode: InputMode = InputMode.ModeJapanese; private set
    var onReady: (() -> Unit)? = null
    var ready = false; private set
    var loading = false; private set
    var resolvedLayout: KeyboardLayout? = null; private set
    var nativeBody: View? = null; private set
    private var scope: CoroutineScope? = null
    private var attachmentScope: CoroutineScope? = null
    private var renderedOrientation: Int? = null
    private var loadJob: Job? = null
    private var scene: View? = null
    private var logicalWidth = 1
    private var logicalHeight = 1
    private var body = RectF()
    private class RenderResources {
        val releases = mutableListOf<() -> Unit>()
        val players = mutableListOf<ExoPlayer>()
        fun release() {
            val pending = releases.toList()
            releases.clear(); players.clear()
            pending.forEach { it() }
        }
    }
    private var activeResources = RenderResources()
    private var loadingResources: RenderResources? = null
    private fun players() = activeResources.players + loadingResources?.players.orEmpty()
    private var playbackActive = true
    fun setPlaybackActive(active: Boolean) {
        playbackActive = active
        players().forEach { it.playWhenReady = active && windowVisibility == VISIBLE }
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        players().forEach { it.playWhenReady = playbackActive && visibility == VISIBLE }
    }
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val density get() = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { observer ->
            observer.launch { KeyboardLayoutInflation.mainOrientation.collect { orientation ->
                val requested = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                if (ready && visualType != KeyboardType.SPLIT && renderedOrientation != (orientation ?: requested)) reload()
            } }
        }
        reload()
    }
    override fun onDetachedFromWindow() {
        attachmentScope?.cancel(); attachmentScope = null
        release(); super.onDetachedFromWindow()
    }
    fun release() {
        scope?.cancel(); scope = null
        activeResources.release(); loadingResources?.release(); loadingResources = null
        ready = false; loading = false
    }
    fun reload() {
        if (!isAttachedToWindow) return
        loadJob?.cancel()
        val incoming = RenderResources()
        loadingResources = incoming
        ready = false; loading = true
        if (scene == null) {
            removeAllViews()
            addView(ProgressBar(context), LayoutParams(dp(32), dp(32)))
        }
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        loadJob = scope!!.launch {
            try {
                val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, KeyboardPreviewDependencies::class.java)
                val data = withContext(Dispatchers.IO) {
                    dependencies.fonts().loadIfNeeded()
                    val layouts = dependencies.keyboards().getLayoutsNotFlow()
                    val requestedType = if (respectOrientationOverride) requestedKeyboardForOrientation(target.type, landscape,
                        AppPreference.landscape_force_qwerty_preference, AppPreference.landscape_force_qwerty_romaji_preference) else target.type
                    val displayType = if (respectOrientationOverride) resolveKeyboardDisplay(requestedType, AppPreference.keyboard_order).resolvedKeyboard else target.type
                    val customRow = if (displayType == KeyboardType.CUSTOM) {
                        if (target.type == KeyboardType.CUSTOM && (target.customStableId != null || target.customLayoutId != null)) layouts.firstOrNull(target::matches)
                        else resolveInitialCustomKeyboardSelection(layouts, AppPreference.remember_last_custom_keyboard_preference == true,
                            AppPreference.last_used_custom_keyboard_stable_id)?.let { layouts[it.index] }
                    } else null
                    val displayTarget = target.copy(type = displayType, customStableId = customRow?.stableId, customLayoutId = customRow?.layoutId)
                    val custom = customRow?.let {
                        dependencies.keyboards().convertLayout(dependencies.keyboards().getFullLayout(it.layoutId).first())
                    }
                    PreviewData(layouts, customOverride ?: custom,
                        dependencies.sumire().observeAllActionOverrides().first(),
                        dependencies.sumire().observeAllPlacementOverrides().first(),
                        dependencies.shortcuts().getShortcutsForBackup().filter { it != ShortcutType.GEMMA_HANDWRITING ||
                            (com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig.hasGemma &&
                                dependencies.gemma().imageInputCapability() is com.kazumaproject.markdownhelperkeyboard.gemma.GemmaImageCapability.Available) }, displayTarget, incoming)
                }
                ensureActive()
                val configuration = Configuration(resources.configuration).apply {
                    val requested = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                    orientation = if (data.displayTarget.type == KeyboardType.SPLIT) requested
                        else KeyboardLayoutInflation.mainOrientation.value ?: requested
                }
                renderedOrientation = configuration.orientation
                val ctx = KeyboardPresentationContext.create(context.applicationContext.createConfigurationContext(configuration), skinOverride ?: AppPreference.keyboardSkin)
                val screenWidth = keyboardPresentationWidth(ctx, this@KeyboardPreviewView, landscape)
                val result = if (data.displayTarget.type == KeyboardType.SPLIT) createSplit(ctx, data, screenWidth)
                    else createSurface(ctx, data.displayTarget, data, screenWidth)
                ensureActive()
                removeAllViews()
                val previous = activeResources
                activeResources = incoming
                if (loadingResources === incoming) loadingResources = null
                logicalWidth = screenWidth
                visualType = result.displayType
                scene = result.view
                logicalHeight = result.height
                body = result.body
                addView(result.view)
                previous.release()
                ready = true; loading = false
                requestLayout()
                onReady?.invoke()
            } catch (e: CancellationException) {
                incoming.release()
                if (loadingResources === incoming) loadingResources = null
                throw e
            }
            catch (e: Exception) {
                incoming.release()
                if (loadingResources === incoming) loadingResources = null
                android.util.Log.e("KeyboardPreview", "Cannot render ${target.id}", e)
                showUnavailable()
            }
        }
    }

    private fun showUnavailable() {
        activeResources.release(); loadingResources?.release(); loadingResources = null
        ready = false; loading = false; nativeBody = null; resolvedLayout = null; scene = null
        removeAllViews()
        addView(TextView(context).apply {
            setText(R.string.keyboard_preview_unavailable); gravity = Gravity.CENTER
            setOnClickListener { reload() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        requestLayout()
    }

    private suspend fun createSurface(ctx: Context, target: KeyboardPreviewTarget, data: PreviewData,
        screenWidth: Int, forcedSize: IndependentKeyboardSize? = null, showCandidates: Boolean = includeCandidates,
        candidateFamily: KeyboardType = target.type): Surface {
        val style = KeyboardVisualStyle.read(ctx, skinOverride ?: AppPreference.keyboardSkin)
        val skin = KeyboardSkinRegistry.find(style.skin)
        val binding = MainLayoutBinding.inflate(LayoutInflater.from(ctx))
        val root = binding.root
        val restored = if (respectSavedInputMode && forcedSize == null) KeyboardRestartPresentation.resolve(target.type) else null
        val inputMode = target.inputMode ?: restored?.inputMode ?: InputMode.ModeJapanese
        val flickMode = when (inputMode) {
            InputMode.ModeEnglish -> KeyboardInputMode.ENGLISH
            InputMode.ModeNumber -> KeyboardInputMode.SYMBOLS
            else -> KeyboardInputMode.HIRAGANA
        }
        val englishProxy = inputMode == InputMode.ModeEnglish && when (target.type) {
            KeyboardType.TENKEY, KeyboardType.GOJUON -> TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                ctx.resources.getBoolean(com.kazumaproject.core.R.bool.isTablet), AppPreference.tablet_tenkey_qwerty_switch_english_layout,
                AppPreference.tenkey_qwerty_switch_number_layout == true)
            KeyboardType.SUMIRE -> AppPreference.sumire_english_qwerty_preference || (if (target.inputMode != null) target.presentation == com.kazumaproject.markdownhelperkeyboard.ime_service.RestartInputModePreference.SUMIRE_QWERTY_PROXY
                else restored?.presentation == RestartInputModePresentation.SumireQwertyProxy)
            else -> false
        }
        val numberProxy = if (target.inputMode != null) target.presentation == com.kazumaproject.markdownhelperkeyboard.ime_service.RestartInputModePreference.TENKEY_QWERTY_NUMBER_PROXY
            else restored?.presentation == RestartInputModePresentation.TenkeyQwertyNumberProxy
        val bodyType = if (englishProxy || numberProxy) KeyboardType.QWERTY else target.type
        previewInputMode = when (bodyType) {
            KeyboardType.QWERTY -> if (numberProxy) InputMode.ModeNumber else InputMode.ModeEnglish
            KeyboardType.ROMAJI -> target.inputMode ?: if (AppPreference.qwerty_romaji_initial_input_mode_preference == "english") InputMode.ModeEnglish else InputMode.ModeJapanese
            else -> inputMode
        }
        val baseSize = forcedSize ?: sizeOverride ?: KeyboardSurfaceSizePreferences(prefs).read(
            KeyboardSurfaceSizeTarget.valueOf(bodyType.name), landscape)
        val size = baseSize.copy(widthPercent = widthOverride ?: baseSize.widthPercent, positionEnd = alignEndOverride ?: baseSize.positionEnd)
        val geometry = if (forcedSize != null) KeyboardGeometryPreference() else geometryOverride ?: KeyboardGeometryPreferences(prefs).read(when (bodyType) {
            KeyboardType.QWERTY, KeyboardType.ROMAJI -> GeometryKeyboard.QWERTY
            KeyboardType.SUMIRE -> GeometryKeyboard.SUMIRE
            else -> GeometryKeyboard.TENKEY
        }, landscape)
        val surfaceWidth = minOf(screenWidth - dp(size.marginStartDp + size.marginEndDp), screenWidth * size.widthPercent / 100).coerceAtLeast(1)
        val left = if (size.positionEnd) screenWidth - dp(size.marginEndDp) - surfaceWidth else dp(size.marginStartDp)
        val height = dp(size.heightDp)
        var updateGeometryHeight: ((Int) -> Unit)? = null
        val onGeometryHeight: (Int) -> Unit = { updateGeometryHeight?.invoke(it) }
        val sideWidth = if (bodyType in listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE) && geometry.candidates != GeometryCandidatePlacement.TOP)
            SideCandidateSizing.width(surfaceWidth, density, geometry.sideCandidateWidthPercent) else 0
        val keyboardWidth = surfaceWidth - sideWidth
        val keyboardLeft = if (sideWidth > 0 && geometry.candidates == GeometryCandidatePlacement.LEFT) sideWidth else 0
        val actualBody: View = when (bodyType) {
            KeyboardType.TENKEY -> binding.keyboardView.apply {
                style.apply(this); KeyboardKeyPresentation.tenkey(this)
                setCurrentMode(inputMode)
                setKeyboardGeometry(geometry.geometry, height, onGeometryHeight)
                setSideKeyEnterDrawable(ContextCompat.getDrawable(ctx, com.kazumaproject.core.R.drawable.baseline_keyboard_return_24))
            }
            KeyboardType.GOJUON -> binding.gojuonView.apply { style.apply(this); ModeKeySizing.applyGojuon(this); currentInputMode.set(inputMode); resetLayout() }
            KeyboardType.QWERTY, KeyboardType.ROMAJI -> binding.qwertyView.apply {
                style.apply(this); KeyboardKeyPresentation.qwerty(this)
                val japanese = bodyType == KeyboardType.ROMAJI && previewInputMode != InputMode.ModeEnglish
                if (japanese) setRomajiKeyboard(EditorEnterPolicy.label(EditorEnterAction.Enter, true))
                else resetQWERTYKeyboard(EditorEnterPolicy.label(EditorEnterAction.Enter, false))
                if (numberProxy) setNumberView()
                setSwitchNumberLayoutKeyVisibility(englishProxy || numberProxy)
                setRomajiEnglishSwitchKeyVisibility(bodyType == KeyboardType.ROMAJI)
                setKeyboardGeometry(geometry.geometry, height, onGeometryHeight)
            }
            KeyboardType.SUMIRE, KeyboardType.CUSTOM -> binding.customLayoutDefault.apply {
                style.apply(this)
                val sumire = bodyType == KeyboardType.SUMIRE
                setPopupMapOptions(com.kazumaproject.markdownhelperkeyboard.sumire_popup.FreeFlickPreferences.read(ctx,
                    if (sumire) com.kazumaproject.markdownhelperkeyboard.sumire_popup.FreeFlickPreferences.Scope.SUMIRE
                    else com.kazumaproject.markdownhelperkeyboard.sumire_popup.FreeFlickPreferences.Scope.CUSTOM))
                KeyboardKeyPresentation.flick(this, sumire, flickMode)
                val layout = if (sumire) SumireLayoutPresentation.create(ctx, flickMode,
                    mapOf("enter_key" to EditorEnterPolicy.keyStateIndex(EditorEnterAction.Enter), "dakuten_toggle_key" to 0, "katakana_toggle_key" to 0, "space_convert_key" to 0),
                    data.actions, data.placements) else KeyboardDefaultLayouts.applyDeleteKeyFlickSettings(
                    requireNotNull(data.custom) { "Custom layout no longer exists" },
                    KeyboardDefaultLayouts.DeleteKeyFlickSettings(AppPreference.delete_key_left_flick_preference,
                        AppPreference.delete_key_up_flick_preference, true))
                resolvedLayout = layout
                setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
                setKeyboard(layout, if (sumire) KeyHitTestMode.NEAREST_KEY else KeyHitTestMode.KEY_BOUNDS,
                    if (sumire) KeyboardLayoutOrigin.BUILT_IN else KeyboardLayoutOrigin.CUSTOM)
                if (sumire) setSumireGeometry(geometry.geometry, height, onGeometryHeight)
                else {
                    val row = data.layouts.firstOrNull(target::matches)
                    renderCustomKeyboardToggles(this, CustomKeyboardShiftState.OFF,
                        target.customDirectMode ?: row?.let { KeyboardRestartPresentation.customDirect(it.layoutId, it.stableId, layout.isDirectMode) } ?: layout.isDirectMode,
                        target.customRomajiMode ?: row?.let { KeyboardRestartPresentation.customRomaji(it.layoutId, it.stableId, layout.isRomaji) } ?: layout.isRomaji)
                }
            }
            KeyboardType.MORSE -> binding.morseView.apply {
                val morse = MorsePreferences(ctx)
                configure(morse.settings(), morse.enabledMaps(), morse.selectedMapId)
                val dark = style.night == Configuration.UI_MODE_NIGHT_YES
                val custom = style.mode == "custom"
                setAppearance(style.skin, if (custom) style.background else if (dark) 0xff111722.toInt() else 0xfff1f4fa.toInt(),
                    if (custom) style.key else if (dark) 0xff202a3a.toInt() else Color.WHITE,
                    if (custom) style.text else if (dark) 0xffe2e9fa.toInt() else 0xff243049.toInt(),
                    if (custom) style.special else if (dark) 0xff192231.toInt() else 0xffe8edf6.toInt(), custom)
                inputAllowed = { false }
            }
            KeyboardType.SPLIT -> error("Nested split layout")
        }
        nativeBody = actualBody
        actualBody.isVisible = true
        actualBody.layoutParams = FrameLayout.LayoutParams(keyboardWidth, height, Gravity.BOTTOM or Gravity.LEFT).apply {
            leftMargin = keyboardLeft
        }
        KeyboardFontApplicator.applyToKeyboardViews(actualBody, KeyboardFontApplicator.processSnapshot)
        KeyboardContainerPresentation(ctx, style.skin).apply(binding)
        root.clipToOutline = style.skin != KeyboardSkinId.DEFAULT || AppPreference.keyboard_corner_round_preference
        root.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        val integrated = AppPreference.shortcut_toolbar_integrated_in_suggestion_preference
        val toolbarVisible = showCandidates && AppPreference.shortcut_toolbar_visibility_preference
        val toolbarHeight = if (toolbarVisible && !integrated) dp(AppPreference.shortcut_toolbar_height_dp_preference) else 0
        val emptyHeight = if (showCandidates && sideWidth == 0) dp(if (landscape) AppPreference.candidate_view_empty_height_dp_landscape ?: 60
            else AppPreference.candidate_view_empty_height_dp ?: 60) else 0
        val candidateHeight = if (style.skin == KeyboardSkinId.CUPERTINO_CLASSIC && emptyHeight > 0)
            dp(com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.resolveDockedStripHeightDp((emptyHeight / density).roundToInt())) else emptyHeight
        binding.candidateTabLayout.isVisible = false
        binding.shortcutToolbarRecyclerview.isVisible = toolbarHeight > 0
        if (toolbarHeight > 0) {
            val adapter = ShortcutAdapter().apply {
                setShortcutToolbarSize(toolbarHeight, dp(AppPreference.shortcut_toolbar_icon_size_dp_preference))
                setIconColor(skin?.palette?.specialText ?: if (style.mode == "custom") AppPreference.getCustomThemeShortcutIconColor(style.specialText) else null)
                submitList(data.shortcuts)
            }
            binding.shortcutToolbarRecyclerview.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            binding.shortcutToolbarRecyclerview.adapter = adapter
            binding.shortcutToolbarRecyclerview.layoutParams = FrameLayout.LayoutParams(-1, toolbarHeight)
        }
        binding.suggestionViewParent.isVisible = showCandidates
        binding.suggestionViewParent.layoutParams = FrameLayout.LayoutParams(
            if (sideWidth > 0) sideWidth else -1, if (sideWidth > 0) height else candidateHeight,
            Gravity.TOP or Gravity.LEFT).apply {
                topMargin = if (sideWidth > 0) toolbarHeight else toolbarHeight
                leftMargin = if (sideWidth > 0 && geometry.candidates == GeometryCandidatePlacement.RIGHT) keyboardWidth else 0
            }
        binding.suggestionRecyclerView.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        val suggestions = SuggestionAdapter().apply {
            setCandidateTextSize(AppPreference.candidate_letter_size ?: 14f)
            setShortcutItems(data.shortcuts)
            setIntegratedShortcutItemsVisibility(toolbarVisible && integrated)
            setShortcutIconColor(skin?.palette?.specialText ?: if (style.mode == "custom") AppPreference.getCustomThemeShortcutIconColor(style.specialText) else null)
            submitContent(if (candidateFamily == KeyboardType.CUSTOM && data.layouts.isNotEmpty() && AppPreference.custom_keyboard_suggestion_preference == true) CandidateStripContent.CustomLayoutPicker(data.layouts)
                else CandidateStripContent.EmptyState(false, QuickActionsState(false, false, false, false, "", ""), null,
                    data.shortcuts, toolbarVisible && integrated))
        }
        binding.suggestionRecyclerView.adapter = suggestions
        val recycler = binding.suggestionRecyclerView
        data.resources.releases.add { suggestions.release(); recycler.adapter = null }
        val totalHeight = height + toolbarHeight + candidateHeight
        binding.keyboardBackgroundContainer.layoutParams = FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM)
        binding.keyboardTouchEffectContainer.isVisible = false
        if (style.skin == KeyboardSkinId.DEFAULT) applyMedia(binding, data.resources)
        val keep = setOf(actualBody, binding.keyboardBackgroundContainer, binding.suggestionViewParent,
            binding.shortcutToolbarRecyclerview, binding.candidateTabLayout)
        for (index in root.childCount - 1 downTo 0) if (root.getChildAt(index) !in keep) root.removeViewAt(index)
        val outer = FrameLayout(ctx)
        outer.addView(root, FrameLayout.LayoutParams(surfaceWidth, totalHeight).apply { leftMargin = left })
        val surface = Surface(outer, totalHeight + size.bottomMarginPx, RectF((left + keyboardLeft).toFloat(),
            (toolbarHeight + candidateHeight).toFloat(), (left + keyboardLeft + keyboardWidth).toFloat(), totalHeight.toFloat()), bodyType)
        var effectiveHeight = height
        updateGeometryHeight = { required ->
            if (geometry.geometry.curveDegrees != 0 && geometry.geometry.autoHeight && forcedSize == null) {
                val bottomInset = androidx.core.view.ViewCompat.getRootWindowInsets(this)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
                val available = (resources.displayMetrics.heightPixels - bottomInset - toolbarHeight - candidateHeight).coerceAtLeast(height)
                val next = required.coerceIn(height, available)
                if (effectiveHeight != next) {
                    effectiveHeight = next
                    val total = next + toolbarHeight + candidateHeight
                    root.layoutParams = root.layoutParams.apply { this.height = total }
                    actualBody.layoutParams = actualBody.layoutParams.apply { this.height = next }
                    binding.keyboardBackgroundContainer.layoutParams = binding.keyboardBackgroundContainer.layoutParams.apply { this.height = next }
                    if (sideWidth > 0) binding.suggestionViewParent.layoutParams = binding.suggestionViewParent.layoutParams.apply { this.height = next }
                    surface.height = total + size.bottomMarginPx
                    surface.body.bottom = total.toFloat()
                    if (scene === outer) { logicalHeight = surface.height; requestLayout() }
                }
            }
        }
        return surface
    }

    private suspend fun applyMedia(binding: MainLayoutBinding, owned: RenderResources) {
        val video = AppPreference.keyboard_background_video_uri
        val image = AppPreference.keyboard_background_image_uri
        if (video.isBlank() && image.isNotBlank()) {
            val bitmap = withContext(Dispatchers.IO) {
                KeyboardBackgroundImages.load(context, image)
            }
            requireNotNull(bitmap) { "Background image unavailable" }
            binding.keyboardBackgroundImage.apply {
                setImageBitmap(bitmap); isVisible = true
                scaleType = if (AppPreference.keyboard_background_image_display_mode == "center_crop") ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            }
            val imageView = binding.keyboardBackgroundImage
            owned.releases.add { imageView.setImageDrawable(null) }
        }
        if (video.isNotBlank()) {
            val player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL; volume = 0f
                videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                val maximum = when (AppPreference.keyboard_background_video_quality) { "low" -> 640 to 360; "medium" -> 1280 to 720; else -> Int.MAX_VALUE to Int.MAX_VALUE }
                trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSize(maximum.first, maximum.second).build()
                setMediaItem(MediaItem.fromUri(video)); prepare(); playWhenReady = playbackActive
            }
            binding.root.background?.alpha = 0
            binding.suggestionViewParent.background?.alpha = 0
            binding.candidateTabLayout.background?.alpha = 0
            binding.shortcutToolbarRecyclerview.setBackgroundColor(Color.TRANSPARENT)
            owned.players.add(player)
            binding.keyboardBackgroundVideo.apply { this.player = player; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; isVisible = true }
            val videoView = binding.keyboardBackgroundVideo
            owned.releases.add { videoView.player = null; player.release() }
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (isAttachedToWindow && player in players()) {
                        android.util.Log.e("KeyboardPreview", "Background video is unavailable", error)
                        showUnavailable()
                    }
                }
            })
        }
    }

    private suspend fun createSplit(ctx: Context, data: PreviewData, screenWidth: Int): Surface {
        fun splitDp(value: Int) = (value * density).roundToInt()
        val settings = SplitKeyboardSettings(prefs)
        val host = FrameLayout(ctx)
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val area = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates(manager).safeArea(this)
        val style = KeyboardVisualStyle.read(ctx, skinOverride ?: AppPreference.keyboardSkin)
        val skin = KeyboardSkinRegistry.find(style.skin)
        val colors = KeyboardCandidateColors.resolve(ctx, style.skin)
        val mainFamily = settings.selection(SplitSlot.MAIN).resolved(data.layouts.map { it.stableId }.toSet()).type
        data class Pane(val slot: SplitSlot, val view: View, var placement: SplitPlacement, val horizontal: Int, val vertical: Int)
        val panes = SplitSlot.entries.map { slot ->
            val selection = settings.selection(slot).resolved(data.layouts.map { it.stableId }.toSet())
            val target = KeyboardPreviewTarget(selection.type, selection.customStableId)
            val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, KeyboardPreviewDependencies::class.java)
            val custom = if (selection.type == KeyboardType.CUSTOM) data.layouts.firstOrNull(target::matches)?.let {
                withContext(Dispatchers.IO) { dependencies.keyboards().convertLayout(dependencies.keyboards().getFullLayout(it.layoutId).first()) }
            } else null
            val minimumWidth = if (custom != null) (custom.columnCount * 24).coerceAtLeast(160) else when (selection.type) {
                KeyboardType.GOJUON -> 360; KeyboardType.SUMIRE -> 200; else -> 240
            }
            val minimumHeight = if (custom != null) (custom.rowCount * 32).coerceAtLeast(180) else 180
            val frame = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame(ctx, {})
            frame.contentDescription = ctx.getString(if (slot == SplitSlot.MAIN) R.string.split_keyboard_main else R.string.split_keyboard_sub)
            frame.setHeaderVisible(settings.editPlacement.shows(slot)); frame.setColors(colors); frame.setEditing(false)
            val insets = frame.contentInsets
            var placement = settings.placement(slot, landscape)
            if (!settings.hasPlacement(slot, landscape)) {
                val half = ((area.width() - splitDp(8)) / 2f - insets.left - insets.right) / density
                placement = placement.copy(widthDp = half.coerceAtMost(280f).coerceAtLeast(minimumWidth.toFloat()))
            }
            // Measure the real native body at the same minimum size as the IME's split surface.
            val surface = createSurface(ctx, target, data.copy(custom = custom), splitDp(minimumWidth),
                IndependentKeyboardSize(minimumHeight, 100, 0, 0, 0, true), true, mainFamily)
            val root = (surface.view as ViewGroup).getChildAt(0) as ViewGroup
            val candidates = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestion_recycler_view)
            (candidates.parent as ViewGroup).removeView(candidates)
            root.findViewById<View>(R.id.suggestionView_parent).isVisible = false
            root.findViewById<View>(R.id.shortcut_toolbar_recyclerview).isVisible = false
            root.layoutParams = root.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = ViewGroup.LayoutParams.MATCH_PARENT }
            listOfNotNull(nativeBody, root.findViewById<View>(R.id.keyboard_background_container)).forEach { view ->
                view.layoutParams = view.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            val body = SplitKeyboardBody(ctx, splitDp(minimumWidth), splitDp(minimumHeight)).apply { addView(surface.view) }
            val contents = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                candidates.isVisible = settings.candidates.shows(slot)
                addView(candidates, android.widget.LinearLayout.LayoutParams(-1, splitDp(58)))
                addView(body, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
            }
            frame.contentContainer.addView(contents, FrameLayout.LayoutParams(-1, -1))
            Pane(slot, frame, placement, insets.left + insets.right,
                insets.top + insets.bottom + if (settings.candidates.shows(slot)) splitDp(58) else 0)
        }
        if (panes.sumOf { splitDp(it.placement.widthDp.roundToInt()).coerceAtLeast(splitDp(120)) + it.horizontal } > area.width()
            && !settings.hasPlacement(SplitSlot.MAIN, landscape)) panes.first { it.slot == SplitSlot.MAIN }.let { it.placement = it.placement.copy(y = 0f) }
        val frames = panes.associateWith { splitPaneBounds(area, it.placement, density, it.horizontal, it.vertical) }
        val top = frames.values.minOf { it.top }
        val bottom = frames.values.maxOf { it.bottom }
        // The IME inserts SUB first, then MAIN, so overlapping panes keep the same stacking order.
        panes.reversed().forEach { pane ->
            val bounds = frames.getValue(pane)
            host.addView(pane.view, FrameLayout.LayoutParams(bounds.width(), bounds.height()).apply {
                leftMargin = bounds.left - area.left; topMargin = bounds.top - top
            })
        }
        return Surface(host, bottom - top, RectF(0f, 0f, screenWidth.toFloat(), (bottom - top).toFloat()), KeyboardType.SPLIT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val desired = if (scene != null) (w.toFloat() * logicalHeight / logicalWidth).roundToInt() else dp(156)
        val h = resolveSize(desired.coerceAtLeast(1), heightMeasureSpec)
        setMeasuredDimension(w, h)
        scene?.measure(MeasureSpec.makeMeasureSpec(logicalWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(logicalHeight, MeasureSpec.EXACTLY))
        if (scene == null) for (i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val view = scene
        if (view != null) {
            view.layout(0, 0, logicalWidth, logicalHeight)
            val scale = min(width.toFloat() / logicalWidth, height.toFloat() / logicalHeight)
            if (view.pivotX != 0f) view.pivotX = 0f
            if (view.pivotY != 0f) view.pivotY = 0f
            if (view.scaleX != scale) view.scaleX = scale
            if (view.scaleY != scale) view.scaleY = scale
            val tx = (width - logicalWidth * scale) / 2f
            val ty = (height - logicalHeight * scale) / 2f
            if (view.translationX != tx) view.translationX = tx
            if (view.translationY != ty) view.translationY = ty
        } else for (i in 0 until childCount) getChildAt(i).let { it.layout((width - it.measuredWidth) / 2, (height - it.measuredHeight) / 2,
            (width + it.measuredWidth) / 2, (height + it.measuredHeight) / 2) }
    }
    override fun onInterceptTouchEvent(ev: MotionEvent) = scene != null
    override fun onTouchEvent(event: MotionEvent) = if (isClickable) super.onTouchEvent(event) else false
    fun bodyBounds(): RectF? = if (!ready) null else RectF(body).apply {
        val scale = min(width.toFloat() / logicalWidth, height.toFloat() / logicalHeight)
        left = left * scale + (width - logicalWidth * scale) / 2f; right = right * scale + (width - logicalWidth * scale) / 2f
        top = top * scale + (height - logicalHeight * scale) / 2f; bottom = bottom * scale + (height - logicalHeight * scale) / 2f
    }
    internal fun nativeSceneForTesting(): View? = scene
    internal fun logicalBodyBounds(): RectF = RectF(body)
    internal fun logicalScreenWidth(): Float = logicalWidth.toFloat()
    internal fun displayHeightForWidth(width: Int): Int = (width.toFloat() * logicalHeight / logicalWidth).roundToInt()
    fun adjustmentBounds(): RectF? = bodyBounds()?.let { rect ->
        if (visualType == KeyboardType.SPLIT) return@let rect
        val root = ((scene as? ViewGroup)?.getChildAt(0)) ?: return@let rect
        val placement = root.layoutParams as? FrameLayout.LayoutParams ?: return@let rect
        // A refreshed scene is ready before Android lays out its children. Its saved placement
        // is already resolved; reading root.left/right here would briefly return zero.
        val scale = min(width.toFloat() / logicalWidth, height.toFloat() / logicalHeight)
        RectF(placement.leftMargin * scale + (width - logicalWidth * scale) / 2f, rect.top,
            (placement.leftMargin + placement.width) * scale + (width - logicalWidth * scale) / 2f, rect.bottom)
    }
    private data class Surface(val view: View, var height: Int, val body: RectF, val displayType: KeyboardType)
    private data class PreviewData(val layouts: List<CustomKeyboardLayout>, val custom: KeyboardLayout?,
        val actions: List<com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity>,
        val placements: List<com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity>,
        val shortcuts: List<ShortcutType>, val displayTarget: KeyboardPreviewTarget, val resources: RenderResources)
}
