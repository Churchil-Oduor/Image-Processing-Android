package com.android.example.surfaceview

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.filament.Renderer
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import com.google.android.filament.utils.KtxLoader


class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var surfaceView: SurfaceView
    private lateinit var cameraXManager: CameraXManager
    private lateinit var choreographer: Choreographer
    private lateinit var modelViewer: ModelViewer


    init { Utils.init() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        previewView = findViewById(R.id.viewFinder)
        surfaceView = findViewById(R.id.surface_view)

        cameraXManager = CameraXManager(this, previewView)
        choreographer = Choreographer.getInstance()

        modelViewer = ModelViewer(surfaceView).apply {
            scene.skybox = null
            view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
        }


        surfaceView.setOnTouchListener(modelViewer)
        surfaceView.setZOrderOnTop(true) // Forces it above the camera SurfaceView
        surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT) // Allow transparency


        if (allPermissionsGranted()) {
            cameraXManager.startCamera()
        } else {
            requestPermissions()
        }


        loadGlb("DamagedHelmet")
        loadEnvironment("venetian_crossroads_2k")
        modelViewer.scene.skybox = null

    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(currentTime: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(currentTime) // draws filament model
        }
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                cameraXManager.startCamera()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }


    private fun loadGlb(name: String) {
        val buffer = readAsset("models/${name}.glb")
        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()
    }


    private fun loadEnvironment(ibl: String) {
        // Create the indirect light source and add it to the scene.
        var buffer = readAsset("envs/$ibl/${ibl}_ibl.ktx")
        KtxLoader.createIndirectLight(modelViewer.engine, buffer).apply {
            intensity = 50_000f
            modelViewer.scene.indirectLight = this
        }

        // Create the sky box and add it to the scene.
        buffer = readAsset("envs/$ibl/${ibl}_skybox.ktx")
        KtxLoader.createSkybox(modelViewer.engine, buffer).apply {
            modelViewer.scene.skybox = this
        }
    }

    private fun readAsset(assetName: String): ByteBuffer {
        val input = assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
    }
}
