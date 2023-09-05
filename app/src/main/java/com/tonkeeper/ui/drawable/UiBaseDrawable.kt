package com.tonkeeper.ui.drawable

import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

abstract class UiBaseDrawable: Drawable() {

    override fun setAlpha(alpha: Int) {

    }

    override fun setColorFilter(colorFilter: ColorFilter?) {

    }

    override fun getOpacity() = PixelFormat.UNKNOWN
}