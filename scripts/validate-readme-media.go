package main

import (
	"fmt"
	"image"
	"image/color"
	_ "image/png"
	"os"
	"regexp"
	"strings"
)

type visualRegion struct {
	name       string
	area       image.Rectangle
	windowSize int
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

	fmt.Printf("validated %d dual-screen screenshot(s) with visible battle sprites on both sides\n", len(paths))
}

func validateReadme(readme string) error {
	for _, asset := range []string{"media/showdown-battle-hd.png", "media/showdown-switch-hd.png"} {
		if !strings.Contains(readme, asset) {
			return fmt.Errorf("README.md does not embed %s", asset)
		}
	}
	if len(readmeScreenshotPaths(readme)) == 0 {
		return fmt.Errorf("README.md does not embed any PNG screenshots")
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

	if decoded.Bounds().Dx() != 1920 || decoded.Bounds().Dy() != 2160 {
		return fmt.Errorf("%s must be 1920x2160, got %dx%d", path, decoded.Bounds().Dx(), decoded.Bounds().Dy())
	}

	regions := []visualRegion{
		{name: "player side", area: image.Rect(250, 350, 900, 1050), windowSize: 220},
		{name: "opponent side", area: image.Rect(1050, 150, 1600, 700), windowSize: 200},
	}
	for _, region := range regions {
		score := focusedVisualScore(decoded, region.area, region.windowSize)
		if score < 0.04 {
			return fmt.Errorf("%s has no visible battle sprite on the %s (score %.3f)", path, region.name, score)
		}
	}

	if strings.HasSuffix(strings.ToLower(path), "showdown-switch-hd.png") {
		teamPreviewRegions := []visualRegion{
			{name: "player team preview", area: image.Rect(390, 1310, 650, 1600), windowSize: 120},
			{name: "opponent team preview", area: image.Rect(950, 1310, 1260, 1600), windowSize: 120},
		}
		for _, region := range teamPreviewRegions {
			score := focusedVisualScore(decoded, region.area, region.windowSize)
			if score < 0.1 {
				return fmt.Errorf("%s has no visible team-preview Pokémon on the %s (score %.3f)", path, region.name, score)
			}
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
			colorful := max3(pixel.r, pixel.g, pixel.b)-min3(pixel.r, pixel.g, pixel.b) > 42 && max3(pixel.r, pixel.g, pixel.b) > 90
			if edge && colorful {
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

func max3(first, second, third uint8) uint8 {
	return max(first, max(second, third))
}

func min3(first, second, third uint8) uint8 {
	return minByte(first, minByte(second, third))
}

func max(first, second uint8) uint8 {
	if first > second {
		return first
	}
	return second
}

func maxFloat(first, second float64) float64 {
	if first > second {
		return first
	}
	return second
}

func minByte(first, second uint8) uint8 {
	if first < second {
		return first
	}
	return second
}
