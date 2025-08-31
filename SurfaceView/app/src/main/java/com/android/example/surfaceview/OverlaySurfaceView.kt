package com.android.example.surfaceview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class OverlaySurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    @Volatile private var running = false
    private var renderThread: Thread? = null

    init {
        holder.addCallback(this)
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        renderThread = Thread {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                textSize = 64f
                style = Paint.Style.FILL
            }

            while (running && holder.surface.isValid) {
                val canvas = holder.lockCanvas() ?: continue
                try {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    // Example drawing
                    canvas.drawCircle(canvas.width / 2f, canvas.height / 4f, 70f, paint)
                    paint.color = Color.GREEN
                    canvas.drawText("Hello Overlay", 60f, 120f, paint)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
                Thread.sleep(16)
            }
        }.also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.join()
    }
}
