package main

import (
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strings"
	"sync"
	"time"
)

const albumURL = "https://downloads.khinsider.com/game-soundtracks/album/pokemon-sfx-gen-7-attack-moves-sumo-usum"

var (
	songPattern = regexp.MustCompile(`<td class="clickable-row"><a href="([^"]+\.mp3)">([^<]+)</a></td>`)
	linkPattern = regexp.MustCompile(`href="(https://lambda\.vgmtreasurechest\.com/[^"]+\.mp3)"`)
	httpClient  = &http.Client{Timeout: 30 * time.Second}
)

type song struct {
	title string
	url   string
}

type downloadResult struct {
	title string
	name  string
	err   error
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run() error {
	assetDirectory := resolveAssetDirectory()
	if err := os.MkdirAll(assetDirectory, 0o755); err != nil {
		return fmt.Errorf("create asset directory: %w", err)
	}
	entries, err := songs()
	if err != nil {
		return err
	}
	uniqueEntries := uniqueSongs(entries)
	results := make(chan downloadResult, len(uniqueEntries))
	jobs := make(chan song)
	var workers sync.WaitGroup
	for index := 0; index < 12; index++ {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for entry := range jobs {
				name, downloadErr := downloadSong(entry, assetDirectory)
				results <- downloadResult{title: entry.title, name: name, err: downloadErr}
			}
		}()
	}
	go func() {
		for _, entry := range uniqueEntries {
			jobs <- entry
		}
		close(jobs)
		workers.Wait()
		close(results)
	}()

	downloaded := make([]downloadResult, 0, len(uniqueEntries))
	completed := 0
	for result := range results {
		if result.err != nil {
			return fmt.Errorf("download %s: %w", result.title, result.err)
		}
		downloaded = append(downloaded, result)
		completed++
		fmt.Printf("%d/%d %s\n", completed, len(uniqueEntries), result.title)
	}
	sort.Slice(downloaded, func(left, right int) bool {
		return downloaded[left].title < downloaded[right].title
	})
	indexLines := make([]string, 0, len(downloaded))
	for _, result := range downloaded {
		indexLines = append(indexLines, identifier(result.title)+"\t"+result.title)
	}
	indexPath := filepath.Join(assetDirectory, "index.tsv")
	if err := os.WriteFile(indexPath, []byte(strings.Join(indexLines, "\n")+"\n"), 0o644); err != nil {
		return fmt.Errorf("write asset index: %w", err)
	}
	return nil
}

func songs() ([]song, error) {
	page, err := fetch(albumURL)
	if err != nil {
		return nil, fmt.Errorf("fetch album page: %w", err)
	}
	baseURL, err := url.Parse(albumURL)
	if err != nil {
		return nil, fmt.Errorf("parse album URL: %w", err)
	}
	matches := songPattern.FindAllStringSubmatch(string(page), -1)
	entries := make([]song, 0, len(matches))
	for _, match := range matches {
		trackURL, err := baseURL.Parse(html.UnescapeString(match[1]))
		if err != nil {
			return nil, fmt.Errorf("parse track URL: %w", err)
		}
		entries = append(entries, song{
			title: strings.TrimSpace(html.UnescapeString(match[2])),
			url:   trackURL.String(),
		})
	}
	return entries, nil
}

func downloadSong(entry song, assetDirectory string) (string, error) {
	name := identifier(entry.title) + ".mp3"
	target := filepath.Join(assetDirectory, name)
	if fileInfo, err := os.Stat(target); err == nil && fileInfo.Size() > 0 {
		return name, nil
	}
	page, err := fetch(entry.url)
	if err != nil {
		return "", fmt.Errorf("fetch track page: %w", err)
	}
	matches := linkPattern.FindStringSubmatch(string(page))
	if len(matches) < 2 {
		return "", fmt.Errorf("missing download link")
	}
	audio, err := fetch(html.UnescapeString(matches[1]))
	if err != nil {
		return "", fmt.Errorf("fetch audio: %w", err)
	}
	temporary := target + ".part"
	if err := os.WriteFile(temporary, audio, 0o644); err != nil {
		return "", fmt.Errorf("write temporary audio: %w", err)
	}
	if err := os.Rename(temporary, target); err != nil {
		return "", fmt.Errorf("commit audio: %w", err)
	}
	return name, nil
}

func fetch(rawURL string) ([]byte, error) {
	request, err := http.NewRequest(http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("User-Agent", "ShowdownDS asset fetcher")
	response, err := httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		return nil, fmt.Errorf("HTTP %s", response.Status)
	}
	return io.ReadAll(response.Body)
}

func uniqueSongs(entries []song) []song {
	seen := make(map[string]struct{}, len(entries))
	unique := make([]song, 0, len(entries))
	for _, entry := range entries {
		key := identifier(entry.title)
		if key == "" {
			continue
		}
		if _, exists := seen[key]; exists {
			continue
		}
		seen[key] = struct{}{}
		unique = append(unique, entry)
	}
	return unique
}

func identifier(value string) string {
	var builder strings.Builder
	for _, character := range strings.ToLower(value) {
		if (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') {
			builder.WriteRune(character)
		}
	}
	return builder.String()
}

func resolveAssetDirectory() string {
	workingDirectory, _ := os.Getwd()
	workingPath := filepath.Join(workingDirectory, "app", "src", "main", "assets", "move-sfx")
	if _, err := os.Stat(filepath.Dir(workingPath)); err == nil {
		return workingPath
	}
	_, sourcePath, _, _ := runtime.Caller(0)
	return filepath.Join(filepath.Dir(filepath.Dir(sourcePath)), "app", "src", "main", "assets", "move-sfx")
}
