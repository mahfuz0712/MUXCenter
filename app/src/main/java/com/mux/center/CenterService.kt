package com.mux.center

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/** Full screen colour layer for Eye Care, Reading Mode and Extra Dim. */
private class TintView(ctx: Context) : View(ctx) {
    var eye = 0f
    var read = 0f
    var dim = 0f

    override fun onDraw(canvas: Canvas) {
        if (eye > 0f) canvas.drawColor(Color.argb((eye * 255).toInt(), 255, 150, 40))
        if (read > 0f) canvas.drawColor(Color.argb((read * 255).toInt(), 235, 200, 140))
        if (dim > 0f) canvas.drawColor(Color.argb((dim * 255).toInt(), 0, 0, 0))
    }
}

/**
 * Draws the HUD status bar (which also catches the swipe down gesture), the full screen panels and the
 * screen filters as accessibility overlay windows. It never reads the content of other apps.
 */
class CenterService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CenterService? = null
    }

    private lateinit var wm: WindowManager
    private val owner = OverlayOwner()
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var bar: RootFrame? = null
    private var barLp: WindowManager.LayoutParams? = null
    private var panel: RootFrame? = null
    private var tint: TintView? = null
    private var awakeView: View? = null
    private var imePackages: Set<String> = emptySet()
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var seenVisible = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (instance != null) return
        instance = this
        Prefs.init(applicationContext)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        owner.start()
        UiState.statusPx = statusBarHeight()
        Controls.initTorch(applicationContext)
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) { }

        val sc = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = sc
        sc.launch {
            while (isActive) {
                Sys.update(applicationContext)
                UiState.landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val h = statusBarHeight()
                if (h != UiState.statusPx) {
                    UiState.statusPx = h
                    applyBarMode(barMode())
                }
                delay(1000)
            }
        }
        sc.launch { snapshotFlow { Prefs.barEnabled }.collect { if (it) showBar() else hideBar() } }
        sc.launch { snapshotFlow { barMode() }.collect { applyBarMode(it) } }
        sc.launch { snapshotFlow { Triple(Prefs.eyeLevel, Prefs.reading, Prefs.dimLevel) }.collect { applyTint() } }
        sc.launch { snapshotFlow { Prefs.awake }.collect { if (it) showAwake() else hideAwake() } }
    }

    /** Tracks the app in front, so the bar can hide in chosen apps, and closes the panel when you leave. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = event.packageName?.toString() ?: return
        if (p == packageName || p == "com.android.systemui" || p == "android" || p in imePackages) return
        UiState.pkg = p
        if (panel != null) closePanel()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    private fun cleanup() {
        if (instance !== this) return
        scope?.cancel()
        scope = null
        handler.removeCallbacksAndMessages(null)
        removePanel()
        hideBar()
        removeTint()
        hideAwake()
        owner.stop()
        instance = null
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (26 * resources.displayMetrics.density).toInt()
    }

    private fun overlayParams(w: Int, h: Int, flags: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    // ------------------------------------------------------------------ status bar

    /**
     * 1 = visible, 2 = hidden but a thin invisible strip at the top edge still catches the swipe,
     * 0 = hidden and not touchable. The bar hides in full screen apps (when Android reports it),
     * in landscape (optional) and in the apps chosen in the LOOK tab.
     */
    private fun barMode(): Int {
        val hide = (Prefs.hideInFullscreen && !UiState.barShown) ||
            (Prefs.hideLandscape && UiState.landscape) ||
            (UiState.pkg.isNotEmpty() && UiState.pkg in Prefs.hideApps)
        return if (!hide) 1 else if (Prefs.edgeSwipe) 2 else 0
    }

    private fun showBar() {
        if (bar != null) return
        val root = makeRoot(this, owner, false) { HudStatusBar(Modifier.fillMaxSize()) }
        root.onTouch = { e -> handleBarTouch(e) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val visible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
            if (visible) {
                seenVisible = true
                UiState.barShown = true
            } else if (seenVisible) {
                UiState.barShown = false
            }
            insets
        }
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            UiState.statusPx.coerceAtLeast(1),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        try {
            wm.addView(root, lp)
            bar = root
            barLp = lp
            root.requestApplyInsets()
            applyBarMode(barMode())
        } catch (e: Exception) { }
    }

    private fun hideBar() {
        val b = bar ?: return
        try {
            b.dispose()
            wm.removeView(b)
        } catch (e: Exception) { }
        bar = null
        barLp = null
    }

    private fun applyBarMode(mode: Int) {
        val b = bar ?: return
        val lp = barLp ?: return
        val notTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        when (mode) {
            1 -> {
                lp.height = UiState.statusPx.coerceAtLeast(1)
                lp.flags = lp.flags and notTouchable.inv()
                b.alpha = 1f
            }
            2 -> {
                lp.height = (8 * resources.displayMetrics.density).toInt()
                lp.flags = lp.flags and notTouchable.inv()
                b.alpha = 0f
            }
            else -> {
                lp.flags = lp.flags or notTouchable
                b.alpha = 0f
            }
        }
        try { wm.updateViewLayout(b, lp) } catch (e: Exception) { }
    }

    /** Swipe down on the status bar: left part opens one panel, the right part the other. */
    private fun handleBarTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                tracking = Prefs.gesturesEnabled && panel == null
            }
            MotionEvent.ACTION_MOVE -> {
                val threshold = 24 * resources.displayMetrics.density
                if (tracking && e.rawY - downY > threshold) {
                    tracking = false
                    val left = downX / resources.displayMetrics.widthPixels < Prefs.split
                    val notifications = if (Prefs.swapSides) !left else left
                    if (Prefs.haptic) bar?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    openPanel(if (notifications) 1 else 2)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }

    // ------------------------------------------------------------------ screen filters

    private fun applyTint() {
        val eye = floatArrayOf(0f, 0.18f, 0.30f, 0.42f)[Prefs.eyeLevel.coerceIn(0, 3)]
        val read = if (Prefs.reading) 0.30f else 0f
        val dim = max(floatArrayOf(0f, 0.30f, 0.50f, 0.70f)[Prefs.dimLevel.coerceIn(0, 3)], if (Prefs.reading) 0.10f else 0f)
        if (eye == 0f && read == 0f && dim == 0f) {
            removeTint()
            return
        }
        var v = tint
        if (v == null) {
            v = TintView(this)
            val lp = overlayParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            try {
                wm.addView(v, lp)
                tint = v
            } catch (e: Exception) { return }
        }
        v.eye = eye
        v.read = read
        v.dim = dim
        v.invalidate()
    }

    private fun removeTint() {
        val v = tint ?: return
        try { wm.removeView(v) } catch (e: Exception) { }
        tint = null
    }

    private fun showAwake() {
        if (awakeView != null) return
        val v = View(this)
        val lp = overlayParams(
            1, 1,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        try {
            wm.addView(v, lp)
            awakeView = v
        } catch (e: Exception) { }
    }

    private fun hideAwake() {
        val v = awakeView ?: return
        try { wm.removeView(v) } catch (e: Exception) { }
        awakeView = null
    }

    // ------------------------------------------------------------------ panels

    fun openPanel(kind: Int) {
        handler.removeCallbacksAndMessages(null)
        if (panel != null) {
            UiState.panel = kind
            UiState.open = true
            return
        }
        UiState.panel = kind
        UiState.open = false
        val root = makeRoot(this, owner, true) { PanelHost { closePanel() } }
        root.onKey = { e ->
            if (e.keyCode == KeyEvent.KEYCODE_BACK) {
                if (e.action == KeyEvent.ACTION_UP) closePanel()
                true
            } else false
        }
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        try {
            wm.addView(root, lp)
            panel = root
            root.requestFocus()
            handler.post { UiState.open = true }
        } catch (e: Exception) { }
    }

    fun closePanel() {
        if (panel == null) return
        UiState.open = false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ removePanel() }, 240)
    }

    private fun removePanel() {
        val p = panel ?: return
        try {
            p.dispose()
            wm.removeView(p)
        } catch (e: Exception) { }
        panel = null
        UiState.panel = 0
        UiState.open = false
    }

    /** Run a system action (screenshot, lock, power menu, recents) after the panel is gone. */
    fun act(action: Int) {
        closePanel()
        handler.postDelayed({ performGlobalAction(action) }, 400)
    }
}