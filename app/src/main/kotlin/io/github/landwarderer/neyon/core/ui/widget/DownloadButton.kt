package io.github.landwarderer.neyon.core.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.R as appcompatR
import androidx.core.content.ContextCompat
import com.google.android.material.R as materialR
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.util.ext.getThemeColor

class DownloadButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        DEFAULT, PENDING, ACTIVE, COMPLETED
    }

    private var _state: State = State.DEFAULT
    var state: State
        get() = _state
        set(value) {
            if (_state != value) {
                _state = value
                if (_state == State.PENDING) startRotation() else stopRotation()
                invalidate()
            }
        }

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (_state == State.ACTIVE) {
                invalidate()
            }
        }

    private val dp = context.resources.displayMetrics.density

    private val colorPrimary = context.getThemeColor(appcompatR.attr.colorPrimary)
    private val colorOnSurfaceVariant = try {
        context.getThemeColor(materialR.attr.colorOnSurfaceVariant)
    } catch (e: Exception) {
        context.getThemeColor(materialR.attr.colorOnSurface)
    }
    private val colorSurfaceVariant = try {
        context.getThemeColor(materialR.attr.colorSurfaceVariant)
    } catch (e: Exception) {
        context.getThemeColor(appcompatR.attr.colorButtonNormal)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        strokeCap = Paint.Cap.ROUND
    }

    private val backgroundArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        color = colorSurfaceVariant
    }

    private val stopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorPrimary
    }

    private val defaultIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_download)?.mutate()?.apply { setTint(colorOnSurfaceVariant) }
    private val completedIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.apply { setTint(colorPrimary) }
    private val pendingIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_sync)?.mutate()?.apply { setTint(colorOnSurfaceVariant) }
    private val rect = RectF()

    private var rotationAngle = 0f
    private var rotationAnimator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
        setBackgroundResource(typedValue.resourceId)
    }

    private fun startRotation() {
        if (rotationAnimator == null) {
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    rotationAngle = anim.animatedValue as Float
                    invalidate()
                }
            }
        }
        rotationAnimator?.start()
    }

    private fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAngle = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        val visualRadius = 14f * dp
        val strokeHalf = arcPaint.strokeWidth / 2f
        val radius = visualRadius - strokeHalf

        when (_state) {
            State.DEFAULT -> {
                drawIcon(canvas, defaultIcon)
            }
            State.PENDING -> {
                canvas.save()
                canvas.rotate(rotationAngle, cx, cy)
                drawIcon(canvas, pendingIcon)
                canvas.restore()
            }
            State.ACTIVE -> {
                rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
                canvas.drawArc(rect, 0f, 360f, false, backgroundArcPaint)
                
                arcPaint.color = colorPrimary
                canvas.drawArc(rect, -90f, progress * 360f, false, arcPaint)

                val stopSize = 3f * dp
                canvas.drawRoundRect(
                    cx - stopSize, cy - stopSize, cx + stopSize, cy + stopSize,
                    1.5f * dp, 1.5f * dp, stopPaint
                )
            }
            State.COMPLETED -> {
                drawIcon(canvas, completedIcon)
            }
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable?) {
        icon?.let {
            val size = (12f * dp).toInt()
            val cx = width / 2
            val cy = height / 2
            it.setBounds(cx - size, cy - size, cx + size, cy + size)
            it.draw(canvas)
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRotation()
    }
}
