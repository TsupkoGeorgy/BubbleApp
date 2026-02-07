# User Profiles (Профили пользователей)

> **Version:** 1.0
> **Created:** 2026-02-04
> **Status:** Draft

## History

| Version | Date       | Author | Changes |
|---------|------------|--------|---------|
| 1.0     | 2026-02-04 | system-analytics | Initial specification |

---

## 1. Context & Problem

### Текущий контекст системы
- BubbleApp — мессенджер на Kotlin Multiplatform + Compose Multiplatform (iOS/Android) с видео-кружками
- Авторизация полностью реализована: телефон → SMS код → создание профиля при первом входе
- Модель `User` существует: `id`, `phone`, `username`, `displayName`, `avatarUrl`, `lastSeen`, `isOnline`
- Навигация реализована через `sealed class Screen` в `App.kt` с `AnimatedContent`
- Архитектура: Screen → ViewModel → UseCase → Repository → DataSource
- DI через Kodein (`AppState.kt`)

### API эндпоинты (уже реализованы на бэкенде)
- `GET /users/me` — получение своего профиля
- `PATCH /users/me` — обновление профиля (displayName, username)
- `POST /users/me/avatar` — загрузка аватара (multipart/form-data)
- `GET /users/:id` — профиль другого пользователя

### Проблема / потребность пользователя
- Пользователь не может просмотреть/редактировать свой профиль после регистрации
- Нет возможности загрузить или сменить аватар
- При просмотре чужого профиля нет удобного способа начать чат или позвонить
- Отсутствует кнопка выхода из аккаунта в удобном месте (сейчас только на HomeScreen)

### Связь с бизнес-целями
- Улучшение UX и персонализации приложения
- Повышение вовлечённости пользователей
- Социальная составляющая мессенджера (профили, аватары)

---

## 2. Goals & Non-Goals

### Goals
- G-1: Реализовать экран просмотра и редактирования своего профиля (MyProfileScreen)
- G-2: Реализовать загрузку/смену аватара из галереи или камеры
- G-3: Реализовать экран просмотра профиля другого пользователя (UserProfileScreen)
- G-4: Добавить действия на профиле другого пользователя: "Написать", "Позвонить"
- G-5: Отображать online/offline статус пользователя
- G-6: Добавить функционал logout на экране профиля

### Non-Goals
- NG-1: Групповые профили / настройки группы (нет групповых чатов)
- NG-2: Блокировка пользователей
- NG-3: Настройки приватности профиля
- NG-4: Редактирование номера телефона
- NG-5: Двухфакторная аутентификация

---

## 3. User Stories / Scenarios

### US-1: Просмотр своего профиля
**Как** пользователь
**Я хочу** открыть свой профиль
**Чтобы** увидеть свои данные (аватар, имя, username, телефон)

### US-2: Редактирование displayName
**Как** пользователь
**Я хочу** изменить отображаемое имя
**Чтобы** другие пользователи видели актуальное имя

### US-3: Редактирование username
**Как** пользователь
**Я хочу** изменить свой username
**Чтобы** меня было проще найти

### US-4: Смена аватара
**Как** пользователь
**Я хочу** загрузить фото профиля
**Чтобы** персонализировать свой аккаунт

### US-5: Выход из аккаунта
**Как** пользователь
**Я хочу** выйти из аккаунта
**Чтобы** сменить аккаунт или защитить свои данные

### US-6: Просмотр чужого профиля
**Как** пользователь
**Я хочу** открыть профиль собеседника из чата
**Чтобы** узнать больше о нём

### US-7: Начать чат из профиля
**Как** пользователь
**Я хочу** нажать "Написать" на профиле
**Чтобы** быстро перейти к переписке

### US-8: Позвонить из профиля
**Как** пользователь
**Я хочу** нажать "Позвонить" на профиле
**Чтобы** начать звонок без перехода в другие разделы

---

## 4. Scope & Out of Scope

### In Scope
- MyProfileScreen с просмотром и редактированием
- UserProfileScreen (read-only) для других пользователей
- Загрузка аватара (галерея + камера)
- Интеграция с существующим API (`/users/me`, `/users/:id`, `/users/me/avatar`)
- Навигация: из HomeScreen, из ChatScreen (по клику на аватар собеседника)
- Online/offline статус на UserProfileScreen
- Кнопка logout на MyProfileScreen

