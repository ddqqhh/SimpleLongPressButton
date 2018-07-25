package com.dongqh.simplelongpressbutton

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.support.annotation.ColorInt
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

class SimpleLongClickButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    var buttonTitle: String? = null

    private val outsideCirclePaint = Paint()
    private val insideCirclePaint = Paint()
    private val rollingArcPaint = Paint().apply { style = Paint.Style.STROKE }
    private val textPaint = Paint().apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
    }

    private var enlargeAnimator = ValueAnimator()
        set(value) {
            field.cancel()
            field = value
        }

    private var shrinkAnimator = ValueAnimator()
        set(value) {
            field.cancel()
            field = value
        }
    private var rollingAnimator = ValueAnimator()
        set(value) {
            field.cancel()
            field = value
        }

    private val PHASE_NORMAL = 0
    private val PHASE_ENLARGE = 1
    private val PHASE_ROLLING = 3
    private val PHASE_RESTORE = 4

    private val OUTSIDE_CIRCLE_MAX get() = diameter
    private val OUTSIDE_CIRCLE_MIN get() = diameter * 0.8f
    private val INSIDE_CIRCLE_MAX get() = diameter * 0.6f
    private val INSIDE_CIRCLE_MIN get() = diameter * 0.3f
    private val START_ANGLE = -90f
    private val MIN_ANGLE = 0f
    private val MAX_ANGLE = 360f

    private var currentOutsideRadius = 0f
    private var currentInsideRadius = 0f
    private var currentAngle = 0f
    private val rollingRectF = RectF()

    private var phase = PHASE_NORMAL

    private val diameter get() = Math.min(width, height).toFloat()
    private val centerX get() = width.toFloat() / 2
    private val centerY get() = height.toFloat() / 2

    private var onTriggered: () -> Unit = {}
    private var onCancel: () -> Unit = {}

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SimpleLongClickButton)
        val outsideCircleColor = typedArray.getColor(R.styleable.SimpleLongClickButton_dongqh_outsideCircleColor, Color.parseColor("#dddddd"))
        val insideCircleColor = typedArray.getColor(R.styleable.SimpleLongClickButton_dongqh_insideCircleColor, Color.WHITE)
        val rollingArcColor = typedArray.getColor(R.styleable.SimpleLongClickButton_dongqh_rollingArcColor, Color.RED)
        val textColor = typedArray.getColor(R.styleable.SimpleLongClickButton_dongqh_textColor, Color.BLACK)
        val textSize = typedArray.getDimensionPixelSize(R.styleable.SimpleLongClickButton_dongqh_textSize, 0)
        buttonTitle = typedArray.getString(R.styleable.SimpleLongClickButton_dongqh_text)

        outsideCirclePaint.color = outsideCircleColor
        insideCirclePaint.color = insideCircleColor
        rollingArcPaint.color = rollingArcColor
        textPaint.color = textColor
        textPaint.textSize = textSize.toFloat()

        typedArray.recycle()
    }

    fun setCircleColor(@ColorInt outsideCircleColor: Int = Color.parseColor("#dddddd"),
                       @ColorInt insideCircleColor: Int = Color.WHITE) {
        outsideCirclePaint.color = outsideCircleColor
        insideCirclePaint.color = insideCircleColor
    }

    fun setRollingProgressDesign(@ColorInt arcColor: Int = Color.RED,
                                 strokeWidth: Float = 0f) {
        rollingArcPaint.color = arcColor
        rollingArcPaint.strokeWidth = strokeWidth
    }

    fun setTextDesign(@ColorInt textColor: Int = Color.BLACK,
                      textSize: Float = 0f) {
        textPaint.color = textColor
        textPaint.textSize = textSize
    }

    fun setListener(onTriggered: () -> Unit = {},
                    onCancel: () -> Unit = {}) {
        this.onTriggered = onTriggered
        this.onCancel = onCancel
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> drawEnlarge()
            MotionEvent.ACTION_UP -> {
                enlargeAnimator.cancel()
                shrinkAnimator.cancel()
                rollingAnimator.cancel()
                drawRestore()
            }
            else -> Unit
        }
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (textPaint.textSize == 0f && diameter != 0f) {
            textPaint.textSize = diameter / 6
        }
        if (rollingArcPaint.strokeWidth == 0f && diameter != 0f) {
            rollingArcPaint.strokeWidth = diameter / 6 / 2.5f
        }
    }

    override fun onDraw(canvas: Canvas) {
        when (phase) {
            PHASE_NORMAL -> {
                canvas.drawCircle(centerX, centerY, OUTSIDE_CIRCLE_MIN / 2, outsideCirclePaint)
                canvas.drawCircle(centerX, centerY, INSIDE_CIRCLE_MAX / 2, insideCirclePaint)
                buttonTitle?.let { canvas.drawText(it, centerX, centerY + textPaint.textSize / 2, textPaint) }
            }
            PHASE_ENLARGE,
            PHASE_RESTORE -> {
                canvas.drawCircle(centerX, centerY, currentOutsideRadius / 2, outsideCirclePaint)
                canvas.drawCircle(centerX, centerY, currentInsideRadius / 2, insideCirclePaint)
            }
            PHASE_ROLLING -> {
                canvas.drawCircle(centerX, centerY, OUTSIDE_CIRCLE_MAX / 2, outsideCirclePaint)
                canvas.drawCircle(centerX, centerY, INSIDE_CIRCLE_MIN / 2, insideCirclePaint)
                val left = centerX - diameter / 2 + diameter / 12 / 2.5f
                val top = centerY - diameter / 2 + diameter / 12 / 2.5f
                val right = centerX + diameter / 2 - diameter / 12 / 2.5f
                val bottom = centerY + diameter / 2 - diameter / 12 / 2.5f
                rollingRectF.set(left, top, right, bottom)
                canvas.drawArc(rollingRectF, START_ANGLE, currentAngle, false, rollingArcPaint)
            }
            else -> Unit
        }
    }

    private fun drawNormal() {
        phase = PHASE_NORMAL
        invalidate()
    }

    private fun drawEnlarge() {
        phase = PHASE_ENLARGE
        enlargeAnimator = ValueAnimator.ofFloat(OUTSIDE_CIRCLE_MIN, OUTSIDE_CIRCLE_MAX).apply {
            interpolator = LinearInterpolator()
            duration = 200L
            addUpdateListener { radius ->
                currentOutsideRadius = (radius.animatedValue as Float)
                invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                var onFinished = { drawRolling() }
                override fun onAnimationRepeat(animation: Animator?) = Unit
                override fun onAnimationEnd(animation: Animator?) = onFinished.invoke()
                override fun onAnimationCancel(animation: Animator?) {
                    onFinished = onCancel
                }

                override fun onAnimationStart(animation: Animator?) = Unit
            })
        }
        shrinkAnimator = ValueAnimator.ofFloat(INSIDE_CIRCLE_MAX, INSIDE_CIRCLE_MIN)
                .apply {
                    interpolator = LinearInterpolator()
                    duration = 200L
                    addUpdateListener { radius ->
                        currentInsideRadius = (radius.animatedValue as Float)
                        invalidate()
                    }
                }
        enlargeAnimator.start()
        shrinkAnimator.start()
    }

    private fun drawRolling() {
        phase = PHASE_ROLLING
        rollingAnimator = ValueAnimator.ofFloat(MIN_ANGLE, MAX_ANGLE)
                .apply {
                    interpolator = LinearInterpolator()
                    duration = 1500L
                    addUpdateListener { radius ->
                        currentAngle = (radius.animatedValue as Float)
                        invalidate()
                    }
                    addListener(object : Animator.AnimatorListener {
                        var onFinish = onTriggered
                        override fun onAnimationRepeat(animation: Animator?) = Unit
                        override fun onAnimationEnd(animation: Animator?) = onFinish.invoke()
                        override fun onAnimationCancel(animation: Animator?) {
                            onFinish = onCancel
                        }

                        override fun onAnimationStart(animation: Animator?) = Unit
                    })
                }
        rollingAnimator.start()
    }

    private fun drawRestore() {
        phase = PHASE_RESTORE
        enlargeAnimator = ValueAnimator.ofFloat(currentOutsideRadius, OUTSIDE_CIRCLE_MIN).apply {
            interpolator = LinearInterpolator()
            duration = 100L
            addUpdateListener { radius ->
                currentOutsideRadius = (radius.animatedValue as Float)
                invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) = Unit
                override fun onAnimationEnd(animation: Animator?) = drawNormal()
                override fun onAnimationCancel(animation: Animator?) = Unit
                override fun onAnimationStart(animation: Animator?) = Unit
            })
        }
        shrinkAnimator = ValueAnimator.ofFloat(currentInsideRadius, INSIDE_CIRCLE_MAX)
                .apply {
                    interpolator = LinearInterpolator()
                    duration = 100L
                    addUpdateListener { radius ->
                        currentInsideRadius = (radius.animatedValue as Float)
                        invalidate()
                    }
                }
        enlargeAnimator.start()
        shrinkAnimator.start()
    }
}