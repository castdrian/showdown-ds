package main

import (
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"os"
	"path/filepath"
	"testing"
)

func TestREADMEAssetsContainBothBattleSprites(t *testing.T) {
	for _, path := range []string{"../media/showdown-battle-hd-both-sides.png", "../media/showdown-switch-hd-both-sides.png"} {
		if err := validateScreenshot(path); err != nil {
			t.Fatalf("validateScreenshot(%q): %v", path, err)
		}
	}
}

func TestREADMEAssetsRejectMissingBattleSprites(t *testing.T) {
	for _, test := range []struct {
		name   string
		origin image.Point
		source string
	}{
		{name: "missing player sprite", origin: image.Pt(420, 350), source: "../media/showdown-switch-hd-both-sides.png"},
		{name: "missing opponent sprite", origin: image.Pt(1050, 150), source: "../media/showdown-switch-hd-both-sides.png"},
	} {
		t.Run(test.name, func(t *testing.T) {
			file, err := os.Open(test.source)
			if err != nil {
				t.Fatal(err)
			}
			defer file.Close()
			source, _, err := image.Decode(file)
			if err != nil {
				t.Fatal(err)
			}
			modified := image.NewNRGBA(source.Bounds())
			draw.Draw(modified, modified.Bounds(), source, source.Bounds().Min, draw.Src)
			draw.Draw(modified, image.Rectangle{Min: test.origin, Max: test.origin.Add(image.Pt(480, 550))}, image.NewUniform(color.NRGBA{R: 4, G: 15, B: 24, A: 255}), image.Point{}, draw.Src)
			path := filepath.Join(t.TempDir(), "showdown-switch-hd-both-sides.png")
			output, err := os.Create(path)
			if err != nil {
				t.Fatal(err)
			}
			if err := png.Encode(output, modified); err != nil {
				output.Close()
				t.Fatal(err)
			}
			if err := output.Close(); err != nil {
				t.Fatal(err)
			}
			if err := validateScreenshot(path); err == nil {
				t.Fatal("validateScreenshot accepted a screenshot with a missing battle sprite")
			}
		})
	}
}