### Out of Scope
- Редактирование/удаление номера телефона
- Настройки приватности (кто видит lastSeen и т.д.)
- Обрезка/редактирование фото перед загрузкой
- Анимации при смене аватара
- История изменений профиля
- Верификация username на уникальность (предполагается, что API возвращает ошибку)

---

## 5. Functional Requirements

### FR-1: MyProfileScreen
- FR-1.1: Отображение аватара (круглый, 120dp)
  - Если `avatarUrl != null` — загрузить и показать изображение
  - Если `avatarUrl == null` — placeholder с первой буквой `displayName` или `?`
- FR-1.2: Отображение `displayName` (редактируемое поле)
- FR-1.3: Отображение `username` с префиксом `@` (редактируемое поле)
- FR-1.4: Отображение `phone` (только чтение, без возможности редактирования)
- FR-1.5: Кнопка "Сохранить" — активна только если есть изменения
- FR-1.6: Кнопка "Сменить аватар" — открывает выбор источника (галерея/камера)
- FR-1.7: Кнопка "Выйти" — logout с подтверждением

### FR-2: UserProfileScreen
- FR-2.1: Отображение аватара (круглый, 120dp)
- FR-2.2: Отображение `displayName`
- FR-2.3: Отображение `username` с префиксом `@` (если есть)
- FR-2.4: Отображение online/offline статуса
  - Online: зелёный индикатор + текст "в сети"
  - Offline: серый индикатор + "был(а) в сети: <lastSeen>"
- FR-2.5: Кнопка "Написать" — открыть существующий чат или создать новый
- FR-2.6: Кнопка "Позвонить" — начать звонок (интеграция с CallManager)

### FR-3: Загрузка аватара
- FR-3.1: Поддержка выбора из галереи (Image Picker)
- FR-3.2: Поддержка съёмки с камеры
- FR-3.3: Автоматическое сжатие изображения (max 1024x1024, JPEG quality 80%)
- FR-3.4: Показ progress-индикатора при загрузке
- FR-3.5: Обработка ошибок (сеть, формат файла, размер)
- FR-3.6: Обновление UI после успешной загрузки

### FR-4: Навигация
- FR-4.1: Добавить `Screen.MyProfile` и `Screen.UserProfile(userId: String)` в sealed class
- FR-4.2: Переход на MyProfileScreen из HomeScreen (иконка профиля в header)
- FR-4.3: Переход на UserProfileScreen из ChatScreen (клик на аватар/имя собеседника)
- FR-4.4: Кнопка "Назад" на обоих экранах

### FR-5: Валидация
- FR-5.1: `displayName` — минимум 1 символ, максимум 64 символа
- FR-5.2: `username` — только латиница, цифры, underscore; 3-32 символа
- FR-5.3: Показывать ошибки валидации под соответствующими полями

---

## 6. API / Integration

### Existing APIs

#### GET /users/me
**Response:**
```json
{
  "id": "uuid",
  "phone": "+79001234567",
  "username": "john_doe",
  "displayName": "John Doe",
  "avatarUrl": "https://s3.../avatar.jpg",
  "lastSeen": "2026-02-04T12:00:00Z",
  "isOnline": true
}
```

#### PATCH /users/me
**Request:**
```json
{
  "username": "new_username",
  "displayName": "New Name"
}
```
**Response:** Updated User object

**Error codes:**
- 400 Bad Request — validation error
- 409 Conflict — username already taken

#### POST /users/me/avatar
**Request:** multipart/form-data with `file` field
**Response:** Updated User object with new `avatarUrl`

**Error codes:**
- 400 Bad Request — invalid file format
- 413 Payload Too Large — file too big

#### GET /users/:id
**Response:** User object (same as /users/me)

**Error codes:**
- 404 Not Found — user not found

### New / Changed APIs
Новые API не требуются. Все эндпоинты уже реализованы.

### Клиентские изменения

#### UserRemoteDataSource
Добавить методы:
```kotlin
suspend fun getMe(): User
suspend fun updateProfile(request: UpdateProfileRequest): User
suspend fun uploadAvatar(file: ByteArray, fileName: String): User
```

#### UserRepository
Добавить методы:
```kotlin
suspend fun getMyProfile(): User
suspend fun updateMyProfile(displayName: String?, username: String?): User
suspend fun uploadAvatar(imageData: ByteArray, fileName: String): User
```

