package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Customer

@Composable
fun CustomerBehaviorDialog(
    stationCustomers: List<Customer>,
    onDismiss: () -> Unit,
    onSubmit: (customerId: Long, points: Long, reason: String) -> Unit
) {
    var selectedCustomerId by remember { mutableStateOf<Long?>(if (stationCustomers.size == 1) stationCustomers.first().id else null) }
    
    // Predefined behaviors
    val predefinedOptions = listOf(
        Pair("😡 فحاشی / توهین", -10L),
        Pair("❌ بی‌نظمی / خسارت", -5L),
        Pair("👍 رفتار مناسب", 5L)
    )
    
    var selectedOptionIdx by remember { mutableStateOf<Int?>(null) }
    var isManual by remember { mutableStateOf(false) }
    
    var manualReason by remember { mutableStateOf("") }
    var manualPointsStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ثبت رفتار مخاطب",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (stationCustomers.size > 1) {
                    Text("مخاطب را انتخاب کنید:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stationCustomers.forEach { cust ->
                            val isSel = selectedCustomerId == cust.id
                            Surface(
                                selected = isSel,
                                onClick = { selectedCustomerId = cust.id },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isSel) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Text(cust.fullName, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
                
                Text("نوع رفتار / امتیاز:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                
                predefinedOptions.forEachIndexed { index, option ->
                    val isSel = selectedOptionIdx == index && !isManual
                    Surface(
                        onClick = {
                            selectedOptionIdx = index
                            isManual = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(option.first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (option.second > 0) "+${option.second}" else "${option.second}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (option.second > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                Surface(
                    onClick = {
                        isManual = true
                        selectedOptionIdx = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isManual) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isManual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("✍️ ثبت دستی امتیاز", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                if (isManual) {
                    OutlinedTextField(
                        value = manualReason,
                        onValueChange = { manualReason = it },
                        label = { Text("علت (مثال: تاخیر)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = manualPointsStr,
                        onValueChange = { manualPointsStr = it },
                        label = { Text("مقدار امتیاز (مثبت یا منفی)", fontSize = 10.sp) },
                        placeholder = { Text("مثال: -2 یا 5", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedCustomerId == null) return@Button
                    val pts: Long
                    val reason: String
                    if (isManual) {
                        pts = manualPointsStr.toLongOrNull() ?: 0L
                        reason = manualReason.trim().ifBlank { "ثبت دستی امتیاز" }
                    } else {
                        if (selectedOptionIdx == null) return@Button
                        val opt = predefinedOptions[selectedOptionIdx!!]
                        pts = opt.second
                        reason = opt.first.replace(Regex("[^آ-یا-شa-zA-Z\\s/\\\\]"), "").trim() // strip emojis
                    }
                    if (pts != 0L) {
                        onSubmit(selectedCustomerId!!, pts, reason)
                    }
                },
                enabled = selectedCustomerId != null && (selectedOptionIdx != null || (isManual && manualPointsStr.isNotBlank())),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ثبت", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
