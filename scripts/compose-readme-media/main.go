package main

import (
	"fmt"
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"os"
	"path/filepath"
)

func main() {
	var liveBattle image.Image
	var partyView image.Image
	if len(os.Args) == 4 {
		upper := readImage(os.Args[1])
		moveChoiceLower := readImage(os.Args[2])
		partyLower := readImage(os.Args[3])
		var err error
		liveBattle, err = composeDualScreenPanels(upper, moveChoiceLower)
		if err != nil {
			fail(fmt.Errorf("live battle panels: %w", err))
		}
		partyView, err = composeDualScreenPanels(upper, partyLower)
		if err != nil {
			fail(fmt.Errorf("party view panels: %w", err))
		}
	} else if len(os.Args) == 3 {
		liveBattle = readImage(os.Args[1])
		partyView = readImage(os.Args[2])
	} else {
		fail(fmt.Errorf("usage: go run scripts/compose-readme-media/main.go upper.png move-choice-lower.png party-lower.png"))
	}
	writeReadmeMedia(liveBattle, partyView)
}

func writeReadmeMedia(liveBattle image.Image, partyView image.Image) {
	liveUpper, liveLower, err := splitDualScreenCapture(liveBattle)
	if err != nil {
		fail(fmt.Errorf("live battle capture: %w", err))
	}
	partyUpper, partyLower, err := splitDualScreenCapture(partyView)
	if err != nil {
		fail(fmt.Errorf("party view capture: %w", err))
	}
	writeImage("media/showdown-battle-upper-screen-hd.png", liveUpper)
	writeImage("media/showdown-battle-lower-screen-hd.png", liveLower)
	writeImage("media/showdown-battle-hd-both-sides.png", liveBattle)
	writeImage("media/showdown-battle-party-upper-screen-hd.png", partyUpper)
	writeImage("media/showdown-battle-party-screen-hd.png", partyLower)
	writeImage("media/showdown-battle-party-both-sides.png", partyView)
	writeImage("media/validation/showdown-battle-player.png", crop(liveUpper, image.Rect(480, 520, 800, 955)))
	writeImage("media/validation/showdown-battle-opponent.png", crop(liveUpper, image.Rect(1138, 335, 1394, 480)))
}

func composeDualScreenPanels(upper image.Image, lower image.Image) (image.Image, error) {
	if upper.Bounds().Dx() != 1920 || upper.Bounds().Dy() != 1080 {
		return nil, fmt.Errorf("upper display must be 1920x1080, got %dx%d", upper.Bounds().Dx(), upper.Bounds().Dy())
	}
	if lower.Bounds().Dx() != 1240 || lower.Bounds().Dy() != 1080 {
		return nil, fmt.Errorf("lower display must be a 1240x1080 lower display, got %dx%d", lower.Bounds().Dx(), lower.Bounds().Dy())
	}
	composite := image.NewRGBA(image.Rect(0, 0, 1920, 2160))
	draw.Draw(composite, composite.Bounds(), image.NewUniform(color.RGBA{A: 255}), image.Point{}, draw.Src)
	draw.Draw(composite, image.Rect(0, 0, 1920, 1080), upper, upper.Bounds().Min, draw.Src)
	lowerOrigin := image.Pt((1920-lower.Bounds().Dx())/2, 1080)
	draw.Draw(
		composite,
		image.Rectangle{Min: lowerOrigin, Max: lowerOrigin.Add(lower.Bounds().Size())},
		lower,
		lower.Bounds().Min,
		draw.Src,
	)
	return composite, nil
}

func readImage(path string) image.Image {
	file, err := os.Open(path)
	if err != nil {
		fail(fmt.Errorf("open %s: %w", path, err))
	}
	defer file.Close()
	decoded, err := png.Decode(file)
	if err != nil {
		fail(fmt.Errorf("decode %s: %w", path, err))
	}
	return decoded
}

func splitDualScreenCapture(source image.Image) (image.Image, image.Image, error) {
	bounds := source.Bounds()
	if bounds.Dx() != 1920 || bounds.Dy() != 2160 {
		return nil, nil, fmt.Errorf("must be a 1920x2160 dual-screen capture, got %dx%d", bounds.Dx(), bounds.Dy())
	}
	upperBounds := image.Rect(bounds.Min.X, bounds.Min.Y, bounds.Min.X+1920, bounds.Min.Y+1080)
	lowerBounds := image.Rect(bounds.Min.X, bounds.Min.Y+1080, bounds.Min.X+1920, bounds.Min.Y+2160)
	return crop(source, upperBounds), crop(source, lowerBounds), nil
}

func crop(source image.Image, bounds image.Rectangle) image.Image {
	result := image.NewRGBA(image.Rect(0, 0, bounds.Dx(), bounds.Dy()))
	draw.Draw(result, result.Bounds(), source, bounds.Min, draw.Src)
	return result
}

func writeImage(path string, source image.Image) {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		fail(fmt.Errorf("create %s: %w", filepath.Dir(path), err))
	}
	file, err := os.Create(path)
	if err != nil {
		fail(fmt.Errorf("create %s: %w", path, err))
	}
	if err := png.Encode(file, source); err != nil {
		file.Close()
		fail(fmt.Errorf("encode %s: %w", path, err))
	}
	if err := file.Close(); err != nil {
		fail(fmt.Errorf("close %s: %w", path, err))
	}
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