---

## 7. Data Model

### Существующие модели (без изменений)

#### User (data/model/User.kt)
```kotlin
@Serializable
data class User(
    val id: String,
    val phone: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val lastSeen: String? = null,
    val isOnline: Boolean = false
)
```

### Новые модели

#### UpdateProfileRequest (если ещё нет)
```kotlin
@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val displayName: String? = null
)
```

### Инварианты и ограничения
- `username` уникален в системе
- `displayName` может быть пустым (в этом случае показывать телефон или username)
- `avatarUrl` — полный URL к изображению в S3
- `lastSeen` — ISO 8601 формат

---

## 8. UX / UI Overview

### MyProfileScreen

```
┌─────────────────────────────────┐
│  < Назад           Профиль     │ Header
├─────────────────────────────────┤
│                                 │
│         ┌───────────┐           │
│         │  Avatar   │           │ 120dp, круглый
│         │   (J)     │           │ Placeholder: первая буква
│         └───────────┘           │
│      [Сменить фото]             │ TextButton под аватаром
│                                 │
│  Имя                            │
│  ┌─────────────────────────┐    │
│  │ John Doe                │    │ OutlinedTextField
│  └─────────────────────────┘    │
│                                 │
│  Username                       │
│  ┌─────────────────────────┐    │
│  │ @ john_doe              │    │ OutlinedTextField с префиксом
│  └─────────────────────────┘    │
│                                 │
│  Телефон                        │
│  +7 900 123-45-67               │ Text (read-only, серый)
│                                 │
│                                 │
│  ┌─────────────────────────┐    │
│  │      Сохранить          │    │ Primary Button (enabled если есть изменения)
│  └─────────────────────────┘    │
│                                 │
│                                 │
│       [Выйти из аккаунта]       │ TextButton, красный
│                                 │
└─────────────────────────────────┘
```

### UserProfileScreen

```
┌─────────────────────────────────┐
│  < Назад                        │ Header
├─────────────────────────────────┤
│                                 │
│         ┌───────────┐           │
│         │  Avatar   │           │ 120dp, круглый
│         │   (J)     │           │
│         └───────────┘           │
│                                 │
│        John Doe                 │ Bold, 24sp
│        @john_doe                │ Gray, 16sp
│                                 │
│      ● В сети                   │ Зелёный индикатор
│   или                           │
│      ○ Был(а) 5 мин назад       │ Серый индикатор
│                                 │
│                                 │
│  ┌────────────┐ ┌────────────┐  │
│  │ Написать   │ │ Позвонить  │  │ Две кнопки в ряд
│  └────────────┘ └────────────┘  │
│                                 │
└─────────────────────────────────┘
```

### Навигация
- HomeScreen: добавить иконку профиля в правом верхнем углу (вместо текста "Выйти")
- ChatScreen: клик на имя/аватар собеседника в header → UserProfileScreen
- UserProfileScreen → "Написать" → ChatScreen
- UserProfileScreen → "Позвонить" → CallsScreen (с автоматическим вызовом)

