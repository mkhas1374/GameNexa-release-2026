package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    viewModel: GameNetViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E17),
                        Color(0xFF111827),
                        Color(0xFF030712)
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F2937).copy(alpha = 0.95f)
            ),
            border = BorderStroke(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF3B82F6),
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin Login",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ورود به پنل مدیریت گیم‌نت",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "سیستم جامع مدیریت سالن، استیشن‌ها و بوفه GameNexa",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Role identity info badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF374151)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ورود با شناسه مدیریت اصلی (Super Manager) یا مدیر اجرایی",
                            color = Color(0xFFD1D5DB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("نام کاربری / شناسه کاربری", color = Color(0xFFD1D5DB), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF60A5FA))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_username_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF60A5FA),
                        unfocusedBorderColor = Color(0xFF4B5563)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("رمز عبور", color = Color(0xFFD1D5DB), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF60A5FA))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = Color(0xFF9CA3AF)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_password_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF60A5FA),
                        unfocusedBorderColor = Color(0xFF4B5563)
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFFCA5A5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Login Button
                Button(
                    onClick = {
                        val cleanUser = username.trim()
                        val cleanPass = password.trim()

                        if (cleanUser.isBlank() || cleanPass.isBlank()) {
                            errorMessage = "لطفاً نام کاربری و رمز عبور را وارد کنید."
                            return@Button
                        }

                        isLoggingIn = true
                        viewModel.authenticateAdmin(cleanUser, cleanPass) { success, msg ->
                            isLoggingIn = false
                            if (success) {
                                onLoginSuccess()
                            } else {
                                errorMessage = msg ?: "نام کاربری یا رمز عبور نامعتبر است."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("admin_login_submit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB)
                    ),
                    enabled = !isLoggingIn
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ورود به سالن گیم‌نت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
