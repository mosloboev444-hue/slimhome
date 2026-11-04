# Сборка SlimHome

## Что нужно

| компонент | версия |
|---|---|
| JDK | 17 (проверено на Temurin 17.0.21) |
| Android SDK | platform `android-34`, build-tools `34.0.0` |
| Gradle | 8.9 (скачивается wrapper-ом автоматически) |
| AGP | 8.5.2 |

## Переменные окружения

`JAVA_HOME` должен указывать на JDK 17. Android SDK ищется по `ANDROID_HOME`
(или укажите путь в `local.properties`: `sdk.dir=C\:\\android-sdk`).

## Сборка

```bash
./gradlew assembleRelease      # release APK (подпись debug-ключом — для сайдлоада)
./gradlew assembleDebug        # debug APK
```

Результат: `app/build/outputs/apk/release/app-release.apk`.

## Установка на приставку

```bash
adb connect 192.168.0.50:5555           # ADB по сети, если включён
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity --user 0 com.slimhome.launcher/.MainActivity
```

Для продакшена подпишите release своим ключом: в `app/build.gradle` замените
`signingConfig signingConfigs.debug` на свой `signingConfigs.release`.

## Подводные камни на Windows

- Если Gradle падает с `AccessDeniedException` в `%TEMP%`, задайте
  `TEMP`/`TMP` и `GRADLE_OPTS=-Djava.io.tmpdir=<своя папка>`.
- `ANDROID_USER_HOME` помогает, если `C:\Users\<user>\.android` недоступен для записи.
- Ставьте `platform-tools` из SDK — ADB из сторонних сборок может не видеть устройство.