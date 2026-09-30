package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun AuthDialog(
    viewModel: GameNetViewModel,
    onDismiss: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val language by viewModel.language.collectAsState()
    val isFa = language == "fa"
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(0) } // 0: Login, 1: Register

    // Form fields
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    var regUsername by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }

    var localErrorMessage by remember { mutableStateOf<String?>(null) }
    var localSuccessMessage by remember { mutableStateOf<String?>(null) }

    val isLoading = authState is AuthState.Authenticating

    Dialog(onDismissRequest = {
        if (!isLoading) {
            focusManager.clearFocus()
            onDismiss()
        }
    }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .testTag("auth_dialog_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFa) "حساب کاربری سرور مرکزی" else "User Account & Cloud Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            if (!isLoading) {
                                focusManager.clearFocus()
                                onDismiss()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.size(32.dp).testTag("close_auth_dialog_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            focusManager.clearFocus()
                            selectedTab = 0
                            localErrorMessage = null
                            localSuccessMessage = null
                        },
                        text = {
                            Text(
                                text = if (isFa) "ورود به حساب" else "Login",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("tab_login")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            focusManager.clearFocus()
                            selectedTab = 1
                            localErrorMessage = null
                            localSuccessMessage = null
                        },
                        text = {
                            Text(
                                text = if (isFa) "ثبت‌نام جدید" else "Register",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("tab_register")
                    )
                }

                // Error Message Display
                val displayError = localErrorMessage ?: (authState as? AuthState.AuthenticationError)?.message
                AnimatedVisibility(visible = !displayError.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = displayError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Success Message Display
                AnimatedVisibility(visible = !localSuccessMessage.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF00C853).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = localSuccessMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00C853),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    // ==========================================
                    // TAB 0: LOGIN FORM
                    // ==========================================
                    OutlinedTextField(
                        value = loginUsername,
                        onValueChange = {
                            loginUsername = it
                            localErrorMessage = null
                        },
                        label = { Text(if (isFa) "نام کاربری یا شماره موبایل" else "Username / Phone") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("login_username_input")
                    )

                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = {
                            loginPassword = it
                            localErrorMessage = null
                        },
                        label = { Text(if (isFa) "رمز عبور" else "Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                Icon(
                                    imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (loginUsername.isBlank() || loginPassword.isBlank()) {
                                localErrorMessage = if (isFa) "لطفاً نام کاربری و رمز عبور را وارد کنید." else "Please enter username and password."
                                return@Button
                            }
                            localErrorMessage = null
                            localSuccessMessage = null
                            viewModel.loginUser(loginUsername, loginPassword) { success, msg ->
                                if (success) {
                                    localSuccessMessage = msg
                                } else {
                                    localErrorMessage = msg
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("login_submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFa) "در حال ورود..." else "Logging in...")
                        } else {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFa) "ورود به حساب" else "Login", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // ==========================================
                    // TAB 1: REGISTER FORM
                    // ==========================================
                    OutlinedTextField(
                        value = regUsername,
                        onValueChange = {
                            regUsername = it
                            localErrorMessage = null
                        },
                        label = { Text(if (isFa) "نام کاربری (الزامی)" else "Username (Required)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("register_username_input")
                    )

                    OutlinedTextField(
                        value = regPhone,
                        onValueChange = {
                            regPhone = it
                            localErrorMessage = null
                        },
                        label = { Text(if (isFa) "شماره موبایل (الزامی - جهت لایسنس)" else "Phone Number (Required)") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("register_phone_input")
                    )

                    OutlinedTextField(
                        value = regEmail,
                        onValueChange = {
                            regEmail = it
                            localErrorMessage = null
                        },
                        label = { Text(if (isFa) "ایمیل (الزامی - جهت فاکتور)" else "Email (Required)") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("register_email_input")
                    )

                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = {
                            regPassword = it
                            localErrorMessage = null
                        },
                        label = { Text(if (isFa) "رمز عبور (الزامی)" else "Password (Required)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                Icon(
                                    imageVector = if (regPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("register_password_input")
                    )

                    OutlinedTextField(
                        value = regConfirmPassword,
                        onValueChange = {
                            regConfirmPassword = it
                            localErrorMessage = null
                        },
                        label = { Text(if (isFa) "تکرار رمز عبور (الزامی)" else "Confirm Password (Required)") },
                        leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                        visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("register_confirm_password_input")
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (regUsername.trim().length < 3) {
                                localErrorMessage = if (isFa) "نام کاربری باید حداقل ۳ کاراکتر باشد." else "Username must be at least 3 characters."
                                return@Button
                            }
                            if (regPhone.trim().isBlank() || regPhone.trim().length < 10) {
                                localErrorMessage = if (isFa) "لطفاً شماره موبایل معتبر (مثال: 09123456789) وارد کنید." else "Please enter a valid phone number."
                                return@Button
                            }
                            if (regEmail.trim().isBlank() || !regEmail.contains("@") || !regEmail.contains(".")) {
                                localErrorMessage = if (isFa) "لطفاً آدرس ایمیل معتبر (مثال: name@domain.com) وارد کنید." else "Please enter a valid email."
                                return@Button
                            }
                            if (regPassword.trim().length < 4) {
                                localErrorMessage = if (isFa) "رمز عبور باید حداقل ۴ کاراکتر باشد." else "Password must be at least 4 characters."
                                return@Button
                            }
                            if (regPassword != regConfirmPassword) {
                                localErrorMessage = if (isFa) "رمز عبور و تکرار آن یکسان نیستند." else "Passwords do not match."
                                return@Button
                            }

                            localErrorMessage = null
                            localSuccessMessage = null
                            viewModel.registerUser(
                                username = regUsername,
                                password = regPassword,
                                phone = regPhone.trim(),
                                email = regEmail.trim()
                            ) { success, msg ->
                                if (success) {
                                    localSuccessMessage = msg
                                } else {
                                    localErrorMessage = msg
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("register_submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFa) "در حال ثبت‌نام..." else "Registering...")
                        } else {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFa) "ثبت‌نام و ورود به برنامه" else "Register & Login", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
