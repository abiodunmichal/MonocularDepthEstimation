package com.haruncetin.depthestimation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.DisplayMetrics
import android.view.SurfaceView
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ExperimentalGetImage
class FrameAnalyser(
    private var depthModel : MidasNetSmall,
    private var depthView: SurfaceView
) : ImageAnalysis.Analyzer {

    private var metrics: DisplayMetrics? = null
    private var inferenceTime: Long = 0
    private var mLastTime: Long = 0
    private var fps = 0
    private var ifps:Int = 0
    private var readyToProcess = true

    // Occupancy grid parameters
    private val gridWidth = 64
    private val gridHeight = 48
    val occupancyGrid = Array(gridHeight) { IntArray(gridWidth) { 0 } }

    init {
        metrics = DepthEstimationApp.applicationContext().resources.displayMetrics
    }

    override fun analyze(image: ImageProxy) {
        if (!readyToProcess) {
            image.close()
            return
        }
        readyToProcess = false

        if (image.image != null) {
            val bitmap = image.image!!.toBitmap(image.imageInfo.rotationDegrees)
            image.close()
            CoroutineScope(Dispatchers.Main).launch {
                run(bitmap)
            }
        }
    }

    private fun draw(image: Bitmap) {
        val canvas: Canvas = depthView.holder.lockCanvas() ?: return
        val now: Long = System.currentTimeMillis()
        synchronized(depthView.holder) {
            val scaled: Bitmap = Bitmap.createScaledBitmap(
                image,
                depthView.width,
                depthView.height,
                true
            )
            val paint = Paint()
            canvas.drawBitmap(scaled, 0f, 0f, null)
            paint.color = Color.RED
            paint.isAntiAlias = true
            paint.textSize = 14f * (metrics!!.densityDpi / 160f)
            canvas.drawText("Inference Time : $inferenceTime ms", 50f, 80f, paint)
            canvas.drawText("FPS : $fps", 50f, 150f, paint)
            depthView.holder.unlockCanvasAndPost(canvas)
        }
        ifps++
        if (now > (mLastTime + 1000)) {
            mLastTime = now
            fps = ifps
            ifps = 0
        }
    }

    private suspend fun run(inputImage: Bitmap) = withContext(Dispatchers.Default) {
        val output = depthModel.getDepthMap(inputImage)
        inferenceTime = depthModel.getInferenceTime()

        // Build occupancy grid from depth map bitmap
        buildOccupancyGrid(output)

        withContext(Dispatchers.Main) {
            draw(output)
            readyToProcess = true
        }
    }

    private fun buildOccupancyGrid(depthBitmap: Bitmap) {
        val scaledDepth = Bitmap.createScaledBitmap(depthBitmap, gridWidth, gridHeight, true)
        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                val pixel = scaledDepth.getPixel(x, y)
                val depthGray = Color.red(pixel) // red=green=blue in grayscale depth
                // Threshold: closer pixels marked as obstacle
                occupancyGrid[y][x] = if (depthGray > 180) 0 else 1
            }
        }
    }
}
