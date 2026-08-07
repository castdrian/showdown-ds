package com.showdown.ds

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

class VulkanSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var surfaceId = 0L

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceId = MainActivity.nativeAttachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (surfaceId != 0L) {
            MainActivity.nativeDetachSurface(surfaceId)
            surfaceId = 0L
        }
    }
}
