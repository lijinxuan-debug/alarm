package com.example.alarm_jinxuan.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SideIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = listOf("#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")

    private val paint = Paint().apply {
        color = 0xFF1D1D1F.toInt()
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private var currentLetter: String = ""
    private var letterHeight = 0f
    private var letterRect = Rect()
    var onLetterSelectedListener: ((String) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制右侧字母列表
        val centerY = height / 2f
        val totalHeight = letters.size * letterHeight
        var startY = centerY - totalHeight / 2f + letterHeight / 2f

        letters.forEach { letter ->
            paint.getTextBounds(letter, 0, letter.length, letterRect)
            val x = width / 2f
            val y = startY + (letterHeight - letterRect.height()) / 2f

            // 当前选中的字母用蓝色
            if (letter == currentLetter) {
                paint.color = 0xFF007AFF.toInt()  // 蓝色
                paint.textSize = 38f  // 稍微大一点
            } else {
                paint.color = 0xFF8E8E93.toInt()  // 灰色
                paint.textSize = 36f
            }

            canvas.drawText(letter, x, y, paint)
            startY += letterHeight
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        letterHeight = h.toFloat() / letters.size
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = (event.y / letterHeight).toInt()
                if (index in 0 until letters.size) {
                    val letter = letters[index]
                    if (letter != currentLetter) {
                        currentLetter = letter
                        onLetterSelectedListener?.invoke(letter)
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentLetter = ""
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}