package main

import (
	"fmt"
	"image"
	"image/draw"
	"image/png"
	"os"
	"path/filepath"
)

func main() {
	if len(os.Args) != 3 {
		fail(fmt.Errorf("usage: go run scripts/compose-readme-media/main.go live-battle.png party-view.png"))
	}

	liveBattle := readImage(os.Args[1])
	partyView := readImage(os.Args[2])
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
