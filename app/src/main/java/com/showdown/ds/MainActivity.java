package com.showdown.ds;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private DisplayManager displayManager;
    private ThorPresentation secondaryPresentation;
    private boolean vulkanReady;

    static {
        System.loadLibrary("showdown_vulkan");
    }

    private static native boolean nativeInitializeVulkan();

    private static native void nativeReleaseVulkan();

    static native long nativeAttachSurface(Surface surface);

    static native void nativeDetachSurface(long surfaceId);

    static native String nativeGetVulkanApiVersion();

    static native int nativeGetSurfaceCount();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vulkanReady = nativeInitializeVulkan();
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        setContentView(createScreen("Primary display", "1920 × 1080", Color.rgb(24, 62, 104)));
        displayManager.registerDisplayListener(displayListener, null);
        showSecondaryDisplay();
    }

    @Override
    protected void onDestroy() {
        dismissSecondaryDisplay();
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        nativeReleaseVulkan();
        super.onDestroy();
    }

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            showSecondaryDisplay();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            showSecondaryDisplay();
        }

        @Override
        public void onDisplayChanged(int displayId) {
            showSecondaryDisplay();
        }
    };

    private void showSecondaryDisplay() {
        if (isFinishing() || displayManager == null || secondaryPresentation != null) {
            return;
        }
        Display display = findThorDisplay();
        if (display != null) {
            secondaryPresentation = new ThorPresentation(this, display);
            secondaryPresentation.setOnDismissListener(dialog -> secondaryPresentation = null);
            secondaryPresentation.show();
        }
    }

    private Display findThorDisplay() {
        Display fallback = null;
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == getDisplayId()) {
                continue;
            }
            if (display.getMode().getPhysicalWidth() == 1240 && display.getMode().getPhysicalHeight() == 1080) {
                return display;
            }
            if (fallback == null) {
                fallback = display;
            }
        }
        return fallback;
    }

    private int getDisplayId() {
        return Display.DEFAULT_DISPLAY;
    }

    private void dismissSecondaryDisplay() {
        if (secondaryPresentation != null) {
            secondaryPresentation.dismiss();
            secondaryPresentation = null;
        }
    }

    private View createScreen(String title, String resolution, int screenColor) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(screenColor);
        VulkanSurfaceView surfaceView = new VulkanSurfaceView(this);
        surfaceView.setBackgroundColor(screenColor);
        frame.addView(surfaceView, new FrameLayout.LayoutParams(-1, -1));

        TextView status = new TextView(this);
        status.setText(buildStatus(title, resolution));
        status.setTextColor(Color.WHITE);
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(0f, 1.2f);
        status.setBackgroundColor(Color.argb(210, 5, 15, 28));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        frame.addView(status, statusParams);
        return frame;
    }

    private String buildStatus(String title, String resolution) {
        String vulkan = vulkanReady ? nativeGetVulkanApiVersion() : "Unavailable";
        return "showdown-ds\n" + title + "\n" + resolution + "\n" + vulkan;
    }

    private final class ThorPresentation extends Presentation {
        ThorPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Window window = getWindow();
            if (window != null) {
                window.setDimAmount(0f);
            }
            setContentView(createPresentationScreen());
        }

        private View createPresentationScreen() {
            FrameLayout frame = new FrameLayout(getContext());
            int screenColor = Color.rgb(21, 83, 74);
            frame.setBackgroundColor(screenColor);
            VulkanSurfaceView surfaceView = new VulkanSurfaceView(getContext());
            surfaceView.setBackgroundColor(screenColor);
            frame.addView(surfaceView, new FrameLayout.LayoutParams(-1, -1));

            TextView status = new TextView(getContext());
            status.setText(buildStatus("Secondary display", "1240 × 1080"));
            status.setTextColor(Color.WHITE);
            status.setTextSize(20);
            status.setGravity(Gravity.CENTER);
            status.setLineSpacing(0f, 1.2f);
            status.setBackgroundColor(Color.argb(210, 5, 15, 28));
            frame.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            return frame;
        }
    }
}
