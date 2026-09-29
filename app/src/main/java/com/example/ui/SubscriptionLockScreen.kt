package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SubscriptionLockScreen: A full-screen security lock shown when trial or subscription
 * has expired. Provides options for subscription renewal, license activation, and support.
 * Strict wording policy: No mentions of "دائمی" or "لایسنس دائم"; strictly uses "تمدید یا خرید اشتراک".
 */
@Composable
fun SubscriptionLockScreen(
    reason: String = "مهلت تست ۲۴ ساعته به پایان رسید.",
    deviceId: String,
    onRenewSubscription: () -> Unit = {},
    onActivateCode: (String) -> Unit = {},
    onContactSupport: () -> Unit = {},
    onRefreshStatus: () -> Unit = {}
) {
    val context = LocalContext.current
    var showActivationDialog by remember { mutableStateOf(false) }
    var licenseInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Lock Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "قفل اشتراک",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Title
                Text(
                    text = "پایان مهلت دسترسی",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                // Reason message
                Text(
                    text = reason,
                    fontSize = 14.sp,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                // Hardware Device ID Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F172A)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "شناسه سخت‌افزاری دستگاه (Device ID):",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = deviceId,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Device ID", deviceId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "شناسه دستگاه کپی شد", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "کپی شناسه",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Primary Action: Renew or Purchase Subscription
                Button(
                    onClick = onRenewSubscription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "تمدید یا خرید اشتراک",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                // Secondary Action: Enter activation license code
                OutlinedButton(
                    onClick = { showActivationDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF38BDF8)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ثبت کد لایسنس اشتراک",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // Additional Row: Check Status & Support
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onRefreshStatus,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "بررسی مجدد",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    TextButton(
                        onClick = onContactSupport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "پشتیبانی",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }

    // License Activation Dialog
    if (showActivationDialog) {
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            title = {
                Text(
                    text = "فعال‌سازی با کد لایسنس",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "کد لایسنس دریافت شده پس از تمدید یا خرید اشتراک را در کادر زیر وارد نمایید:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = licenseInput,
                        onValueChange = { licenseInput = it },
                        placeholder = { Text("مثال: GN-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (licenseInput.isNotBlank()) {
                            onActivateCode(licenseInput.trim())
                            showActivationDialog = false
                        } else {
                            Toast.makeText(context, "لطفاً کد لایسنس را وارد کنید", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("فعال‌سازی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivationDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
