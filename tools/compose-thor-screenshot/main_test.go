package main

import (
	"image"
	"image/color"
	"image/draw"
	"testing"
)

func TestComposeThorScreenshotCentersNativeLowerDisplay(t *testing.T) {
	upperColor := color.RGBA{R: 20, G: 40, B: 60, A: 255}
	lowerColor := color.RGBA{R: 90, G: 110, B: 130, A: 255}
	upper := image.NewRGBA(image.Rect(0, 0, upperWidth, upperHeight))
	lower := image.NewRGBA(image.Rect(0, 0, lowerWidth, lowerHeight))
	draw.Draw(upper, upper.Bounds(), image.NewUniform(upperColor), image.Point{}, draw.Src)
	draw.Draw(lower, lower.Bounds(), image.NewUniform(lowerColor), image.Point{}, draw.Src)

	composite, err := composeThorScreenshot(upper, lower)
	if err != nil {
		t.Fatal(err)
	}
	if composite.Bounds() != image.Rect(0, 0, upperWidth, upperHeight+lowerHeight) {
		t.Fatalf("unexpected bounds: %v", composite.Bounds())
	}
	if got := composite.RGBAAt(0, 0); got != upperColor {
		t.Fatalf("upper display pixel = %v, want %v", got, upperColor)
	}
	lowerLeft := (upperWidth - lowerWidth) / 2
	if got := composite.RGBAAt(lowerLeft, upperHeight); got != lowerColor {
		t.Fatalf("lower display pixel = %v, want %v", got, lowerColor)
	}
	if got := composite.RGBAAt(lowerLeft-1, upperHeight); got != (color.RGBA{A: 255}) {
		t.Fatalf("left margin pixel = %v, want opaque black", got)
	}
}

func TestComposeThorScreenshotRejectsIncorrectDisplayDimensions(t *testing.T) {
	validUpper := image.NewRGBA(image.Rect(0, 0, upperWidth, upperHeight))
	validLower := image.NewRGBA(image.Rect(0, 0, lowerWidth, lowerHeight))

	for _, test := range []struct {
		name  string
		upper image.Image
		lower image.Image
	}{
		{name: "upper", upper: image.NewRGBA(image.Rect(0, 0, upperWidth-1, upperHeight)), lower: validLower},
		{name: "lower", upper: validUpper, lower: image.NewRGBA(image.Rect(0, 0, lowerWidth, lowerHeight-1))},
	} {
		t.Run(test.name, func(t *testing.T) {
			if _, err := composeThorScreenshot(test.upper, test.lower); err == nil {
				t.Fatal("composeThorScreenshot accepted an incorrect display size")
			}
		})
	}
}
