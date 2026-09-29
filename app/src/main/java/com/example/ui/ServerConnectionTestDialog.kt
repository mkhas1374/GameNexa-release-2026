package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.data.network.ConnectionTestResult
import com.example.data.network.SelfHostedManager
import com.example.data.network.NetworkLogger
import kotlinx.coroutines.launch

@Composable
fun ServerConnectionTestDialog(
    onDismiss: () -> Unit,
    isAdmin: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(true) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var showDiagnosticLogs by remember { mutableStateOf(false) }

    fun runTest() {
        isTesting = true
        testResult = null
        coroutineScope.launch {
            testResult = SelfHostedManager.testServerConnection()
            isTesting = false
        }
    }

    LaunchedEffect(Unit) {
        runTest()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = { if (!isTesting) onDismiss() },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "تست اتصال زنده با سرور",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GameNexa Cloud Diagnostic",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isTesting) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "در حال بررسی وضعیت شبکه و سرور ابری...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val result = testResult
                        if (result != null) {
                            // Status Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (result.isSuccess) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    1.5.dp,
                                    if (result.isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (result.isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (result.isSuccess) "اتصال برقرار" else "عدم اتصال",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (result.isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { runTest() },
                    enabled = !isTesting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تست مجدد")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDiagnosticLogs = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("لاگ‌های شبکه")
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("بستن")
                    }
                }
            }
        )
    }

    if (showDiagnosticLogs) {
        DiagnosticLogsDialog(onDismiss = { showDiagnosticLogs = false })
    }
}

@Composable
fun DiagnosticLogsDialog(onDismiss: () -> Unit) {
    val logs by NetworkLogger.logs.collectAsState()
    
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            title = {
                Text("Diagnostic Logs", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs.reversed()) { log ->
                        val color = if (!log.isSuccess) Color.Red else Color.DarkGray
                        Column(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                            Text(text = "[${log.timestampFormatted}] ${log.method} ${log.url}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                            Text(text = if (log.isSuccess) "Status: ${log.statusCode} (${log.durationMs}ms)" else "Error: ${log.errorMessage ?: "Unknown error"}", fontSize = 11.sp, color = color)
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}
