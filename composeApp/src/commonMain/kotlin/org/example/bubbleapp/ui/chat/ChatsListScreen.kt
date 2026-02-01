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
import org.example.bubbleapp.data.model.Chat

@Composable
fun ChatsListScreen(
    viewModel: ChatsViewModel,
    onBack: () -> Unit,
    onChatClick: (Chat) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    // Подписка на one-time события
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatsEvent.ChatCreated -> onChatClick(event.chat)
            }
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
                        .clickable { viewModel.showNewChatDialog() },
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
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6C63FF))
                    }
                }

                state.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.errorMessage ?: "Ошибка",
                                color = Color(0xFFFF6B6B),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadChats() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6C63FF)
                                )
                            ) {
                                Text("Повторить")
                            }
                        }
                    }
                }

                state.chats.isEmpty() -> {
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
                        items(state.chats) { chat ->
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
        if (dialogState.isVisible) {
            NewChatDialog(
                state = dialogState,
                onDismiss = { viewModel.hideNewChatDialog() },
                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onSearch = { viewModel.searchUsers() },
                onUserSelected = { viewModel.createChatWithUser(it) }
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
    state: NewChatDialogState,
    onDismiss: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onUserSelected: (org.example.bubbleapp.data.model.User) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isCreating) onDismiss() },
        title = {
            Text("Новый чат", color = Color.White)
        },
        text = {
            Column {
                // Search field
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    label = { Text("Телефон") },
                    placeholder = { Text("+7...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isCreating,
                    trailingIcon = {
                        if (state.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF6C63FF),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6C63FF),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF6C63FF),
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = Color.White,
                        disabledTextColor = Color.White.copy(alpha = 0.5f),
                        disabledBorderColor = Color.Gray.copy(alpha = 0.5f)
                    )
                )

                // Error message
                state.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp
                    )
                }

                // Search results
                if (state.searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Результаты:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    state.searchResults.forEach { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !state.isCreating) { onUserSelected(user) },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF3a3a5e)
                            ),
                            shape = RoundedCornerShape(8.dp)
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
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF6C63FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (user.displayName?.firstOrNull()
                                            ?: user.username?.firstOrNull()
                                            ?: user.phone.lastOrNull()
                                            ?: '?').toString().uppercase(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName ?: user.username ?: user.phone,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    if (user.displayName != null || user.username != null) {
                                        Text(
                                            text = user.phone,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                if (state.isCreating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF6C63FF),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSearch,
                enabled = state.searchQuery.isNotBlank() && !state.isSearching && !state.isCreating
            ) {
                Text(
                    "Найти",
                    color = if (state.searchQuery.isNotBlank() && !state.isSearching && !state.isCreating)
                        Color(0xFF6C63FF) else Color.Gray
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isCreating
            ) {
                Text("Отмена", color = if (!state.isCreating) Color.Gray else Color.Gray.copy(alpha = 0.5f))
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
