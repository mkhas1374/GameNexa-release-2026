package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.LicenseBuyResponse
import com.example.data.network.SubscriptionPlanDto

@Composable
fun SubscriptionActivationScreen(viewModel: GameNetViewModel, isEmbedded: Boolean = false) {
    val plans by viewModel.subscriptionPlans.collectAsState()
    var selectedPlanForPurchase by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    
    // Buyer / Manager inputs for Super Manager activation
    var userPhoneInput by remember { mutableStateOf("") }
    var userNameInput by remember { mutableStateOf("") }
    var gameNetNameInput by remember { mutableStateOf("") }
    var couponCodeInput by remember { mutableStateOf("") }
    var extraDevicesInput by remember { mutableStateOf("0") }
    var isExtendLicenseChecked by remember { mutableStateOf(false) }
    var extendLicenseCodeInput by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    var showPaymentDialog by remember { mutableStateOf(false) }

    // Gateway default is ZARINPAL for online site payment
    var selectedGateway by remember { mutableStateOf("ZARINPAL") }
    
    var showSuccessRedirectDialog by remember { mutableStateOf(false) }
    var recentBuyResult by remember { mutableStateOf<LicenseBuyResponse?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchSubscriptionPlans()
        try {
            val savedPhone = viewModel.decryptSetting("enc_user_phone")
            val savedName = viewModel.decryptSetting("enc_user_name")
            val savedGameNet = viewModel.decryptSetting("enc_gamenet_name")
            if (savedPhone.isNotBlank()) userPhoneInput = savedPhone
            if (savedName.isNotBlank()) userNameInput = savedName
            if (savedGameNet.isNotBlank()) gameNetNameInput = savedGameNet
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openPaymentUrl(url: String) {
        if (url.isNotBlank()) {
            try {
                val parsedUri = Uri.parse(url)
                val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "در حال انتقال به درگاه پرداخت...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "خطا در باز کردن مرورگر: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "خرید یا تمدید اشتراک",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (plans.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(plans) { plan ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        onClick = {
                            selectedPlanForPurchase = plan
                            showPaymentDialog = true
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(plan.name ?: "پلن اشتراک", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "قیمت: %,.0f تومان".format(plan.price ?: 0.0),
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (!plan.savingText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(plan.savingText, fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    selectedPlanForPurchase = plan
                                    showPaymentDialog = true
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("انتخاب و خرید")
                            }
                        }
                    }
                }
            }
        }
    }

    // Purchase Dialog
    if (showPaymentDialog && selectedPlanForPurchase != null) {
        val basePrice = selectedPlanForPurchase?.price ?: 0.0

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showPaymentDialog = false },
            title = { Text("تایید خرید اشتراک ${selectedPlanForPurchase?.name ?: ""}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("خلاصه فاکتور:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("مبلغ پایه: %,.0f تومان".format(basePrice), fontSize = 12.sp)
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text("روش پرداخت:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedGateway == "ZARINPAL",
                                    onClick = { selectedGateway = "ZARINPAL" }
                                )
                                Text("درگاه زرین‌پال (پرداخت آنلاین - انتقال به سایت)", fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedGateway == "MANUAL",
                                    onClick = { selectedGateway = "MANUAL" }
                                )
                                Text("ثبت دستی (انتقال کارت به کارت / تایید Super Manager)", fontSize = 12.sp)
                            }
                        }
                    }

                    Text("اطلاعات خریدار (ارسال به Super Manager):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = userPhoneInput,
                        onValueChange = { userPhoneInput = it },
                        label = { Text("شماره همراه مدیر (الزامی)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        label = { Text("نام و نام خانوادگی مدیر") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = gameNetNameInput,
                        onValueChange = { gameNetNameInput = it },
                        label = { Text("نام گیم‌نت") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = extraDevicesInput,
                        onValueChange = { extraDevicesInput = it.filter { char -> char.isDigit() } },
                        label = { Text("تعداد سیستم/کنسول اضافی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = couponCodeInput,
                        onValueChange = { couponCodeInput = it },
                        label = { Text("کد تخفیف (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isExtendLicenseChecked,
                            onCheckedChange = { isExtendLicenseChecked = it }
                        )
                        Text("تمدید اشتراک/لایسنس موجود", fontSize = 12.sp)
                    }

                    if (isExtendLicenseChecked) {
                        OutlinedTextField(
                            value = extendLicenseCodeInput,
                            onValueChange = { extendLicenseCodeInput = it },
                            label = { Text("کد لایسنس قبلی جهت تمدید") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmitting && userPhoneInput.isNotBlank(),
                    onClick = {
                        isSubmitting = true
                        val extraDevInt = extraDevicesInput.toIntOrNull() ?: 0

                        viewModel.initiateSubscriptionPurchase(
                            plan = selectedPlanForPurchase!!.id ?: "",
                            extraDevices = extraDevInt,
                            coupon = couponCodeInput,
                            extendLicense = isExtendLicenseChecked,
                            existingLicenseCode = extendLicenseCodeInput,
                            userPhone = userPhoneInput,
                            userName = userNameInput,
                            gameNetName = gameNetNameInput,
                            gateway = selectedGateway
                        ) { res ->
                            isSubmitting = false
                            if (res != null) {
                                recentBuyResult = res
                                val targetUrl = res.paymentUrl ?: ""
                                val targetLicense = res.licenseCode ?: ""
                                viewModel.savePendingPurchasedLicense(targetLicense)

                                showPaymentDialog = false
                                showSuccessRedirectDialog = true

                                // Automatically open the gateway URL if valid
                                if (targetUrl.isNotBlank() && (selectedGateway == "ZARINPAL" || targetUrl.startsWith("http"))) {
                                    openPaymentUrl(targetUrl)
                                }
                            } else {
                                Toast.makeText(context, "خطا در برقراری ارتباط با سرور و ثبت درخواست خرید", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(if (selectedGateway == "ZARINPAL") "انتقال به سایت جهت خرید آنلاین" else "ثبت تراکنش و ارسال به Super Manager")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = { showPaymentDialog = false }
                ) {
                    Text("انصراف")
                }
            }
        )
    }

    // Success / Gateway Redirect Dialog
    if (showSuccessRedirectDialog && recentBuyResult != null) {
        val res = recentBuyResult!!
        val paymentUrl = res.paymentUrl ?: ""

        AlertDialog(
            onDismissRequest = { showSuccessRedirectDialog = false },
            title = { Text("تراکنش و فعال‌سازی ثبت گردید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اطلاعات خرید با موفقیت ثبت شد و به Super Manager ارسال گردید.", fontSize = 13.sp)
                    if (!res.licenseCode.isNullOrBlank()) {
                        Text("کد لایسنس رزرو شده: ${res.licenseCode}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                    if (res.amount != null && res.amount > 0) {
                        Text("مبلغ قابل پرداخت: %,.0f تومان".format(res.amount), fontSize = 12.sp)
                    }

                    if (paymentUrl.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("جهت تکمیل پرداخت آنلاین، روی دکمه زیر کلیک کنید:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                if (paymentUrl.isNotBlank()) {
                    Button(onClick = {
                        openPaymentUrl(paymentUrl)
                    }) {
                        Text("ورود به سایت جهت خرید")
                    }
                } else {
                    Button(onClick = { showSuccessRedirectDialog = false }) {
                        Text("متوجه شدم")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuccessRedirectDialog = false }) {
                    Text("بستن")
                }
            }
        )
    }
}
