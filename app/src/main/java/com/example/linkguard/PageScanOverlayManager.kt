package com.example.linkguard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PageScanOverlayManager {

    private var currentLoadingView: View? = null
    private var currentResultView: View? = null
    private var windowManager: WindowManager? = null

    /**
     * Scans the active screen for all URLs, queries VirusTotal API v3 in real time,
     * and shows a floating pop-up stating whether each link is SAFE or NOT SAFE.
     */
    fun startScan(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please allow 'Display over other apps'", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if Accessibility service is running
        if (!PageScanAccessibilityService.isServiceRunning()) {
            Toast.makeText(
                context,
                "Please enable 'LinkGuard Page Scanner' in Accessibility to scan on-screen links!",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }

        Handler(Looper.getMainLooper()).post {
            dismissAll()

            val wm = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return@post
            windowManager = wm

            // 1. Show Loading HUD
            showLoadingHud(context, wm)

            // 2. Perform Real Scan & VirusTotal Queries in Coroutine
            CoroutineScope(Dispatchers.IO).launch {
                val (appName, texts) = PageScanAccessibilityService.captureCurrentPage(context)
                val extractedUrls = PageScanner.extractUrls(texts)
                val apiKey = VirusTotalService.getApiKey(context)

                val scanList = mutableListOf<VirusTotalResult>()
                if (extractedUrls.isNotEmpty()) {
                    // Query VirusTotal for up to 5 URLs found on screen
                    for (url in extractedUrls.take(5)) {
                        val vtResult = VirusTotalService.checkUrl(url, apiKey)
                        scanList.add(vtResult)
                    }
                }

                withContext(Dispatchers.Main) {
                    dismissLoadingHud()
                    showResultPopup(context, wm, appName, scanList)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showLoadingHud(context: Context, wm: WindowManager) {
        val dm = context.resources.displayMetrics
        val hudWidth = (dm.widthPixels * 0.88f).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A")) // Dark Navy
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#3B82F6")) // Blue outline
            }
            background = bg
            setPadding(36, 32, 36, 32)
            elevation = 25f
        }

        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            val params = LinearLayout.LayoutParams(
                (44 * dm.density).toInt(),
                (44 * dm.density).toInt()
            ).apply {
                bottomMargin = 18
            }
            layoutParams = params
        }
        container.addView(progressBar)

        val title = TextView(context).apply {
            text = "🔍 Scanning Screen with VirusTotal..."
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        container.addView(title)

        val subtitle = TextView(context).apply {
            text = "Extracting URLs and querying 80+ security engines..."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
            layoutParams = params
        }
        container.addView(subtitle)

        val params = WindowManager.LayoutParams(
            hudWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismissLoadingHud()
                true
            } else {
                false
            }
        }

        try {
            wm.addView(container, params)
            currentLoadingView = container
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showResultPopup(
        context: Context,
        wm: WindowManager,
        appName: String,
        results: List<VirusTotalResult>
    ) {
        val dm = context.resources.displayMetrics
        val popupWidth = (dm.widthPixels * 0.90f).toInt()

        val hasMalicious = results.any { it.risk == VirusTotalRisk.MALICIOUS }
        val hasSuspicious = results.any { it.risk == VirusTotalRisk.SUSPICIOUS }
        val allSafe = results.isNotEmpty() && !hasMalicious && !hasSuspicious

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = 24f
                val strokeColor = when {
                    hasMalicious -> Color.parseColor("#EF4444") // Red
                    hasSuspicious -> Color.parseColor("#F59E0B") // Amber
                    allSafe -> Color.parseColor("#10B981") // Green
                    else -> Color.parseColor("#64748B")
                }
                setStroke(3, strokeColor)
            }
            background = bg
            setPadding(32, 26, 32, 26)
            elevation = 25f
        }

        // Top Header Row
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val headerText = TextView(context).apply {
            text = "🛡️ VirusTotal Page Report"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.LTGRAY)
            setPadding(16, 4, 16, 4)
            setOnClickListener {
                dismissResultPopup()
            }
        }

        topRow.addView(headerText)
        topRow.addView(closeBtn)
        rootCard.addView(topRow)

        val maxRiskPercentage = results.maxOfOrNull { it.riskPercentage } ?: 0

        // Prominent Risk Percentage Score Card
        val scoreCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                val cardBg = when {
                    hasMalicious -> "#450A0A" // Dark red
                    hasSuspicious -> "#451A03" // Dark amber
                    allSafe -> "#022C22" // Dark green
                    else -> "#0F172A"
                }
                val strokeColor = when {
                    hasMalicious -> "#EF4444"
                    hasSuspicious -> "#F59E0B"
                    allSafe -> "#10B981"
                    else -> "#64748B"
                }
                setColor(Color.parseColor(cardBg))
                cornerRadius = 16f
                setStroke(2, Color.parseColor(strokeColor))
            }
            background = bg
            setPadding(24, 18, 24, 18)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
                bottomMargin = 10
            }
            layoutParams = p
        }

        val scoreTitle = TextView(context).apply {
            text = "OVERALL RISK PERCENTAGE"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        scoreCard.addView(scoreTitle)

        val scoreValue = TextView(context).apply {
            text = "$maxRiskPercentage%"
            val valColor = when {
                hasMalicious -> "#EF4444"
                hasSuspicious -> "#F59E0B"
                allSafe -> "#10B981"
                else -> "#94A3B8"
            }
            setTextColor(Color.parseColor(valColor))
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        scoreCard.addView(scoreValue)

        val scoreSubtitle = TextView(context).apply {
            val statusText = when {
                hasMalicious -> "🚨 NOT SAFE: Malicious Link Detected!"
                hasSuspicious -> "⚠️ CAUTION: Suspicious Link Detected!"
                allSafe -> "✅ SAFE: 0% Threat Risk (Verified Clean by VirusTotal)"
                else -> "ℹ️ No Web Links Found on Screen"
            }
            text = statusText
            val valColor = when {
                hasMalicious -> "#FCA5A5"
                hasSuspicious -> "#FDE68A"
                allSafe -> "#A7F3D0"
                else -> "#CBD5E1"
            }
            setTextColor(Color.parseColor(valColor))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        scoreCard.addView(scoreSubtitle)

        rootCard.addView(scoreCard)

        val subInfo = TextView(context).apply {
            text = "Scanned: $appName  •  Total Links: ${results.size}"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            layoutParams = params
        }
        rootCard.addView(subInfo)

        if (results.isEmpty()) {
            val emptyNotice = TextView(context).apply {
                text = "No web URLs were detected on the active screen.\nOpen a browser page or chat message with links, then tap the bubble again."
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(16, 20, 16, 20)
            }
            rootCard.addView(emptyNotice)
        } else {
            // Scrollable List of Links (constrained to max 38% of mobile screen height)
            val maxScrollHeight = (dm.heightPixels * 0.38f).toInt()
            val scrollView = object : ScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val customHeightSpec = MeasureSpec.makeMeasureSpec(maxScrollHeight, MeasureSpec.AT_MOST)
                    super.onMeasure(widthMeasureSpec, customHeightSpec)
                }
            }.apply {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 14
                }
                layoutParams = params
            }

            val linksList = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            for (res in results) {
                val itemBox = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val boxBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#0F172A"))
                        cornerRadius = 12f
                        setStroke(1, Color.parseColor("#334155"))
                    }
                    background = boxBg
                    setPadding(18, 14, 18, 14)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 10
                    }
                    layoutParams = p
                }

                // URL Text
                val urlText = TextView(context).apply {
                    text = res.url
                    setTextColor(Color.parseColor("#93C5FD"))
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                }
                itemBox.addView(urlText)

                // Risk Badge Row
                val badgeRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 8
                    }
                    layoutParams = p
                }

                // Safe / Not Safe badge
                val (badgeLabel, badgeColor) = when (res.risk) {
                    VirusTotalRisk.MALICIOUS -> "🔴 NOT SAFE (MALICIOUS)" to "#EF4444"
                    VirusTotalRisk.SUSPICIOUS -> "🟡 SUSPICIOUS (RISK)" to "#F59E0B"
                    VirusTotalRisk.CLEAN -> "🟢 SAFE (CLEAN)" to "#10B981"
                    VirusTotalRisk.UNRATED -> "⚪ UNRATED" to "#64748B"
                    VirusTotalRisk.ERROR -> "⚠️ CHECK FAILED" to "#94A3B8"
                }

                val badge = TextView(context).apply {
                    text = badgeLabel
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    val bBg = GradientDrawable().apply {
                        setColor(Color.parseColor(badgeColor))
                        cornerRadius = 8f
                    }
                    background = bBg
                    setPadding(12, 6, 12, 6)
                }
                badgeRow.addView(badge)

                // Risk Percentage Badge
                val percentBadge = TextView(context).apply {
                    text = "Risk: ${res.riskPercentage}%"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    val pColor = when {
                        res.riskPercentage >= 50 -> Color.parseColor("#DC2626") // Red
                        res.riskPercentage >= 20 -> Color.parseColor("#D97706") // Amber
                        else -> Color.parseColor("#059669") // Green
                    }
                    val bBg = GradientDrawable().apply {
                        setColor(pColor)
                        cornerRadius = 8f
                    }
                    background = bBg
                    setPadding(10, 6, 10, 6)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8
                    }
                    layoutParams = p
                }
                badgeRow.addView(percentBadge)

                itemBox.addView(badgeRow)

                // Risk Meter (Horizontal Progress Bar)
                val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = res.riskPercentage
                    isIndeterminate = false
                    val pColor = when {
                        res.riskPercentage >= 50 -> Color.parseColor("#EF4444")
                        res.riskPercentage >= 20 -> Color.parseColor("#F59E0B")
                        else -> Color.parseColor("#10B981")
                    }
                    progressTintList = android.content.res.ColorStateList.valueOf(pColor)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (6 * dm.density).toInt()
                    ).apply {
                        topMargin = 8
                        bottomMargin = 4
                    }
                    layoutParams = p
                }
                itemBox.addView(progressBar)

                if (res.message.isNotBlank()) {
                    val detailText = TextView(context).apply {
                        text = res.message
                        setTextColor(Color.parseColor("#CBD5E1"))
                        textSize = 11.5f
                        val p = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 2
                        }
                        layoutParams = p
                    }
                    itemBox.addView(detailText)
                }

                // Direct Clickable Link to VirusTotal
                if (res.virusTotalWebUrl.isNotBlank()) {
                    val vtWebBtn = TextView(context).apply {
                        text = "🌐 View on VirusTotal.com ↗"
                        setTextColor(Color.parseColor("#38BDF8"))
                        textSize = 11.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(0, 10, 0, 4)
                        setOnClickListener {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(res.virusTotalWebUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    itemBox.addView(vtWebBtn)
                }

                linksList.addView(itemBox)
            }

            scrollView.addView(linksList)
            rootCard.addView(scrollView)
        }

        // Action Buttons Row
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val reScanBtn = Button(context).apply {
            text = "Re-Scan"
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.parseColor("#334155"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 12
            }
            layoutParams = p
            setOnClickListener {
                startScan(context)
            }
        }

        val dismissBtn = Button(context).apply {
            text = "Dismiss"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#475569"))
            setOnClickListener {
                dismissResultPopup()
            }
        }

        buttonRow.addView(reScanBtn)
        buttonRow.addView(dismissBtn)
        rootCard.addView(buttonRow)

        rootCard.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismissResultPopup()
                true
            } else {
                false
            }
        }

        val params = WindowManager.LayoutParams(
            popupWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            wm.addView(rootCard, params)
            currentResultView = rootCard
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissLoadingHud() {
        currentLoadingView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        currentLoadingView = null
    }

    fun dismissResultPopup() {
        currentResultView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        currentResultView = null
    }

    fun dismissAll() {
        dismissLoadingHud()
        dismissResultPopup()
    }
}
