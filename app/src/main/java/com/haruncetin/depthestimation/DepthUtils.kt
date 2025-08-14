package com.haruncetin.depthestimation

import android.graphics.Color
import kotlin.math.*

// Fully optimised depth utilities: bilateral filtering, edge-aware hole fill,
// precomputed Turbo colormap, and fast math for real-time Android performance.
object DepthUtilsPro {

    /** Inline clamp for speed */
    private inline fun clamp(v: Int, minVal: Int, maxVal: Int) =
        if (v < minVal) minVal else if (v > maxVal) maxVal else v

    /** Inline absolute value */
    private inline fun iabs(v: Int) = if (v < 0) -v else v

    // -------------------- 1. Precomputed Turbo Colormap --------------------
    val turboColors = IntArray(256).apply {
        for (i in 0..255) {
            val x = i / 255.0
            val r = (34.61 + x * (1172.33 + x * (-10793.56 + x * (33300.12 +
                    x * (-38394.49 + x * 14825.05))))) / 255.0
            val g = (23.31 + x * (557.33 + x * (1225.33 + x * (-3574.96 +
                    x * (4520.72 + x * -1930.56))))) / 255.0
            val b = (27.2 + x * (3211.1 + x * (-15327.97 + x * (27814.0 +
                    x * (-22569.18 + x * 6838.66))))) / 255.0
            this[i] = Color.rgb(
                clamp((r * 255).toInt(), 0, 255),
                clamp((g * 255).toInt(), 0, 255),
                clamp((b * 255).toInt(), 0, 255)
            )
        }
    }

    fun depthToTurbo(depth: Int) = turboColors[clamp(depth, 0, 255)]

    // -------------------- 2. Bilateral Filter --------------------
    fun bilateralFilter(
        grid: Array<IntArray>,
        radius: Int = 2,
        sigmaSpatial: Double = 1.5,
        sigmaDepth: Double = 20.0
    ): Array<IntArray> {
        val rows = grid.size
        val cols = grid[0].size
        val output = Array(rows) { IntArray(cols) }

        val spatialWeight = Array(radius * 2 + 1) { dy ->
            DoubleArray(radius * 2 + 1) { dx ->
                exp(-(dx * dx + dy * dy) / (2 * sigmaSpatial * sigmaSpatial))
            }
        }

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val centerVal = grid[y][x]
                var weightedSum = 0.0
                var weightTotal = 0.0

                for (dy in -radius..radius) {
                    val ny = clamp(y + dy, 0, rows - 1)
                    for (dx in -radius..radius) {
                        val nx = clamp(x + dx, 0, cols - 1)
                        val neighborVal = grid[ny][nx]
                        val depthDiff = centerVal - neighborVal
                        val rangeWeight = exp(-(depthDiff * depthDiff) / (2 * sigmaDepth * sigmaDepth))
                        val weight = spatialWeight[dy + radius][dx + radius] * rangeWeight
                        weightedSum += neighborVal * weight
                        weightTotal += weight
                    }
                }
                output[y][x] = (weightedSum / weightTotal).toInt()
            }
        }
        return output
    }

    // -------------------- 3. Edge Detection (Sobel) --------------------
    private fun computeEdgeMask(grid: Array<IntArray>): Array<BooleanArray> {
        val rows = grid.size
        val cols = grid[0].size
        val mask = Array(rows) { BooleanArray(cols) }

        val gxKernel = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        val gyKernel = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )

        for (y in 1 until rows - 1) {
            for (x in 1 until cols - 1) {
                var gx = 0
                var gy = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val valDepth = grid[y + ky][x + kx]
                        gx += gxKernel[ky + 1][kx + 1] * valDepth
                        gy += gyKernel[ky + 1][kx + 1] * valDepth
                    }
                }
                val magnitude = sqrt((gx * gx + gy * gy).toDouble())
                mask[y][x] = magnitude > 30 // threshold
            }
        }
        return mask
    }

    // -------------------- 4. Edge-Aware Hole Filling --------------------
    fun fillHolesEdgeAware(grid: Array<IntArray>): Array<IntArray> {
        val rows = grid.size
        val cols = grid[0].size
        val output = Array(rows) { IntArray(cols) }
        val edgeMask = computeEdgeMask(grid)

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (grid[y][x] > 0) {
                    output[y][x] = grid[y][x]
                } else {
                    var sum = 0
                    var count = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val ny = y + dy
                            val nx = x + dx
                            if (ny in 0 until rows && nx in 0 until cols && grid[ny][nx] > 0) {
                                if (!edgeMask[y][x] || !edgeMask[ny][nx]) {
                                    sum += grid[ny][nx]
                                    count++
                                }
                            }
                        }
                    }
                    output[y][x] = if (count > 0) sum / count else 0
                }
            }
        }
        return output
    }
}
