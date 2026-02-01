# BubbleApp - Kotlin Multiplatform видео-кружки

## Архитектура
- **KMP + Compose Multiplatform** (iOS/Android)
- expect/actual для платформенного кода

## Структура iOS (iosMain)
```
org.example.bubbleapp/
├── IOSCameraController.kt    - камера, запись (использует FileSystemManager, MediaEncodingManager)
├── CameraPreview.ios.kt      - превью камеры (UIKitViewController)
├── VideoPlayer.ios.kt        - воспроизведение видео:
│   ├── InlineVideoViewController   - полноэкранный плеер
│   ├── InlineVideoPlayer           - actual class обёртка
│   ├── VideoPreviewViewController  - превью в списке (muted, loop)
│   └── VideoThumbnail              - генерация превью
├── storage/
│   └── FileSystemManager.kt  - работа с файлами (Documents dir)
└── video/
    ├── MediaEncodingManager.kt   - AVAssetWriter encoding
    └── ThumbnailGenerator.kt     - генерация thumbnail
```

## Структура commonMain
```
org.example.bubbleapp/
└── App.kt - UI:
    ├── App()              - главный экран (верх: запись, низ: список)
    ├── VideoCircleItem()  - элемент списка с превью
    └── CameraController   - expect interface
```

## Ключевые моменты
- UIKitViewController перехватывает touch → нужен `userInteractionEnabled = false`
- VideoPreviewPlayer: loader 1.5сек минимум, fade-in анимация
- Список: LazyColumn, кружки 100dp, отступ 16dp
- Клик на кружок → оверлей с InlineVideoPlayerView по центру

## Код-паттерны iOS

### UIViewController в Compose
```kotlin
@Composable
fun SomeView() {
    UIKitViewController(
        modifier = modifier,
        factory = { viewController }
    )
}
```

### Фоновые операции
```kotlin
dispatch_async(sessionQueue) { /* heavy work */ }
dispatch_async(dispatch_get_main_queue()) { /* UI updates */ }
```

### Анимации UIKit
```kotlin
UIView.animateWithDuration(0.3) { view.alpha = 1.0 }
```

### Работа с файлами
```kotlin
val url = fileSystemManager.getVideoFileUrl(fileName)  // NSURL
val videos = fileSystemManager.getRecordedVideos()     // List<String>
```

## Импорты iOS
```kotlin
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.ExperimentalForeignApi
```

## Compose паттерны
```kotlin
// State
var value by remember { mutableStateOf(initial) }

// Side effects
LaunchedEffect(key) { /* coroutine */ }
DisposableEffect(key) { onDispose { /* cleanup */ } }

// Модификаторы для кружков
Modifier.size(100.dp).clip(CircleShape).clickable { }
```

## Сборка
```bash
# iOS проверка компиляции
./gradlew :composeApp:compileKotlinIosSimulatorArm64

# Запуск на симуляторе через Xcode
open iosApp/iosApp.xcodeproj
```

## Известные проблемы / TODO

### UI/UX
- [x] **Safe Area (iOS)**: ~~Верхние элементы UI залезают на status bar~~ — ИСПРАВЛЕНО
  - Добавлены `windowInsetsPadding(WindowInsets.statusBars)` и `windowInsetsPadding(WindowInsets.navigationBars)` во все экраны

- [x] **Keyboard Insets**: ~~При открытии клавиатуры элементы UI остаются под ней~~ — ИСПРАВЛЕНО
  - Добавлен `windowInsetsPadding(WindowInsets.ime)` в экраны с полями ввода (ChatScreen, auth screens, CallsScreen)

### Конфигурация
- При тестировании на реальном устройстве нужно менять `DEFAULT_BASE_URL` в `AppState.kt` на IP компьютера в локальной сети

## Документация
- [Kotlin/Native iOS interop](https://kotlinlang.org/docs/native-objc-interop.html)
- [Compose Multiplatform iOS](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-getting-started.html)
- [UIKitViewController](https://developer.android.com/reference/kotlin/androidx/compose/ui/interop/package-summary)
- [AVFoundation](https://developer.apple.com/documentation/avfoundation)
- [expect/actual](https://kotlinlang.org/docs/multiplatform-expect-actual.html)
- [WindowInsets в Compose](https://developer.android.com/develop/ui/compose/layouts/insets)
