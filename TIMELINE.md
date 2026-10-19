# MansxBudget — Timeline Rilis

Aplikasi Android native (Kotlin, XML + Material Components, bukan Compose).
Package `com.budgeting.me`, minSdk 21, targetSdk 35.
Terverifikasi on-device per rilis: install → launch → `logcat -b crash` kosong.

> Detail v2.0–v3.7: riwayat sebelum sesi agent, tidak ada catatan fitur.
> Detail v3.8+ dicatat dari sesi pengerjaan.

| Versi | versionCode | Tanggal build | Ukuran | Highlight |
|---|---|---|---|---|
| v2.0 | — | 2026-09-23 17:29 | 5,0 MB | Rilis awal yang tersimpan (5 tab navigasi) |
| v2.1 | — | 2026-09-23 18:15 | 2,2 MB | Perampingan ukuran APK (~5 MB → ~2,2 MB) |
| v2.2 | — | 2026-09-23 18:22 | 2,2 MB | Iterasi lanjutan |
| v2.3 | — | 2026-09-23 18:35 | 2,2 MB | Iterasi lanjutan |
| v3.0 | — | 2026-09-23 19:02 | 2,2 MB | Major v3 |
| v3.1 | — | 2026-09-23 19:21 | 2,2 MB | Iterasi lanjutan |
| v3.2 | — | 2026-09-26 08:08 | 2,2 MB | Iterasi lanjutan |
| v3.3 | — | 2026-09-29 18:06 | 2,4 MB | Iterasi lanjutan |
| v3.4 | — | 2026-09-29 18:21 | 2,4 MB | Iterasi lanjutan |
| v3.5 | — | 2026-09-29 18:39 | 2,4 MB | Iterasi lanjutan |
| v3.6 | — | 2026-09-29 18:54 | 2,4 MB | Iterasi lanjutan |
| v3.7 | — | 2026-09-29 21:31 | 2,4 MB | Stabil (+ varian debug `v3.7-RWDBG`) |
| v3.8 | — | 2026-09-29 21:58 | 2,4 MB | **Fix format Rupiah**: `RupiahTextWatcher` tulis balik via `setText()` penuh + guard composing-span (anti bug "5000.000" di Gboard); field uang `inputType="number"` |
| v3.9 | — | 2026-09-29 22:33 | 2,4 MB | **Tab Hutang/Piutang**: model Debt (total/paid → sisa, status turunan), ringkasan 3 kolom, filter jenis + status, search, swipe edit/hapus, select-hapus massal |
| v4.0 | — | 2026-09-29 22:46 | 2,4 MB | **Redesign 4 tab** (Beranda, Transaksi, Anggaran, Laporan) |
| v4.1 | 17 | 2026-10-01 23:20 | 2,5 MB | **Rebrand visual**: palette Coolors di 3 tema (white/dark/amoled) + Plus Jakarta Sans ExtraBold untuk headline & angka nominal |
| v4.2 | 18 | 2026-10-01 23:40 | 2,7 MB | **Target Dana + tanggal**: countdown hari/minggu di kartu Goal & Beranda, sync opsional ke Google Calendar (event 09:00 + reminder H-30/H-7/H-1), migrasi DB v4 → v5 (additive) |
| v4.3 | 19 | 2026-10-02 00:01 | 2,7 MB | **Beranda customizable**: sheet "Atur Beranda" (toggle 7 card + slider horizon kalender 7–90 hari), preferensi di SharedPreferences `home_prefs` |
| v4.4 | 20 | 2026-10-02 00:17 | 2,6 MB | **Auto-parse notifikasi bank** via `NotificationListenerService` (tanpa READ_SMS): bottom sheet konfirmasi → form transaksi pre-filled; opt-in dari Pengaturan |
| v4.5 | 21 | 2026-10-02 00:37 | 2,6 MB | **Perluasan bank + auto-learn**: whitelist 43 sumber (32 bank + 11 e-wallet), nominal terdekat keyword, sheet "Daftarkan sumber ini?" untuk package asing berpola bank, dialog daftar sumber + hapus custom |
| v4.7 | 22 | 2026-10-02 16:14 | 2,7 MB | **Otak klasifikasi (Fase 1 tahap 1)**: `NotifScorer` (skor -5..+12) + 4 kelas (TRANSACTION / PROMO / OTP / INFO / AMBIGU), `CategoryMatcher` (81 merchant → 6 kategori existing, longest-match + word-boundary), ekstraksi merchant, fallback sumber asing, gate listener (PROMO/OTP/INFO skip total), badge "PERLU DICEK" untuk AMBIGU. DB tetap v5 |

## Catatan teknis lintas versi

- Format Rupiah selalu manual `chunked(3)` — nol ICU/`NumberFormat` (aturan keras).
- Bottom nav maksimal 5 item; Pengaturan di toolbar.
- DB v5 sejak v4.2, migrasi satu arah additive, backup/restore JSON ikut semua tabel.
- Desugaring aktif (`java.time` aman di minSdk 21).
- Release: `minifyEnabled` + `shrinkResources` true, signed `mansxbudget-release.jks`.
- Notifikasi bank diproses on-device saja: `NotificationListenerService`, tanpa API cloud, tanpa READ_SMS/RECEIVE_SMS.
- Auto-kategorisasi merchant hanya memakai kategori existing (`Makanan`, `Transportasi`, `Gaji`, `Belanja`, `Tagihan`, `Lainnya`) supaya tidak ada kategori liar dari notifikasi.

## Catatan device Xiaomi/MIUI

MIUI memblokir `NotificationListenerService` lewat AutoStart. Symptomsheet tidak muncul walau listener sudah "diizinkan":

```
AutoStartManagerService: MIUILOG- Reject service :Intent { act=android.service.notification.NotificationListenerService cmp=com.budgeting.me/.util.BankNotifListener }
NotificationListeners: Autostart Unable to bind notification listener service
```

Solusi: MIUI > Security > Autostart > aktifkan **MansxBudget**, lalu toggle listener off/on supaya service di-bind ulang.
Perintah untuk cek: `adb shell dumpsys activity services com.budgeting.me | grep BankNotifListener` (harus ada `isBindService:true`).

## Cara build

```bash
export JAVA_HOME=/opt/android-studio/jbr
./gradlew :app:assembleRelease
./gradlew --stop
# hasil: app/build/outputs/apk/release/app-release.apk
```

Butuh file `mansxbudget-release.jks` di root project (tidak ikut repo).
