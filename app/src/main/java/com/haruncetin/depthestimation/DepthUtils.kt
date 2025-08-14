package com.haruncetin.depthestimation

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

object DepthUtils {

    /**
     * Median filter to smooth occupancy grid values
     */
    fun medianFilter(grid: Array<IntArray>, windowSize: Int = 3): Array<IntArray> {
        val rows = grid.size
        val cols = grid[0].size
        val output = Array(rows) { IntArray(cols) }

        val offset = windowSize / 2
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val values = mutableListOf<Int>()
                for (dy in -offset..offset) {
                    for (dx in -offset..offset) {
                        val ny = min(max(y + dy, 0), rows - 1)
                        val nx = min(max(x + dx, 0), cols - 1)
                        values.add(grid[ny][nx])
                    }
                }
                values.sort()
                output[y][x] = values[values.size / 2] // median
            }
        }
        return output
    }

    /**
     * Fill missing depth values (-1) by averaging neighbors
     */
    fun fillHoles(grid: Array<IntArray>): Array<IntArray> {
        val rows = grid.size
        val cols = grid[0].size
        val output = Array(rows) { IntArray(cols) }

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (grid[y][x] >= 0) {
                    output[y][x] = grid[y][x]
                } else {
                    // Average valid neighbors
                    var sum = 0
                    var count = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val ny = y + dy
                            val nx = x + dx
                            if (ny in 0 until rows && nx in 0 until cols && grid[ny][nx] >= 0) {
                                sum += grid[ny][nx]
                                count++
                            }
                        }
                    }
                    output[y][x] = if (count > 0) sum / count else 0
                }
            }
        }
        return output
    }

    /**
     * Map depth (0–255) to a heatmap color
     */
    fun depthToHeatmap(depth: Int): Int {
        val clamped = depth.coerceIn(0, 255)
        val r = (255 * clamped / 255)
        val g = (255 * (255 - kotlin.math.abs(clamped - 128)) / 255)
        val b = (255 * (255 - clamped) / 255)
        return Color.rgb(r, g, b)
    }
}
