package com.android.example.camerax_app

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraExecutor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

import com.android.example.camerax_app.ui.theme.CameraX_appTheme
import java.nio.file.WatchEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.jar.Manifest
import androidx.camera.core.Preview
import android.util.Log
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale

typealias LumaListener = (luma: Double) -> Unit
class MainActivity : ComponentActivity() {

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var record: Recording? = null
    private lateinit var cameraExecutor: ExecutorService
    companion object {
	    private const val TAG = "CameraXApp"
	    private const val FILENAME_FORMAT = "yyy-MM-dd-HH-mm-ss-SSS"
	    private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA,
	        android.Manifest.permission.RECORD_AUDIO
    ).apply {

	    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
		    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
	    }
    }.toTypedArray()
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!allPermissionsGranted())
            requestPermissions()


        enableEdgeToEdge()
        setContent {
            startCamera(lifecycleOwner = this@MainActivity)
            CameraX_appTheme {
              CameraLayout(
                  modifier = Modifier.fillMaxSize().fillMaxHeight(),
                  takePhoto = {takePhoto()},
                  captureVideo = {captureVideo()}
              )
            }

            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    override fun onDestroy() {
	    super.onDestroy()
	    cameraExecutor.shutdown()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }
    private fun captureVideo() {}
    @Composable
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        val context = LocalContext.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder().build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraXApp", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val activityResultLauncher = registerForActivityResult (
        ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
        var permissionGranted = true

        //looping through every permission and displaying results
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && it.value == false) {
                permissionGranted = false

            }

            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permisson request Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


}


@Composable

fun CameraLayout(
    modifier: Modifier = Modifier,
    takePhoto: () -> Unit,
    captureVideo: () -> Unit
) {

    Column (
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = takePhoto, modifier = Modifier.padding(10.dp)) {
                Text(stringResource(R.string.take_photo))
            }
            //Spacer(modifier.width(20.dp))
            Button(onClick = captureVideo, modifier = Modifier.padding(10.dp)) {
                Text(stringResource(R.string.capture_video))
            }
        }
    }


}

