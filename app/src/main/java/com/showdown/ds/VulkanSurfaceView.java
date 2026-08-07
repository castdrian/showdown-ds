package com.showdown.ds;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public final class VulkanSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private long surfaceId;

    public VulkanSurfaceView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceId = MainActivity.nativeAttachSurface(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (surfaceId != 0L) {
            MainActivity.nativeDetachSurface(surfaceId);
            surfaceId = 0L;
        }
    }
}
