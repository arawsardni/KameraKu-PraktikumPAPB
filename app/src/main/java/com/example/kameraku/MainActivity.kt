package com.example.kameraku

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.camera.core.AspectRatio
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraScreen()
                }
            }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    // State untuk menyimpan status izin
    var permissionsGranted by remember { mutableStateOf(false) }

    // 1. Launcher untuk meminta MULTIPLE permissions (Kamera & Audio)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Cek apakah semua izin diberikan
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        permissionsGranted = cameraGranted && audioGranted

        if (!permissionsGranted) {
            Toast.makeText(context, "Izin Kamera & Audio diperlukan", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Cek izin saat start
    LaunchedEffect(Unit) {
        launcher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    if (permissionsGranted) {
        CameraContent()
    } else {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Menunggu izin Kamera & Audio...")
        }
    }
}

@Composable
fun CameraContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // --- USE CASES ---
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }

    // --- STATE UI & LOGIC ---
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchOn by remember { mutableStateOf(false) }

    // State untuk Recording Video
    var currentRecording: Recording? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }

    val previewView = remember { PreviewView(context) }

    // --- SETUP KAMERA ---
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. Preview Strategy
            val ratioSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build()
            val preview = Preview.Builder().setResolutionSelector(ratioSelector).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // 2. ImageCapture Strategy
            val imageCaptureInstance = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(ratioSelector)
                .build()
            imageCapture = imageCaptureInstance

            // 3. VideoCapture Strategy (BARU)
            // Membuat Recorder dengan kualitas tertinggi yang tersedia
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            val videoCaptureInstance = VideoCapture.withOutput(recorder)
            videoCapture = videoCaptureInstance

            // 4. Camera Selector
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()

                // BIND SEMUA USE CASE (Preview + Photo + Video)
                // Note: Pada beberapa device low-end, binding 3 use case sekaligus bisa gagal.
                // Solusi robust: Buat toggle mode Photo/Video terpisah yang melakukan re-bind.
                val cameraInstance = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCaptureInstance,
                    videoCaptureInstance // Bind VideoCapture di sini
                )
                camera = cameraInstance

                // Reset state
                isTorchOn = false
                cameraInstance.cameraControl.enableTorch(false)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Gagal memuat kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // --- UI LAYOUT ---
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Preview
        AndroidView(
            factory = { previewView.apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Tombol Kontrol Atas (Flash & Switch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Flash
            IconButton(
                onClick = {
                    isTorchOn = !isTorchOn
                    camera?.cameraControl?.enableTorch(isTorchOn)
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Flash"
                )
            }

            // Switch Camera
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                },
                enabled = !isRecording, // Disable ganti kamera saat merekam
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
            ) {
                Icon(imageVector = Icons.Filled.Cameraswitch, contentDescription = "Switch")
            }
        }

        // 3. Kontrol Bawah (Foto & Video)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TOMBOL FOTO
            Button(
                onClick = {
                    if (!isRecording) {
                        imageCapture?.let { takePhoto(context, it) }
                    }
                },
                enabled = !isRecording // Disable foto saat merekam video
            ) {
                Text("Foto")
            }

            // TOMBOL VIDEO (Start/Stop)
            Button(
                onClick = {
                    if (isRecording) {
                        // STOP RECORDING
                        currentRecording?.stop()
                        isRecording = false
                    } else {
                        // START RECORDING
                        videoCapture?.let { vc ->
                            isRecording = true
                            currentRecording = recordVideo(context, vc) {
                                isRecording = false // Reset jika error/selesai
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isRecording) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                } else {
                    // Menggunakan icon default atau text jika icon Videocam tidak ada
                    // Icon(Icons.Filled.Videocam, contentDescription = "Record")
                    Text("Rekam")
                }
            }
        }

        // Indikator Recording (Overlay Merah Kedip/Teks)
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Red.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Merekam...", color = Color.White)
            }
        }
    }
}

// --- LOGIKA PHOTO ---
fun takePhoto(context: Context, imageCapture: ImageCapture) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Photo-$name")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KameraKu-PAPB")
        }
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions, ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Toast.makeText(context, "Foto: ${output.savedUri}", Toast.LENGTH_SHORT).show()
            }
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(context, "Gagal Foto: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

// --- LOGIKA VIDEO (BARU) ---
fun recordVideo(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    onStop: () -> Unit
): Recording {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())

    // Konfigurasi Metadata Video
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "Video-$name")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KameraKu-PAPB")
        }
    }

    val mediaStoreOutputOptions = MediaStoreOutputOptions
        .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(contentValues)
        .build()

    // Cek izin Audio secara eksplisit sebelum start recording
    val hasAudioPermission = PermissionChecker.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PermissionChecker.PERMISSION_GRANTED

    // Siapkan Recording
    var pendingRecording = videoCapture.output
        .prepareRecording(context, mediaStoreOutputOptions)

    if (hasAudioPermission) {
        pendingRecording = pendingRecording.withAudioEnabled()
    }

    // Mulai Merekam
    return pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
        when(recordEvent) {
            is VideoRecordEvent.Start -> {
                Toast.makeText(context, "Mulai Merekam", Toast.LENGTH_SHORT).show()
            }
            is VideoRecordEvent.Finalize -> {
                // Selesai merekam (baik sukses atau error)
                onStop()
                if (!recordEvent.hasError()) {
                    val msg = "Video tersimpan: ${recordEvent.outputResults.outputUri}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    // Abaikan error jika user sengaja stop (ERROR_SOURCE_INVALID)
                    // tapi tampilkan jika error lain
                    val msg = "Video Gagal: ${recordEvent.error}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}