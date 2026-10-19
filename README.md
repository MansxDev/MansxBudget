<div align="center">

# 💰 MansxBudget

**Aplikasi keuangan pribadi Android native — offline, privat, dan rapi.**

Catat transaksi tanpa ribet: notifikasi bank masuk → aplikasi menawarkan isi otomatis → kamu konfirmasi. Semua diproses **di perangkat**, tanpa server, tanpa API cloud, tanpa izin baca SMS.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/minSdk-21-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![targetSdk](https://img.shields.io/badge/targetSdk-35-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Material](https://img.shields.io/badge/Material_Components-1.11.0-757575?style=flat-square&logo=materialdesign&logoColor=white)](https://material.io)
[![SQLite](https://img.shields.io/badge/SQLite-DB_v5-003B57?style=flat-square&logo=sqlite&logoColor=white)](https://sqlite.org)
[![Version](https://img.shields.io/badge/version-4.8_(23)-blue?style=flat-square)](#-riwayat-rilis)
[![Rilis](https://img.shields.io/badge/rilis-23_versi-orange?style=flat-square)](TIMELINE.md)

</div>

<!-- TODO(screenshot): tambahkan 5-6 screenshot lalu ganti baris ini dengan tabel gambar
     yang disarankan: Beranda (light), Transaksi, Anggaran + Target Dana, Laporan (donut+bar),
     Sheet konfirmasi capture notifikasi bank, dan satu tampilan tema AMOLED. -->

---

## ✨ Fitur

### 📝 Transaksi
CRUD lengkap, pencarian, filter tanggal & kategori, swipe untuk edit/hapus, hapus massal (select mode), badge pemasukan/pengeluaran, penghitung hasil filter, dan FAB tambah cepat.

### 🎯 Anggaran & Target Dana
Batas anggaran per kategori, *envelope* harian di Beranda, serta Target Dana (Goal) dengan progress bar dan status warna (hijau / amber / merah). Dilengkapi **countdown hari atau minggu** dan sinkronisasi opsional ke Google Calendar.

### 💳 Hutang & Piutang
Tab terpisah dengan ringkasan tiga kolom (total, lunas, sisa), model `total`/`paid` dengan status turunan, filter jenis (*hutangku* / *piutang*) dan status lunas, pencarian, swipe edit/hapus, serta hapus massal.

### 📊 Laporan
Distribusi pengeluaran bulan ini (donut chart dengan teks tengah), tren 6 bulan (bar chart dengan empty-state), dan tabel per kategori. Export CSV.

### 🏠 Beranda yang bisa diatur
Sheet "Atur Beranda": toggle 7 kartu dan slider horizon kalender **7–90 hari**. Preferensi disimpan di `SharedPreferences` (`home_prefs`).

### 🔔 Auto-capture notifikasi bank *(fitur unggulan)*
Tanpa mengetik ulang. Notifikasi bank/e-wallet yang masuk diproses **on-device** lewat `NotificationListenerService`:

| Lapisan | Yang dikerjakan |
|---|---|
| **Whitelist sumber** | 43 sumber (32 bank + 11 e-wallet), plus sheet "Daftarkan sumber ini?" untuk package asing berpola bank |
| **Parser** | Ekstraksi nominal, jenis (masuk/keluar), merchant, dan fallback untuk format yang tidak dikenal |
| **`NotifScorer`** | Skor −5…+12 untuk mengklasifikasi notifikasi: `TRANSACTION` / `PROMO` / `OTP` / `INFO` / `AMBIGU` |
| **`CategoryMatcher`** | 81 merchant → 6 kategori bawaan, pencocokan *longest-match* + batas kata |
| **Gate** | Notifikasi `PROMO` / `OTP` / `INFO` dilewati total — tidak pernah mengganggu |
| **Antrian review** | Transaksi `AMBIGU` masuk antrian review dengan alasan, aksi simpan/skip/bersihkan, dan **undo** |
| **Badge "PERLU DICEK"** | Menandai transaksi yang perlu konfirmasi, bukan langsung menyetujui |

Alur: notifikasi masuk → bottom sheet konfirmasi berisi form yang sudah terisi (nominal, merchant, kategori otomatis) → kamu tekan simpan → **bisa di-undo**.

### ⚙️ Lainnya
Kalkulator bawaan, pengingat harian, pengaturan tema, backup/restore JSON (mencakup semua tabel), About, dan How-to.

---

## 🔐 Privasi

- Notifikasi bank diproses **sepenuhnya di perangkat** — tidak ada API cloud, tidak ada pengiriman data ke mana pun
- Menggunakan `NotificationListenerService`, **bukan** `READ_SMS` / `RECEIVE_SMS`
- Bersifat **opt-in**: kamu nyalakan sendiri dari Pengaturan, dan bisa dimatikan kapan saja
- Auto-kategorisasi hanya memakai kategori bawaan (`Makanan`, `Transportasi`, `Gaji`, `Belanja`, `Tagihan`, `Lainnya`) agar tidak muncul kategori liar
- Backup/restore berupa file JSON lokal — kamu yang pegang datanya

---

## 🛠️ Tech stack

| | |
|---|---|
| Bahasa | Kotlin 2.2.20 |
| UI | **XML + Material Components** (bukan Compose), ConstraintLayout, RecyclerView |
| Chart | MPAndroidChart v3.1.0 |
| Database | SQLite via `DatabaseHelper` — **DB v5**, migrasi satu arah (additive), **tanpa DROP** |
| Preferensi | `SharedPreferences` |
| Tema | white / dark / AMOLED, warna semantik lewat `ThemeHelper` |
| Font | Plus Jakarta Sans (ExtraBold untuk headline & nominal) |
| Lain | `java.time` via desugaring (aman di minSdk 21), `SwipeRefreshLayout`, Google Calendar sync opsional |

**Skala kode:** 46 file Kotlin · 21 layout XML · 21 kelas util · `minifyEnabled` + `shrinkResources` aktif pada build release.

---

## 🗂️ Struktur proyek

```
app/src/main/java/com/budgeting/me/
├── MainActivity.kt              # Shell 4 tab + bottom nav
├── data/
│   └── DatabaseHelper.kt        # SQLite: schema, migrasi additive, CRUD
├── model/                       # Transaction, Budget, Goal, Debt, CategorySummary
├── ui/                          # Home, Transactions, Budget, Debts, Report,
│                                # Settings, Calculator + Adapter & Sheet
└── util/                        # 21 kelas: parser notifikasi, kalkulator,
                                 # format, ekspor, tema, countdown, capture
```

---

## 🧠 Sorotan teknis (yang bikin aplikasi ini tidak biasa)

**1. Format Rupiah tanpa ICU.** Pengelompokan ribuan ditulis manual dengan `chunked(3)` — nol `NumberFormat` / ICU. `RupiahTextWatcher` menulis ulang lewat `setText()` penuh dengan guard *composing span*, sehingga bug klasik seperti `5000.000` di Gboard tidak terjadi. Field uang memakai `inputType="number"`.

**2. Migrasi database yang tidak merusak data.** Sejak awal DB dirancang **satu arah dan additive** — dilarang `DROP`. Naik dari v4 → v5 (Target Dana + capture) tanpa kehilangan transaksi pengguna. Backup/restore JSON selalu disinkronkan dengan tabel baru.

**3. Bottom nav maksimal 5 item.** Batas dari Material Components dipatuhi secara sadar: Pengaturan dipindah ke toolbar, bukan memaksa item keenam.

**4. Quirk Xiaomi/MIUI terdokumentasi.** MIUI memblokir `NotificationListenerService` lewat AutoStart sehingga sheet konfirmasi tidak muncul walau listener sudah "diizinkan". Penyebab (`MIUILOG- Reject service` pada `AutoStartManagerService`) dan solusinya didokumentasikan di [TIMELINE.md](TIMELINE.md#catatan-device-xiaomimiui), lengkap dengan perintah `adb shell dumpsys` untuk memverifikasi.

**5. Optimasi ukuran APK.** Dari ~5 MB di rilis awal menjadi ~2,4–2,7 MB — lewat `minifyEnabled`, `shrinkResources`, dan pemangkasan dependensi yang tidak terpakai.

---

## 📅 Riwayat rilis

**23 rilis tersimpan**, terdokumentasi lengkap (versi, `versionCode`, tanggal, ukuran, highlight) di **[TIMELINE.md](TIMELINE.md)**.

| Versi | Highlight |
|---|---|
| v2.x | Rilis awal 5 tab · perampingan APK 5 MB → 2,2 MB |
| v3.8 | Fix format Rupiah (`RupiahTextWatcher`) |
| v3.9 | Tab Hutang/Piutang (model `Debt`) |
| v4.0 | Redesign jadi 4 tab |
| v4.1 | Rebrand visual: 3 tema + Plus Jakarta Sans |
| v4.2 | Target Dana + countdown + Google Calendar sync · DB v5 |
| v4.3 | Beranda customizable (7 kartu + horizon kalender) |
| v4.4 | Auto-parse notifikasi bank (opt-in, tanpa READ_SMS) |
| v4.5 | Whitelist 43 sumber bank & e-wallet + auto-learn |
| v4.7 | `NotifScorer` + `CategoryMatcher` (81 merchant) + badge "PERLU DICEK" |
| **v4.8** | **Antrian review + undo + badge risiko + merchant otomatis** |

---

## 🚀 Cara build

Butuh JDK 17+ dan Android SDK (`local.properties` dengan `sdk.dir`).

```bash
export JAVA_HOME=/opt/android-studio/jbr   # atau path JDK 17 kamu
./gradlew :app:assembleRelease
./gradlew --stop

# hasil: app/build/outputs/apk/release/app-release.apk
```

> **Catatan:** build release ditandatangani `mansxbudget-release.jks` yang **tidak disertakan di repo** (lihat `.gitignore`). Untuk build sendiri, buat keystore-mu atau ubah `signingConfigs` di `app/build.gradle`.

Debug build:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📲 Mendapatkan APK

APK release tersedia di halaman **[Releases](../../releases)**. Aktifkan juga *Install from unknown sources* di perangkatmu (APK belum dipublikasikan ke Play Store).

---

## 🗺️ Status & rencana

| Item | Status |
|---|---|
| Fitur inti (transaksi, anggaran, target, hutang/piutang, laporan) | ✅ Stabil |
| Auto-capture notifikasi bank | ✅ Jalan (43 sumber) |
| Otak klasifikasi notifikasi — Fase 1 | ✅ `NotifScorer` + `CategoryMatcher` |
| Antrian review + undo | ✅ v4.8 |
| Otak klasifikasi lanjutan (pembelajaran dari koreksi pengguna) | ⏳ Direncanakan |
| Publikasi Play Store | ⏳ Belum |

---

## 📄 Lisensi & kredit

Belum ada berkas lisensi — hubungi pemilik repo untuk izin penggunaan.

Dibuat dan dipelihara oleh **[MansxDev](https://github.com/MansxDev)** (Rohiman).
Package `com.budgeting.me` · Aplikasi ini tumbuh lewat 23 rilis yang semuanya diuji langsung di perangkat.
