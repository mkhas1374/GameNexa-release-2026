package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*

/**
 * Top-Level Notification Bell with Dynamic Unread Badge
 */
@Composable
fun AdminNotificationBellAction(
    viewModel: GameNetViewModel,
    onOpenNotificationCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "bell_pulse")
    val bellScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (unreadCount > 0) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    IconButton(
        onClick = onOpenNotificationCenter,
        modifier = modifier.testTag("admin_notification_bell")
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = Color(0xFFE11D48),
                        contentColor = Color.White,
                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                    ) {
                        Text(
                            text = if (unreadCount > 99) "+99" else "$unreadCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                contentDescription = "مرکز اعلانات مدیریت",
                tint = if (unreadCount > 0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Persistent / State-managed Top-Level Overlay Banner for Incoming Requests & Payment Proofs
 */
@Composable
fun AdminUrgentAlertOverlay(
    viewModel: GameNetViewModel,
    onOpenNotificationCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAlert by viewModel.activeUrgentOverlayAlert.collectAsState()
    val context = LocalContext.current

    AnimatedVisibility(
        visible = activeAlert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        val alert = activeAlert ?: return@AnimatedVisibility

        val isPayment = alert.type == "PAYMENT_PROOF"
        val isReservation = alert.type == "RESERVATION"

        val containerColor = if (isPayment) Color(0xFF064E3B) else if (isReservation) Color(0xFF312E81) else Color(0xFF78350F)
        val accentColor = if (isPayment) Color(0xFF10B981) else if (isReservation) Color(0xFF818CF8) else Color(0xFFF59E0B)
        val iconVector = if (isPayment) Icons.Default.Payments else if (isReservation) Icons.Default.SportsEsports else Icons.Default.ReceiptLong

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.5.dp, accentColor.copy(alpha = 0.8f)), RoundedCornerShape(16.dp))
                .testTag("admin_urgent_alert_overlay"),
            shape = RoundedCornerShape(16.dp),
            color = containerColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = alert.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE11D48)
                                ) {
                                    Text(
                                        text = "فوری",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = alert.description,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.dismissUrgentOverlayAlert() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن اعلان",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPayment && alert.rawPayment != null) {
                        Button(
                            onClick = {
                                viewModel.approveNotificationPayment(alert) { ok ->
                                    if (ok) {
                                        Toast.makeText(context, "پرداخت با موفقیت تایید و حساب مشتری شارژ شد", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "خطا در تایید پرداخت", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تایید و شارژ سریع", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (isReservation && alert.rawReservation != null) {
                        Button(
                            onClick = {
                                viewModel.confirmNotificationReservation(alert) { ok ->
                                    if (ok) {
                                        Toast.makeText(context, "رزرو با موفقیت تایید و ثبت شد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تایید نوبت رزرو", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenNotificationCenter,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.ListAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاهده در مرکز اعلانات", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Modal Dialog / Notification Center for Manager
 */
@Composable
fun AdminNotificationCenterDialog(
    viewModel: GameNetViewModel,
    onDismiss: () -> Unit
) {
    val notifications by viewModel.managerNotifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val currentManagerId by viewModel.currentManagerId.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "PAYMENTS", "RESERVATIONS", "TRANSACTIONS"

    val filteredList = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "PAYMENTS" -> notifications.filter { it.type == "PAYMENT_PROOF" }
            "RESERVATIONS" -> notifications.filter { it.type == "RESERVATION" }
            "TRANSACTIONS" -> notifications.filter { it.type == "UNREVIEWED_TRANSACTION" }
            else -> notifications
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("admin_notification_center_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "مرکز اعلانات مدیریت",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                if (unreadCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFE11D48)
                                    ) {
                                        Text(
                                            text = "$unreadCount پیام جدید",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (currentManagerId.isNotBlank()) "شناسه مدیریت: $currentManagerId" else "همه شعبات گیم‌نت",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filter Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("همه (${notifications.size})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == "PAYMENTS",
                            onClick = { selectedFilter = "PAYMENTS" },
                            label = {
                                Text(
                                    "فیش‌ها (${notifications.count { it.type == "PAYMENT_PROOF" }})",
                                    fontSize = 11.sp
                                )
                            }
                        )
                        FilterChip(
                            selected = selectedFilter == "RESERVATIONS",
                            onClick = { selectedFilter = "RESERVATIONS" },
                            label = {
                                Text(
                                    "رزروها (${notifications.count { it.type == "RESERVATION" }})",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }

                    if (notifications.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.markAllNotificationsAsRead()
                                Toast.makeText(context, "تمام اعلانات به عنوان خوانده شده علامت‌گذاری شدند", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("خوانده شد همه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Notification Items List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "هیچ اعلان جدیدی وجود ندارد",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "فیش‌های واریزی و رزروهای ارسالی مشتریان در اینجا نمایش داده خواهند شد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            AdminNotificationCard(
                                item = item,
                                onApprovePayment = {
                                    viewModel.approveNotificationPayment(item) { ok ->
                                        if (ok) {
                                            Toast.makeText(context, "فیش واریزی با موفقیت تایید شد", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "خطا در تایید فیش", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onConfirmReservation = {
                                    viewModel.confirmNotificationReservation(item) { ok ->
                                        if (ok) {
                                            Toast.makeText(context, "نوبت رزرو با موفقیت تایید شد", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDeleteReservation = {
                                    viewModel.deleteNotificationReservation(item) { ok ->
                                        if (ok) {
                                            Toast.makeText(context, "رزرو حذف شد", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onMarkRead = {
                                    viewModel.markNotificationAsRead(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Notification Card with Contextual Quick Actions
 */
@Composable
private fun AdminNotificationCard(
    item: GameNetViewModel.AdminNotificationItem,
    onApprovePayment: () -> Unit,
    onConfirmReservation: () -> Unit,
    onDeleteReservation: () -> Unit,
    onMarkRead: () -> Unit
) {
    val context = LocalContext.current
    val isPending = item.status == "PENDING" || item.status == "UNREVIEWED"
    val isPayment = item.type == "PAYMENT_PROOF"
    val isReservation = item.type == "RESERVATION"

    val cardBg = if (!item.isRead && isPending) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val iconTint = if (isPayment) Color(0xFF10B981) else if (isReservation) Color(0xFF818CF8) else Color(0xFFF59E0B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMarkRead() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = if (!item.isRead && isPending) BorderStroke(1.dp, iconTint.copy(alpha = 0.6f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = iconTint.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPayment) Icons.Default.Payments else if (isReservation) Icons.Default.SportsEsports else Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!item.isRead && isPending) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE11D48),
                                    modifier = Modifier.size(8.dp)
                                ) {}
                            }
                        }

                        if (item.customerName.isNotBlank()) {
                            Text(
                                text = "مشتری: ${item.customerName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale("fa")).format(Date(item.timestamp)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )

            if (item.trackingCode.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "کد رهگیری / ارجاع: ${item.trackingCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPayment && item.rawPayment != null && item.status == "PENDING") {
                    Button(
                        onClick = onApprovePayment,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تایید فیش و شارژ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isReservation && item.rawReservation != null) {
                    Button(
                        onClick = onConfirmReservation,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تایید نوبت", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (item.phoneNumber.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.phoneNumber}"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "امکان برقراری تماس وجود ندارد", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "تماس", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تماس", fontSize = 10.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onDeleteReservation,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
