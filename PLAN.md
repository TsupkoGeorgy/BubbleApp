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
- [x] `ApiClient` — HTTP клиент (Ktor)
- [x] `TokenManager` — хранение JWT в Keychain
- [ ] Автоматический refresh токена при 401
- [x] `WebSocketManager` — расширить `SignalingClient` для чатов
- [x] `FileUploader` — загрузка медиа на сервер

## И2. Локальное хранилище
- [ ] SQLDelight схема (chats, messages, users)
- [x] Кэширование чатов и сообщений (in-memory)
- [ ] Очередь неотправленных сообщений
- [ ] Кэш файлов (видео, аватары)

## И3. Модели данных (commonMain) ✅
```kotlin
data class User(id, phone, username, displayName, avatarUrl, lastSeen, isOnline)
data class Chat(id, type, name, avatarUrl, members, lastMessage, unreadCount)
data class Message(id, chatId, senderId, type, content, attachments, replyTo, createdAt, status)
data class Attachment(id, type, url, thumbnailUrl, duration, size)

enum class MessageType { TEXT, VIDEO_BUBBLE, VOICE, STICKER }
enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }
enum class ChatType { DIRECT, GROUP }
```

## И4. Авторизация (UI)
- [x] `PhoneInputScreen` — ввод номера телефона
- [x] `CodeVerifyScreen` — ввод SMS кода
- [x] `ProfileSetupScreen` — имя и аватар (первый вход)
- [x] Сохранение сессии, автовход

## И5. Список чатов
- [x] `ChatsListScreen` — список чатов
- [x] Превью последнего сообщения
- [x] Счётчик непрочитанных
- [x] Онлайн-индикатор
- [ ] Pull-to-refresh
- [x] Создание нового приватного чата (1-на-1)

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
- [x] `ChatScreen` — переписка
- [x] Список сообщений (LazyColumn, пагинация вверх)
- [x] Поле ввода текста + кнопка отправки
- [x] Кнопка записи кружка
- [x] Кнопка звонка
- [x] "Печатает..." индикатор
- [x] Статусы сообщений (галочки)

## И7. Компоненты сообщений
- [x] `TextMessageBubble` — текстовое сообщение
- [x] `VideoBubbleMessage` — кружок (адаптировать существующий)
- [x] `VoiceMessageBubble` — заглушка (UI без функционала)
- [ ] `StickerMessageBubble` — заглушка (UI без функционала)
- [x] Время отправки, статус доставки

## И8. Запись кружка в чате
- [x] Интеграция `IOSCameraController` в чат
- [x] Оверлей записи поверх чата
- [x] Прогресс загрузки на сервер
- [x] Оптимистичное отображение (показать сразу, загрузить в фоне)

## И9. Воспроизведение кружков
- [x] Адаптировать `VideoPlayer` для чата
- [x] Автовоспроизведение при скролле (muted)
- [x] Тап — воспроизведение со звуком
- [x] Кэширование загруженных видео

## И10. Звонки из чата
- [x] Кнопка звонка в header чата
- [x] Интеграция с существующим `CallManager`
- [ ] Сохранение истории звонков
- [ ] Отображение звонков как системных сообщений в чате

## И11. Профили
- [x] `MyProfileScreen` — редактирование профиля
- [x] `UserProfileScreen` — просмотр профиля собеседника
- [x] Смена аватара (камера/галерея)

## И12. Push-уведомления
- [ ] Запрос разрешений
- [ ] Регистрация токена на сервере
- [ ] Обработка push (открытие нужного чата)
- [ ] Badge count

## И13. Дополнительно
- [x] Поиск по чатам
- [x] Удаление сообщений
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
│  - Вызывает UseCase (не Repository напрямую)           │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                   Domain Layer (UseCase)                │
│  UseCase — единица бизнес-логики                       │
│  - Один UseCase = одно действие                        │
│  - Комбинирует несколько Repository при необходимости  │
│  - Содержит бизнес-правила и валидацию                 │
│  - Не знает про UI (чистая логика)                     │
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
- [x] `AuthViewModel` — логин, верификация кода, профиль
- [x] `ChatsViewModel` — список чатов, создание чата
- [x] `ChatViewModel` — сообщения конкретного чата
- [x] `ProfileViewModel` — редактирование профиля (MyProfileViewModel, UserProfileViewModel)

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
- [x] `AuthRepository` — авторизация + хранение токенов
- [x] `ChatRepository` — чаты + кэш
- [x] `MessageRepository` — сообщения + очередь отправки
- [x] `UserRepository` — профили пользователей

