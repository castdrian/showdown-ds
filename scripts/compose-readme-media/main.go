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
	if len(os.Args) != 4 {
		fail(fmt.Errorf("usage: go run scripts/compose-readme-media/main.go upper.png fight.png party.png"))
	}

	upper := readImage(os.Args[1])
	fight := readImage(os.Args[2])
	party := readImage(os.Args[3])
	if upper.Bounds().Dx() != 1920 || upper.Bounds().Dy() != 1080 {
		fail(fmt.Errorf("upper display capture must be 1920x1080"))
	}
	if fight.Bounds().Dx() != 1240 || fight.Bounds().Dy() != 1080 {
		fail(fmt.Errorf("fight display capture must be 1240x1080"))
	}
	if party.Bounds().Dx() != 1240 || party.Bounds().Dy() != 1080 {
		fail(fmt.Errorf("party display capture must be 1240x1080"))
	}

	fightScreen := centerLowerDisplay(fight)
	partyScreen := centerLowerDisplay(party)
	writeImage("media/showdown-battle-upper-screen-hd.png", upper)
	writeImage("media/showdown-battle-lower-screen-hd.png", fightScreen)
	writeImage("media/showdown-battle-party-screen-hd.png", partyScreen)
	writeImage("media/showdown-battle-hd-both-sides.png", combineDisplays(upper, fightScreen))
	writeImage("media/showdown-battle-party-both-sides.png", combineDisplays(upper, partyScreen))
	writeImage("media/validation/showdown-battle-player.png", crop(upper, image.Rect(480, 520, 800, 955)))
	writeImage("media/validation/showdown-battle-opponent.png", crop(upper, image.Rect(1138, 335, 1394, 480)))
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

func centerLowerDisplay(source image.Image) image.Image {
	canvas := image.NewRGBA(image.Rect(0, 0, 1920, 1080))
	draw.Draw(canvas, canvas.Bounds(), &image.Uniform{C: color.Black}, image.Point{}, draw.Src)
	draw.Draw(canvas, image.Rect(340, 0, 1580, 1080), source, source.Bounds().Min, draw.Src)
	return canvas
}

func combineDisplays(upper image.Image, lower image.Image) image.Image {
	canvas := image.NewRGBA(image.Rect(0, 0, 1920, 2160))
	draw.Draw(canvas, image.Rect(0, 0, 1920, 1080), upper, upper.Bounds().Min, draw.Src)
	draw.Draw(canvas, image.Rect(0, 1080, 1920, 2160), lower, lower.Bounds().Min, draw.Src)
	return canvas
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
