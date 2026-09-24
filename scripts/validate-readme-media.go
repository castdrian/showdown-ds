package main

import (
	"fmt"
	"image"
	"image/color"
	_ "image/png"
	"os"
	"strings"
)

type visualRegion struct {
	name string
	area image.Rectangle
}

func main() {
	if err := validateReadme(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	paths := os.Args[1:]
	if len(paths) == 0 {
		paths = []string{"media/showdown-battle-hd.png", "media/showdown-switch-hd.png"}
	}

	for _, path := range paths {
		if err := validateScreenshot(path); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	}

	fmt.Printf("validated %d dual-screen screenshot(s) with visible battle sprites on both sides\n", len(paths))
}

func validateReadme() error {
	readme, err := os.ReadFile("README.md")
	if err != nil {
		return fmt.Errorf("read README.md: %w", err)
	}
	for _, asset := range []string{"media/showdown-battle-hd.png", "media/showdown-switch-hd.png"} {
		if !strings.Contains(string(readme), asset) {
			return fmt.Errorf("README.md does not embed %s", asset)
		}
	}
	return nil
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
		{name: "player side", area: image.Rect(250, 260, 900, 900)},
		{name: "opponent side", area: image.Rect(820, 80, 1180, 680)},
	}
	for _, region := range regions {
		score := visualScore(decoded, region.area)
		if score < 0.08 {
			return fmt.Errorf("%s has no visible foreground in the %s capture region (score %.3f)", path, region.name, score)
		}
	}

	return nil
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
			if edge || colorful {
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

func minByte(first, second uint8) uint8 {
	if first < second {
		return first
	}
	return second
}
