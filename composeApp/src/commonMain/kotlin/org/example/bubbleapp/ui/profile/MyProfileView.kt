package org.example.bubbleapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.example.bubbleapp.util.fixAvatarUrl

@Composable
fun MyProfileView(
    state: MyProfileViewState,
    onDisplayNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
    onChangeAvatarClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onLogoutConfirm: () -> Unit,
    onLogoutCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF6C63FF)
                )
            }

            state.errorMessage != null && state.user == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.errorMessage,
                        color = Color(0xFFFF6B6B),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6C63FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isUploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = Color.White
                            )
                        } else {
                            val avatarUrl = fixAvatarUrl(state.user?.avatarUrl)
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Аватар",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    onError = { println("Avatar load error: ${it.result.throwable}") },
                                    onSuccess = { println("Avatar loaded successfully") }
                                )
                                // Show first letter as fallback overlay while loading
                                val displayChar = state.user?.displayName?.firstOrNull()
                                    ?: state.user?.username?.firstOrNull()
                                    ?: '?'
                                // Placeholder while loading
                            } else {
                                // Avatar placeholder with first letter
                                val displayChar = state.user?.displayName?.firstOrNull()
                                    ?: state.user?.username?.firstOrNull()
                                    ?: '?'
                                Text(
                                    text = displayChar.uppercase(),
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Change photo button
                    TextButton(
                        onClick = onChangeAvatarClick,
                        enabled = !state.isUploadingAvatar
                    ) {
                        Text(
                            text = "Сменить фото",
                            color = Color(0xFF6C63FF),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Display Name field
                    OutlinedTextField(
                        value = state.editedDisplayName,
                        onValueChange = onDisplayNameChanged,
                        label = { Text("Имя") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.validationErrors.displayNameError != null,
                        supportingText = state.validationErrors.displayNameError?.let {
                            { Text(text = it, color = Color(0xFFFF6B6B)) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF6C63FF),
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color.White,
                            errorBorderColor = Color(0xFFFF6B6B),
                            errorLabelColor = Color(0xFFFF6B6B)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username field
                    OutlinedTextField(
                        value = state.editedUsername,
                        onValueChange = onUsernameChanged,
                        label = { Text("Username") },
                        prefix = { Text("@", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.validationErrors.usernameError != null,
                        supportingText = state.validationErrors.usernameError?.let {
                            { Text(text = it, color = Color(0xFFFF6B6B)) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF6C63FF),
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color.White,
                            errorBorderColor = Color(0xFFFF6B6B),
                            errorLabelColor = Color(0xFFFF6B6B)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phone (read-only)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Телефон",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        Text(
                            text = state.user?.phone ?: "",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Save button
                    Button(
                        onClick = onSaveClick,
                        enabled = state.hasChanges && !state.isSaving &&
                                  state.validationErrors.displayNameError == null &&
                                  state.validationErrors.usernameError == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6C63FF),
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                        )
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Сохранить",
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(32.dp))

                    // Logout button
                    TextButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Выйти из аккаунта",
                            color = Color(0xFFFF6B6B),
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Logout confirmation dialog
        if (state.showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = onLogoutCancel,
                title = {
                    Text(
                        text = "Выход",
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "Вы уверены, что хотите выйти из аккаунта?",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = onLogoutConfirm) {
                        Text(
                            text = "Выйти",
                            color = Color(0xFFFF6B6B)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onLogoutCancel) {
                        Text(
                            text = "Отмена",
                            color = Color(0xFF6C63FF)
                        )
                    }
                },
                containerColor = Color(0xFF2a2a4e)
            )
        }
    }
}
