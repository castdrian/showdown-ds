from concurrent.futures import ThreadPoolExecutor, as_completed
from html import unescape
from pathlib import Path
from urllib.parse import urljoin
from urllib.request import Request, urlopen
import re

album_url = "https://downloads.khinsider.com/game-soundtracks/album/pokemon-sfx-gen-7-attack-moves-sumo-usum"
asset_directory = Path(__file__).resolve().parents[1] / "app/src/main/assets/move-sfx"
headers = {"User-Agent": "ShowdownDS asset fetcher"}


def fetch(url):
    request = Request(url, headers=headers)
    with urlopen(request, timeout=30) as response:
        return response.read()


def identifier(value):
    return "".join(character.lower() for character in value if character.isalnum())


def songs():
    page = fetch(album_url).decode("utf-8")
    pattern = re.compile(r'<td class="clickable-row"><a href="([^"]+\.mp3)">([^<]+)</a></td>')
    return [(unescape(name).strip(), urljoin(album_url, unescape(path))) for path, name in pattern.findall(page)]


def download_song(song):
    title, song_url = song
    target = asset_directory / f"{identifier(title)}.mp3"
    if target.is_file() and target.stat().st_size > 0:
        return title, target.name
    page = fetch(song_url).decode("utf-8")
    matches = re.findall(r'href="(https://lambda\.vgmtreasurechest\.com/[^"]+\.mp3)"', page)
    if not matches:
        raise RuntimeError(f"Missing download link for {title}")
    temporary = target.with_suffix(".mp3.part")
    temporary.write_bytes(fetch(unescape(matches[0])))
    temporary.replace(target)
    return title, target.name


def main():
    asset_directory.mkdir(parents=True, exist_ok=True)
    entries = songs()
    seen = set()
    unique_entries = []
    for title, song_url in entries:
        key = identifier(title)
        if not key or key in seen:
            continue
        seen.add(key)
        unique_entries.append((title, song_url))
    downloaded = []
    with ThreadPoolExecutor(max_workers=12) as executor:
        futures = [executor.submit(download_song, entry) for entry in unique_entries]
        for future in as_completed(futures):
            downloaded.append(future.result())
            print(f"{len(downloaded)}/{len(unique_entries)} {downloaded[-1][0]}", flush=True)
    index = asset_directory / "index.tsv"
    index.write_text("\n".join(f"{identifier(title)}\t{title}" for title, _ in sorted(downloaded)) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
