package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import com.example.data.network.SelfHostedManager

@Composable
fun ManagerReservationsScreen(viewModel: GameNetViewModel) {
    val lang by viewModel.language.collectAsState()
    val reservations by viewModel.managerReservations.collectAsState()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var paymentRequests by remember { mutableStateOf<org.json.JSONArray?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchManagerReservations()
        paymentRequests = SelfHostedManager.fetchPendingReservationPayments()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (lang == "fa") "مدیریت و تایید رزروها" else "Manager Reservations",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        paymentRequests?.let { requests ->
            if (requests.length() > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (lang == "fa") "پرداخت‌های رزرو در انتظار بررسی" else "Pending reservation payments", fontWeight = FontWeight.Bold)
                        for (i in 0 until requests.length()) {
                            val p = requests.optJSONObject(i) ?: continue
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.optString("full_name"), fontWeight = FontWeight.Bold)
                                    Text("رزرو #" + p.optLong("reservation_id") + " | مبلغ " + p.optLong("amount"), fontSize = 11.sp)
                                    Text("روش: " + p.optString("payment_method_code") + " | پیگیری: " + p.optString("payment_reference"), fontSize = 10.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                SelfHostedManager.approveManagerReservationPayment(p.optLong("id"))
                                                paymentRequests = SelfHostedManager.fetchPendingReservationPayments()
                                                viewModel.fetchManagerReservations()
                                                isLoading = false
                                            }
                                        },
                                        enabled = !isLoading
                                    ) { Text(if (lang == "fa") "تأیید" else "Approve") }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                SelfHostedManager.rejectManagerReservationPayment(p.optLong("id"))
                                                paymentRequests = SelfHostedManager.fetchPendingReservationPayments()
                                                isLoading = false
                                            }
                                        },
                                        enabled = !isLoading
                                    ) { Text(if (lang == "fa") "رد" else "Reject") }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (reservations.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (lang == "fa") "رزروی یافت نشد." else "No reservations found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reservations) { res ->
                    ReservationManagerCard(
                        reservation = res,
                        lang = lang,
                        onApprove = {
                            isLoading = true
                            viewModel.updateReservationStatus(res.id, "CONFIRMED")
                            isLoading = false
                        },
                        onReject = {
                            isLoading = true
                            viewModel.updateReservationStatus(res.id, "REJECTED")
                            isLoading = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReservationManagerCard(
    reservation: com.example.data.network.ReservationDbDto,
    lang: String,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val dateStr = df.format(Date(reservation.reservationTimeMillis))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = reservation.customerName ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = reservation.status, fontWeight = FontWeight.Bold, color = getStatusColor(reservation.status))
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            Text(text = if (lang == "fa") "نوع: ${reservation.reservationType}" else "Type: ${reservation.reservationType}", fontSize = 14.sp)
            Text(text = if (lang == "fa") "زمان: $dateStr" else "Time: $dateStr", fontSize = 14.sp)
            Text(text = if (lang == "fa") "مدت: ${reservation.durationMinutes} دقیقه" else "Duration: ${reservation.durationMinutes} min", fontSize = 14.sp)
            if (reservation.reservationType == "NORMAL_RESERVATION" || reservation.reservationType == "VIP") {
                Text(
                    text = if (reservation.stationId == null) {
                        if (lang == "fa") "ایستگاه: رزرو کامل سالن" else "Station: Full hall"
                    } else {
                        if (lang == "fa") "ایستگاه: " + reservation.stationId else "Station: " + reservation.stationId
                    },
                    fontSize = 14.sp
                )
            }
            Text(text = if (lang == "fa") "مبلغ نهایی: ${reservation.finalPrice ?: "—"} تومان" else "Final Price: ${reservation.finalPrice ?: "—"}", fontSize = 14.sp)
            
            // Actions
            if (reservation.status == "PENDING_APPROVAL" || reservation.status == "VIP_PAYMENT_PAID") {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "تایید" else "Approve")
                    }
                    Button(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "رد کردن" else "Reject")
                    }
                }
            }
        }
    }
}

private fun getStatusColor(status: String): Color {
    return when(status) {
        "CONFIRMED", "ACTIVE", "COMPLETED" -> Color(0xFF4CAF50)
        "PENDING", "PAYMENT_PENDING" -> Color(0xFFFF9800)
        "VIP_PAYMENT_PAID" -> Color(0xFF2196F3)
        "CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW" -> Color(0xFFF44336)
        else -> Color.Gray
    }
}
