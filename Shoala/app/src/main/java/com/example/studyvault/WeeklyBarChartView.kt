package com.example.studyvault

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class WeeklyBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<Pair<String, Long>> = emptyList()
    private var isMonthlyMode: Boolean = false

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#241918")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0A8A8")
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAB00")
        textAlign = Paint.Align.CENTER
    }

    fun setData(chartData: List<Pair<String, Long>>, isMonthly: Boolean = false) {
        this.data = chartData
        this.isMonthlyMode = isMonthly
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty() || width == 0 || height == 0) return

        val usableWidth = (width - paddingLeft - paddingRight).toFloat()
        if (usableWidth <= 0f) return

        valuePaint.textSize = sp(10f)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL

        val highestMinutes = data.maxOf { it.second }
        val maxVal = maxOf(60L, highestMinutes)
        val chartBottom = height - paddingBottom - dp(18f)
        val chartTop = paddingTop + dp(16f)
        val chartHeight = maxOf(dp(4f), chartBottom - chartTop)

        val colWidth = usableWidth / data.size
        val barWidth = if (isMonthlyMode) colWidth * 0.65f else colWidth * 0.45f
        val cornerRadius = if (isMonthlyMode) dp(2f) else dp(6f)
        val labelY = height - paddingBottom - dp(3f)

        data.forEachIndexed { index, (label, minutes) ->
            val pos = if (rtl) data.size - 1 - index else index
            val centerX = paddingLeft + (pos * colWidth) + (colWidth / 2f)
            val left = centerX - (barWidth / 2f)
            val right = centerX + (barWidth / 2f)

            canvas.drawRoundRect(
                RectF(left, chartTop, right, chartBottom),
                cornerRadius, cornerRadius, emptyBarPaint
            )

            if (minutes > 0) {
                val barHeight = (minutes.toFloat() / maxVal.toFloat()) * chartHeight
                val top = chartBottom - barHeight

                val colors = if (minutes == highestMinutes) {
                    intArrayOf(Color.parseColor("#FFF176"), Color.parseColor("#FFAB00"), Color.parseColor("#FF3D00"))
                } else {
                    intArrayOf(Color.parseColor("#FFD54F"), Color.parseColor("#FF6D00"), Color.parseColor("#D50000"))
                }

                barPaint.shader = LinearGradient(0f, top, 0f, chartBottom, colors, null, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(left, top, right, chartBottom), cornerRadius, cornerRadius, barPaint)

                if (!isMonthlyMode || minutes == highestMinutes) {
                    canvas.drawText("${minutes}د", centerX, top - dp(4f), valuePaint)
                }
            }

            if (!isMonthlyMode) {
                textPaint.textSize = sp(11f)
                canvas.drawText(label, centerX, labelY, textPaint)
            } else {
                val dayNum = index + 1
                val show = dayNum == 1 || dayNum == data.size ||
                        (dayNum % 5 == 0 && data.size - dayNum >= 2)   // avoids 30/31 overlap
                if (show) {
                    textPaint.textSize = sp(9f)
                    canvas.drawText(label, centerX, labelY, textPaint)
                }
            }
        }
    }
}
