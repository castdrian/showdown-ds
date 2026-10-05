package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"image"
	"image/color"
	_ "image/png"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
)

type visualRegion struct {
	name       string
	area       image.Rectangle
	windowSize int
}

type spriteTemplate struct {
	name   string
	path   string
	origin image.Point
}

func main() {
	readme, err := os.ReadFile("README.md")
	if err != nil {
		fmt.Fprintln(os.Stderr, fmt.Errorf("read README.md: %w", err))
		os.Exit(1)
	}
	if err := validateReadme(string(readme)); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	paths := os.Args[1:]
	if len(paths) == 0 {
		paths = readmeScreenshotPaths(string(readme))
	}

	for _, path := range paths {
		if err := validateScreenshot(path); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	}

	fmt.Printf("validated %d corresponding README dual-screen capture(s) with both battle Pokémon visible on the upper display\n", len(paths))
}

func validateReadme(readme string) error {
	assets := []string{
		"media/showdown-battle-hd-both-sides.png",
		"media/showdown-battle-party-both-sides.png",
	}
	for _, asset := range assets {
		if !strings.Contains(readme, asset) {
			return fmt.Errorf("README.md does not embed %s", asset)
		}
	}
	if len(readmeScreenshotPaths(readme)) != len(assets) {
		return fmt.Errorf("README.md must embed both matching battle screenshots")
	}
	pairs := []struct {
		upper  string
		lower  string
		source string
	}{
		{
			upper:  "media/showdown-battle-upper-screen-hd.png",
			lower:  "media/showdown-battle-lower-screen-hd.png",
			source: assets[0],
		},
		{
			upper:  "media/showdown-battle-upper-screen-hd.png",
			lower:  "media/showdown-battle-party-screen-hd.png",
			source: assets[1],
		},
	}
	for _, pair := range pairs {
		if err := validateReadmeScreenPair(
			repositoryFile(pair.upper),
			repositoryFile(pair.lower),
			repositoryFile(pair.source),
		); err != nil {
			return err
		}
	}
	if err := validateLiveMoveChoiceScreen(repositoryFile(pairs[0].lower)); err != nil {
		return err
	}
	return validateReadmeImageCacheKeys(readme)
}

func validateLiveMoveChoiceScreen(path string) error {
	screenshot, err := decodeScreenshot(path)
	if err != nil {
		return err
	}
	if screenshot.Bounds().Dx() != 1920 || screenshot.Bounds().Dy() != 1080 {
		return fmt.Errorf("%s must be a 1920x1080 lower-display capture", path)
	}
	fightTabGreen := averageGreen(screenshot, image.Rect(388, 116, 402, 156))
	pokemonTabGreen := averageGreen(screenshot, image.Rect(684, 116, 698, 156))
	if fightTabGreen-pokemonTabGreen < 30 {
		return fmt.Errorf("%s must show the live Fight move-choice screen, not replay controls or another battle tab", path)
	}
	return nil
}

func averageGreen(source image.Image, area image.Rectangle) float64 {
	area = area.Intersect(source.Bounds())
	if area.Empty() {
		return 0
	}
	total := 0
	for y := area.Min.Y; y < area.Max.Y; y++ {
		for x := area.Min.X; x < area.Max.X; x++ {
			total += int(rgba(source.At(x, y)).g)
		}
	}
	return float64(total) / float64(area.Dx()*area.Dy())
}

func validateReadmeScreenPair(upperPath string, lowerPath string, sourcePath string) error {
	upper, err := decodeScreenshot(upperPath)
	if err != nil {
		return err
	}
	lower, err := decodeScreenshot(lowerPath)
	if err != nil {
		return err
	}
	source, err := decodeScreenshot(sourcePath)
	if err != nil {
		return err
	}
	if source.Bounds().Dx() != 1920 || source.Bounds().Dy() != 2160 {
		return fmt.Errorf("%s must be the 1920x2160 source capture for both screens", sourcePath)
	}
	if !matchesImageRegion(source, upper, image.Rect(0, 0, 1920, 1080)) {
		return fmt.Errorf("%s is not the upper screen from %s", upperPath, sourcePath)
	}
	if !matchesImageRegion(source, lower, image.Rect(0, 1080, 1920, 2160)) {
		return fmt.Errorf("%s is not the corresponding lower screen from %s", lowerPath, sourcePath)
	}
	return nil
}

