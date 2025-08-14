package com.haruncetin.depthestimation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OccupancyGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gridData: Array<IntArray>? = null
    private val paint = Paint()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val grid = gridData ?: return
        val rows = grid.size
        val cols = grid[0].size

        val cellWidth = width.toFloat() / cols
        val cellHeight = height.toFloat() / rows

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                paint.color = if (grid[y][x] == 1) Color.BLACK else Color.WHITE
                canvas.drawRect(
                    x * cellWidth,
                    y * cellHeight,
                    (x + 1) * cellWidth,
                    (y + 1) * cellHeight,
                    paint
                )
            }
        }
    }

    fun updateGrid(newGrid: Array<IntArray>) {
        gridData = newGrid
        invalidate() // Redraw the view
    }
}
