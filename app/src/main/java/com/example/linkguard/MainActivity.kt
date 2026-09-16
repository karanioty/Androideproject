package com.example.linkguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.linkguard.ui.theme.LinkGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CleanMainScreen()
                }
            }
        }
    }
}

@Composable
fun CleanMainScreen() {
    val context = LocalContext.current
    val isBubbleRunning by DetectionHistoryRepository.isBubbleRunning.collectAsState()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(PageScanAccessibilityService.isServiceRunning()) }

    // Re-check permissions when returning to the app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = PageScanAccessibilityService.isServiceRunning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🛡️ LinkGuard",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "VirusTotal Page Link Scanner",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        // Floating Bubble Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Floating Bubble Scanner",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (isBubbleRunning) Color(0xFF22C55E) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBubbleRunning) "Active" else "Disabled",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isBubbleRunning) Color(0xFF15803D) else Color.Gray
                            )
                        }
                    }

                    Text(
                        text = "Tap the floating bubble anytime while using any app or browser to scan all on-screen links using VirusTotal and view their risk levels.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                    Toast.makeText(
                                        context,
                                        "Please allow 'Display over other apps' to show the bubble",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    startBubbleService(context)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Enable Bubble", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { stopBubbleService(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disable Bubble", color = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }

        // Permissions Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Permissions Required",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    PermissionItem(
                        title = "Display Over Other Apps",
                        description = "Shows the floating bubble and VirusTotal scan pop-up",
                        isGranted = hasOverlayPermission,
                        onFix = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider()

                    PermissionItem(
                        title = "Page Scanner (Accessibility)",
                        description = "Enables LinkGuard to detect all links on the active screen",
                        isGranted = hasAccessibilityPermission,
                        onFix = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // How It Works Guide Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💡 How to Use",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "1. Tap \"Enable Bubble\" above to launch the floating shield.\n" +
                                "2. Open any app or browser (Chrome, WhatsApp, Telegram, etc.).\n" +
                                "3. Tap the floating bubble at any time. LinkGuard will scan all links on your page with VirusTotal and pop up their risk levels.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = description, fontSize = 12.sp, color = Color.Gray)
        }
        if (isGranted) {
            Text(
                text = "✓ Allowed",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16A34A)
            )
        } else {
            Button(
                onClick = onFix,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
            ) {
                Text("Enable", fontSize = 12.sp)
            }
        }
    }
}

private fun startBubbleService(context: Context) {
    val intent = Intent(context, BubbleService::class.java).apply {
        action = BubbleService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.startService(intent)
    }
    Toast.makeText(context, "Bubble enabled", Toast.LENGTH_SHORT).show()
}

private fun stopBubbleService(context: Context) {
    val intent = Intent(context, BubbleService::class.java).apply {
        action = BubbleService.ACTION_STOP
    }
    context.stopService(intent)
    PageScanOverlayManager.dismissAll()
    Toast.makeText(context, "Bubble disabled", Toast.LENGTH_SHORT).show()
}