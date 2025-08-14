package com.haruncetin.depthestimation

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
    private var depthModel: MidasNetSmall,
    private var depthView: SurfaceView
) : ImageAnalysis.Analyzer {

    private var metrics: DisplayMetrics? = null
    private var inferenceTime: Long = 0
    private var mLastTime: Long = 0
    private var fps = 0
    private var ifps: Int = 0
    private var readyToProcess = true

    // Occupancy grid parameters – increased resolution
    private val gridWidth = 128
    private val gridHeight = 96
    var occupancyGrid = Array(gridHeight) { IntArray(gridWidth) { 0 } }

    init {
        metrics = DepthEstimationApp.applicationContext().resources.displayMetrics
    }

    override fun analyze(image: ImageProxy) {
        if (!readyToProcess) {
            image.close()
            return
        }
        readyToProcess = false

        image.image?.let { img ->
            val bitmap = img.toBitmap(image.imageInfo.rotationDegrees)
            image.close()
            CoroutineScope(Dispatchers.Main).launch {
                run(bitmap)
            }
        } ?: image.close()
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

        // Build processed occupancy grid from depth map bitmap
        buildOccupancyGrid(output)

        withContext(Dispatchers.Main) {
            draw(output) // currently still drawing raw depth output
            readyToProcess = true
        }
    }

    private fun buildOccupancyGrid(depthBitmap: Bitmap) {
        // Step 1 – Scale depth to grid resolution
        val scaledDepth = Bitmap.createScaledBitmap(depthBitmap, gridWidth, gridHeight, true)

        // Step 2 – Store raw depth values (0–255 grayscale)
        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                val pixel = scaledDepth.getPixel(x, y)
                val depthGray = Color.red(pixel)
                occupancyGrid[y][x] = depthGray
            }
        }

        // Step 3 – Apply bilateral smoothing (DepthUtilsPro)
        occupancyGrid = DepthUtilsPro.bilateralFilter(
            occupancyGrid,
            radius = 2,
            sigmaSpatial = 1.5,
            sigmaDepth = 20.0
        )

        // Step 4 – Apply edge-aware hole filling (DepthUtilsPro)
        occupancyGrid = DepthUtilsPro.fillHolesEdgeAware(occupancyGrid)
    }
}
