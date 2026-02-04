package org.example.bubbleapp.di

import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.example.bubbleapp.data.repository.AttachmentRepository
import org.example.bubbleapp.data.repository.ChatRepository
import org.example.bubbleapp.data.repository.MessageRepository
import org.example.bubbleapp.data.repository.UserRepository

val repositoryModule = DI.Module("repository") {
    bindSingleton { ChatRepository(instance()) }
    bindSingleton { UserRepository(instance()) }
    bindSingleton { AttachmentRepository(instance()) }
    bindSingleton { MessageRepository(instance(), instanceOrNull()) }
}
