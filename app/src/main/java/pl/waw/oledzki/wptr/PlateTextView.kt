package pl.waw.oledzki.wptr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PlateTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var text: String = ""
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    companion object {
        // Padding baked into each PNG (pixels on each side)
        private const val IMG_PAD = 10
        // Character body height in the PNGs (pixels)
        private const val BODY_H = 240
        // Full image height including padding
        private const val IMG_H = 260
        // Gap between characters in mm (regulation)
        private const val CHAR_GAP_MM = 8f
        // Character height in mm (regulation)
        private const val CHAR_H_MM = 80f
        // Wide gap after 2nd character (district code separator) in mm
        private const val DISTRICT_GAP_MM = 18f
        // Space width in mm
        private const val SPACE_W_MM = 12f

        private val cache = mutableMapOf<Char, Bitmap>()

        fun getBitmap(context: Context, ch: Char): Bitmap {
            return cache.getOrPut(ch) {
                val prefix = if (ch.isDigit()) "D" else "L"
                val name = "${prefix}_${ch}.png"
                context.assets.open("chars/$name").use { BitmapFactory.decodeStream(it) }
            }
        }

        /** Body width in mm, derived from bitmap aspect ratio. */
        fun bodyWidthMm(context: Context, ch: Char): Float {
            val bmp = getBitmap(context, ch)
            val bodyW = bmp.width - 2 * IMG_PAD
            // bodyW / BODY_H = widthMm / 80mm
            return bodyW.toFloat() / BODY_H * CHAR_H_MM
        }
    }

    fun setPlateText(plateText: String) {
        text = plateText.uppercase().trim()
        requestLayout()
        invalidate()
    }

    /** Strip spaces and get the plain character sequence. */
    private val plainText: String get() = text.replace(" ", "")

    /** Gap after the character at plain-text index [plainIdx]. */
    private fun gapAfter(plainIdx: Int): Float =
        if (plainIdx == 1) DISTRICT_GAP_MM else CHAR_GAP_MM

    /** Total content width in mm. */
    private fun measureContentWidthMm(): Float {
        val pt = plainText
        if (pt.isEmpty()) return 0f
        var w = 0f
        for ((i, c) in pt.withIndex()) {
            w += bodyWidthMm(context, c)
            if (i < pt.length - 1) {
                w += gapAfter(i)
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

        // scale: px per mm
        val pxPerMm = desiredH / CHAR_H_MM
        val contentWMm = measureContentWidthMm()
        val desiredW = (contentWMm * pxPerMm).toInt() + paddingLeft + paddingRight

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

        val contentWMm = measureContentWidthMm()
        val availH = height.toFloat() - paddingTop - paddingBottom
        val availW = width.toFloat() - paddingLeft - paddingRight

        val scaleByH = availH / CHAR_H_MM
        val scaleByW = availW / contentWMm
        val pxPerMm = minOf(scaleByH, scaleByW)

        val totalW = contentWMm * pxPerMm
        val totalH = CHAR_H_MM * pxPerMm
        val offsetX = paddingLeft + (availW - totalW) / 2
        val offsetY = paddingTop + (availH - totalH) / 2

        val pt = plainText
        var x = offsetX
        for ((i, c) in pt.withIndex()) {
            val bmp = getBitmap(context, c)
            val bodyW = bmp.width - 2 * IMG_PAD
            val bodyWMm = bodyW.toFloat() / BODY_H * CHAR_H_MM
            val drawW = bodyWMm * pxPerMm
            val drawH = totalH

            // Source rect: just the body (strip padding)
            val src = Rect(IMG_PAD, IMG_PAD, bmp.width - IMG_PAD, bmp.height - IMG_PAD)
            // Destination rect
            val dst = RectF(x, offsetY, x + drawW, offsetY + drawH)
            canvas.drawBitmap(bmp, src, dst, paint)

            x += drawW
            if (i < pt.length - 1) {
                x += gapAfter(i) * pxPerMm
            }
        }
    }
}
