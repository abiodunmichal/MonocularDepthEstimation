package com.example.monoculardepthestimation

import android.graphics.Bitmap
import android.graphics.Color

object MapProcessor {
    /**
     * Converts raw depth float array into an occupancy map.
     * @param depthData Depth values from the MiDaS model (length = size*size).
     * @param size The dimension of the depth image (e.g. 256).
     * @param threshold Depth cutoff for "occupied" space.
     */
    fun depthToOccupancy(depthData: FloatArray, size: Int, threshold: Float = 100f): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val d = depthData[y * size + x]
                val color = if (d < threshold) Color.BLACK else Color.WHITE
                bmp.setPixel(x, y, color)
            }
        }
        return bmp
    }
}
