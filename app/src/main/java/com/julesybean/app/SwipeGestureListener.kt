package com.julesybean.app

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

open class SwipeGestureListener(context: Context) : GestureDetector.SimpleOnGestureListener() {

    private val SWIPE_THRESHOLD = 100
    private val SWIPE_VELOCITY_THRESHOLD = 100

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        var result = false
        try {
            if (e1 != null) {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        result = true
                    }
                }
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return result
    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
}