func decodeScreenshot(path string) (image.Image, error) {
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

func matchesImageRegion(source image.Image, candidate image.Image, region image.Rectangle) bool {
	if !region.In(source.Bounds()) || candidate.Bounds().Size() != region.Size() {
		return false
	}
	for y := 0; y < region.Dy(); y++ {
		for x := 0; x < region.Dx(); x++ {
			if rgba(source.At(region.Min.X+x, region.Min.Y+y)) != rgba(candidate.At(candidate.Bounds().Min.X+x, candidate.Bounds().Min.Y+y)) {
				return false
			}
		}
	}
	return true
}

func validateReadmeImageCacheKeys(readme string) error {
	imagePattern := regexp.MustCompile(`(?i)<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>`)
	for _, match := range imagePattern.FindAllStringSubmatch(readme, -1) {
		parts := strings.SplitN(match[1], "?", 2)
		if len(parts) != 2 {
			return fmt.Errorf("README screenshot %s is missing its content cache key", match[1])
		}
		query, err := url.ParseQuery(parts[1])
		if err != nil {
			return fmt.Errorf("parse cache key for %s: %w", parts[0], err)
		}
		cacheKey := query.Get("v")
		if cacheKey == "" {
			return fmt.Errorf("README screenshot %s is missing its content cache key", parts[0])
		}
		if len(cacheKey) < 12 {
			return fmt.Errorf("README screenshot %s needs a SHA-256 cache key of at least 12 characters", parts[0])
		}
		contents, err := os.ReadFile(repositoryFile(parts[0]))
		if err != nil {
			return fmt.Errorf("read README screenshot %s: %w", parts[0], err)
		}
		checksum := sha256.Sum256(contents)
		if !strings.HasPrefix(hex.EncodeToString(checksum[:]), cacheKey) {
			return fmt.Errorf("README screenshot %s has a stale content cache key", parts[0])
		}
	}
	return nil
}

func readmeScreenshotPaths(readme string) []string {
	pattern := regexp.MustCompile(`(?:src\s*=\s*["']|!\[[^\]]*\]\()\s*(media/[^"' )>]+\.png)`)
	seen := map[string]bool{}
	paths := make([]string, 0)
	for _, match := range pattern.FindAllStringSubmatch(readme, -1) {
		path := match[1]
		if !seen[path] {
			seen[path] = true
			paths = append(paths, path)
		}
	}
	return paths
}

func validateScreenshot(path string) error {
	file, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("open %s: %w", path, err)
	}
	defer file.Close()

	decoded, format, err := image.Decode(file)
	if err != nil {
		return fmt.Errorf("decode %s: %w", path, err)
	}
	if format != "png" {
		return fmt.Errorf("%s must be PNG, got %s", path, format)
	}

	expectedHeight := 1080
	if strings.HasSuffix(path, "showdown-battle-hd-both-sides.png") || strings.HasSuffix(path, "showdown-battle-party-both-sides.png") {
		expectedHeight = 2160
	}
	if decoded.Bounds().Dx() != 1920 || decoded.Bounds().Dy() != expectedHeight {
		return fmt.Errorf("%s must be 1920x%d, got %dx%d", path, expectedHeight, decoded.Bounds().Dx(), decoded.Bounds().Dy())
	}

	templates := spriteTemplates(path)
	if len(templates) == 0 && !strings.HasSuffix(path, "showdown-battle-lower-screen-hd.png") {
		return fmt.Errorf("%s has no battle sprite templates", path)
	}
	for _, template := range templates {
		if err := compareSpriteTemplate(decoded, template); err != nil {
			return fmt.Errorf("%s %s: %w", path, template.name, err)
		}
	}

	return nil
}

func spriteTemplates(path string) []spriteTemplate {
	switch {
	case strings.HasSuffix(path, "showdown-battle-upper-screen-hd.png"), strings.HasSuffix(path, "showdown-battle-hd-both-sides.png"), strings.HasSuffix(path, "showdown-battle-party-both-sides.png"):
		return []spriteTemplate{
			{name: "player side", path: repositoryFile("media/validation/showdown-battle-player.png"), origin: image.Pt(480, 520)},
			{name: "opponent side", path: repositoryFile("media/validation/showdown-battle-opponent.png"), origin: image.Pt(1138, 335)},
		}
	default:
		return nil
	}
}

