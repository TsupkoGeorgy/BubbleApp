package org.example.bubbleapp.di

import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance
import org.example.bubbleapp.domain.usecase.auth.InitializeAuthUseCase
import org.example.bubbleapp.domain.usecase.auth.LogoutUseCase
import org.example.bubbleapp.domain.usecase.auth.SendCodeUseCase
import org.example.bubbleapp.domain.usecase.auth.UpdateProfileUseCase
import org.example.bubbleapp.domain.usecase.auth.VerifyCodeUseCase
import org.example.bubbleapp.domain.usecase.chat.CreateDirectChatUseCase
import org.example.bubbleapp.domain.usecase.chat.DeleteChatUseCase
import org.example.bubbleapp.domain.usecase.chat.GetChatUseCase
import org.example.bubbleapp.domain.usecase.chat.GetChatsUseCase
import org.example.bubbleapp.domain.usecase.message.DeleteMessageUseCase
import org.example.bubbleapp.domain.usecase.message.LoadMessagesUseCase
import org.example.bubbleapp.domain.usecase.message.MarkAsReadUseCase
import org.example.bubbleapp.domain.usecase.message.SendMessageUseCase
import org.example.bubbleapp.domain.usecase.message.SendVideoBubbleUseCase
import org.example.bubbleapp.domain.usecase.user.GetMyProfileUseCase
import org.example.bubbleapp.domain.usecase.user.GetUserProfileUseCase
import org.example.bubbleapp.domain.usecase.user.GetUserUseCase
import org.example.bubbleapp.domain.usecase.user.SearchUsersByPhoneUseCase
import org.example.bubbleapp.domain.usecase.user.UpdateMyProfileUseCase
import org.example.bubbleapp.domain.usecase.user.UploadAvatarUseCase

val useCaseModule = DI.Module("useCase") {
    // Auth
    bindProvider { SendCodeUseCase(instance()) }
    bindProvider { VerifyCodeUseCase(instance(), instance()) }
    bindProvider { UpdateProfileUseCase(instance(), instance()) }
    bindProvider { InitializeAuthUseCase(instance(), instance()) }
    bindProvider { LogoutUseCase(instance(), instance()) }

    // Chat
    bindProvider { GetChatsUseCase(instance()) }
    bindProvider { GetChatUseCase(instance()) }
    bindProvider { CreateDirectChatUseCase(instance()) }
    bindProvider { DeleteChatUseCase(instance()) }

    // Message
    bindProvider { LoadMessagesUseCase(instance()) }
    bindProvider { SendMessageUseCase(instance()) }
    bindProvider { SendVideoBubbleUseCase(instance(), instance()) }
    bindProvider { DeleteMessageUseCase(instance()) }
    bindProvider { MarkAsReadUseCase(instance()) }

    // User
    bindProvider { SearchUsersByPhoneUseCase(instance()) }
    bindProvider { GetUserUseCase(instance()) }
    bindProvider { GetMyProfileUseCase(instance()) }
    bindProvider { GetUserProfileUseCase(instance()) }
    bindProvider { UpdateMyProfileUseCase(instance()) }
    bindProvider { UploadAvatarUseCase(instance()) }
}
