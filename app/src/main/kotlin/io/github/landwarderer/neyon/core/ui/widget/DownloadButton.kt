package io.github.landwarderer.neyon.core.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.appcompat.R as appcompatR
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.util.ext.getThemeColor
import com.google.android.material.R as materialR
import kotlin.math.min

class DownloadButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        DEFAULT, PENDING, ACTIVE, COMPLETED
    }

    var state: State = State.DEFAULT
        set(value) {
            field = value
            invalidate()
        }

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (state == State.ACTIVE) {
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = context.getThemeColor(appcompatR.attr.colorPrimary)
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = context.getThemeColor(materialR.attr.colorSurfaceVariant)
    }

    private val defaultIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_download)
    private val completedIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_check)
    private val pendingIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_sync) // Replace with proper spin icon or animate
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - paint.strokeWidth / 2f

        when (state) {
            State.DEFAULT -> {
                drawIcon(canvas, defaultIcon)
            }
            State.PENDING -> {
                drawIcon(canvas, pendingIcon)
            }
            State.ACTIVE -> {
                rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
                canvas.drawArc(rect, 0f, 360f, false, backgroundPaint)
                canvas.drawArc(rect, -90f, progress * 360f, false, paint)
                // Draw pause/cancel icon in center optionally
            }
            State.COMPLETED -> {
                drawIcon(canvas, completedIcon)
            }
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable?) {
        icon?.let {
            val size = (min(width, height) * 0.5f).toInt()
            val cx = width / 2
            val cy = height / 2
            it.setBounds(cx - size, cy - size, cx + size, cy + size)
            it.draw(canvas)
        }
    }
}
