package org.example.bubbleapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MyProfileScreen(
    viewModel: MyProfileViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onPickImage: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MyProfileViewEvent.LoggedOut -> onLoggedOut()
                is MyProfileViewEvent.NavigateBack -> onBack()
                is MyProfileViewEvent.ShowToast -> {
                    // TODO: Show toast/snackbar
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2a2a4e))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<-",
                fontSize = 24.sp,
                color = Color.White,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Профиль",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        // Content
        MyProfileView(
            state = state,
            onDisplayNameChanged = viewModel::onDisplayNameChanged,
            onUsernameChanged = viewModel::onUsernameChanged,
            onSaveClick = viewModel::saveProfile,
            onChangeAvatarClick = {
                viewModel.showImagePicker()
                onPickImage()
            },
            onLogoutClick = viewModel::showLogoutConfirmation,
            onLogoutConfirm = viewModel::logout,
            onLogoutCancel = viewModel::hideLogoutConfirmation,
            modifier = Modifier.weight(1f)
        )
    }

    // Image picker modal
    if (state.showImagePicker) {
        ImagePickerSheet(
            onDismiss = viewModel::hideImagePicker,
            onImageSelected = { imageData, fileName ->
                viewModel.uploadAvatar(imageData, fileName)
            }
        )
    }
}

@Composable
expect fun ImagePickerSheet(
    onDismiss: () -> Unit,
    onImageSelected: (ByteArray, String) -> Unit
)
