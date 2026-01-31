# Backend Code Review - BubbleApp

**Дата:** 2026-01-31
**Версия:** 1.0.0

---

## Критические проблемы (CRITICAL)

### 1. Хардкод кода верификации
- **Файл:** `auth/service/AuthService.kt:31`
- **Проблема:** Код верификации захардкожен как "1234"
- **Риск:** Любой может авторизоваться с любым номером телефона
- **Решение:** Интеграция с SMS-провайдером (Twilio, SMS.ru) или временно генерировать случайный код
```kotlin
val code = (1000..9999).random().toString()
```

### 2. Коды верификации в памяти без TTL
- **Файл:** `auth/service/AuthService.kt:23-25`
- **Проблема:** `verificationCodes` хранятся в `mutableMapOf` без срока жизни
- **Риск:** Brute force атаки, коды не истекают
- **Решение:** Использовать Redis с TTL 5 минут
```kotlin
// Redis example
redisTemplate.opsForValue().set("verify:$phone", code, Duration.ofMinutes(5))
```

### 3. WebSocket без обязательной авторизации
- **Файл:** `websocket/JwtWebSocketInterceptor.kt:36-41`
- **Проблема:** `beforeHandshake()` возвращает `true` даже без JWT токена
- **Риск:** Неавторизованные пользователи могут подключаться к WebSocket
- **Решение:**
```kotlin
if (token == null || !jwtService.validateToken(token)) {
    log.warn("WebSocket connection rejected - no valid token")
    return false  // Reject connection
}
```

### 4. Attachments без проверки доступа
- **Файл:** `attachment/controller/AttachmentController.kt:43-57`
- **Проблема:** Эндпоинты `GET /attachments/{id}` и `GET /attachments/{id}/download-url` не проверяют права доступа
- **Риск:** Любой пользователь может скачать любой файл, зная UUID
- **Решение:** Добавить проверку, что пользователь является участником чата, к которому относится attachment

### 5. Нет Rate Limiting
- **Файл:** `auth/controller/AuthController.kt`
- **Проблема:** `/auth/send-code` позволяет неограниченное количество запросов
- **Риск:** DoS атаки, спам SMS
- **Решение:** Добавить rate limiting (bucket4j + Redis или Spring Cloud Gateway)
```yaml
# Рекомендуемые лимиты:
# - 1 запрос на номер в 60 секунд
# - 5 запросов на IP в минуту
# - 100 запросов на IP в час
```

### 6. Небезопасный type cast в JWT
- **Файл:** `security/JwtService.kt:64-79`
- **Проблема:** `claims["phone"] as String` может выбросить ClassCastException
- **Риск:** Падение приложения на невалидных токенах
- **Решение:**
```kotlin
fun getPhoneFromToken(token: String): String {
    val claims = parseClaimsJws(token)
    return claims["phone"]?.toString()
        ?: throw InvalidTokenException("Phone not found in token")
}
```

---

## Высокие проблемы (HIGH)

### 7. Signaling без валидации userId
- **Файл:** `signaling/SignalingHandler.kt:62-76`
- **Проблема:** `handleRegister` позволяет регистрацию с любым userId без проверки JWT
- **Риск:** Пользователь может выдать себя за другого
- **Решение:** Брать userId из JWT токена в WebSocket session attributes

### 8. Path traversal в именах файлов
- **Файл:** `attachment/service/S3StorageService.kt:30-31`
- **Проблема:** Расширение файла извлекается без санитизации
- **Риск:** Потенциальная атака path traversal
- **Решение:**
```kotlin
val extension = fileName
    .substringAfterLast('.', "bin")
    .replace(Regex("[^a-zA-Z0-9]"), "")
    .take(10)
```

### 9. Нет валидации пагинации
- **Файл:** `message/service/MessageService.kt:90`
- **Проблема:** Параметры `page` и `size` не проверяются на допустимые значения
- **Риск:** OOM при запросе `size=1000000`
- **Решение:**
```kotlin
fun getMessages(chatId: UUID, userId: UUID, page: Int = 0, size: Int = 50): MessageListResponse {
    val validPage = maxOf(0, page)
    val validSize = minOf(maxOf(1, size), 100) // Max 100 messages per page
    // ...
}
```

