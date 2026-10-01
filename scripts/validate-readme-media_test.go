package main

import (
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"os"
	"path/filepath"
	"strings"
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
		name             string
		origin           image.Point
		copyBackground   bool
		backgroundOrigin image.Point
		side             string
		source           string
	}{
		{name: "missing player sprite", origin: image.Pt(420, 350), side: "player side", source: "../media/showdown-switch-hd-both-sides.png"},
		{name: "missing opponent sprite", origin: image.Pt(1050, 150), side: "opponent side", source: "../media/showdown-switch-hd-both-sides.png"},
		{name: "player sprite replaced by background", origin: image.Pt(420, 350), copyBackground: true, backgroundOrigin: image.Pt(1380, 300), side: "player side", source: "../media/showdown-switch-hd-both-sides.png"},
		{name: "opponent sprite replaced by background", origin: image.Pt(1050, 150), copyBackground: true, backgroundOrigin: image.Pt(1440, 150), side: "opponent side", source: "../media/showdown-switch-hd-both-sides.png"},
		{name: "battle missing player sprite", origin: image.Pt(420, 350), side: "player side", source: "../media/showdown-battle-hd-both-sides.png"},
		{name: "battle missing opponent sprite", origin: image.Pt(1050, 150), side: "opponent side", source: "../media/showdown-battle-hd-both-sides.png"},
		{name: "battle player sprite replaced by background", origin: image.Pt(420, 350), copyBackground: true, backgroundOrigin: image.Pt(1380, 300), side: "player side", source: "../media/showdown-battle-hd-both-sides.png"},
		{name: "battle opponent sprite replaced by background", origin: image.Pt(1050, 150), copyBackground: true, backgroundOrigin: image.Pt(1440, 150), side: "opponent side", source: "../media/showdown-battle-hd-both-sides.png"},
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
			spriteArea := image.Rectangle{Min: test.origin, Max: test.origin.Add(image.Pt(480, 550))}
			if test.copyBackground {
				draw.Draw(modified, spriteArea, source, test.backgroundOrigin, draw.Src)
			} else {
				draw.Draw(modified, spriteArea, image.NewUniform(color.NRGBA{R: 4, G: 15, B: 24, A: 255}), image.Point{}, draw.Src)
			}
			path := filepath.Join(t.TempDir(), filepath.Base(test.source))
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
			err = validateScreenshot(path)
			if err == nil {
				t.Fatal("validateScreenshot accepted a screenshot with a missing battle sprite")
			}
			if !strings.Contains(err.Error(), test.side) {
				t.Fatalf("validateScreenshot rejected the screenshot without identifying the missing %s: %v", test.side, err)
			}
		})
	}
}
