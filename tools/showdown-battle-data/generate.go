package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	bundleURL       = "https://play.pokemonshowdown.com/data/teambuilder-tables.js"
	bundleMarker    = "exports.BattleTeambuilderTable = JSON.parse('"
	maxBundleBytes  = 32 * 1024 * 1024
	outputAssetPath = "app/src/main/assets/showdown-generation-overrides.json"
)

type speciesOverride struct {
	BaseStats map[string]int    `json:"baseStats,omitempty"`
	Abilities map[string]string `json:"abilities,omitempty"`
}

func main() {
	if err := generate(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func generate() error {
	request, err := http.NewRequest(http.MethodGet, bundleURL, nil)
	if err != nil {
		return fmt.Errorf("create Showdown data request: %w", err)
	}
	request.Header.Set("User-Agent", "showdown-ds-battle-data-generator")
	client := &http.Client{Timeout: 90 * time.Second}
	response, err := client.Do(request)
	if err != nil {
		return fmt.Errorf("download Showdown teambuilder data: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("download Showdown teambuilder data: unexpected HTTP status %s", response.Status)
	}
	bundle, err := io.ReadAll(io.LimitReader(response.Body, maxBundleBytes+1))
	if err != nil {
		return fmt.Errorf("read Showdown teambuilder data: %w", err)
	}
	if len(bundle) > maxBundleBytes {
		return fmt.Errorf("Showdown teambuilder data exceeds %d bytes", maxBundleBytes)
	}
	overrides, err := parseBattleGenerationOverrides(string(bundle))
	if err != nil {
		return fmt.Errorf("parse Showdown teambuilder data: %w", err)
	}
	contents, err := json.Marshal(overrides)
	if err != nil {
		return fmt.Errorf("encode generation overrides: %w", err)
	}
	outputPath := repositoryFile(outputAssetPath)
	if err := os.MkdirAll(filepath.Dir(outputPath), 0o755); err != nil {
		return fmt.Errorf("create asset directory: %w", err)
	}
	temporary, err := os.CreateTemp(filepath.Dir(outputPath), ".showdown-generation-overrides-*")
	if err != nil {
		return fmt.Errorf("create temporary asset: %w", err)
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if _, err := temporary.Write(contents); err != nil {
		temporary.Close()
		return fmt.Errorf("write generated asset: %w", err)
	}
	if err := temporary.Sync(); err != nil {
		temporary.Close()
		return fmt.Errorf("sync generated asset: %w", err)
	}
	if err := temporary.Close(); err != nil {
		return fmt.Errorf("close generated asset: %w", err)
	}
	if err := os.Rename(temporaryPath, outputPath); err != nil {
		return fmt.Errorf("replace generated asset: %w", err)
	}
	fmt.Printf("generated %d mod overrides at %s (%d bytes)\n", len(overrides), outputPath, len(contents))
	return nil
}

func parseBattleGenerationOverrides(bundle string) (map[string]map[string]speciesOverride, error) {
	markerIndex := strings.Index(bundle, bundleMarker)
	if markerIndex < 0 {
		return nil, fmt.Errorf("missing teambuilder table assignment")
	}
	contents, err := decodeJavaScriptString(bundle[markerIndex+len(bundleMarker):])
	if err != nil {
		return nil, err
	}
	var table map[string]json.RawMessage
	if err := json.Unmarshal([]byte(contents), &table); err != nil {
		return nil, fmt.Errorf("decode teambuilder table JSON: %w", err)
	}
	overrides := make(map[string]map[string]speciesOverride)
	for mod, data := range table {
		if !isGenerationMod(mod) && mod != "champions" {
			continue
		}
		var modData struct {
			OverrideSpeciesData map[string]json.RawMessage `json:"overrideSpeciesData"`
		}
		if err := json.Unmarshal(data, &modData); err != nil {
			return nil, fmt.Errorf("decode %s overrides: %w", mod, err)
		}
		speciesOverrides := make(map[string]speciesOverride)
		for species, raw := range modData.OverrideSpeciesData {
			var override speciesOverride
			if err := json.Unmarshal(raw, &override); err != nil {
				return nil, fmt.Errorf("decode %s species %s: %w", mod, species, err)
			}
			if len(override.BaseStats) == 0 && len(override.Abilities) == 0 {
				continue
			}
			speciesOverrides[species] = override
		}
		if len(speciesOverrides) > 0 {
			overrides[mod] = speciesOverrides
		}
	}
	if len(overrides) == 0 {
		return nil, fmt.Errorf("no generation species overrides found")
	}
	return overrides, nil
}

func decodeJavaScriptString(input string) (string, error) {
	var decoded bytes.Buffer
	for index := 0; index < len(input); {
		if input[index] == '\'' {
			return decoded.String(), nil
		}
		if input[index] == '\\' {
			runeValue, _, remaining, err := strconv.UnquoteChar(input[index:], '\'')
			if err != nil {
				return "", fmt.Errorf("decode JavaScript string escape: %w", err)
			}
			decoded.WriteRune(runeValue)
			index = len(input) - len(remaining)
			continue
		}
		runeValue, width := utf8.DecodeRuneInString(input[index:])
		decoded.WriteRune(runeValue)
		index += width
	}
	return "", fmt.Errorf("unterminated teambuilder table string")
}

func isGenerationMod(mod string) bool {
	if !strings.HasPrefix(mod, "gen") {
		return false
	}
	index := len("gen")
	for index < len(mod) && mod[index] >= '0' && mod[index] <= '9' {
		index++
	}
	return index > len("gen") && (index == len(mod) || strings.HasSuffix(mod, "letsgo"))
}

func repositoryFile(relative string) string {
	_, source, _, _ := runtime.Caller(0)
	return filepath.Clean(filepath.Join(filepath.Dir(source), "..", "..", relative))
}
