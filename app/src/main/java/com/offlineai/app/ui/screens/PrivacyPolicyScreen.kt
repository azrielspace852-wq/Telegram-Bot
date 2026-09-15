package com.offlineai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlineai.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Kebijakan Privasi – AXION AI offline",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(12.dp))
            Text(
                """
Terakhir diperbarui: September 2026

1. Ringkasan
AXION AI offline adalah aplikasi AI yang berjalan sepenuhnya di perangkat Anda (on-device). Kami tidak mengoperasikan server backend untuk menyimpan percakapan atau model Anda.

2. Data yang diproses
• Model AI yang Anda pilih dari penyimpanan lokal perangkat.
• Pesan chat dan lampiran file yang Anda kirim – diproses hanya di memori/proses lokal.
• Preferensi pengaturan (tema, model terpilih, swap, backend) disimpan di DataStore lokal perangkat.

3. Data yang TIDAK dikumpulkan
• Kami tidak mengirim percakapan, model, atau file Anda ke server eksternal.
• Tidak ada analitik pihak ketiga, tracking, atau iklan yang mengumpulkan data pribadi.
• Mode server LAN hanya membuka API lokal di jaringan Anda; kami tidak menerima data dari mode tersebut.

4. Izin yang diminta
• Akses penyimpanan / SAF: untuk memilih dan menyalin model serta file lampiran.
• Internet & jaringan: hanya untuk fitur server lokal (opsional) dan membuka tautan eksternal yang Anda ketuk (GitHub, dokumentasi, feedback).
• Notifikasi: untuk menampilkan status server foreground.

5. Swap / file virtual memory
File swap yang dibuat aplikasi disimpan di folder privat aplikasi (atau external files dir milik aplikasi) dan hanya digunakan lokal. Anda dapat menghapusnya kapan saja dari pengaturan.

6. Tautan eksternal
Aplikasi dapat membuka tautan ke:
• GitHub repository
• Halaman produk Romance Engine
• Dokumentasi perusahaan
• Email feedback
Kebijakan privasi situs pihak ketiga berlaku untuk kunjungan tersebut.

7. Anak-anak
Aplikasi tidak ditujukan khusus untuk anak di bawah 13 tahun. Penggunaan oleh anak harus dalam pengawasan orang tua.

8. Perubahan kebijakan
Kami dapat memperbarui kebijakan ini. Versi terbaru selalu tersedia di dalam aplikasi.

9. Kontak
Untuk pertanyaan privasi atau feedback:
• azrielspace852@gmail.com
• axionneuralis@gmail.com
• Issue di https://github.com/azrielspace852-wq/offlineAI

Dengan menggunakan AXION AI offline, Anda menyetujui kebijakan ini.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
