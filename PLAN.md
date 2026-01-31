/# План разработки BubbleApp Messenger

## Текущее состояние

### Что уже есть
- Запись и воспроизведение видео-кружков
- Аудио-звонки через WebSocket (базовый relay)
- CallKit интеграция
- Транскрипция речи
- Простой WebSocket сервер (Spring Boot)

### Заложено на будущее (не реализуем сейчас)
- **Голосовые сообщения:** `MessageType.VOICE` в enum, UI заглушка
- **Стикеры:** `MessageType.STICKER` в enum, UI заглушка

---

# БЭКЕНД

## Б1. Инфраструктура
- [ ] PostgreSQL база данных
- [ ] S3-совместимое хранилище файлов (MinIO/AWS)
- [ ] Миграции БД (Flyway/Liquibase)
- [ ] Docker-compose для локальной разработки

## Б2. Модели данных
```sql
users (id, phone, username, display_name, avatar_url, created_at, last_seen)
chats (id, type, name, avatar_url, created_at)
chat_members (chat_id, user_id, role, joined_at)
messages (id, chat_id, sender_id, type, content, reply_to_id, created_at, updated_at, deleted_at)
attachments (id, message_id, type, url, thumbnail_url, duration, size, metadata)
devices (id, user_id, push_token, platform, created_at)
```

**message.type:** `text`, `video_bubble`, `voice`, `sticker`

## Б3. Аутентификация
- [ ] POST `/auth/send-code` — отправка SMS (интеграция с SMS-провайдером)
- [ ] POST `/auth/verify` — проверка кода, выдача JWT
- [ ] POST `/auth/refresh` — обновление access token
- [ ] POST `/auth/logout` — инвалидация токена
- [ ] Middleware проверки JWT

## Б4. API пользователей
- [ ] GET `/users/me` — мой профиль
- [ ] PUT `/users/me` — обновление профиля
- [ ] POST `/users/me/avatar` — загрузка аватара
- [ ] GET `/users/:id` — профиль пользователя
- [ ] GET `/users/search?q=` — поиск по username/телефону

## Б5. API чатов
- [ ] GET `/chats` — список чатов (с последним сообщением, unread count)
- [ ] POST `/chats` — создать приватный чат
- [ ] GET `/chats/:id` — информация о чате
- [ ] DELETE `/chats/:id` — удалить чат

## Б6. API сообщений
- [ ] GET `/chats/:id/messages?before=&limit=` — сообщения с пагинацией
- [ ] POST `/chats/:id/messages` — отправить сообщение
- [ ] PUT `/messages/:id` — редактировать
- [ ] DELETE `/messages/:id` — удалить
- [ ] POST `/chats/:id/read` — отметить прочитанным

## Б7. API файлов
- [ ] POST `/attachments/upload` — загрузка файла (multipart)
- [ ] Генерация presigned URL для загрузки напрямую в S3
- [ ] Генерация thumbnail для видео
- [ ] GET `/attachments/:id` — получение (или redirect на S3)

## Б8. WebSocket (расширить существующий)
```
Аутентификация:
→ { type: "auth", token: "jwt..." }
← { type: "auth_ok", user_id: "..." }

Подписки:
→ { type: "subscribe", chat_id: "..." }
→ { type: "unsubscribe", chat_id: "..." }

События сервер → клиент:
← { type: "new_message", message: {...} }
← { type: "message_updated", message: {...} }
← { type: "message_deleted", message_id, chat_id }
← { type: "typing", chat_id, user_id }
← { type: "user_online", user_id }
← { type: "user_offline", user_id, last_seen }
← { type: "messages_read", chat_id, user_id, until_id }

События клиент → сервер:
→ { type: "typing", chat_id }
→ { type: "mark_read", chat_id, message_id }

Звонки (уже есть):
→/← call_request, call_response, call_end, encryption_key, audio_data
```

## Б9. Push-уведомления
- [ ] Сохранение push-токенов устройств
- [ ] APNs интеграция
- [ ] Отправка push при новом сообщении (если офлайн)
- [ ] VoIP push для звонков

## Б10. Дополнительно
- [ ] Rate limiting
- [ ] Логирование
- [ ] Мониторинг
- [ ] API документация (OpenAPI/Swagger)

---

# iOS

