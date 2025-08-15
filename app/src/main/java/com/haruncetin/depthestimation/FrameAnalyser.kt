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
import boofcv.abst.tracker.PointTracker
import boofcv.factory.tracker.FactoryPointTracker
import boofcv.android.ConvertBitmap
import boofcv.struct.image.GrayF32
import org.ddogleg.struct.FastQueue
import georegression.struct.point.Point2D_F64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2

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

    private val gridWidth = 128
    private val gridHeight = 96
    var occupancyGrid = Array(gridHeight) { IntArray(gridWidth) { 0 } }

    private val tracker: PointTracker<GrayF32> = FactoryPointTracker.kltPyramid(
        intArrayOf(3, 5, 7),
        200,
        null,
        GrayF32::class.java,
        null
    )
    private var prevImage: GrayF32? = null
    private val gray = GrayF32(1, 1)

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

        buildOccupancyGrid(output)
        processMotion(inputImage)

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
                val depthGray = Color.red(pixel)
                occupancyGrid[y][x] = depthGray
            }
        }

        occupancyGrid = DepthUtilsPro.bilateralFilter(
            occupancyGrid,
            radius = 2,
            sigmaSpatial = 1.5,
            sigmaDepth = 20.0
        )

        occupancyGrid = DepthUtilsPro.fillHolesEdgeAware(occupancyGrid)
    }

    private fun processMotion(rgbBitmap: Bitmap) {
        gray.reshape(rgbBitmap.width, rgbBitmap.height)
        ConvertBitmap.bitmapToBoof(rgbBitmap, gray, null)

        if (prevImage == null) {
            tracker.process(gray)
            prevImage = gray.clone()
            return
        }

        tracker.process(gray)
        val tracked: FastQueue<Point2D_F64> = tracker.tracksActive(null, null)

        val (dx, dy, dtheta) = estimateMotion(tracked)
        MappingManager.updateMap(occupancyGrid, dx, dy, dtheta)

        prevImage = gray.clone()
    }

    private fun estimateMotion(points: FastQueue<Point2D_F64>): Triple<Double, Double, Double> {
        if (points.size() < 5) return Triple(0.0, 0.0, 0.0)

        var sumDx = 0.0
        var sumDy = 0.0
        for (i in 0 until points.size()) {
            val p = points.get(i)
            sumDx += p.x - (prevImage!!.width / 2.0)
            sumDy += p.y - (prevImage!!.height / 2.0)
        }

        val avgDx = sumDx / points.size()
        val avgDy = sumDy / points.size()

        val scale = 0.2
        val dxCm = avgDx * scale
        val dyCm = avgDy * scale
        val dtheta = atan2(avgDy, avgDx) * 0.01

        return Triple(dxCm, dyCm, dtheta)
    }
}
