package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Customer
import java.util.Locale

@Composable
fun CustomerSelectionDialog(
    maxControllers: Int,
    maxSelectableCustomers: Int? = null,
    currentSelectedIds: List<Long>,
    allCustomers: List<Customer>,
    occupiedCustomerStationMap: Map<Long, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (List<Long>, List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedMap = remember {
        mutableStateMapOf<Long, Customer>().apply {
            allCustomers.filter { currentSelectedIds.contains(it.id) }.forEach {
                put(it.id, it)
            }
            currentSelectedIds.filter { it < 0 }.forEach { guestId ->
                val guestIndex = -guestId
                put(guestId, Customer(id = guestId, fullName = "مهمان $guestIndex", phoneNumber = ""))
            }
        }
    }

    val filteredCustomers = remember(allCustomers, searchQuery) {
        if (searchQuery.isBlank()) allCustomers
        else allCustomers.filter {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery)
        }
    }

    var dynamicGuestCount by remember { mutableIntStateOf(maxOf(8, maxControllers)) }
    val guestSlots = remember(dynamicGuestCount) {
        (1..dynamicGuestCount).map { num ->
            val guestId = -num.toLong()
            guestId to "مهمان $num"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "انتخاب مخاطب / مهمان ایستگاه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "امکان انتخاب هم‌زمان چندین مخاطب و مهمان بدون محدودیت دسته",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Guest Quick Selection Chips
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎮 انتخاب سریع مهمان (بدون ثبت‌نام):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val guestCount = selectedMap.count { it.key < 0 }
                            if (guestCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                                    Text(
                                        text = "$guestCount مهمان انتخابی",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }

                        // Row 1 of guest chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            guestSlots.take(4).forEach { (gId, gName) ->
                                val isGuestSelected = selectedMap.containsKey(gId)
                                Surface(
                                    onClick = {
                                        if (isGuestSelected) {
                                            selectedMap.remove(gId)
                                        } else {
                                            selectedMap[gId] = Customer(id = gId, fullName = gName, phoneNumber = "")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isGuestSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isGuestSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    ),
                                    shadowElevation = if (isGuestSelected) 2.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isGuestSelected) Icons.Default.Check else Icons.Default.PersonOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = if (isGuestSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = gName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isGuestSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isGuestSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (guestSlots.size > 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                guestSlots.drop(4).take(4).forEach { (gId, gName) ->
                                    val isGuestSelected = selectedMap.containsKey(gId)
                                    Surface(
                                        onClick = {
                                            if (isGuestSelected) {
                                                selectedMap.remove(gId)
                                            } else {
                                                selectedMap[gId] = Customer(id = gId, fullName = gName, phoneNumber = "")
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isGuestSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isGuestSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        ),
                                        shadowElevation = if (isGuestSelected) 2.dp else 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isGuestSelected) Icons.Default.Check else Icons.Default.PersonOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = if (isGuestSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = gName,
                                                fontSize = 11.sp,
                                                fontWeight = if (isGuestSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                color = if (isGuestSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Button to add more guest chips if desired
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { dynamicGuestCount += 4 },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("افزودن مهمان‌های بیشتر", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("یا جستجو در مخاطبان ثبت‌شده...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allCustomers.isEmpty()) "مخاطب ثبت‌شده‌ای وجود ندارد (از گزینه‌های مهمان بالا استفاده کنید)" else "مخاطبی یافت نشد",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { cust ->
                            val isSelected = selectedMap.containsKey(cust.id)
                            Surface(
                                onClick = {
                                    if (isSelected) {
                                        selectedMap.remove(cust.id)
                                    } else if (maxSelectableCustomers == null || selectedMap.size < maxSelectableCustomers) {
                                        selectedMap[cust.id] = cust
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cust.fullName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (cust.phoneNumber.isNotBlank()) {
                                            Text(
                                                text = cust.phoneNumber,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            // Controllers limit simultaneous controllers, not the number
                                            // of people who may share/bill the same session.
                                            if (checked) {
                                                if (maxSelectableCustomers == null || selectedMap.size < maxSelectableCustomers) {
                                                    selectedMap[cust.id] = cust
                                                }
                                            } else {
                                                selectedMap.remove(cust.id)
                                            }
                                        }
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
                onClick = {
                    val ids = selectedMap.keys.toList()
                    val names = selectedMap.values.map { it.fullName }
                    onConfirm(ids, names)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("تایید انتخاب‌ها (${selectedMap.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun SaveGuestAsCustomerDialog(
    initialGuestName: String,
    initialDebt: Double = 0.0,
    initialCredit: Double = 0.0,
    onDismiss: () -> Unit,
    onSave: (fullName: String, phone: String, debt: Double, credit: Double, description: String) -> Unit
) {
    var fullName by remember { mutableStateOf(if (initialGuestName.startsWith("مهمان")) "" else initialGuestName) }
    var phoneNumber by remember { mutableStateOf("") }
    var debtInput by remember { mutableStateOf(if (initialDebt > 0) String.format(Locale.US, "%.0f", initialDebt) else "") }
    var creditInput by remember { mutableStateOf(if (initialCredit > 0) String.format(Locale.US, "%.0f", initialCredit) else "") }
    var description by remember { mutableStateOf("تبدیل شده از $initialGuestName") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "ثبت $initialGuestName به عنوان مخاطب جدید",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "با ثبت این مخاطب، تمام فاکتورها، سوابق و بدهی‌های $initialGuestName به نام این مخاطب منتقل می‌شود.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("نام و نام خانوادگی", fontSize = 11.sp) },
                    placeholder = { Text("مثال: علی رضایی", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("شماره تماس (اختیاری)", fontSize = 11.sp) },
                    placeholder = { Text("0912...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = debtInput,
                        onValueChange = { debtInput = it },
                        label = { Text("بدهی (تومان)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = creditInput,
                        onValueChange = { creditInput = it },
                        label = { Text("بستانکاری (تومان)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات / یادداشت", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = fullName.trim().ifBlank { initialGuestName }
                    val d = debtInput.toDoubleOrNull() ?: 0.0
                    val c = creditInput.toDoubleOrNull() ?: 0.0
                    onSave(finalName, phoneNumber.trim(), d, c, description.trim())
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ذخیره و ثبت در مخاطبین")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun PayerAllocationDialog(
    customers: List<Customer>,
    totalCost: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>, List<String>) -> Unit
) {
    var selectedPayerId by remember { mutableStateOf<Long?>(null) }
    var selectedPayerName by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تخصیص پرداخت‌کننده بخش/جلسه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "آیا هزینه این بخش به عهده یک شخص مشخص است یا بین همه تقسیم شود؟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Split equally among all station customers
                Surface(
                    onClick = {
                        selectedPayerId = null
                        selectedPayerName = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedPayerId == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = if (selectedPayerId == null) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "👥 تقسیم مساوی بین همه مخاطبان ایستگاه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (selectedPayerId == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "یا انتخاب یک شخص پرداخت‌کننده:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    customers.distinctBy { it.id }.forEach { cust ->
                        val isSelected = selectedPayerId == cust.id
                        Surface(
                            onClick = {
                                selectedPayerId = cust.id
                                selectedPayerName = cust.fullName
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = cust.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPayerId != null) {
                        onConfirm(listOf(selectedPayerId!!), listOf(selectedPayerName ?: ""))
                    } else {
                        onConfirm(emptyList(), emptyList())
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("تایید")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun BuffetCustomerTargetDialog(
    productName: String,
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onConfirm: (Long?, String?) -> Unit
) {
    var selectedTargetId by remember { mutableStateOf<Long?>(null) }
    var selectedTargetName by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تخصیص سفارش بوفه ($productName)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "این سفارش خوراکی برای کدام مخاطب ثبت شود؟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Shared
                Surface(
                    onClick = {
                        selectedTargetId = null
                        selectedTargetName = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedTargetId == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = if (selectedTargetId == null) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🍕 مصرف اشتراکی (تقسیم مساوی بین همه مخاطبان)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (selectedTargetId == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "یا تخصیص به مخاطب خاص:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(customers, key = { it.id }) { cust ->
                        val isSelected = selectedTargetId == cust.id
                        Surface(
                            onClick = {
                                selectedTargetId = cust.id
                                selectedTargetName = cust.fullName
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = cust.fullName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTargetId, selectedTargetName) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ثبت سفارش")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

data class CustomerPrepaymentStatus(
    val customerId: Long,
    val customerName: String,
    val initialPrepayment: Double,
    val shareOfCost: Double,
    val remainingPrepayment: Double
)

@Composable
fun StationPauseDialog(
    stationId: Int,
    stationCustomers: List<Customer>,
    allCustomers: List<Customer>,
    gameCost: Long = 0L,
    prepaymentTotal: Long = 0L,
    customerPrepaymentsMap: Map<Long, Long> = emptyMap(),
    onDismiss: () -> Unit,
    onSimplePause: () -> Unit,
    onCommitSegmentAndPause: (List<Long>, List<String>) -> Unit,
    onCommitSegmentAndContinue: (List<Long>, List<String>) -> Unit = { _, _ -> }
) {
    var selectedOption by remember { mutableStateOf(1) } // 1: Simple Pause, 2: Commit & Pause, 3: Commit & Continue
    val selectedPayerIds = remember { mutableStateListOf<Long>() }
    val selectedPayerNamesMap = remember { mutableStateMapOf<Long, String>() }

    val availableCustomers = remember(stationCustomers, allCustomers) {
        val source = if (stationCustomers.isNotEmpty()) {
            stationCustomers
        } else {
            // Stop decisions must never silently assign the cost to every customer in the hall.
            // With no station participant, expose only explicit guest payers for the Manager to choose.
            (1..4).map { num ->
                Customer(id = -num.toLong(), fullName = "مهمان $num", phoneNumber = "")
            }
        }
        // A malformed/legacy local customer list may contain the same ID more than once.
        // Compose LazyColumn keys must be unique; deduplicate before rendering the Stop dialog.
        source.distinctBy { it.id }
    }

    // Never auto-select synthetic guest payers. In a no-customer session the Manager
    // must explicitly choose a payer, or leave it empty so settlement is treated as a
    // true walk-in. This avoids injecting negative guest IDs into the pause segment.
    LaunchedEffect(availableCustomers) {
        if (selectedPayerIds.isEmpty() && stationCustomers.isNotEmpty()) {
            stationCustomers.distinctBy { it.id }.forEach { cust ->
                selectedPayerIds.add(cust.id)
                selectedPayerNamesMap[cust.id] = cust.fullName
            }
        }
    }

    val customerStatusList = remember(availableCustomers, gameCost, prepaymentTotal, customerPrepaymentsMap) {
        if (availableCustomers.isEmpty()) emptyList()
        else {
            val count = availableCustomers.size
            val costPerCust = if (count > 0) gameCost.toDouble() / count else 0.0
            availableCustomers.map { cust ->
                val prepay = customerPrepaymentsMap[cust.id]?.toDouble()
                    ?: (if (prepaymentTotal > 0 && count > 0) prepaymentTotal.toDouble() / count else 0.0)
                val rem = prepay - costPerCust
                CustomerPrepaymentStatus(
                    customerId = cust.id,
                    customerName = cust.fullName,
                    initialPrepayment = prepay,
                    shareOfCost = costPerCust,
                    remainingPrepayment = rem
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "توقف بازی - ایستگاه $stationId",
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
                // Prepayment / Balance status banner if customers exist
                if (customerStatusList.isNotEmpty() && (prepaymentTotal > 0 || gameCost > 0 || customerPrepaymentsMap.isNotEmpty())) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💰 مانده اعتبار / پیش‌پرداخت مخاطبان",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "هزینه بازی تا الان: %,d تومان".format(java.util.Locale.US, gameCost),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            customerStatusList.forEach { status ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = status.customerName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (status.initialPrepayment > 0) {
                                            Text(
                                                text = "(پرداخت: %,.0f)".format(java.util.Locale.US, status.initialPrepayment),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (status.remainingPrepayment >= 0) {
                                            Text(
                                                text = "مانده: %,.0f تومان".format(java.util.Locale.US, status.remainingPrepayment),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        } else {
                                            Text(
                                                text = "بدهکار: %,.0f تومان".format(java.util.Locale.US, -status.remainingPrepayment),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Option 1: Simple Pause
                Surface(
                    onClick = { selectedOption = 1 },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedOption == 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selectedOption == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == 1,
                            onClick = { selectedOption = 1 }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "⏸️ ادامه بازی فعلی (توقف ساده)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "توقف موقت بدون محاسبه شرط یا ثبت بخش جدید. با ادامه، زمان ادامه می‌یابد.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Option 2: Commit Record & Pause (Start new segment)
                Surface(
                    onClick = { selectedOption = 2 },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedOption == 2) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selectedOption == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == 2,
                            onClick = { selectedOption = 2 }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "🎯 ثبت رکورد فعلی و توقف (شروع بخش جدید)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "هزینه تا این لحظه ثبت شده و ایستگاه متوقف می‌شود. بخش بعدی از صفر شروع می‌شود.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Option 3: Commit Record & Continue
                Surface(
                    onClick = { selectedOption = 3 },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedOption == 3) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selectedOption == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == 3,
                            onClick = { selectedOption = 3 }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "▶️ ثبت رکورد فعلی و ادامه بلافاصله",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "هزینه تا این لحظه ثبت شده و بازی بدون توقف وارد بخش جدید (صفر) می‌شود.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                if (selectedOption == 2 || selectedOption == 3) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "انتخاب مخاطب/مخاطبانی که هزینه این بخش بر عهده آن‌هاست (1، 2 یا چند نفر):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (availableCustomers.isEmpty()) {
                        Text(
                            text = "هیچ مخاطبی تعریف نشده است.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // This is a short payer list (max 4 guests / station participants).
                        // Keep it as a bounded scrollable Column instead of a nested LazyColumn
                        // inside AlertDialog; this removes the remaining Compose crash path seen
                        // when Stop is opened/confirmed on legacy station data.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableCustomers.forEach { cust ->
                                val isChecked = selectedPayerIds.contains(cust.id)
                                Surface(
                                    onClick = {
                                        if (isChecked) {
                                            selectedPayerIds.remove(cust.id)
                                            selectedPayerNamesMap.remove(cust.id)
                                        } else {
                                            selectedPayerIds.add(cust.id)
                                            selectedPayerNamesMap[cust.id] = cust.fullName
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isChecked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isChecked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cust.fullName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (!selectedPayerIds.contains(cust.id)) {
                                                        selectedPayerIds.add(cust.id)
                                                        selectedPayerNamesMap[cust.id] = cust.fullName
                                                    }
                                                } else {
                                                    selectedPayerIds.remove(cust.id)
                                                    selectedPayerNamesMap.remove(cust.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ids = selectedPayerIds.distinct().toList()
                    val names = ids.map { selectedPayerNamesMap[it].orEmpty() }
                    // Dismiss first, then invoke the operation. A malformed legacy station
                    // must never be able to keep the modal in an unstable Compose state.
                    when (selectedOption) {
                        1 -> { onDismiss(); onSimplePause() }
                        2 -> { onDismiss(); onCommitSegmentAndPause(ids, names) }
                        3 -> { onDismiss(); onCommitSegmentAndContinue(ids, names) }
                    }
                },
                enabled = if (selectedOption == 2 || selectedOption == 3) selectedPayerIds.isNotEmpty() else true,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when (selectedOption) {
                        1 -> "توقف ساده"
                        2 -> "ثبت رکورد و توقف (${selectedPayerIds.size} نفر)"
                        else -> "ثبت رکورد و ادامه (${selectedPayerIds.size} نفر)"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun MultiCustomerPrepaymentDialog(
    selectedCustomers: List<Customer>,
    initialPrepayments: Map<Long, Long>,
    totalPrepayment: Long = 0L,
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, Long>, Long) -> Unit
) {
    val selectedCustomerIdsKey = selectedCustomers.map { it.id }
    var prepayMap by remember(selectedCustomerIdsKey, initialPrepayments, totalPrepayment) {
        mutableStateOf(
            buildMap<Long, String> {
                val uniqueCustomers = selectedCustomers.distinctBy { it.id }
                val hasExplicitAllocation = initialPrepayments.values.any { it > 0L }
                val base = if (!hasExplicitAllocation && totalPrepayment > 0L && uniqueCustomers.isNotEmpty()) totalPrepayment / uniqueCustomers.size else 0L
                val remainder = if (!hasExplicitAllocation && totalPrepayment > 0L && uniqueCustomers.isNotEmpty()) totalPrepayment % uniqueCustomers.size else 0L
                uniqueCustomers.forEachIndexed { index, cust ->
                    val initVal = initialPrepayments[cust.id]?.takeIf { it > 0L }?.toString()
                        ?: if (!hasExplicitAllocation && totalPrepayment > 0L) {
                            (base + if (index == uniqueCustomers.lastIndex) remainder else 0L).toString()
                        } else ""
                    put(cust.id, initVal)
                }
            }
        )
    }
    val totalSum = prepayMap.values.sumOf { it.toLongOrNull() ?: 0L }
    fun updatePrepayment(customerId: Long, value: String) {
        prepayMap = prepayMap.toMutableMap().apply { put(customerId, value.filter(Char::isDigit)) }
    }

    // Do not put a scrollable Column/LazyColumn inside Material3 AlertDialog. Compose's
    // intrinsic measurement path for dialog content has historically produced runtime
    // measurement crashes for scrollable children. A platform Dialog + bounded LazyColumn
    // gives the same Manager UX without that measurement contract.
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "تخصیص پیش‌پرداخت مخاطبان",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "مبلغ پرداختی هر مخاطب را مشخص کنید تا کسر هزینه بازی عادلانه باشد.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedCustomers.distinctBy { it.id }, key = { it.id }) { cust ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cust.fullName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = prepayMap[cust.id] ?: "",
                                    onValueChange = { updatePrepayment(cust.id, it) },
                                    placeholder = { Text("مبلغ (تومان)", fontSize = 10.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(130.dp).height(52.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "مجموع کل پیش‌پرداخت:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "%,d تومان".format(Locale.US, totalSum),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val resultMap = prepayMap.mapValues { it.value.toLongOrNull() ?: 0L }
                            onConfirm(resultMap, totalSum)
                        }
                    ) { Text("ثبت پیش‌پرداخت", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