## И1. Сетевой слой
- [ ] `ApiClient` — HTTP клиент (Ktor)
- [ ] `TokenManager` — хранение JWT в Keychain
- [ ] Автоматический refresh токена при 401
- [ ] `WebSocketManager` — расширить `SignalingClient` для чатов
- [ ] `FileUploader` — загрузка медиа на сервер

## И2. Локальное хранилище
- [ ] SQLDelight схема (chats, messages, users)
- [ ] Кэширование чатов и сообщений
- [ ] Очередь неотправленных сообщений
- [ ] Кэш файлов (видео, аватары)

## И3. Модели данных (commonMain)
```kotlin
data class User(id, phone, username, displayName, avatarUrl, lastSeen, isOnline)
data class Chat(id, type, name, avatarUrl, members, lastMessage, unreadCount)
data class Message(id, chatId, senderId, type, content, attachments, replyTo, createdAt, status)
data class Attachment(id, type, url, thumbnailUrl, duration, size)

enum class MessageType { TEXT, VIDEO_BUBBLE, VOICE, STICKER }
enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }
enum class ChatType { PRIVATE, GROUP }
```

## И4. Авторизация (UI)
- [ ] `PhoneInputScreen` — ввод номера телефона
- [ ] `CodeVerifyScreen` — ввод SMS кода
- [ ] `ProfileSetupScreen` — имя и аватар (первый вход)
- [ ] Сохранение сессии, автовход

## И5. Список чатов
- [ ] `ChatsListScreen` — список чатов
- [ ] Превью последнего сообщения
- [ ] Счётчик непрочитанных
- [ ] Онлайн-индикатор
- [ ] Pull-to-refresh
- [ ] Создание нового приватного чата (1-на-1)

## И5.1. Групповые чаты
- [ ] `CreateGroupScreen` — создание группы
  - Название группы
  - Выбор участников (мульти-выбор из контактов)
  - Аватар группы (опционально)
- [ ] Роли участников:
  - **OWNER** — создатель группы, полные права
  - **ADMIN** — может добавлять/удалять участников
  - **MEMBER** — обычный участник
- [ ] `GroupSettingsScreen` — настройки группы (для OWNER/ADMIN)
  - Изменение названия
  - Изменение аватара
  - Список участников с ролями
  - Добавление участников
  - Удаление участников
  - Назначение админов (только OWNER)
  - Выход из группы
- [ ] UI отличия групп от приватных чатов:
  - Название группы в header (не имя собеседника)
  - Показ имени отправителя над каждым сообщением
  - Иконка группы вместо аватара пользователя
  - Счётчик участников

## И6. Экран чата
- [ ] `ChatScreen` — переписка
- [ ] Список сообщений (LazyColumn, пагинация вверх)
- [ ] Поле ввода текста + кнопка отправки
- [ ] Кнопка записи кружка
- [ ] Кнопка звонка
- [ ] "Печатает..." индикатор
- [ ] Статусы сообщений (галочки)

## И7. Компоненты сообщений
- [ ] `TextMessageBubble` — текстовое сообщение
- [ ] `VideoBubbleMessage` — кружок (адаптировать существующий)
- [ ] `VoiceMessageBubble` — заглушка (UI без функционала)
- [ ] `StickerMessageBubble` — заглушка (UI без функционала)
- [ ] Время отправки, статус доставки

## И8. Запись кружка в чате
- [ ] Интеграция `IOSCameraController` в чат
- [ ] Оверлей записи поверх чата
- [ ] Прогресс загрузки на сервер
- [ ] Оптимистичное отображение (показать сразу, загрузить в фоне)

## И9. Воспроизведение кружков
- [ ] Адаптировать `VideoPlayer` для чата
- [ ] Автовоспроизведение при скролле (muted)
- [ ] Тап — воспроизведение со звуком
- [ ] Кэширование загруженных видео

## И10. Звонки из чата
- [ ] Кнопка звонка в header чата
- [ ] Интеграция с существующим `CallManager`
- [ ] Сохранение истории звонков
- [ ] Отображение звонков как системных сообщений в чате

## И11. Профили
- [ ] `MyProfileScreen` — редактирование профиля
- [ ] `UserProfileScreen` — просмотр профиля собеседника
- [ ] Смена аватара (камера/галерея)

## И12. Push-уведомления
- [ ] Запрос разрешений
- [ ] Регистрация токена на сервере
- [ ] Обработка push (открытие нужного чата)
- [ ] Badge count

