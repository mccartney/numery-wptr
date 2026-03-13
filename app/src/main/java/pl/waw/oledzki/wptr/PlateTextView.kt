package pl.waw.oledzki.wptr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PlateTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var text: String = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    companion object {
        private const val CHAR_GAP = 4f   // mm between characters
        private const val SPACE_W = 12f   // mm for space character
    }

    fun setPlateText(plateText: String) {
        text = plateText.uppercase().trim()
        requestLayout()
        invalidate()
    }

    private fun measureContentWidth(): Float {
        if (text.isEmpty()) return 0f
        var w = 0f
        for ((i, c) in text.withIndex()) {
            if (c == ' ') {
                w += SPACE_W
            } else {
                val (_, cw) = PlateFont.get(c)
                w += cw
                if (i < text.length - 1 && text[i + 1] != ' ' && c != ' ') {
                    w += CHAR_GAP
                }
            }
        }
        return w
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)

        val desiredH = if (hMode == MeasureSpec.EXACTLY) hSize
        else {
            val h = (48 * resources.displayMetrics.scaledDensity).toInt()
            if (hMode == MeasureSpec.AT_MOST) minOf(h, hSize) else h
        }

        val scale = desiredH / PlateFont.H
        val contentW = measureContentWidth()
        val desiredW = (contentW * scale).toInt() + paddingLeft + paddingRight

        val finalW = when (wMode) {
            MeasureSpec.EXACTLY -> wSize
            MeasureSpec.AT_MOST -> minOf(desiredW, wSize)
            else -> desiredW
        }
        setMeasuredDimension(finalW, desiredH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (text.isEmpty()) return

        val contentW = measureContentWidth()
        val availH = height.toFloat() - paddingTop - paddingBottom
        val availW = width.toFloat() - paddingLeft - paddingRight

        val scaleByH = availH / PlateFont.H
        val scaleByW = availW / contentW
        val scale = minOf(scaleByH, scaleByW)

        val totalW = contentW * scale
        val totalH = PlateFont.H * scale
        val offsetX = paddingLeft + (availW - totalW) / 2
        val offsetY = paddingTop + (availH - totalH) / 2

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        var x = 0f
        for ((i, c) in text.withIndex()) {
            if (c == ' ') {
                x += SPACE_W
                continue
            }
            val (path, cw) = PlateFont.get(c)
            canvas.save()
            canvas.translate(x, 0f)
            canvas.drawPath(path, paint)
            canvas.restore()
            x += cw
            if (i < text.length - 1 && text[i + 1] != ' ' && c != ' ') {
                x += CHAR_GAP
            }
        }
        canvas.restore()
    }
}
