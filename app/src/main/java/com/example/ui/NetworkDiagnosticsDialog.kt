package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.NetworkLogEntry
import com.example.data.network.NetworkLogger
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top floating notification banner displayed on connection failures.
 */
@Composable
fun NetworkStatusBanner(
    modifier: Modifier = Modifier,
    onOpenDiagnostics: () -> Unit
) {
    val errorBannerMsg by NetworkLogger.latestErrorBanner.collectAsState()
    val statusInfo by NetworkLogger.status.collectAsState()

    val visible = errorBannerMsg != null || !statusInfo.isConnected

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "قطع ارتباط شبکه",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ارتباط با سرور برقرار نیست",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errorBannerMsg ?: "خطا در فراخوانی api.gamenermayket.ir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onOpenDiagnostics,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "عیب‌یابی",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { NetworkLogger.dismissErrorBanner() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Diagnostics and Network Logs Modal Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDiagnosticsDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val statusInfo by NetworkLogger.status.collectAsState()
    val logs by NetworkLogger.logs.collectAsState()
    var serverLogs by remember { mutableStateOf<List<NetworkLogEntry>>(emptyList()) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyErrors by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isTestingConnection = true
        testResultText = null
        val res = withContext(Dispatchers.IO) {
            SelfHostedManager.testServerConnection()
        }
        serverLogs = withContext(Dispatchers.IO) { SelfHostedManager.fetchServerDiagnostics() }
        isTestingConnection = false
        testResultText = if (res.isSuccess) {
            "✅ ${res.message} (پاسخ در ${res.latencyMs}ms - ایستگاه‌ها: ${res.liveStationsCount}، مشتریان: ${res.customersCount})"
        } else {
            "❌ ${res.message} (تاخیر: ${res.latencyMs}ms)"
        }
    }

    // Refresh server diagnostics continuously while this sheet is open.
    LaunchedEffect(Unit) {
        while (true) {
            serverLogs = withContext(Dispatchers.IO) { SelfHostedManager.fetchServerDiagnostics() }
            kotlinx.coroutines.delay(2000)
        }
    }

    // A single HTTP request can appear once in the Android interceptor and once in the
    // server diagnostics stream. Collapse those two representations into one visible log.
    val allLogs = remember(logs, serverLogs) {
        val deduplicatedServerLogs = serverLogs.filterNot { server ->
            logs.any { client ->
                client.method.equals(server.method, ignoreCase = true) &&
                    client.statusCode == server.statusCode &&
                    client.url.substringBefore("?") == server.url.substringBefore("?") &&
                    kotlin.math.abs((server.id - client.id) - client.durationMs) <= 5000L
            }
        }
        (logs + deduplicatedServerLogs)
            .distinctBy { it.id }
            .sortedByDescending { it.id }
            .take(150)
    }

    val displayedTotalRequests = allLogs.size
    val displayedSuccessfulRequests = allLogs.count { it.isSuccess }
    val displayedFailedRequests = allLogs.count { !it.isSuccess }
    val filteredLogs = remember(allLogs, searchQuery, showOnlyErrors) {
        val filteredBySearch = if (searchQuery.isBlank()) allLogs
        else allLogs.filter {
            it.url.contains(searchQuery, ignoreCase = true) ||
                    it.method.contains(searchQuery, ignoreCase = true) ||
                    (it.errorMessage?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.statusCode?.toString()?.contains(searchQuery) == true)
        }
        
        if (showOnlyErrors) {
            filteredBySearch.filter { !it.isSuccess }
        } else {
            filteredBySearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "عیب‌یابی و لاگ‌های شبکه سرور",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server Status Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "آدرس پایه سرور:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = statusInfo.targetBaseUrl,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Connection Status Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (statusInfo.isConnected) Color(0xFF1B5E20)
                                    else Color(0xFFB71C1C)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (statusInfo.isConnected) "آنلاین" else "آفلاین / عدم پاسخ",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Request Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(title = "کل لاگ‌های نمایش‌داده‌شده", value = displayedTotalRequests.toString(), color = MaterialTheme.colorScheme.onSurface)
                        StatItem(title = "موفق", value = displayedSuccessfulRequests.toString(), color = Color(0xFF2E7D32))
                        StatItem(title = "ناموفق", value = displayedFailedRequests.toString(), color = Color(0xFFC62828))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test Connection Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isTestingConnection = true
                                testResultText = null
                                scope.launch(Dispatchers.IO) {
                                    val res = SelfHostedManager.testServerConnection()
                                    withContext(Dispatchers.Main) {
                                        isTestingConnection = false
                                        testResultText = if (res.isSuccess) {
                                            "✅ ${res.message} (پاسخ در ${res.latencyMs}ms - ایستگاه‌ها: ${res.liveStationsCount}، مشتریان: ${res.customersCount})"
                                        } else {
                                            "❌ ${res.message} (تاخیر: ${res.latencyMs}ms)"
                                        }
                                    }
                                }
                            },
                            enabled = !isTestingConnection,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("در حال بررسی...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تست مجدد پینگ سرور", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { NetworkLogger.clearLogs() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پاک‌سازی لاگ‌ها", fontSize = 12.sp)
                        }
                    }

                    testResultText?.let { text ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (text.contains("ناموفق") || text.contains("خطا")) Color(0xFFD32F2F) else Color(0xFF388E3C)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar for Logs
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("جستجو در لاگ‌های شبکه...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "فقط خطاهای ناموفق (Show Failures Only)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Switch(
                    checked = showOnlyErrors,
                    onCheckedChange = { showOnlyErrors = it },
                    modifier = Modifier.scale(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logs List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (logs.isEmpty()) "هیچ لاگ شبکه‌ای ثبت نشده است." else "موردی یافت نشد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { logItem ->
                        NetworkLogCard(logItem = logItem, onCopyLog = { textToCopy ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Network Log", textToCopy)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "کپی شد!", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun NetworkLogCard(
    logItem: NetworkLogEntry,
    onCopyLog: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when {
        logItem.isSuccess -> Color(0xFF2E7D32)
        logItem.statusCode != null && logItem.statusCode >= 500 -> Color(0xFFC62828)
        else -> Color(0xFFD84315)
    }

    val methodColor = when (logItem.method.uppercase()) {
        "GET" -> Color(0xFF1976D2)
        "POST" -> Color(0xFF388E3C)
        "DELETE" -> Color(0xFFD32F2F)
        "PUT" -> Color(0xFFF57C00)
        else -> MaterialTheme.colorScheme.primary
    }

    val uriPath = remember(logItem.url) {
        try {
            val uri = java.net.URI(logItem.url)
            val path = uri.path
            val query = uri.query
            if (query != null) "$path?$query" else path
        } catch (e: Exception) {
            logItem.url
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (logItem.isSuccess) Color.Transparent else statusColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (logItem.isSuccess) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Method badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(methodColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = logItem.method,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = uriPath,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Code / Time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${logItem.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (logItem.statusCode != null && logItem.statusCode != -1) "${logItem.statusCode}" else "ERR",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!logItem.isSuccess && logItem.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = logItem.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Expanded view details
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "آدرس کامل: ${logItem.url}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "زمان ثبت: ${logItem.timestampFormatted}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                logItem.errorType?.let {
                    Text(
                        text = "نوع خطا: $it",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                logItem.responseBodySnippet?.let { snippet ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "بدنه پاسخ سرور:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = snippet,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val copyText = "URL: ${logItem.url}\nMethod: ${logItem.method}\nStatus: ${logItem.statusCode}\nError: ${logItem.errorMessage}\nBody: ${logItem.responseBodySnippet}"
                            onCopyLog(copyText)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("کپی گزارش لاگ", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
