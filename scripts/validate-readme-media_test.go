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

func TestFreshBackgroundTemplatesRejectMissingBattlePokemon(t *testing.T) {
	upperPath := "../media/showdown-battle-upper-screen-hd.png"
	upper, err := decodeScreenshot(upperPath)
	if err != nil {
		t.Fatal(err)
	}
	for _, test := range []struct {
		name           string
		area           image.Rectangle
		backgroundFrom image.Point
	}{
		{name: "player side", area: image.Rect(500, 470, 780, 980), backgroundFrom: image.Pt(480, 450)},
		{name: "opponent side", area: image.Rect(1120, 230, 1470, 570), backgroundFrom: image.Pt(900, 180)},
	} {
		t.Run(test.name, func(t *testing.T) {
			missing := image.NewNRGBA(upper.Bounds())
			draw.Draw(missing, missing.Bounds(), upper, upper.Bounds().Min, draw.Src)
			draw.Draw(missing, test.area, image.NewUniform(upper.At(test.backgroundFrom.X, test.backgroundFrom.Y)), image.Point{}, draw.Src)
			templatePath := filepath.Join(t.TempDir(), "background-only.png")
			templateImage := image.NewNRGBA(image.Rect(0, 0, test.area.Dx(), test.area.Dy()))
			draw.Draw(templateImage, templateImage.Bounds(), missing, test.area.Min, draw.Src)
			templateFile, err := os.Create(templatePath)
			if err != nil {
				t.Fatal(err)
			}
			if err := png.Encode(templateFile, templateImage); err != nil {
				templateFile.Close()
				t.Fatal(err)
			}
			if err := templateFile.Close(); err != nil {
				t.Fatal(err)
			}
			err = compareSpriteTemplate(missing, spriteTemplate{name: test.name, path: templatePath, origin: test.area.Min})
			if err == nil || !strings.Contains(err.Error(), "contains no visible sprite evidence") {
				t.Fatalf("compareSpriteTemplate accepted a background-only %s template: %v", test.name, err)
			}
		})
	}
}

func TestREADMEAssetsShowCorrespondingBattleScreens(t *testing.T) {
	for _, sourcePath := range []string{
		"../media/showdown-battle-live-both-sides.png",
		"../media/showdown-battle-party-live-both-sides.png",
	} {
		if err := validateScreenshot(sourcePath); err != nil {
			t.Fatalf("validateScreenshot(%q): %v", sourcePath, err)
		}
	}
	for _, pair := range []struct {
		upperPath  string
		lowerPath  string
		sourcePath string
	}{
		{
			upperPath:  "../media/showdown-battle-upper-screen-hd.png",
			lowerPath:  "../media/showdown-battle-lower-screen-hd.png",
			sourcePath: "../media/showdown-battle-live-both-sides.png",
		},
		{
			upperPath:  "../media/showdown-battle-party-upper-screen-hd.png",
			lowerPath:  "../media/showdown-battle-party-screen-hd.png",
			sourcePath: "../media/showdown-battle-party-live-both-sides.png",
		},
	} {
		if err := validateReadmeScreenPair(
			pair.upperPath,
			pair.lowerPath,
			pair.sourcePath,
		); err != nil {
			t.Fatalf("validateReadmeScreenPair rejected corresponding screenshots: %v", err)
		}
	}
}

func TestREADMEPrimaryScreenshotShowsLiveMoveChoice(t *testing.T) {
	if err := validateLiveMoveChoiceScreen("../media/showdown-battle-lower-screen-hd.png"); err != nil {
		t.Fatalf("validateLiveMoveChoiceScreen rejected the active battle screen: %v", err)
	}
	if err := validateLiveMoveChoiceScreen("../media/showdown-battle-party-screen-hd.png"); err == nil {
		t.Fatal("validateLiveMoveChoiceScreen accepted the party screen as the primary move-choice screenshot")
	}
}

func TestREADMEPrimaryScreenshotRejectsMissingMoveCards(t *testing.T) {
	sourcePath := "../media/showdown-battle-lower-screen-hd.png"
	file, err := os.Open(sourcePath)
	if err != nil {
		t.Fatal(err)
	}
	source, _, err := image.Decode(file)
	file.Close()
	if err != nil {
		t.Fatal(err)
	}
	modified := image.NewNRGBA(source.Bounds())
	draw.Draw(modified, modified.Bounds(), source, source.Bounds().Min, draw.Src)
	draw.Draw(modified, image.Rect(820, 188, 1544, 840), image.NewUniform(color.NRGBA{R: 4, G: 15, B: 24, A: 255}), image.Point{}, draw.Src)
	path := filepath.Join(t.TempDir(), "missing-move-cards.png")
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
	if err := validateLiveMoveChoiceScreen(path); err == nil || !strings.Contains(err.Error(), "move card") {
		t.Fatalf("validateLiveMoveChoiceScreen accepted a battle screen without move cards: %v", err)
	}
}

