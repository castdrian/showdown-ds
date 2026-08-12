# Asset provenance

`app/src/main/res/drawable-nodpi/showdown_logo.png` is the Pokémon Showdown favicon from the `play.pokemonshowdown.com/favicon-256.png` path in the [smogon/pokemon-showdown-client](https://github.com/smogon/pokemon-showdown-client) repository. That repository publishes the asset under AGPL-3.0.

`app/src/main/res/drawable-nodpi/gen5_command_bg.png` and `app/src/main/res/drawable-nodpi/gen5_fight_bg.png` are `cmd_bg.png` and `fight_bg.png` from [CustCast/PokeRogue-App-Android-Thor](https://github.com/CustCast/PokeRogue-App-Android-Thor/tree/main/app/src/main/res/drawable). Permission for their use in this project was provided by the project owner.

`ShowdownSpriteCache` loads animated front and back Pokémon GIFs from `https://play.pokemonshowdown.com/sprites/xyani/` and `https://play.pokemonshowdown.com/sprites/xyani-back/` by default, plus trainer PNGs from `https://play.pokemonshowdown.com/sprites/trainers/`, the `sprites/gen6bgs/bg-aquacordetown.jpg` battle backdrop, `audio/sm-trainer.mp3`, `audio/notification.wav`, and species cries from `audio/cries/`. The classic setting uses the Gen 5 animation collections. These files are fetched at runtime and are not bundled in the APK. The app keeps an internal 96 MB least-recently-used disk cache and a 16-entry memory cache.

Iron Valiant's true back-facing fallback uses Showdown's dedicated static back sprite when the animated back asset is unavailable, with the [PokeAPI sprites repository](https://github.com/PokeAPI/sprites) as a secondary fallback.

The local HD texture collection was inspected but is not bundled. It contains hash-named texture dumps without a title-to-file mapping, so its contents cannot be attributed or selected reproducibly yet.
