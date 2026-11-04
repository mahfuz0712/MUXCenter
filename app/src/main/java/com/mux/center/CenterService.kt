package com.mux.center

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Draws the HUD status bar (which also catches the swipe down gesture) and the full screen panels
 * as accessibility overlay windows. It never reads the content of other apps.
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

        val sc = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = sc
        sc.launch {
            while (isActive) {
                Sys.update(applicationContext)
                delay(1000)
            }
        }
        sc.launch {
            snapshotFlow { Prefs.barEnabled }.collect { if (it) showBar() else hideBar() }
        }
        sc.launch {
            snapshotFlow { UiState.barShown || !Prefs.hideInFullscreen }.collect { applyBarVisible(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || panel == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString() ?: return
            if (p != packageName && p != "com.android.systemui") closePanel()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
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
        owner.stop()
        instance = null
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (26 * resources.displayMetrics.density).toInt()
    }

    // ------------------------------------------------------------------ status bar

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
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            UiState.statusPx.coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            wm.addView(root, lp)
            bar = root
            barLp = lp
            root.requestApplyInsets()
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

    private fun applyBarVisible(show: Boolean) {
        val b = bar ?: return
        val lp = barLp ?: return
        lp.flags = if (show) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        b.alpha = if (show) 1f else 0f
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
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
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
