package org.example.bubbleapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
@Profile("local")
class LocalConfig {

    @Bean
    fun s3Client(): S3Client? = null

    @Bean
    fun s3Presigner(): S3Presigner? = null
}
