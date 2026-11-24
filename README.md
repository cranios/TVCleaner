# TV Cleaner (Android 7.1.2 / TV Box)

## Como gerar o APK

### Opção A – Android Studio
1. Abra a pasta `TVCleaner` no Android Studio (aceite instalar SDK 30 e atualizar o Gradle wrapper).
2. Build > Build APK(s). Arquivo: `app/build/outputs/apk/debug/app-debug.apk`.

### Opção B – GitHub (sem instalar nada)
1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta.
2. Aba Actions > "Build APK" > Run workflow.
3. Baixe o artefato `TVCleaner-apk` (contém o .apk).

## Instalar na TV Box
Copie o APK por pendrive ou use `adb connect IP:5555` e `adb install app-debug.apk`.

## O que faz
- Cache externo de todos os apps (/sdcard/Android/data/*/cache) e do próprio app
- Miniaturas, .tmp, .log, .chk, .dmp, LOST.DIR, lixeiras
- Encerra apps de terceiros em segundo plano (libera RAM)
- Modo root (opcional): `pm trim-caches` + `drop_caches` do kernel
- Atalhos para as telas de armazenamento/apps do sistema