func repositoryFile(relative string) string {
	_, source, _, _ := runtime.Caller(0)
	return filepath.Clean(filepath.Join(filepath.Dir(source), "..", relative))
}

func compareSpriteTemplate(source image.Image, template spriteTemplate) error {
	file, err := os.Open(template.path)
	if err != nil {
		return fmt.Errorf("open template %s: %w", template.path, err)
	}
	defer file.Close()

	decoded, format, err := image.Decode(file)
	if err != nil {
		return fmt.Errorf("decode template %s: %w", template.path, err)
	}
	if format != "png" {
		return fmt.Errorf("template %s must be PNG, got %s", template.path, format)
	}
	if focusedVisualScore(decoded, decoded.Bounds(), 120) < 0.1 {
		return fmt.Errorf("template %s contains no visible sprite evidence", template.path)
	}

	area := image.Rectangle{Min: template.origin, Max: template.origin.Add(decoded.Bounds().Size())}
	if !area.In(source.Bounds()) {
		return fmt.Errorf("template area %s is outside the screenshot", area)
	}

	inspected := 0
	difference := 0
	for y := 0; y < decoded.Bounds().Dy(); y += 4 {
		for x := 0; x < decoded.Bounds().Dx(); x += 4 {
			first := rgba(source.At(template.origin.X+x, template.origin.Y+y))
			second := rgba(decoded.At(decoded.Bounds().Min.X+x, decoded.Bounds().Min.Y+y))
			difference += abs(int(first.r)-int(second.r)) + abs(int(first.g)-int(second.g)) + abs(int(first.b)-int(second.b))
			inspected++
		}
	}
	if inspected == 0 {
		return fmt.Errorf("template %s is empty", template.path)
	}
	normalizedDifference := float64(difference) / float64(inspected*3*255)
	if normalizedDifference > 0.03 {
		sourceArea := image.Rectangle{Min: template.origin, Max: template.origin.Add(decoded.Bounds().Size())}
		if focusedVisualScore(source, sourceArea, 120) < 0.1 {
			return fmt.Errorf("no sprite evidence in refreshed screenshot")
		}
	}
	return nil
}

func focusedVisualScore(source image.Image, area image.Rectangle, windowSize int) float64 {
	area = area.Intersect(source.Bounds())
	if area.Empty() {
		return 0
	}
	windowSize = min(windowSize, min(area.Dx(), area.Dy()))
	best := 0.0
	step := windowSize / 6
	if step < 12 {
		step = 12
	}
	for y := area.Min.Y; y+windowSize <= area.Max.Y; y += step {
		for x := area.Min.X; x+windowSize <= area.Max.X; x += step {
			best = maxFloat(best, visualScore(source, image.Rect(x, y, x+windowSize, y+windowSize)))
		}
	}
	return best
}

func visualScore(source image.Image, area image.Rectangle) float64 {
	area = area.Intersect(source.Bounds())
	if area.Empty() {
		return 0
	}

	inspected := 0
	foreground := 0
	for y := area.Min.Y; y < area.Max.Y; y += 3 {
		for x := area.Min.X; x < area.Max.X; x += 3 {
			pixel := rgba(source.At(x, y))
			right := rgba(source.At(min(x+2, area.Max.X-1), y))
			down := rgba(source.At(x, min(y+2, area.Max.Y-1)))
			edge := colorDistance(pixel, right) > 48 || colorDistance(pixel, down) > 48
			if edge {
				foreground++
			}
			inspected++
		}
	}
	if inspected == 0 {
		return 0
	}
	return float64(foreground) / float64(inspected)
}

type rgbaPixel struct {
	r uint8
	g uint8
	b uint8
}

func rgba(value color.Color) rgbaPixel {
	r, g, b, _ := value.RGBA()
	return rgbaPixel{uint8(r >> 8), uint8(g >> 8), uint8(b >> 8)}
}

func colorDistance(first rgbaPixel, second rgbaPixel) int {
	return abs(int(first.r)-int(second.r)) + abs(int(first.g)-int(second.g)) + abs(int(first.b)-int(second.b))
}

func abs(value int) int {
	if value < 0 {
		return -value
	}
	return value
}

func min(value, other int) int {
	if value < other {
		return value
	}
	return other
}

func maxFloat(first, second float64) float64 {
	if first > second {
		return first
	}
	return second
}
