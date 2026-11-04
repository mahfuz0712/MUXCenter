package com.mux.center

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/** Root of an overlay window. Lets the service intercept touch and key events before Compose sees them. */
class RootFrame(ctx: Context) : FrameLayout(ctx) {
    var onKey: ((KeyEvent) -> Boolean)? = null
    var onTouch: ((MotionEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val h = onKey
        if (h != null && h(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val h = onTouch
        if (h != null && h(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    fun dispose() {
        for (i in 0 until childCount) (getChildAt(i) as? ComposeView)?.disposeComposition()
    }
}

fun makeRoot(ctx: Context, owner: OverlayOwner, focusable: Boolean, content: @Composable () -> Unit): RootFrame {
    val root = RootFrame(ctx)
    if (focusable) {
        root.isFocusable = true
        root.isFocusableInTouchMode = true
    }
    val cv = ComposeView(ctx)
    cv.setContent(content)
    root.addView(cv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    OverlayOwner.attach(root, owner)
    OverlayOwner.attach(cv, owner)
    return root
}
