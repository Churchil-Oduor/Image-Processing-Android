package com.android.example.ar_k

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
	private lateinit var cameraExecutor: ExecutorService

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (!OpenCVLoader.initDebug()) {
			Log.e("OpenCV", "Unable to load OpenCV!")
		} else {
			Log.d("OpenCV", "OpenCV loaded successfully")
		}
		cameraExecutor = Executors.newSingleThreadExecutor()
		setContent {
			CameraPreviewScreen(cameraExecutor)
		}
		if (!allPermissionsGranted()) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.CAMERA),
				REQUEST_CODE_PERMISSIONS
			)
		}
	}

	private fun allPermissionsGranted() = arrayOf(Manifest.permission.CAMERA).all {
		ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == REQUEST_CODE_PERMISSIONS && !allPermissionsGranted()) {
			finish()
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		cameraExecutor.shutdown()
	}

	companion object {
		private const val REQUEST_CODE_PERMISSIONS = 10
	}
}

@Composable
fun CameraPreviewScreen(cameraExecutor: ExecutorService) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
	val processedBitmap = remember { mutableStateOf<Bitmap?>(null) }

	Box(modifier = Modifier.fillMaxSize()) {
		// Camera Preview
		AndroidView(
			modifier = Modifier.fillMaxSize(),
			factory = { ctx ->
				PreviewView(ctx).apply {
					implementationMode = PreviewView.ImplementationMode.COMPATIBLE
					scaleType = PreviewView.ScaleType.FILL_CENTER
				}
			},
			update = { previewView ->
				val cameraProvider = cameraProviderFuture.get()
				val preview = Preview.Builder().build().also {
					it.setSurfaceProvider(previewView.surfaceProvider)
				}
				val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
				val imageAnalyzer = ImageAnalysis.Builder()
					.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
					.build()
					.also {
						it.setAnalyzer(cameraExecutor) { imageProxy ->
							processImage(imageProxy, processedBitmap)
						}
					}
				try {
					cameraProvider.unbindAll()
					cameraProvider.bindToLifecycle(
						lifecycleOwner,
						cameraSelector,
						preview,
						imageAnalyzer
					)
				} catch (exc: Exception) {
					Log.e("CameraX", "Use case binding failed", exc)
				}
			}
		)
		// Overlay processed image
		processedBitmap.value?.let { bitmap ->
			Image(
				bitmap = bitmap.asImageBitmap(),
				contentDescription = "Processed Camera Feed",
				modifier = Modifier.fillMaxSize(),
				alpha = 0.7f // Semi-transparent to see preview underneath
			)
		}
	}
}

private fun processImage(imageProxy: ImageProxy, processedBitmap: MutableState<Bitmap?>) {
	// Extract YUV planes
	val planes = imageProxy.planes
	val yBuffer = planes[0].buffer
	val uBuffer = planes[1].buffer
	val vBuffer = planes[2].buffer

	val ySize = yBuffer.remaining()
	val uSize = uBuffer.remaining()
	val vSize = vBuffer.remaining()

	val data = ByteArray(ySize + uSize + vSize)
	yBuffer.get(data, 0, ySize)
	vBuffer.get(data, ySize, vSize)
	uBuffer.get(data, ySize + vSize, uSize)

	// Convert to Mat
	val mat = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
	mat.put(0, 0, data)
	var rgbMat = Mat()
	Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)

	// Handle rotation
	val rotationDegrees = imageProxy.imageInfo.rotationDegrees
	if (rotationDegrees != 0) {
		val rotatedMat = Mat()
		Core.rotate(rgbMat, rotatedMat, when (rotationDegrees) {
			90 -> Core.ROTATE_90_CLOCKWISE
			180 -> Core.ROTATE_180
			270 -> Core.ROTATE_90_COUNTERCLOCKWISE
			else -> Core.ROTATE_90_CLOCKWISE // Default
		})
		rgbMat.release()
		rgbMat = rotatedMat
	}

	// Process: Blur and Canny
	val blurredMat = Mat()
	Imgproc.GaussianBlur(rgbMat, blurredMat, Size(15.0, 15.0), 0.0)
	val cannyMat = Mat()
	Imgproc.Canny(blurredMat, cannyMat, 100.0, 200.0)

	// Convert to Bitmap for display
	val bitmap = Bitmap.createBitmap(cannyMat.cols(), cannyMat.rows(), Bitmap.Config.ARGB_8888)
	Utils.matToBitmap(cannyMat, bitmap)

	// Update Compose UI
	processedBitmap.value = bitmap

	// Release resources
	imageProxy.close()
	mat.release()
	rgbMat.release()
	blurredMat.release()
	cannyMat.release()
}