## И13. Дополнительно
- [ ] Поиск по чатам
- [ ] Удаление сообщений
- [ ] Ответ на сообщение (reply)
- [ ] Пересылка сообщений

---

# ТОЧКИ СИНХРОНИЗАЦИИ

Перед началом работы согласовать:

| # | Что | Формат |
|---|-----|--------|
| 1 | API контракты | OpenAPI spec |
| 2 | WebSocket протокол | JSON schema событий |
| 3 | Формат JWT | payload структура |
| 4 | Формат ошибок | `{ error: string, code: string }` |
| 5 | Форматы файлов | video: mp4 h264, voice: m4a aac |

---

# ПОРЯДОК РЕАЛИЗАЦИИ

## Фаза 1: Авторизация
| Бэкенд | iOS |
|--------|-----|
| Б1, Б2, Б3 | И1, И3, И4 |

## Фаза 2: Чаты и текст
| Бэкенд | iOS |
|--------|-----|
| Б4, Б5, Б6, Б8 | И2, И5, И6, И7 (текст) |

## Фаза 3: Видео-кружки
| Бэкенд | iOS |
|--------|-----|
| Б7 | И7 (видео), И8, И9 |

## Фаза 4: Звонки + Push
| Бэкенд | iOS |
|--------|-----|
| Б9 | И10, И11, И12 |

## Фаза 5: Полировка
| Бэкенд | iOS |
|--------|-----|
| Б10 | И13 |

---

# РЕФАКТОРИНГ: Архитектура iOS

## Проблема
Сейчас сетевые запросы и бизнес-логика находятся прямо в Composable функциях (внутри `rememberCoroutineScope().launch`). Это нарушает принцип разделения ответственности.

## Целевая архитектура

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                           │
│  Composable functions — ТОЛЬКО отрисовка               │
│  - Подписка на StateFlow из ViewModel                  │
│  - Вызов методов ViewModel по событиям (onClick и тд)  │
│  - Никакой бизнес-логики, никаких suspend функций      │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                   ViewModel Layer                       │
│  - Держит UI State (StateFlow)                         │
│  - Обрабатывает Intent/Event от UI                     │
│  - Запускает корутины в viewModelScope                 │
│  - Вызывает Repository/UseCase                         │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    Data Layer                           │
│  Repository — единая точка доступа к данным            │
│  - Координирует API и локальный кэш                    │
│  - Возвращает Flow для реактивных данных               │
│                                                         │
│  ApiClient — HTTP запросы                              │
│  LocalDatabase — SQLDelight/Room                       │
└─────────────────────────────────────────────────────────┘
```

## Задачи рефакторинга

### Р1. Создать ViewModel'и
- [ ] `AuthViewModel` — логин, верификация кода, профиль
- [ ] `ChatsViewModel` — список чатов, создание чата
- [ ] `ChatViewModel` — сообщения конкретного чата
- [ ] `ProfileViewModel` — редактирование профиля

### Р2. Вынести состояние в ViewModel
```kotlin
// Было (плохо):
@Composable
fun SomeScreen() {
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            isLoading = true
            apiClient.doSomething()  // ❌ API в UI
            isLoading = false
        }
    })
}

// Должно быть (хорошо):
@Composable
fun SomeScreen(viewModel: SomeViewModel) {
    val state by viewModel.state.collectAsState()

    Button(onClick = { viewModel.onButtonClick() })  // ✅ Только вызов
}

class SomeViewModel(private val repository: SomeRepository) : ViewModel() {
    private val _state = MutableStateFlow(SomeState())
    val state: StateFlow<SomeState> = _state

    fun onButtonClick() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.doSomething()
            _state.update { it.copy(isLoading = false) }
        }
    }
}
```

### Р3. Создать Repository слой
- [ ] `AuthRepository` — авторизация + хранение токенов
- [ ] `ChatRepository` — чаты + кэш
- [ ] `MessageRepository` — сообщения + очередь отправки
- [ ] `UserRepository` — профили пользователей

### Р4. Добавить DI
- [ ] Koin или manual DI для инъекции зависимостей
- [ ] Убрать глобальный singleton `AppState`

## Приоритет
Рефакторинг можно делать постепенно при добавлении новых фич, не переписывая всё сразу.