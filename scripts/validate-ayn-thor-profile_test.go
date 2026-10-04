package main

import (
	"os"
	"strings"
	"testing"
)

func TestThorAVDResourceProfileBoundsCPU(t *testing.T) {
	script, err := os.ReadFile("run-ayn-thor-avd.sh")
	if err != nil {
		t.Fatal(err)
	}
	baseConfig, err := os.ReadFile("../config/avd/ayn-thor-base.ini")
	if err != nil {
		t.Fatal(err)
	}
	profile, err := os.ReadFile("../config/avd/ayn-thor.ini")
	if err != nil {
		t.Fatal(err)
	}

	for _, expected := range []struct {
		name string
		text string
	}{
		{name: "one-core default", text: `cpu_cores="${AYN_THOR_CPU_CORES:-1}"`},
		{name: "one-core guard", text: `if [[ "$cpu_cores" != "1" ]]`},
		{name: "explicit emulator core limit", text: `-cores "$cpu_cores"`},
		{name: "30 Hz default", text: `vsync_rate="${AYN_THOR_VSYNC_RATE:-30}"`},
		{name: "30 Hz guard", text: `if [[ "$vsync_rate" != "30" ]]`},
		{name: "base AVD core limit", text: "hw.cpu.ncore = 1"},
		{name: "base AVD refresh limit", text: "hw.lcd.vsync = 30"},
		{name: "AVD profile core limit", text: "hw.cpu.ncore=1"},
		{name: "AVD profile refresh limit", text: "hw.lcd.vsync=30"},
	} {
		var source []byte
		switch expected.name {
		case "base AVD core limit", "base AVD refresh limit":
			source = baseConfig
		case "AVD profile core limit", "AVD profile refresh limit":
			source = profile
		default:
			source = script
		}
		if !strings.Contains(string(source), expected.text) {
			t.Errorf("%s missing %q", expected.name, expected.text)
		}
	}
}

func TestThorAVDAllowsColdAndroidBootToFinish(t *testing.T) {
	script, err := os.ReadFile("run-ayn-thor-avd.sh")
	if err != nil {
		t.Fatal(err)
	}

	for _, expected := range []string{
		"boot_attempts=300",
		"while (( attempt < boot_attempts )); do",
	} {
		if !strings.Contains(string(script), expected) {
			t.Errorf("resource-limited AVD startup missing %q", expected)
		}
	}
}
