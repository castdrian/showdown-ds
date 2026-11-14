/**
 * Scripted opponent for the local Showdown server (see scripts/local-showdown.sh).
 *
 * Run with Node 22.18+ (TypeScript type stripping, built-in WebSocket):
 *   node tools/local-showdown/bot.ts --mode search
 *   node tools/local-showdown/bot.ts --mode challenge --target "Guest 123"
 *   node tools/local-showdown/bot.ts --mode accept --stall-after-turn 2
 */

type Mode = "accept" | "challenge" | "search";
type LogLevel = "debug" | "info" | "warning" | "error";

interface Options {
  url: string;
  name: string;
  format: string;
  mode: Mode;
  target: string | undefined;
  stallAfterTurn: number | undefined;
}

interface BattleState {
  turn: number;
  stalled: boolean;
}

const DEFAULT_URL = "ws://127.0.0.1:8000/showdown/websocket";
const DEFAULT_NAME = "LocalBot";
const DEFAULT_FORMAT = "gen9randombattle";
const MODES: readonly Mode[] = ["accept", "challenge", "search"];
const DEFAULT_CHOICE = "/choose default";

function log(level: LogLevel, message: string): void {
  console.log(`${new Date().toISOString()} ${level.toUpperCase().padEnd(7)} ${message}`);
}

function parseOptions(argv: readonly string[]): Options {
  const values = new Map<string, string>();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!flag?.startsWith("--") || value === undefined) {
      throw new Error(`Expected "--flag value" pairs, got "${flag}"`);
    }
    values.set(flag.slice(2), value);
  }
  const mode = (values.get("mode") ?? "search") as Mode;
  if (!MODES.includes(mode)) throw new Error(`--mode must be one of ${MODES.join(", ")}`);
  const target = values.get("target");
  if (mode === "challenge" && !target) throw new Error("--mode challenge requires --target");
  const stall = values.get("stall-after-turn");
  return {
    url: values.get("url") ?? DEFAULT_URL,
    name: values.get("name") ?? DEFAULT_NAME,
    format: values.get("format") ?? DEFAULT_FORMAT,
    mode,
    target,
    stallAfterTurn: stall === undefined ? undefined : Number(stall),
  };
}

/** Splits a server frame into its room id (empty for global) and protocol lines. */
function parseFrame(frame: string): { roomId: string; lines: string[] } {
  const lines = frame.split("\n");
  if (lines[0]?.startsWith(">")) return { roomId: lines[0].slice(1), lines: lines.slice(1) };
  return { roomId: "", lines };
}

class Bot {
  private readonly options: Options;
  private readonly socket: WebSocket;
  private readonly battles = new Map<string, BattleState>();

  constructor(options: Options) {
    this.options = options;
    this.socket = new WebSocket(options.url);
    this.socket.addEventListener("open", () => log("info", `connected to ${options.url}`));
    this.socket.addEventListener("close", () => {
      log("info", "connection closed");
      process.exit(0);
    });
    this.socket.addEventListener("error", () => log("error", "socket error"));
    this.socket.addEventListener("message", (event) => this.onFrame(String(event.data)));
  }

  private send(roomId: string, message: string): void {
    log("debug", `> ${roomId}|${message}`);
    this.socket.send(`${roomId}|${message}`);
  }

  private onFrame(frame: string): void {
    const { roomId, lines } = parseFrame(frame);
    for (const line of lines) this.onLine(roomId, line);
  }

  private onLine(roomId: string, line: string): void {
    if (line.startsWith("|challstr|")) this.send("", `/trn ${this.options.name},0,`);
    else if (line.startsWith(`|updateuser| ${this.options.name}|`)) this.onLoggedIn();
    else if (line.startsWith("|pm|")) this.onPrivateMessage(line);
    else if (line.startsWith("|popup|") || line.startsWith("|nametaken|") || line.startsWith("|error|")) {
      log("warning", `server said: ${line}`);
    }
    else if (roomId.startsWith("battle-")) this.onBattleLine(roomId, line);
  }

  private onLoggedIn(): void {
    log("info", `logged in as ${this.options.name} (mode ${this.options.mode})`);
    if (this.options.mode === "search") {
      this.send("", "/utm null");
      this.send("", `/search ${this.options.format}`);
    }
    if (this.options.mode === "challenge") {
      this.send("", `/challenge ${this.options.target}, ${this.options.format}`);
    }
  }

  private onPrivateMessage(line: string): void {
    const [, , sender, , body] = line.split("|");
    if (this.options.mode !== "accept" || !body?.startsWith("/challenge ")) return;
    const challenger = (sender ?? "").trim().replace(/^[^A-Za-z0-9]/, "");
    log("info", `accepting challenge from ${challenger}`);
    this.send("", `/accept ${challenger}`);
  }

  private onBattleLine(roomId: string, line: string): void {
    const battle = this.battles.get(roomId) ?? { turn: 0, stalled: false };
    this.battles.set(roomId, battle);
    if (line.startsWith("|turn|")) this.onTurn(roomId, battle, Number(line.split("|")[2]));
    else if (line.startsWith("|request|")) this.onRequest(roomId, battle, line.slice("|request|".length));
    else if (line.startsWith("|win|") || line === "|tie|") log("info", `${roomId} finished: ${line}`);
  }

  private onTurn(roomId: string, battle: BattleState, turn: number): void {
    battle.turn = turn;
    const limit = this.options.stallAfterTurn;
    if (limit !== undefined && turn >= limit && !battle.stalled) {
      battle.stalled = true;
      log("warning", `${roomId} stalling from turn ${turn}; the battle will stay unfinished`);
    }
  }

  private onRequest(roomId: string, battle: BattleState, json: string): void {
    if (battle.stalled) return;
    const request = JSON.parse(json) as { wait?: boolean };
    if (!request.wait) this.send(roomId, DEFAULT_CHOICE);
  }
}

new Bot(parseOptions(process.argv.slice(2)));
