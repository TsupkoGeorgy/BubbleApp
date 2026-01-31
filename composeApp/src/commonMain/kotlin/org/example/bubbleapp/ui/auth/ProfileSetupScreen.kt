package org.example.bubbleapp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileSetupScreen(
    viewModel: AuthViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.profileState.collectAsState()
    val event by viewModel.events.collectAsState()

    // Handle navigation event
    LaunchedEffect(event) {
        if (event is AuthEvent.ProfileSaved) {
            viewModel.clearEvent()
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Text(
                text = "Настройте профиль",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6C63FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.displayName.isNotBlank()) state.displayName.first().uppercase() else "?",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display name input
            OutlinedTextField(
                value = state.displayName,
                onValueChange = { viewModel.onDisplayNameChanged(it) },
                label = { Text("Ваше имя") },
                placeholder = { Text("Как вас называть?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6C63FF),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF6C63FF),
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color.White
                )
            )

            // Username input
            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.onUsernameChanged(it) },
                label = { Text("Username (необязательно)") },
                placeholder = { Text("your_username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6C63FF),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF6C63FF),
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color.White
                ),
                prefix = { Text("@", color = Color.Gray) }
            )

            // Error message
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue button
            Button(
                onClick = { viewModel.saveProfile() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF)
                ),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Продолжить",
                        fontSize = 18.sp
                    )
                }
            }

            // Skip button
            TextButton(
                onClick = { viewModel.skipProfile() }
            ) {
                Text(
                    text = "Пропустить",
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
