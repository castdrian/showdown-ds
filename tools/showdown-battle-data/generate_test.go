package main

import "testing"

func TestParsesGenerationSpeciesOverrides(t *testing.T) {
	bundle := `exports.BattleTeambuilderTable = JSON.parse('{"gen1":{"overrideSpeciesData":{"pikachu":{"baseStats":{"spe":90},"abilities":{"0":"Static"},"types":["Electric"]}}},"gen7letsgo":{"overrideSpeciesData":{"pikachu":{"baseStats":{"spe":90}}}},"tiers":[]}');`

	actual, err := parseBattleGenerationOverrides(bundle)
	if err != nil {
		t.Fatal(err)
	}
	if actual["gen1"]["pikachu"].BaseStats["spe"] != 90 {
		t.Fatalf("unexpected Gen 1 speed override: %#v", actual["gen1"]["pikachu"].BaseStats)
	}
	if actual["gen1"]["pikachu"].Abilities["0"] != "Static" {
		t.Fatalf("unexpected Gen 1 ability override: %#v", actual["gen1"]["pikachu"].Abilities)
	}
	if actual["gen7letsgo"]["pikachu"].BaseStats["spe"] != 90 {
		t.Fatalf("unexpected Let's Go speed override: %#v", actual["gen7letsgo"]["pikachu"].BaseStats)
	}
}

func TestParsesSingleQuotedJavaScriptEscapes(t *testing.T) {
	bundle := `exports.BattleTeambuilderTable = JSON.parse('{"gen1":{"overrideSpeciesData":{"pikachu":{"abilities":{"0":"King\'s Rock"}}}}}');`

	actual, err := parseBattleGenerationOverrides(bundle)
	if err != nil {
		t.Fatal(err)
	}
	if actual["gen1"]["pikachu"].Abilities["0"] != "King's Rock" {
		t.Fatalf("unexpected decoded ability: %#v", actual["gen1"]["pikachu"].Abilities)
	}
}
