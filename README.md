# AXION AI offline – Android (Android 10+)

Aplikasi **AI offline** untuk Android dengan fitur:

- ✅ Minimal Android 10 (API 29)
- ✅ Nama aplikasi: **AXION AI offline**
- ✅ Multi-format model: GGUF, ONNX, PT/PTH, SafeTensors, BIN, GGML (engine native menentukan yang benar-benar runnable)
- ✅ Chat interface + lampiran file
- ✅ Model terpilih **tetap** saat pindah halaman (shared ViewModel + DataStore)
- ✅ Keyboard tidak menutup kolom input (`adjustResize` + `imePadding`)
- ✅ Tema: System / Light / Dark
- ✅ Bahasa mengikuti pengaturan perangkat (ID + EN string resources)
- ✅ Mode Thinking (otomatis aktif jika nama model mengandung indikator reasoning)
- ✅ Swap virtual hingga **7 GB** (buat / hapus dari Settings)
- ✅ Estimasi memori mempertimbangkan swap
- ✅ Halaman Privacy Policy
- ✅ Settings: GitHub star, Romance Engine promo, dokumentasi perusahaan, feedback (Issues + email)
- ✅ Mode Server LAN OpenAI-compatible
- ✅ Deteksi RAM/Storage + peringatan
- ✅ Backend CPU / GPU / Auto
- ✅ TTS
- ✅ Siap di-build lewat GitHub Actions
- ✅ **Inference engine aktif** memakai prebuilt llama.cpp

## Status Inference Engine

**Sudah aktif** menggunakan prebuilt library:

```kotlin
implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
```

- Tidak perlu NDK / CMake / compile native sendiri.
- Engine: `LlamaCppInferenceEngine` (membungkus library di atas).
- Jika library gagal di-load (sangat jarang), otomatis fallback ke `StubInferenceEngine`.

### Model yang didukung

Gunakan model berformat **GGUF**. Contoh yang cocok untuk Android:

| Model              | Ukuran (Q4_K_M) | Catatan          |
|--------------------|-----------------|------------------|
| Qwen2.5 0.5B       | ~400 MB         | Sangat cepat     |
| Gemma 2 2B         | ~1.6 GB         | Seimbang         |
| Llama 3.2 3B       | ~2.0 GB         | Bagus untuk chat |
| Phi-3.5 Mini 3.8B  | ~2.2 GB         | Reasoning kuat   |

Letakkan file `.gguf` di storage perangkat, lalu pilih dari halaman Model.

## Bug yang sudah diperbaiki

1. **APK release gagal instal** – Release di-sign dengan debug keystore (sideload-ready).
2. **Model hilang saat pindah halaman** – ViewModel di-scope ke Activity + persist di DataStore.
3. **Keyboard menutup input** – `windowSoftInputMode=adjustResize` + `imePadding()`.
4. **Card clickable di Settings** – urutan parameter sudah diperbaiki.

## Fitur Swap

- Maksimal 7 GB.
- File disimpan di folder privat aplikasi.
- Pada perangkat non-root, true `swapon` tidak tersedia; file berfungsi sebagai disk-backed virtual memory.
- Tombol buat / hapus di Settings.

## Build requirements

- Android Gradle Plugin 8.9.1 or newer.
- Android API 36 must be available as the compile SDK.
- Gradle 8.11.1 and JDK 17.

## Build

```bash
./gradlew assembleDebug    # APK debug
./gradlew assembleRelease  # APK release (signed, installable)
```

## Lisensi

MIT
