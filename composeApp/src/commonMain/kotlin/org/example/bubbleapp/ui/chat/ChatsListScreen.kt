package org.example.bubbleapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.bubbleapp.AppState
import org.example.bubbleapp.data.model.Chat

@Composable
fun ChatsListScreen(
    onBack: () -> Unit,
    appState: AppState,
    onChatClick: (Chat) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewChatDialog by remember { mutableStateOf(false) }

    // Load chats
    LaunchedEffect(Unit) {
        try {
            chats = appState.apiClient.getChats()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "<",
                    fontSize = 24.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Чаты",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                // New chat button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6C63FF))
                        .clickable { showNewChatDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontSize = 24.sp,
                        color = Color.White
                    )
                }
            }

            // Content
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6C63FF))
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage ?: "Ошибка",
                                color = Color(0xFFFF6B6B),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            chats = appState.apiClient.getChats()
                                        } catch (e: Exception) {
                                            errorMessage = e.message
                                        }
                                        isLoading = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6C63FF)
                                )
                            ) {
                                Text("Повторить")
                            }
                        }
                    }
                }

                chats.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Нет чатов",
                                color = Color.White,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Нажмите + чтобы начать",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(chats) { chat ->
                            ChatListItem(
                                chat = chat,
                                onClick = { onChatClick(chat) }
                            )
                        }
                    }
                }
            }
        }

        // New chat dialog
        if (showNewChatDialog) {
            NewChatDialog(
                onDismiss = { showNewChatDialog = false },
                onCreateChat = { phone ->
                    scope.launch {
                        try {
                            // Search for user by phone
                            val users = appState.apiClient.searchUsers(phone)
                            if (users.isNotEmpty()) {
                                val user = users.first()
                                // Create private chat
                                val chat = appState.apiClient.createChat(
                                    org.example.bubbleapp.data.api.CreateChatRequest(
                                        type = "DIRECT",
                                        memberIds = listOf(user.id)
                                    )
                                )
                                chats = listOf(chat) + chats
                                showNewChatDialog = false
                            }
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatListItem(
    chat: Chat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2a2a4e)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6C63FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (chat.name?.firstOrNull()?.toString() ?: "?").uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chat info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.name ?: "Чат",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    chat.lastMessage?.let { msg ->
                        Text(
                            text = formatTime(msg.createdAt),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.lastMessage?.content ?: "Нет сообщений",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6C63FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onCreateChat: (phone: String) -> Unit
) {
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Новый чат", color = Color.White)
        },
        text = {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Телефон или username") },
                placeholder = { Text("+7...") },
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
        },
        confirmButton = {
            TextButton(
                onClick = { onCreateChat(phone) },
                enabled = phone.isNotBlank()
            ) {
                Text("Создать", color = Color(0xFF6C63FF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2a2a4e)
    )
}

private fun formatTime(isoDate: String): String {
    // Simple time extraction from ISO date
    // Format: "2024-01-15T10:30:00"
    return try {
        val timePart = isoDate.substringAfter("T").substringBefore(".")
        timePart.substring(0, 5) // "10:30"
    } catch (e: Exception) {
        ""
    }
}
