package pl.waw.oledzki.wptr

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * LinearLayout that enforces the official Polish plate aspect ratio (520:114).
 * Height is determined by children; width is computed from the ratio.
 */
class PlateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // First pass: let children determine the height
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Enforce width from the plate ratio
        val h = measuredHeight
        val w = (h * 520f / 114f).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }
}
