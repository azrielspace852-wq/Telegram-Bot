# Offline AI – Android (Android 10+)

Aplikasi **AI offline** untuk Android dengan fitur lengkap sesuai permintaan:

- ✅ Minimal Android 10 (API 29)
- ✅ Pilih model GGUF langsung dari storage → langsung load
- ✅ Chat interface
- ✅ Lampirkan file: `md, txt, json, jsonl, pdf, png, jpg, mp3, mp4, zip` (+ lainnya)
- ✅ **ZIP khusus**: dibaca (list + extract teks), dan AI bisa generate ZIP
- ✅ Mode **Server**: device jadi OpenAI-compatible API server (bisa diakses hardware lain di LAN)
- ✅ Deteksi **RAM + Storage** → peringatan jika model terlalu besar (pilihan "Tetap Jalankan" / Batal)
- ✅ Pilihan backend: **CPU / GPU / Otomatis** (default Otomatis)
- ✅ Fitur **Bacakan** (TTS)
- ✅ Siap di-build lewat **GitHub Actions**

## Status Inference Engine

Saat ini menggunakan **StubInferenceEngine** agar UI & semua fitur non-native bisa langsung dicoba.

Untuk production, ganti dengan salah satu:

1. **Official** `llama.cpp` Android example (`examples/llama.android`)
2. Library [kotlinllamacpp](https://github.com/ljcamargo/kotlinllamacpp)
3. [Ai-Core](https://github.com/Siddhesh2377/Ai-Core) (AAR + OpenCL GPU)
4. MLC-LLM / LiteRT jika ingin format lain

Lihat file `app/src/main/java/com/offlineai/app/inference/InferenceEngine.kt`.

### Cara integrasi cepat (llama.cpp)

1. Clone llama.cpp dan build library untuk Android (NDK + CMake).
2. Letakkan `libllama.so` + headers di `app/src/main/jniLibs/arm64-v8a/` dan `cpp/`.
3. Uncomment `externalNativeBuild` di `app/build.gradle.kts`.
4. Implementasikan `LlamaCppEngine : InferenceEngine` menggunakan JNI.
5. Ganti `StubInferenceEngine()` di `ChatViewModel` dengan engine baru.

## Fitur File

| Format | Dibaca sebagai teks | Multimodal (butuh model khusus) | Catatan |
|--------|---------------------|----------------------------------|---------|
| txt, md, json, jsonl | ✅ | - | Full extract |
| pdf | ✅ (PDFBox) | - | Text extract |
| zip | ✅ | - | List isi + extract teks di dalamnya (max 50 file) |
| png, jpg | - | ✅ (vision model + mmproj) | Path dikirim ke model |
| mp3 | - | ✅ (audio model) | Path dikirim |
| mp4 | - | ✅ (video model) | Path dikirim |

AI juga bisa meminta generate ZIP lewat helper `generateZipFromText()`.

## Mode Server

1. Load model dulu.
2. Buka tab **Settings** → Start Server (default port 8080).
3. Device lain di jaringan yang sama bisa memanggil:

```bash
curl http://<IP-HP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role":"user","content":"Halo!"}]
  }'
```

Endpoint: `GET /v1/models`, `POST /v1/chat/completions`.

## Build Lokal

```bash
# Butuh Android SDK + JDK 17
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Workflow sudah disediakan di `.github/workflows/android.yml`.

- Trigger: push ke `main` / manual
- Output: APK debug + release (signed jika secret diset)

## Struktur Project

```
OfflineAI-Android/
├── app/
│   ├── src/main/java/com/offlineai/app/
│   │   ├── data/           # ModelInfo, ChatMessage, Attachment
│   │   ├── inference/      # InferenceEngine interface + Stub
│   │   ├── server/         # LocalAIServerService (Ktor)
│   │   ├── ui/             # Compose screens + ViewModel
│   │   └── util/           # HardwareInfo, FileUtils, TtsHelper
│   └── build.gradle.kts
├── .github/workflows/android.yml
└── README.md
```

## Catatan Penting

- Model GGUF besar butuh RAM tinggi. Gunakan Q4_K_M / Q5_K_M untuk mobile.
- GPU acceleration (Vulkan/OpenCL) hanya aktif setelah engine native diintegrasikan.
- Permission storage: app menggunakan SAF (Storage Access Framework) + copy ke internal storage agar path stabil.
- TTS menggunakan Android TextToSpeech (bahasa Indonesia jika tersedia).

## Lisensi

MIT – silakan modifikasi dan distribusi bebas.
