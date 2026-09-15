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

## Status Inference Engine

Saat ini menggunakan **StubInferenceEngine** agar UI & semua fitur non-native bisa langsung dicoba.

Untuk production, ganti dengan llama.cpp / kotlinllamacpp / engine native lain. Lihat `InferenceEngine.kt`.

## Bug yang diperbaiki

1. **APK release gagal instal** – Release sekarang di-sign dengan debug keystore (sideload-ready). ProGuard rules diperkuat.
2. **Model hilang saat pindah halaman** – ViewModel di-scope ke Activity + persist path di DataStore.
3. **Keyboard menutup input** – `windowSoftInputMode=adjustResize` + `Modifier.imePadding()` pada ChatScreen.

## Fitur Swap

- Maksimal 7 GB.
- File disimpan di folder privat aplikasi.
- Pada perangkat non-root, true `swapon` tidak tersedia; file berfungsi sebagai disk-backed virtual memory untuk estimasi & future mmap engine.
- Tombol buat / hapus di Settings.

## Build

```bash
./gradlew assembleDebug    # APK debug
./gradlew assembleRelease  # APK release (signed, installable)
```

## Lisensi

MIT