### Р4. Добавить DI
- [x] Koin или manual DI для инъекции зависимостей (AppState)
- [ ] Убрать глобальный singleton `AppState`

### Р5. Создать UseCase слой (по мере необходимости)
UseCase нужен когда есть бизнес-логика, которая:
- Комбинирует данные из нескольких источников
- Содержит валидацию или бизнес-правила
- Переиспользуется в разных ViewModel'ях

Примеры UseCase'ов:
- [x] `SendMessageUseCase` — отправка сообщения + загрузка attachment + оптимистичное обновление UI
- [x] `CreateChatUseCase` — проверка существующего чата + создание нового
- [x] `LoginUseCase` — отправка кода + верификация + сохранение токенов + загрузка профиля
- [x] `SyncChatsUseCase` — синхронизация чатов с сервером + обновление кэша (GetChatsUseCase)
- [x] `UploadVideoBubbleUseCase` — сжатие видео + загрузка + создание сообщения (SendVideoBubbleUseCase)

```kotlin
// Пример UseCase
class SendMessageUseCase(
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository
) {
    suspend operator fun invoke(
        chatId: String,
        text: String?,
        attachments: List<LocalFile>
    ): Result<Message> {
        // 1. Загрузить attachments если есть
        val uploadedIds = attachments.map { file ->
            attachmentRepository.upload(file).getOrThrow().id
        }

        // 2. Отправить сообщение
        return messageRepository.send(chatId, text, uploadedIds)
    }
}

// Использование в ViewModel
class ChatViewModel(private val sendMessage: SendMessageUseCase) {
    fun onSendClick(text: String) {
        viewModelScope.launch {
            sendMessage(chatId, text, attachments)
        }
    }
}
```

**Когда НЕ нужен UseCase:**
- Простой CRUD без логики (просто проксирует Repository)
- Один источник данных, нет комбинирования

### Р6. Правила обработки событий из ViewModel

**StateFlow — для UI состояния:**
```kotlin
// Данные которые отрисовываются на экране
private val _state = MutableStateFlow(ScreenState())
val state: StateFlow<ScreenState> = _state.asStateFlow()
```

**SharedFlow — для one-time событий:**
```kotlin
// Навигация, тосты, ошибки — события которые должны произойти один раз
private val _events = MutableSharedFlow<ScreenEvent>()
val events: SharedFlow<ScreenEvent> = _events.asSharedFlow()

// Emit в корутине
scope.launch {
    _events.emit(ScreenEvent.NavigateToChat(chatId))
}
```

**В UI — подписка через LaunchedEffect(Unit):**
```kotlin
// ✅ Правильно: подписка один раз при старте экрана
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is ScreenEvent.NavigateToChat -> onNavigate(event.chatId)
            is ScreenEvent.ShowError -> showToast(event.message)
        }
    }
}
```

**❌ Антипаттерн — НЕ использовать:**
```kotlin
// Плохо: StateFlow для событий + clearEvent()
private val _events = MutableStateFlow<Event?>(null)

fun clearEvent() { _events.value = null }  // ❌ Race conditions

// Плохо: LaunchedEffect с event как key
LaunchedEffect(events) {  // ❌ Срабатывает на каждое изменение
    events?.let { handle(it) }
    viewModel.clearEvent()
}
```

**Почему SharedFlow:**
- Не хранит значение (не повторится при recompose)
- Нет race conditions
- Идиоматично для Kotlin Flow
- События гарантированно доставляются подписчикам

## Приоритет
Рефакторинг можно делать постепенно при добавлении новых фич, не переписывая всё сразу.