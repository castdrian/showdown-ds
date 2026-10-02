package main

import (
	"fmt"
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"os"
)

const (
	upperWidth  = 1920
	upperHeight = 1080
	lowerWidth  = 1240
	lowerHeight = 1080
)

func main() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "usage: compose-thor-screenshot <upper.png> <lower.png> <output.png>")
		os.Exit(2)
	}

	upper, err := readPNG(os.Args[1])
	if err != nil {
		fail(err)
	}
	lower, err := readPNG(os.Args[2])
	if err != nil {
		fail(err)
	}
	composite, err := composeThorScreenshot(upper, lower)
	if err != nil {
		fail(err)
	}
	output, err := os.Create(os.Args[3])
	if err != nil {
		fail(fmt.Errorf("create output image: %w", err))
	}
	if err := png.Encode(output, composite); err != nil {
		output.Close()
		fail(fmt.Errorf("encode output image: %w", err))
	}
	if err := output.Close(); err != nil {
		fail(fmt.Errorf("close output image: %w", err))
	}
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}

func composeThorScreenshot(upper, lower image.Image) (*image.RGBA, error) {
	if upper.Bounds().Dx() != upperWidth || upper.Bounds().Dy() != upperHeight {
		return nil, fmt.Errorf("upper screenshot must be %dx%d, got %dx%d", upperWidth, upperHeight, upper.Bounds().Dx(), upper.Bounds().Dy())
	}
	if lower.Bounds().Dx() != lowerWidth || lower.Bounds().Dy() != lowerHeight {
		return nil, fmt.Errorf("lower screenshot must be %dx%d, got %dx%d", lowerWidth, lowerHeight, lower.Bounds().Dx(), lower.Bounds().Dy())
	}

	canvas := image.NewRGBA(image.Rect(0, 0, upperWidth, upperHeight+lowerHeight))
	draw.Draw(canvas, canvas.Bounds(), image.NewUniform(color.RGBA{A: 255}), image.Point{}, draw.Src)
	draw.Draw(canvas, image.Rect(0, 0, upperWidth, upperHeight), upper, upper.Bounds().Min, draw.Src)
	lowerLeft := (upperWidth - lowerWidth) / 2
	draw.Draw(canvas, image.Rect(lowerLeft, upperHeight, lowerLeft+lowerWidth, upperHeight+lowerHeight), lower, lower.Bounds().Min, draw.Src)
	return canvas, nil
}

func readPNG(path string) (image.Image, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	defer file.Close()

	decoded, format, err := image.Decode(file)
	if err != nil {
		return nil, fmt.Errorf("decode %s: %w", path, err)
	}
	if format != "png" {
		return nil, fmt.Errorf("%s must be PNG, got %s", path, format)
	}
	return decoded, nil
}
