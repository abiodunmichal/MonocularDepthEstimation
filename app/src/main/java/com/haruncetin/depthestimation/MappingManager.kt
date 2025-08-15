package your.package.name

import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles building a global occupancy map from multiple frames.
 * This is pure-vision SLAM-style mapping (no gyro/compass).
 */
object MappingManager {

    // Size of global map (in grid cells)
    private const val MAP_SIZE = 1000
    private const val CELL_SIZE_CM = 5 // 5 cm per cell

    // 2D array for occupancy values (-1 = unknown, 0 = free, 1 = occupied)
    private val globalMap = Array(MAP_SIZE) { IntArray(MAP_SIZE) { -1 } }

    // Camera position in map coordinates
    private var currentX = MAP_SIZE / 2
    private var currentY = MAP_SIZE / 2
    private var currentAngle = 0.0 // in radians

    /**
     * Update the global map with the new local occupancy grid.
     * @param localGrid - 2D array from depth estimation
     * @param dx - change in X (cm) since last frame
     * @param dy - change in Y (cm) since last frame
     * @param dtheta - change in angle (radians) since last frame
     */
    fun updateMap(localGrid: Array<IntArray>, dx: Double, dy: Double, dtheta: Double) {
        // Update camera pose
        currentAngle += dtheta
        currentX += (dx * cos(currentAngle) - dy * sin(currentAngle)).toInt()
        currentY += (dx * sin(currentAngle) + dy * cos(currentAngle)).toInt()

        // Merge local grid into global map
        val half = localGrid.size / 2
        for (y in localGrid.indices) {
            for (x in localGrid[0].indices) {
                val worldX = currentX + (x - half)
                val worldY = currentY + (y - half)
                if (worldX in 0 until MAP_SIZE && worldY in 0 until MAP_SIZE) {
                    if (localGrid[y][x] != -1) {
                        globalMap[worldY][worldX] = localGrid[y][x]
                    }
                }
            }
        }
    }

    fun getGlobalMap(): Array<IntArray> {
        return globalMap
    }
}