### 10. Логирование кода верификации
- **Файл:** `auth/service/AuthService.kt:34`
- **Проблема:** `log.info("Verification code sent to $phone: $code (mock)")`
- **Риск:** Коды видны в логах
- **Решение:** Убрать код из логов или использовать DEBUG уровень только для dev

---

## Средние проблемы (MEDIUM)

### 11. Нет CORS конфигурации
- **Файл:** `config/SecurityConfig.kt`
- **Проблема:** CORS не настроен явно
- **Решение:**
```kotlin
@Bean
fun corsConfigurationSource(): CorsConfigurationSource {
    val config = CorsConfiguration()
    config.allowedOrigins = listOf("https://app.bubbleapp.com")
    config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
    config.allowedHeaders = listOf("*")
    config.allowCredentials = true

    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/**", config)
    return source
}
```

### 12. Синхронные push-уведомления
- **Файл:** `message/service/MessageService.kt:72-85`
- **Проблема:** Push-уведомления отправляются синхронно в forEach
- **Риск:** Блокировка потока запроса
- **Решение:** Использовать `@Async` или очередь сообщений

### 13. Захардкоженные лимиты файлов
- **Файл:** `attachment/service/AttachmentService.kt:24-27`
- **Проблема:** Лимиты файлов в коде, не в конфигурации
- **Решение:** Вынести в `application.yml`
```yaml
attachment:
  max-size:
    video: 419430400   # 400MB
    image: 10485760    # 10MB
    voice: 104857600   # 100MB
    file: 1073741824   # 1GB
```

### 14. Нет индексов в JPA entities
- **Проблема:** Отсутствуют `@Index` аннотации на часто запрашиваемых полях
- **Решение:** Добавить индексы (уже есть в SQL миграциях, но можно добавить в entities для документации)

---

## Низкие проблемы (LOW)

### 15. Inconsistent exception hierarchy
- Разные модули определяют свои исключения без общего базового класса
- **Решение:** Создать `BubbleAppException` как базовый класс

### 16. Дублирование кода валидации membership
- `ChatService`, `MessageService` дублируют логику проверки членства в чате
- **Решение:** Вынести в общий сервис или аспект

### 17. Отсутствует health check эндпоинт для БД
- `/status` не проверяет соединение с БД
- **Решение:** Использовать Spring Actuator health endpoint

---

## Рекомендуемый порядок исправлений

### Фаза 1 — Критические (перед production)
- [ ] #3 WebSocket авторизация
- [ ] #4 Проверка доступа к attachments
- [ ] #6 Безопасный type cast в JWT
- [ ] #9 Валидация пагинации
- [ ] #10 Убрать код из логов

### Фаза 2 — Высокие (перед релизом)
- [ ] #1 Реальная генерация кодов
- [ ] #2 Redis для кодов с TTL
- [ ] #5 Rate limiting
- [ ] #7 Валидация userId в signaling
- [ ] #8 Санитизация имен файлов

### Фаза 3 — Средние (улучшения)
- [ ] #11 CORS конфигурация
- [ ] #12 Async push-уведомления
- [ ] #13 Конфигурируемые лимиты

### Фаза 4 — Технический долг
- [ ] #14-17 Рефакторинг и улучшения

---

## Конфигурация для Production

```yaml
# application-prod.yml
spring:
  datasource:
    url: ${DATABASE_URL}
    hikari:
      maximum-pool-size: 20

jwt:
  secret: ${JWT_SECRET}  # Минимум 256 бит, из env variable

push:
  fcm:
    enabled: true
    server-key: ${FCM_SERVER_KEY}
  apns:
    enabled: true
    key-id: ${APNS_KEY_ID}
    team-id: ${APNS_TEAM_ID}

logging:
  level:
    org.example.bubbleapp: INFO
    org.example.bubbleapp.auth: WARN  # Не логировать коды
```

---

## Чеклист безопасности перед релизом

- [ ] JWT secret минимум 256 бит
- [ ] HTTPS только (HTTP redirect)
- [ ] Rate limiting на auth endpoints
- [ ] Все WebSocket требуют JWT
- [ ] Attachments проверяют права доступа
- [ ] Логи не содержат sensitive data
- [ ] CORS настроен на конкретные домены
- [ ] Database credentials в environment variables
- [ ] S3/MinIO credentials в environment variables
