// Store da partida ao vivo (revisão do bloco 6b, D-90 e D-93): o play-by-play que a tela guarda não perde lances (o cursor de
// `/events?after=` sai dos lances já conhecidos, antes de juntar os 20 de `live.lastEvents`, e pagina até o `lastSeq`), e uma
// resposta atrasada (o refresh do fim do prazo, de versão menor ou de outra partida) não volta o estado. O servidor aqui é um
// dublê em memória no lugar das rotas de partida do modo mock.
import { test } from "node:test";
import assert from "node:assert/strict";
import { getApi } from "@/lib/api";
import { matchStateFromDto } from "@/lib/api/live-match";
import type { MatchEvents, MatchState, PlayEvent } from "@/lib/api/match-types";
import { useMatch } from "@/store/match";
import { loadGolden } from "../golden.mts";

const golden = matchStateFromDto(loadGolden("match-state.json"));
const MID = golden.matchId;
const ev = (seq: number): PlayEvent => ({ ...golden.live.lastEvents[0], seq, drive: Math.ceil(seq / 8), textKey: "match:event.run" });
const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => ev(from + i));

/** Servidor de mentira: `n` lances gravados e a versão do snapshot; o estado traz os 20 últimos, como o backend. */
function server(n: number, version: number) {
  const srv = {
    n,
    version,
    calls: [] as number[],
    state(matchId = MID): MatchState {
      return { ...golden, matchId, status: "LIVE", decisionDeadlineAt: null, advanceModes: ["PLAY", "DRIVE", "QUARTER", "TO_DECISION", "FULL"], live: { ...golden.live, version: srv.version, decision: null, lastEvents: range(Math.max(1, srv.n - 19), srv.n) } };
    },
    page(after: number, limit = 200): MatchEvents {
      srv.calls.push(after);
      const events = range(after + 1, srv.n).slice(0, limit);
      return { matchId: MID, after, lastSeq: srv.n, finished: false, events };
    },
  };
  const m = getApi().matches;
  m.get = async (id) => srv.state(id);
  m.events = async (_id, after = 0, limit = 200) => srv.page(after, Math.min(limit, 15));
  m.advance = async () => {
    srv.n += 25;
    srv.version += 1;
    return srv.state();
  };
  return srv;
}

const seqs = () => useMatch.getState().events.map((e) => e.seq);

test("abrir a partida no meio: o feed traz os lances 1–60, não só os 20 de live.lastEvents (41–60)", async () => {
  useMatch.getState().reset();
  const srv = server(60, 5);
  await useMatch.getState().load(MID);
  assert.deepEqual(seqs(), range(1, 60).map((e) => e.seq), "todos os lances, em ordem, sem buraco");
  assert.equal(srv.calls[0], 0, "o cursor começa nos lances que a tela já tinha (nenhum), não no fim de lastEvents");
});

test("avanço de 40 → 65: chegam os lances 41–65, inclusive os que ficaram fora de lastEvents (41–45)", async () => {
  useMatch.getState().reset();
  const srv = server(40, 5);
  await useMatch.getState().load(MID);
  assert.deepEqual(seqs(), range(1, 40).map((e) => e.seq));
  srv.calls.length = 0;
  await useMatch.getState().advance("DRIVE");
  assert.deepEqual(seqs(), range(1, 65).map((e) => e.seq), "sem buraco entre o que a tela tinha e lastEvents");
  assert.equal(srv.calls[0], 40, "o cursor sai do último lance conhecido");
  assert.equal(new Set(seqs()).size, seqs().length, "sem duplicar");
});

test("resposta atrasada: o refresh de versão v que chega depois da v+2 não volta o estado", async () => {
  useMatch.getState().reset();
  const srv = server(30, 5);
  await useMatch.getState().load(MID);
  // o refresh do fim do prazo sai com o estado v5 e demora; enquanto isso, o avanço confirma v6 e outro v7
  let release!: () => void;
  const stale = srv.state();
  getApi().matches.get = () => new Promise((r) => (release = () => r(structuredClone(stale))));
  const late = useMatch.getState().refresh();
  await useMatch.getState().advance("PLAY");
  await useMatch.getState().advance("PLAY");
  assert.equal(useMatch.getState().state?.live.version, 7);
  release();
  await late;
  assert.equal(useMatch.getState().state?.live.version, 7, "a resposta velha (v5) foi ignorada");
  assert.equal(seqs().at(-1), 80, "o feed também não volta");
});

test("resposta de outra partida (a tela já trocou) não entra no estado", async () => {
  useMatch.getState().reset();
  const srv = server(10, 3);
  await useMatch.getState().load(MID);
  let release!: () => void;
  const other = srv.state("00000000-0000-4000-8000-0000000000ff");
  getApi().matches.get = (id) => (id === MID ? new Promise((r) => (release = () => r(structuredClone(other)))) : Promise.resolve(srv.state(id)));
  const late = useMatch.getState().refresh();
  release();
  await late;
  assert.equal(useMatch.getState().state?.matchId, MID);
});
