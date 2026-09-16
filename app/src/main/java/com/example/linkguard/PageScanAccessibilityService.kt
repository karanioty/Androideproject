package com.example.linkguard

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class PageScanAccessibilityService : AccessibilityService() {

    companion object {
        var instance: PageScanAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        fun captureCurrentPage(context: Context): Pair<String, List<String>> {
            val s = instance
            if (s != null) {
                return s.extractScreenTexts()
            }
            return fallbackCapture(context)
        }

        private fun fallbackCapture(context: Context): Pair<String, List<String>> {
            val texts = mutableListOf<String>()
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard?.hasPrimaryClip() == true) {
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val clipText = clip.getItemAt(0).text?.toString()
                        if (!clipText.isNullOrBlank()) {
                            texts.add(clipText)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return Pair("Screen Clipboard", texts)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.flags = info.flags or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            serviceInfo = info
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitored as needed
    }

    override fun onInterrupt() {
        // Interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Extracts text ONLY from views that are currently visible within the physical mobile screen dimensions.
     */
    private fun extractScreenTexts(): Pair<String, List<String>> {
        val texts = mutableListOf<String>()
        var detectedPkg = "Active Screen"

        val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        val screenRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            wm.currentWindowMetrics.bounds
        } else {
            val dm = resources.displayMetrics
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }

        // 1. Traverse interactive application windows within the mobile screen
        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                for (w in allWindows) {
                    // Only scan application windows (skip system UI, overlays, keyboard unless needed)
                    if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

                    val windowBounds = Rect()
                    w.getBoundsInScreen(windowBounds)
                    if (!Rect.intersects(windowBounds, screenRect)) continue

                    val root = w.root ?: continue
                    val pkg = root.packageName?.toString()
                    if (!pkg.isNullOrBlank() && pkg != packageName && pkg != "com.android.systemui") {
                        detectedPkg = pkg
                    }
                    traverseNode(root, texts, screenRect)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to rootInActiveWindow if no application window was found
        if (texts.isEmpty()) {
            try {
                val activeRoot = rootInActiveWindow
                if (activeRoot != null) {
                    val pkg = activeRoot.packageName?.toString()
                    if (!pkg.isNullOrBlank() && pkg != packageName && pkg != "com.android.systemui") {
                        detectedPkg = pkg
                    }
                    traverseNode(activeRoot, texts, screenRect)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Pair(detectedPkg, texts)
    }

    /**
     * Traverses the accessibility node tree, extracting text ONLY if the node
     * is physically visible to the user within the mobile screen bounds.
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        collected: MutableList<String>,
        screenRect: Rect
    ) {
        if (node == null) return

        val isVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            node.isVisibleToUser
        } else {
            true
        }

        val nodeBounds = Rect()
        node.getBoundsInScreen(nodeBounds)

        // Must strictly intersect mobile screen viewport and have positive dimensions
        val isInsideMobileScreen = Rect.intersects(nodeBounds, screenRect) &&
                nodeBounds.width() > 0 && nodeBounds.height() > 0 &&
                nodeBounds.bottom > 0 && nodeBounds.top < screenRect.bottom &&
                nodeBounds.right > 0 && nodeBounds.left < screenRect.right

        if (isVisible && isInsideMobileScreen) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                collected.add(it)
            }

            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
                collected.add(it)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let {
                    collected.add(it)
                }
            }

            // Specifically check viewIdResourceName for URL bars (e.g. com.android.chrome:id/url_bar)
            try {
                val viewId = node.viewIdResourceName
                if (!viewId.isNullOrBlank() && viewId.contains("url", ignoreCase = true)) {
                    node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                        collected.add(it)
                    }
                }
            } catch (e: Exception) {
                // viewIdResourceName might throw on some OEM custom Android builds
            }
        }

        // If the node has non-zero size and is completely off-screen (scrolled past top/bottom/left/right),
        // skip recursing its children to avoid scanning off-screen content.
        val isCompletelyOffScreen = nodeBounds.width() > 0 && nodeBounds.height() > 0 &&
                (nodeBounds.top >= screenRect.bottom || nodeBounds.bottom <= 0 ||
                 nodeBounds.left >= screenRect.right || nodeBounds.right <= 0)

        if (!isCompletelyOffScreen) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                traverseNode(child, collected, screenRect)
            }
        }
    }
}
