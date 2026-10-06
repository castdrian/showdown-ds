package main

import (
	"image"
	"image/color"
	"image/draw"
	"strings"
	"testing"
)

func TestSplitDualScreenCaptureKeepsScreensFromOneCaptureTogether(t *testing.T) {
	capture := image.NewRGBA(image.Rect(0, 0, 1920, 2160))
	draw.Draw(capture, image.Rect(0, 0, 1920, 1080), image.NewUniform(color.RGBA{R: 255, A: 255}), image.Point{}, draw.Src)
	draw.Draw(capture, image.Rect(0, 1080, 1920, 2160), image.NewUniform(color.RGBA{B: 255, A: 255}), image.Point{}, draw.Src)

	upper, lower, err := splitDualScreenCapture(capture)
	if err != nil {
		t.Fatalf("splitDualScreenCapture returned error: %v", err)
	}
	if upper.Bounds().Dx() != 1920 || upper.Bounds().Dy() != 1080 {
		t.Fatalf("upper screen has bounds %v", upper.Bounds())
	}
	if lower.Bounds().Dx() != 1920 || lower.Bounds().Dy() != 1080 {
		t.Fatalf("lower screen has bounds %v", lower.Bounds())
	}
	if upper.At(960, 540) != (color.RGBA{R: 255, A: 255}) {
		t.Fatalf("upper screen does not contain the upper capture: %v", upper.At(960, 540))
	}
	if lower.At(960, 540) != (color.RGBA{B: 255, A: 255}) {
		t.Fatalf("lower screen does not contain the matching lower capture: %v", lower.At(960, 540))
	}
}

func TestSplitDualScreenCaptureRejectsSeparateDisplayImages(t *testing.T) {
	_, _, err := splitDualScreenCapture(image.NewRGBA(image.Rect(0, 0, 1920, 1080)))
	if err == nil || !strings.Contains(err.Error(), "1920x2160 dual-screen capture") {
		t.Fatalf("splitDualScreenCapture accepted a single-display image: %v", err)
	}
}
