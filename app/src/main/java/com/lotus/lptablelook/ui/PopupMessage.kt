package com.lotus.lptablelook.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.lotus.lptablelook.R

class PopupMessage private constructor(
    private val activity: Activity,
    private val message: String,
    private val type: Type,
    private val duration: Long
) {
    enum class Type {
        SUCCESS,
        ERROR,
        INFO,
        WARNING
    }

    companion object {
        private const val DEFAULT_DURATION = 3000L
        private var currentPopup: View? = null

        fun success(activity: Activity, message: String, duration: Long = DEFAULT_DURATION): PopupMessage {
            return PopupMessage(activity, message, Type.SUCCESS, duration)
        }

        fun error(activity: Activity, message: String, duration: Long = 4000L): PopupMessage {
            return PopupMessage(activity, message, Type.ERROR, duration)
        }

        fun info(activity: Activity, message: String, duration: Long = DEFAULT_DURATION): PopupMessage {
            return PopupMessage(activity, message, Type.INFO, duration)
        }

        fun warning(activity: Activity, message: String, duration: Long = DEFAULT_DURATION): PopupMessage {
            return PopupMessage(activity, message, Type.WARNING, duration)
        }
    }

    fun show() {
        // Remove any existing popup
        currentPopup?.let { dismissImmediately(it) }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_message, rootView, false)

        val cardPopup = popupView.findViewById<CardView>(R.id.cardPopup)
        val container = popupView.findViewById<View>(R.id.popupContainer)
        val ivIcon = popupView.findViewById<ImageView>(R.id.ivIcon)
        val tvMessage = popupView.findViewById<TextView>(R.id.tvMessage)

        // Set message
        tvMessage.text = message

        // Set style based on type
        val (bgColor, iconRes) = when (type) {
            Type.SUCCESS -> Pair(
                ContextCompat.getColor(activity, R.color.table_available),
                android.R.drawable.ic_dialog_info
            )
            Type.ERROR -> Pair(
                ContextCompat.getColor(activity, R.color.table_occupied),
                android.R.drawable.ic_dialog_alert
            )
            Type.INFO -> Pair(
                ContextCompat.getColor(activity, R.color.primary_dark),
                android.R.drawable.ic_dialog_info
            )
            Type.WARNING -> Pair(
                ContextCompat.getColor(activity, R.color.table_occupied_orange),
                android.R.drawable.ic_dialog_alert
            )
        }

        container.setBackgroundColor(bgColor)
        ivIcon.setImageResource(iconRes)

        // Set layout params for bottom center positioning
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 120
        }
        popupView.layoutParams = params

        // Add to root
        rootView.addView(popupView)
        currentPopup = popupView

        // Animate in - slide up with bounce
        popupView.translationY = 200f
        popupView.alpha = 0f

        popupView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        // Schedule dismiss
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss(popupView, rootView)
        }, duration)

        // Click to dismiss early
        popupView.setOnClickListener {
            dismiss(popupView, rootView)
        }
    }

    private fun dismiss(popupView: View, rootView: ViewGroup) {
        popupView.animate()
            .translationY(100f)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        rootView.removeView(popupView)
                        if (currentPopup == popupView) {
                            currentPopup = null
                        }
                    } catch (e: Exception) {
                        // View already removed
                    }
                }
            })
            .start()
    }

    private fun dismissImmediately(popupView: View) {
        try {
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView.removeView(popupView)
            currentPopup = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
