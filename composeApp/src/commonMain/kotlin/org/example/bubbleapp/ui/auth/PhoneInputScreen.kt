package org.example.bubbleapp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhoneInputScreen(
    viewModel: AuthViewModel,
    onCodeSent: () -> Unit
) {
    val state by viewModel.phoneState.collectAsState()
    val event by viewModel.events.collectAsState()

    // Handle navigation event
    LaunchedEffect(event) {
        if (event is AuthEvent.CodeSent) {
            viewModel.clearEvent()
            onCodeSent()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .windowInsetsPadding(WindowInsets.ime)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo
            Text(
                text = "Bubble",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Войти по номеру телефона",
                fontSize = 20.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Phone input
            OutlinedTextField(
                value = state.phone,
                onValueChange = { viewModel.onPhoneChanged(it) },
                label = { Text("Номер телефона") },
                placeholder = { Text("+7 999 123 45 67") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6C63FF),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF6C63FF),
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color.White
                ),
                isError = state.errorMessage != null
            )

            // Error message
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp
                )
            }

            // Info text
            Text(
                text = "Мы отправим SMS с кодом подтверждения",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Send code button
            Button(
                onClick = { viewModel.sendCode() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF)
                ),
                enabled = !state.isLoading && state.phone.length >= 10
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Получить код",
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
