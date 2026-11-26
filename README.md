# San Mouse Pro

Mapping mouse & keyboard fisik -> sentuhan layar (joystick WASD, kamera mouse, tombol tembak, dll).
Tema gaming, landscape, tombol melayang "SM" + editor layout geser-geser, sensitivitas X/Y, kursor bawaan yang bisa disembunyikan.

## Cara kerja
- Pakai **Shizuku** (sambung lewat Debugging Nirkabel + kode pairing) supaya app bisa membaca
  /dev/input (mouse/keyboard) dan menyuntik sentuhan, TANPA root.
- Mouse/keyboard di-"grab" eksklusif, jadi tidak bocor ke sistem.
- Tombol `~` = ganti mode GAME (mouse = kamera) <-> KURSOR (mouse = kursor, klik kiri = tap).

## Pasang & pakai
1. Pasang aplikasi **Shizuku**. Aktifkan Opsi Pengembang -> Debugging nirkabel -> "Pairing dengan kode",
   masukkan kodenya di Shizuku (notifikasi), lalu tekan Start.
2. Pasang APK San Mouse Pro, buka, beri izin Shizuku + overlay.
3. Colok mouse & keyboard (OTG / Bluetooth).
4. Pilih game -> MULAI MAIN. Di dalam game ketuk bulatan "SM" -> Edit Layout -> geser tombol/joystick/area kamera.

## Build (dari HP, tanpa Android Studio)
Pakai GitHub Actions (sudah ada di .github/workflows/build.yml):
```
pkg install git unzip
termux-setup-storage
cd ~ && unzip ~/storage/downloads/SanMousePro.zip && cd SanMousePro
git init && git add . && git commit -m "init" && git branch -M main
git remote add origin https://github.com/USERNAME/SanMousePro.git
git push -u origin main      # password = Personal Access Token GitHub
```
Lalu buka repo -> tab Actions -> tunggu hijau -> download artifact `SanMousePro-APK`.

Build lokal (Android Studio / PC): `gradle assembleDebug` (JDK 17, Gradle 8.7, NDK 26.1, CMake 3.22.1).

## Catatan
- Layout disimpan per game (nama paket).
- Selama mode GAME/KURSOR aktif, keyboard fisik "diambil" app -> tidak bisa ngetik chat pakai keyboard fisik (pakai keyboard layar / stop overlay).
- Hanya perangkat USB/Bluetooth yang di-grab (tombol volume/power HP aman).