### Особые требования
- **Цветовая схема:** соответствует текущей (тёмная тема, primary: #6C63FF)
- **Safe Area:** использовать `windowInsetsPadding(WindowInsets.statusBars/navigationBars/ime)`
- **Клавиатура:** при открытии клавиатуры экран должен прокручиваться
- **Локализация:** русский язык (как в остальном приложении)

---

## 9. State & Flows

### MyProfileViewModel State

```kotlin
data class MyProfileState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val editedDisplayName: String = "",
    val editedUsername: String = "",
    val hasChanges: Boolean = false,
    val errorMessage: String? = null,
    val validationErrors: ValidationErrors = ValidationErrors()
)

data class ValidationErrors(
    val displayNameError: String? = null,
    val usernameError: String? = null
)
```

### UserProfileViewModel State

```kotlin
data class UserProfileState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val existingChatId: String? = null  // для кнопки "Написать"
)
```

### Основные потоки

#### Happy Path: Редактирование профиля
1. Пользователь открывает MyProfileScreen
2. ViewModel загружает данные через `GET /users/me`
3. Данные отображаются в UI
4. Пользователь изменяет displayName
5. `hasChanges = true`, кнопка "Сохранить" активируется
6. Пользователь нажимает "Сохранить"
7. ViewModel отправляет `PATCH /users/me`
8. Успех → показать toast "Сохранено", `hasChanges = false`

#### Happy Path: Загрузка аватара
1. Пользователь нажимает "Сменить фото"
2. Показывается bottom sheet: "Галерея" / "Камера" / "Отмена"
3. Пользователь выбирает "Галерея"
4. Открывается системный Image Picker
5. Пользователь выбирает фото
6. `isUploadingAvatar = true`, показывается progress
7. Изображение сжимается и загружается через `POST /users/me/avatar`
8. Успех → аватар обновляется в UI

#### Edge Case: Username занят
1. Пользователь меняет username на занятый
2. Нажимает "Сохранить"
3. API возвращает 409 Conflict
4. Показать ошибку под полем username: "Этот username уже занят"

#### Edge Case: Нет сети
1. Пользователь пытается сохранить/загрузить
2. Сеть недоступна
3. Показать toast: "Нет подключения к интернету"

#### Error Recovery
- При ошибке загрузки профиля: показать кнопку "Повторить"
- При ошибке сохранения: сохранить введённые данные, показать ошибку

---

## 10. Non-Functional Requirements

### Производительность
- Время загрузки профиля: < 500ms (при нормальном соединении)
- Время загрузки аватара: < 5s для изображения до 5MB
- Сжатие аватара: выполнять асинхронно, не блокировать UI

### Надёжность / отказоустойчивость
- Кэширование данных профиля (через UserRepository.userCache)
- Retry при сетевых ошибках с экспоненциальной задержкой (опционально)
- Сохранение состояния при смене конфигурации (rotation)

### Безопасность / приватность
- Передача данных только по HTTPS
- Токен авторизации в header (уже реализовано в ApiClient)
- Не логировать чувствительные данные (номер телефона)

### Логирование / наблюдаемость
- Логировать ошибки API (уже есть в ApiClient: LogLevel.BODY)
- Логировать действия пользователя для отладки (опционально)

---

## 11. Dependencies & Constraints

### Зависимости от других сервисов/модулей
- **ApiClient** — HTTP-клиент для API запросов
- **TokenManager** — управление токенами авторизации
- **AuthStateHolder** — состояние авторизации (для logout)
- **ChatRepository** — для создания/получения чата при нажатии "Написать"
- **CallManager** — для начала звонка при нажатии "Позвонить"
- **AttachmentRepository** — существующий код загрузки файлов (может использоваться для аватара)

### Зависимости от библиотек
- **Coil/Kamel** — для загрузки изображений (нужно добавить, если ещё нет)
- **compose-imageloader** или аналог для KMP

### Ограничения
- iOS-first разработка, но код должен быть в commonMain
- Нет Decompose — используется простая навигация через sealed class Screen
- Image Picker — требуется expect/actual для каждой платформы
- Загрузка multipart — требуется platform-specific реализация

---

## 12. Migration / Rollout Plan

### Как выкатываем
- Feature branch → PR → merge в develop
- Фича полностью автономна, не ломает существующий функционал
- Можно выкатывать без feature flag

### Миграции данных
- Не требуются — модель User не меняется

### Feature flags
- Не требуются

### План отката
- Revert PR при критических багах
- Данные пользователей не затрагиваются

---

## 13. Testing Strategy

### Unit Tests
- MyProfileViewModel: загрузка, редактирование, валидация, сохранение
- UserProfileViewModel: загрузка, обработка ошибок
- Валидация username/displayName

### Integration Tests
- API интеграция: GET/PATCH /users/me, POST avatar
- Repository: кэширование, обновление кэша

### UI Tests (опционально)
- MyProfileScreen: отображение данных, редактирование, клик на кнопки
- UserProfileScreen: отображение, навигация

### Основные тест-кейсы
| # | Сценарий | Ожидаемый результат |
|---|----------|---------------------|
| 1 | Открытие MyProfileScreen | Загрузка и отображение данных |
| 2 | Изменение displayName | hasChanges = true, кнопка активна |
| 3 | Сохранение валидных данных | API вызван, данные обновлены |
| 4 | Сохранение с занятым username | Ошибка под полем |
| 5 | Загрузка аватара из галереи | Аватар обновлён |
| 6 | Logout | Переход на PhoneInputScreen |
| 7 | Открытие UserProfileScreen | Загрузка профиля по ID |
| 8 | "Написать" на UserProfileScreen | Переход в чат |

### Требования к тестовым данным
- Тестовый пользователь с заполненным профилем
- Тестовый пользователь с пустым профилем (нет аватара, нет displayName)
- Тестовое изображение для загрузки аватара

---

## 14. Acceptance Criteria

- **AC-1:** Пользователь может открыть свой профиль из HomeScreen
- **AC-2:** На MyProfileScreen отображаются: аватар, displayName, username, phone
- **AC-3:** Пользователь может редактировать displayName и username
- **AC-4:** Валидация полей работает: показываются ошибки, кнопка сохранения блокируется
- **AC-5:** После сохранения изменения отражаются в UI
- **AC-6:** Пользователь может загрузить аватар из галереи
- **AC-7:** Пользователь может сделать фото с камеры и установить как аватар
- **AC-8:** При загрузке аватара показывается progress-индикатор
- **AC-9:** Пользователь может выйти из аккаунта через MyProfileScreen
- **AC-10:** После logout пользователь попадает на экран ввода телефона
- **AC-11:** Пользователь может открыть профиль собеседника из ChatScreen
- **AC-12:** На UserProfileScreen отображается online/offline статус
- **AC-13:** Кнопка "Написать" открывает существующий чат или создаёт новый
- **AC-14:** Кнопка "Позвонить" инициирует звонок

---

## 15. Risks & Open Questions

### Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Image Picker expect/actual сложность | Medium | Medium | Использовать готовую KMP библиотеку (compose-imageloader, peekaboo) |
| Загрузка multipart на iOS | Medium | Low | Использовать NSURLSession напрямую |
| Производительность при сжатии изображений | Low | Low | Сжимать в фоновом потоке |
| Coil не поддерживает iOS | High | Medium | Использовать Kamel или compose-imageloader |

### Open Questions

1. **Q:** Какую библиотеку использовать для загрузки изображений в KMP?
   **Предложение:** Kamel (https://github.com/Kamel-Media/Kamel) или compose-imageloader

2. **Q:** Какую библиотеку использовать для Image Picker в KMP?
   **Предложение:** Peekaboo (https://github.com/niceprograms/peekaboo) или expect/actual

3. **Q:** Нужно ли обрезать изображение перед загрузкой (crop to square)?
   **Предложение:** MVP без crop, добавить в следующей итерации

4. **Q:** Максимальный размер аватара на сервере?
   **Предположение:** 5MB (уточнить у бэкенда)

5. **Q:** Формат lastSeen на сервере (для отображения "был X минут назад")?
   **Предположение:** ISO 8601, парсить на клиенте

---

## Appendix A: File Structure

```
composeApp/src/commonMain/kotlin/org/example/bubbleapp/
├── data/
│   ├── model/
│   │   └── User.kt                    # (существует)
│   ├── datasource/remote/
│   │   └── UserRemoteDataSource.kt    # (добавить методы)
│   └── repository/
│       └── UserRepository.kt          # (добавить методы)
├── domain/usecase/
│   └── user/
│       ├── GetMyProfileUseCase.kt     # (новый)
│       ├── UpdateMyProfileUseCase.kt  # (новый)
│       └── UploadAvatarUseCase.kt     # (новый)
├── ui/
│   └── profile/
│       ├── MyProfileScreen.kt         # (новый)
│       ├── MyProfileViewModel.kt      # (новый)
│       ├── UserProfileScreen.kt       # (новый)
│       └── UserProfileViewModel.kt    # (новый)
└── App.kt                             # (добавить Screen.MyProfile, Screen.UserProfile)
```

## Appendix B: Navigation Changes

```kotlin
// App.kt - добавить в sealed class Screen
sealed class Screen {
    // ... existing
    object MyProfile : Screen()
    data class UserProfile(val userId: String) : Screen()
}
```

## Appendix C: Color Palette (reference)

- Primary: #6C63FF (фиолетовый)
- Background: #1a1a2e (тёмно-синий)
- Card: #2a2a4e
- Online indicator: #4CAF50 (зелёный)
- Offline indicator: #9E9E9E (серый)
- Error: #FF6B6B (красный)
- Text primary: #FFFFFF
- Text secondary: #FFFFFF с alpha 0.6-0.7
