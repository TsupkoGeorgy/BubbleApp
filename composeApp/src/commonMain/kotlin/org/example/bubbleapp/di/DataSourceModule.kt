package org.example.bubbleapp.di

import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.example.bubbleapp.data.datasource.remote.AttachmentRemoteDataSource
import org.example.bubbleapp.data.datasource.remote.AuthRemoteDataSource
import org.example.bubbleapp.data.datasource.remote.ChatRemoteDataSource
import org.example.bubbleapp.data.datasource.remote.MessageRemoteDataSource
import org.example.bubbleapp.data.datasource.remote.UserRemoteDataSource

val dataSourceModule = DI.Module("dataSource") {
    bindSingleton { AuthRemoteDataSource(instance()) }
    bindSingleton { ChatRemoteDataSource(instance()) }
    bindSingleton { MessageRemoteDataSource(instance()) }
    bindSingleton { UserRemoteDataSource(instance()) }
    bindSingleton { AttachmentRemoteDataSource(instance()) }
}
