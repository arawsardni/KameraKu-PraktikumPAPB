# KameraKu 📸

**KameraKu** adalah aplikasi kamera Android sederhana berbasis **Jetpack Compose** dan **CameraX**. Aplikasi ini dirancang untuk mendemonstrasikan penggunaan *Use Cases* CameraX modern seperti Pratinjau (*Preview*), Pengambilan Foto (*ImageCapture*), dan Perekaman Video (*VideoCapture*).

## Fitur Utama
* 📷 **Preview Live**: Tampilan kamera real-time dengan rasio 16:9 (Auto Fallback).
* 📸 **Ambil Foto**: Menyimpan gambar `.jpg` kualitas tinggi.
* 🎥 **Rekam Video**: Merekam video `.mp4` beserta audio.
* 🔦 **Flash/Torch**: Toggle untuk menyalakan senter saat kondisi minim cahaya.
* 🔄 **Switch Kamera**: Beralih antara kamera depan dan belakang.

---

## Penjelasan Teknis

### 1. Alur Perizinan (Permission Flow) 🛡️
Aplikasi ini menggunakan **Runtime Permissions** sesuai standar Android modern untuk menjaga privasi pengguna.
* **Mekanisme:** Menggunakan `ActivityResultContracts.RequestMultiplePermissions()` untuk meminta izin **Camera** dan **Record Audio** secara bersamaan.
* **Logika:**
    1.  Saat aplikasi dibuka (`LaunchedEffect`), sistem mengecek apakah izin sudah diberikan.
    2.  Jika **Belum**, *pop-up* sistem akan muncul meminta persetujuan user. UI akan menampilkan status "Menunggu izin...".
    3.  Jika **Ditolak (Deny)**, fitur kamera tidak akan diinisialisasi untuk mencegah *crash* (`SecurityException`).
    4.  Jika **Diterima (Grant)**, `hasPermission` menjadi `true` dan fungsi `CameraContent` dipanggil untuk memulai *binding* kamera.

### 2. Penyimpanan dengan MediaStore (Scoped Storage) 💾
Aplikasi tidak lagi menggunakan izin lawas `WRITE_EXTERNAL_STORAGE` untuk Android 10+ (API 29++), melainkan menggunakan API **MediaStore**.
* **Konsep:** Aplikasi "menitipkan" file ke koleksi publik (`Shared Storage`) milik sistem, yaitu folder **Pictures** (untuk foto) dan **Movies** (untuk video).
* **Implementasi:**
    * Menggunakan `ContentValues` untuk menyiapkan metadata file (Nama, MIME Type, dan Relative Path).
    * `ImageCapture` menulis ke `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`.
    * `VideoCapture` menulis ke `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`.
* **Keunggulan:** Lebih aman, tidak memerlukan izin akses penuh ke seluruh memori internal, dan file langsung terindeks di aplikasi Galeri bawaan HP.

### 3. Penanganan Rotasi (Rotation & EXIF) 🔄
Sensor kamera HP secara fisik dipasang dalam orientasi tetap (biasanya Landscape memanjang). Tanpa penanganan khusus, foto yang diambil dalam mode Portrait akan terlihat miring 90 derajat di Galeri.
* **Solusi:** Menggunakan `.setTargetRotation(previewView.display.rotation)` pada konfigurasi `ImageCapture`.
* **Cara Kerja:**
    1.  Aplikasi membaca rotasi layar saat tombol *shutter* ditekan.
    2.  CameraX tidak memutar data piksel mentah (yang lambat), melainkan menyuntikkan instruksi orientasi ke dalam **Metadata EXIF** file gambar.
    3.  Aplikasi Galeri membaca metadata EXIF tersebut dan secara otomatis memutar tampilan gambar agar tegak lurus sesuai pandangan mata pengguna saat memotret.

---

## Dependensi Utama
* `androidx.camera:camera-core`
* `androidx.camera:camera-camera2`
* `androidx.camera:camera-lifecycle`
* `androidx.camera:camera-view`
* `androidx.camera:camera-video`
* `androidx.compose.material:material-icons-extended` (Untuk ikon Flash & Switch)

---
*Dibuat untuk Tugas Praktikum Pemrograman Aplikasi Perangkat Bergerak (Modul 9).*