func TestREADMEPartyScreenshotShowsPartyTab(t *testing.T) {
	if err := validatePartyRosterScreen("../media/showdown-battle-party-screen-hd.png"); err != nil {
		t.Fatalf("validatePartyRosterScreen rejected the party screen: %v", err)
	}
	if err := validatePartyRosterScreen("../media/showdown-battle-lower-screen-hd.png"); err == nil {
		t.Fatal("validatePartyRosterScreen accepted the live Fight screen")
	}
}

func TestREADMEAssetsRejectMismatchedBattleScreens(t *testing.T) {
	upperPath := "../media/showdown-battle-upper-screen-hd.png"
	lowerPath := "../media/showdown-battle-lower-screen-hd.png"
	sourcePath := "../media/showdown-battle-live-both-sides.png"
	lowerFile, err := os.Open(lowerPath)
	if err != nil {
		t.Fatal(err)
	}
	lower, _, err := image.Decode(lowerFile)
	lowerFile.Close()
	if err != nil {
		t.Fatal(err)
	}
	modified := image.NewNRGBA(lower.Bounds())
	draw.Draw(modified, modified.Bounds(), lower, lower.Bounds().Min, draw.Src)
	modified.Set(650, 40, color.NRGBA{R: 255, G: 0, B: 0, A: 255})
	modifiedPath := filepath.Join(t.TempDir(), "showdown-battle-lower-screen-hd.png")
	modifiedFile, err := os.Create(modifiedPath)
	if err != nil {
		t.Fatal(err)
	}
	if err := png.Encode(modifiedFile, modified); err != nil {
		modifiedFile.Close()
		t.Fatal(err)
	}
	if err := modifiedFile.Close(); err != nil {
		t.Fatal(err)
	}
	if err := validateReadmeScreenPair(upperPath, modifiedPath, sourcePath); err == nil || !strings.Contains(err.Error(), "corresponding lower screen") {
		t.Fatalf("validateReadmeScreenPair accepted a lower screen from a different battle: %v", err)
	}
}

func TestREADMEAssetsRequireFreshImageCacheKeys(t *testing.T) {
	readme, err := os.ReadFile("../README.md")
	if err != nil {
		t.Fatal(err)
	}
	if err := validateReadmeImageCacheKeys(string(readme)); err != nil {
		t.Fatalf("validateReadmeImageCacheKeys rejected current screenshot hashes: %v", err)
	}
	stale := strings.Replace(string(readme), "?v=", "?v=000000000000", 1)
	if err := validateReadmeImageCacheKeys(stale); err == nil || !strings.Contains(err.Error(), "stale content cache key") {
		t.Fatalf("validateReadmeImageCacheKeys accepted a stale screenshot hash: %v", err)
	}
	missing := strings.Replace(string(readme), "?v=", "?", 1)
	if err := validateReadmeImageCacheKeys(missing); err == nil || !strings.Contains(err.Error(), "missing its content cache key") {
		t.Fatalf("validateReadmeImageCacheKeys accepted a screenshot without a hash: %v", err)
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
		{name: "missing player sprite", origin: image.Pt(420, 350), side: "player side", source: "../media/showdown-battle-upper-screen-hd.png"},
		{name: "missing opponent sprite", origin: image.Pt(1050, 150), side: "opponent side", source: "../media/showdown-battle-upper-screen-hd.png"},
		{name: "player sprite replaced by background", origin: image.Pt(420, 350), copyBackground: true, backgroundOrigin: image.Pt(1380, 300), side: "player side", source: "../media/showdown-battle-upper-screen-hd.png"},
		{name: "opponent sprite replaced by background", origin: image.Pt(1050, 150), copyBackground: true, backgroundOrigin: image.Pt(1440, 150), side: "opponent side", source: "../media/showdown-battle-upper-screen-hd.png"},
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
			spriteArea := image.Rectangle{Min: test.origin, Max: test.origin.Add(image.Pt(480, 700))}
			if test.copyBackground {
				backgroundColor := source.At(test.backgroundOrigin.X, test.backgroundOrigin.Y)
				draw.Draw(modified, spriteArea, image.NewUniform(backgroundColor), image.Point{}, draw.Src)
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
