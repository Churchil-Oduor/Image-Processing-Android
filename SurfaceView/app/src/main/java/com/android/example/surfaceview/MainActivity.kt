package com.android.example.surfaceview

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    

    private lateinit var surfaceView: SurfaceView
    private lateinit var holder: SurfaceHolder
    @Volatile private var running = false
    private var renderThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        surfaceView = findViewById(R.id.surface)
        holder = surfaceView.holder
        holder.addCallback(this)

        surfaceView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        surfaceView.setZOrderOnTop(true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        renderThread = Thread {
            // Simple render loop
            while (running && holder.surface.isValid) {
                val canvas = holder.lockCanvas() ?: continue
                try {
                    // Clear (paint a background)
                    canvas.drawColor(Color.BLACK) // change color for appearance
                    // Draw something:
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.RED
                        textSize = 64f
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(canvas.width/2f, canvas.height/2f, 120f, p)
                    p.color = Color.WHITE
                    canvas.drawText("Hello SurfaceView", 60f, 120f, p)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
                // Cap frame rate
                Thread.sleep(16)
            }
        }.also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // handle size/orientation changes if needed
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.join()
    }
}