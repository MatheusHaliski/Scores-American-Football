# -*- coding: utf-8 -*-
"""Gera diagramas PlantUML (atividade, sequência, componentes, classes, máquina de estados) para cada
RF/RNF a partir do modelo em model/*.py, mais catálogos (atores, gateways, endpoints, estados).

Uso:  python3 generate.py [--only RF40,RNF1] [--out ..]

Saída (uma pasta por requisito, docs/RF1 … RF66 e docs/RNF1 … RNF13):
  <out>/<ID>/<ID>-act.puml · -seq · -cmp · -cls · -stm (+ -stm-<sujeito> extras) e README.md com a história de usuário.
Os .svg ficam na mesma pasta (PlantUML). Sem board do Trello neste repositório: a história de usuário vem do campo hu do modelo.
"""
import argparse, importlib.util, os, re, sys, glob, json
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import common  # noqa

def load_models():
    reqs = []
    for path in sorted(glob.glob(os.path.join(HERE, "model", "*.py"))):
        spec = importlib.util.spec_from_file_location(os.path.basename(path)[:-3], path)
        mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
        reqs.extend(getattr(mod, "REQUIREMENTS", []))
    # ordem: RF por número, depois RNF
    def key(r):
        m = re.match(r"(RNF|RF|SEG)(\d*)", r["id"]); return ({"RF": 0, "RNF": 1, "SEG": 2}[m.group(1)], int(m.group(2) or 0))
    reqs.sort(key=key)
    ids = [r["id"] for r in reqs]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup: raise SystemExit("ids duplicados no modelo: " + ", ".join(sorted(dup)))
    return reqs

def esc(s): return str(s).replace('"', "'")

def md(s):
    """Célula de tabela Markdown: `|` vira `\\|` (inclusive dentro de `código`) e quebra de linha vira espaço. O `esc` é só para o PlantUML."""
    return esc(s).replace("|", "\\|").replace("\n", " ")

def check_tables(text, where):
    """Toda linha de uma tabela Markdown tem o mesmo número de colunas do cabeçalho (pipes escapados não contam)."""
    cols = None
    for n, line in enumerate(text.split("\n"), 1):
        if not line.startswith("|"):
            cols = None; continue
        k = len(re.findall(r"(?<!\\)\|", line))
        if cols is None: cols = k
        elif k != cols: raise SystemExit(f"{where}:{n}: tabela com {k - 1} colunas, esperado {cols - 1}: {line[:120]}")

# ---- endpoints implementados (sync_endpoints.py): o README e o diagrama de componentes marcam o que ainda é alvo
_EP_FILE = os.path.join(HERE, "endpoints_code.json")
_IMPLEMENTED = set()
_HAS_EP = os.path.exists(_EP_FILE)
if _HAS_EP:
    _IMPLEMENTED = {(e["method"], re.sub(r"\{[^}]+\}", "{}", e["path"].rstrip("/"))) for e in json.load(open(_EP_FILE, encoding="utf-8"))["endpoints"]}

def implemented(e):
    """True se método+rota existem num controller do scores-af-web; rotas genéricas (`*`) contam como transversais."""
    if not _HAS_EP or e["method"] == "*": return None
    return (e["method"].upper(), re.sub(r"\{[^}]+\}", "{}", e["path"].rstrip("/"))) in _IMPLEMENTED

# ------------------------------------------------------------------ atividade
def activity(r):
    out = ["@startuml", f"title {r['id']} — {esc(r['title'])} · Diagrama de atividades", "skinparam ActivityBackgroundColor #FFF7EE", "skinparam ActivityBorderColor #DC5B26", "skinparam ActivityDiamondBackgroundColor #FBE9D8", "skinparam swimlaneWidth same"]
    state = {"lane": None}
    def lane(actor):
        if actor and actor != state["lane"]:
            out.append(f"|{actor}|"); state["lane"] = actor
    def emit(steps, indent=""):
        for s in steps:
            if s == "start": lane(steps_first_actor(steps)); out.append(indent + "start")
            elif s == "stop": out.append(indent + "stop")
            elif s == "end": out.append(indent + "end")
            elif isinstance(s, str): out.append(indent + f":{esc(s)};")
            elif "do" in s:
                lane(s.get("actor")); out.append(indent + f":{esc(s['do'])};")
            elif "if" in s:
                lane(s.get("actor"))
                out.append(indent + f"if ({esc(s['if'])}) then (sim)")
                emit(s.get("yes", []), indent + "  ")
                if s.get("no"):
                    out.append(indent + "else (não)"); emit(s["no"], indent + "  ")
                out.append(indent + "endif")
            elif "while" in s:
                lane(s.get("actor"))
                out.append(indent + f"while ({esc(s['while'])}) is (sim)")
                emit(s.get("body", []), indent + "  ")
                out.append(indent + "endwhile (não)")
            elif "fork" in s:
                out.append(indent + "fork")
                for i, branch in enumerate(s["fork"]):
                    if i: out.append(indent + "fork again")
                    emit(branch, indent + "  ")
                out.append(indent + "end fork")
            elif "note" in s:
                out.append(indent + f"note right: {esc(s['note'])}")
            elif "partition" in s:
                out.append(indent + f"partition \"{esc(s['partition'])}\" {{"); emit(s.get("body", []), indent + "  "); out.append(indent + "}")
    def steps_first_actor(steps):
        for s in steps:
            if isinstance(s, dict) and s.get("actor"): return s["actor"]
        return "Manager"
    emit(r["activity"])
    out.append("@enduml"); return "\n".join(out)

# ------------------------------------------------------------------ sequência
def sequence(r):
    order = [p for p, _ in common.PARTICIPANTS]; kinds = dict(common.PARTICIPANTS)
    used = OrderedDict()
    def collect(items):
        for it in items:
            if isinstance(it, (list, tuple)):
                used.setdefault(it[0], True); used.setdefault(it[1], True)
            elif isinstance(it, dict):
                for k in ("then", "else", "body"):
                    if k in it: collect(it[k])
    collect(r["sequence"])
    names = sorted(used.keys(), key=lambda n: order.index(n) if n in order else 999)
    out = ["@startuml", f"title {r['id']} — {esc(r['title'])} · Diagrama de sequência", "skinparam sequenceMessageAlign left", "skinparam responseMessageBelowArrow true", "autonumber"]
    for n in names:
        kind = kinds.get(n, "participant"); label = common.ACTORS.get(n, common.STORES.get(n, n)).split(" — ")[0]
        out.append(f'{kind} "{esc(label)}" as {n}')
    def emit(items, indent=""):
        for it in items:
            if isinstance(it, (list, tuple)):
                a, b, msg = it[0], it[1], it[2]
                arrow = "-->" if (len(it) > 3 and it[3] == "return") else "->"
                out.append(indent + f"{a} {arrow} {b}: {esc(msg)}")
            elif isinstance(it, dict):
                if "alt" in it:
                    out.append(indent + f"alt {esc(it['alt'])}"); emit(it.get("then", []), indent + "  ")
                    if it.get("else") is not None:
                        out.append(indent + f"else {esc(it.get('elseLabel','senão'))}"); emit(it["else"], indent + "  ")
                    out.append(indent + "end")
                elif "loop" in it:
                    out.append(indent + f"loop {esc(it['loop'])}"); emit(it.get("body", []), indent + "  "); out.append(indent + "end")
                elif "par" in it:
                    out.append(indent + "par " + esc(it["par"])); emit(it.get("body", []), indent + "  "); out.append(indent + "end")
                elif "note" in it:
                    out.append(indent + f"note over {it.get('over','UseCase')}: {esc(it['note'])}")
    emit(r["sequence"])
    out.append("@enduml"); return "\n".join(out)

# ------------------------------------------------------------------ componentes
def component(r):
    c = r.get("components", {})
    out = ["@startuml", f"title {r['id']} — {esc(r['title'])} · Diagrama de componentes", "skinparam componentStyle rectangle", "skinparam linetype ortho", "skinparam nodesep 14", "skinparam ranksep 36"]
    ids = {}
    def cid(name):
        if name not in ids: ids[name] = "c" + str(len(ids) + 1)
        return ids[name]
    for actor in r.get("actors", []):
        out.append(f'actor "{esc(common.ACTORS.get(actor, actor))}" as {cid(actor)}')
    fe = c.get("frontend", []); web = c.get("web", []); app = c.get("application", []); eng = c.get("engine", []); ports = c.get("ports", []); ad = c.get("adapters", [])
    if fe:
        out.append(f'package "{common.LAYERS["frontend"]}" as pkg_fe {{')
        out.append(f'  component "{esc(fe[0])}" as {cid(fe[0])}')
        if len(fe) > 1:
            out.append(f'  frame "componentes de {esc(fe[0])}" as pkg_fe_sub {{')
            for it in fe[1:]: out.append(f'    component "{esc(it)}" as {cid(it)}')
            out.append("  }")
        out.append("}")
    if web:
        out.append(f'package "{common.LAYERS["web"]}" as pkg_web {{')
        for it in web: out.append(f'  component "{esc(it)}" as {cid(it)}')
        out.append("}")
    if app:
        out.append(f'package "{common.LAYERS["application"]}" as pkg_app {{')
        for it in app: out.append(f'  component "{esc(it)}" as {cid(it)}')
        out.append("}")
    if eng:
        out.append(f'package "{common.LAYERS["engine"]}" as pkg_eng {{')
        for it in eng: out.append(f'  component "{esc(it)}" as {cid(it)}')
        out.append("}")
    if ports:
        out.append(f'package "{common.LAYERS["ports"]}" as pkg_ports {{')
        for it in ports: out.append(f'  interface "{esc(it)}" as {cid(it)}')
        out.append("}")
    if ad:
        out.append(f'package "{common.LAYERS["adapters"]}" as pkg_ad {{')
        for it in ad: out.append(f'  component "{esc(it)}" as {cid(it)}')
        out.append("}")
    store_ids = {}
    for st in r.get("stores", []):
        store_ids[st] = "s" + str(len(store_ids) + 1)
        out.append(f'database "{esc(common.STORES.get(st, st).split(" — ")[0])}" as {store_ids[st]}')
    def ref(name):
        return store_ids[name] if name in store_ids and name not in ids else cid(name)
    def link(a, b, label="", arrow="-->"):
        out.append(f"{ref(a)} {arrow} {ref(b)}" + (f" : {esc(label)}" if label else ""))
    for actor in r.get("actors", []):
        if actor in ("Manager", "Visitante", "Admin") and fe: link(actor, fe[0], "usa")
        elif actor in ("Scheduler",) and app: link(actor, app[0], "dispara")
    eps = r.get("endpoints", [])
    label = "\\n".join(e["method"] + " " + e["path"].replace("/api/v1", "") + ("" if implemented(e) in (True, None) else " (alvo)") for e in eps[:3]) + ("\\n…" if len(eps) > 3 else "")
    if fe and web: link(fe[0], web[0], label)
    if len(web) > 1 and fe:
        for w in web[1:]: link(fe[0], w)
    for w in web:
        for a in app[:1]: link(w, a)
    if len(app) > 1:
        out.append(f"{cid(app[0])} ..> pkg_app : orquestra")
    for e in eng:
        if app: link(app[0], e, "usa")
    if ports:
        out.append("pkg_app --> pkg_ports : depende (DIP)")
    wiring = c.get("wiring", {})
    for p in ports:
        target = wiring.get(p)
        if target and target in ad: out.append(f"{cid(target)} .up.|> {cid(p)}")
    for a in ad:
        for st in c.get("adapterStores", {}).get(a, []):
            out.append(f"{cid(a)} --> {store_ids.get(st, cid(st))}")
    for extra in c.get("links", []):
        link(extra[0], extra[1], extra[2] if len(extra) > 2 else "")
    out.append("@enduml"); return "\n".join(out)

# ------------------------------------------------------------------ classes
def classes(r):
    focus = list(r.get("classes", {}).get("focus", r.get("entities", [])))
    extra = r.get("classes", {}).get("extra", [])
    out = ["@startuml", f"title {r['id']} — {esc(r['title'])} · Diagrama de classes", "skinparam classAttributeIconSize 0", "hide empty methods"]
    ctxs = OrderedDict()
    for e in focus:
        if e not in common.ENTITIES: raise SystemExit(f"{r['id']}: entidade desconhecida no catálogo: {e}")
        ctxs.setdefault(common.ENTITIES[e]["ctx"], []).append(e)
    for ctx, ents in ctxs.items():
        out.append(f'package "{ctx}" {{')
        for e in ents:
            out.append(f"  class {e} {{")
            for a in common.ENTITIES[e]["attrs"]: out.append(f"    {a}")
            out.append("  }")
        out.append("}")
    for x in extra:  # {"name","stereotype","attrs":[],"methods":[]}
        st = f' <<{x.get("stereotype")}>>' if x.get("stereotype") else ""
        kind = x.get("kind", "class")
        out.append(f"{kind} {x['name']}{st} {{")
        for a in x.get("attrs", []): out.append(f"  {a}")
        for m in x.get("methods", []): out.append(f"  {m}()" if "(" not in m else f"  {m}")
        out.append("}")
    fs = set(focus)
    for a, b, ca, cb, lbl in common.RELATIONS:
        if a in fs and b in fs:
            out.append(f'{a} "{ca}" -- "{cb}" {b}' + (f" : {esc(lbl)}" if lbl else ""))
    for a, b, lbl in r.get("classes", {}).get("links", []):
        out.append(f"{a} ..> {b}" + (f" : {esc(lbl)}" if lbl else ""))
    out.append("@enduml"); return "\n".join(out)

# ------------------------------------------------------------------ estados
def slug(text):
    import unicodedata
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:40]

def state_machines(r):
    """Primeira máquina = `states` (arquivo -stm); adicionais em `moreStates` (arquivos -stm-<sujeito>)."""
    return [r["states"]] + list(r.get("moreStates", []))

def states(r, s=None):
    s = s or r["states"]
    out = ["@startuml", f"title {r['id']} — {esc(r['title'])} · Máquina de estados: {esc(s['subject'])}", "skinparam StateBackgroundColor #FFF7EE", "skinparam StateBorderColor #DC5B26"]
    for st in s["states"]:
        if isinstance(st, dict):
            out.append(f'state "{esc(st["name"])}" as {st["id"]}')
            for d in st.get("desc", []): out.append(f"{st['id']} : {esc(d)}")
        else:
            out.append(f'state "{esc(st)}" as {re.sub(r"[^A-Za-z0-9_]", "_", st)}')
    def sid(name):
        for st in s["states"]:
            if isinstance(st, dict) and st["name"] == name: return st["id"]
        return re.sub(r"[^A-Za-z0-9_]", "_", name)
    out.append(f"[*] --> {sid(s['initial'])}")
    for t in s["transitions"]:
        a, b, lbl = t[0], t[1], (t[2] if len(t) > 2 else "")
        out.append(f"{sid(a)} --> {sid(b)}" + (f" : {esc(lbl)}" if lbl else ""))
    for f in s.get("final", []): out.append(f"{sid(f)} --> [*]")
    out.append("@enduml"); return "\n".join(out)

# ------------------------------------------------------------------ Trello
TRELLO_FILE = os.path.join(HERE, "trello-board.json")

def load_trello():
    """Mapa id → {rf, hu} com nome/descrição/url dos cards (RFn, HU-RFn, RNFn/RNn)."""
    if not os.path.exists(TRELLO_FILE): return {}, {}
    data = json.load(open(TRELLO_FILE, encoding="utf-8"))
    cards = {}
    for lst in data["lists"].values():
        for c in lst:
            m = re.match(r"\s*(HU-)?(RNF|RN|RF)\s*-?\s*(\d+)", c["name"])
            if not m: continue
            kind = "RNF" if m.group(2) in ("RNF", "RN") else "RF"
            rid = f"{kind}{int(m.group(3))}"
            cards.setdefault(rid, {})["hu" if m.group(1) else "req"] = c
    return cards, data

def strip_id(name):
    return re.sub(r"^\s*(HU-)?(RNF|RN|RF)\s*-?\s*\d+\s*[-—:]?\s*", "", name).strip()

def kinds(r):
    base = [("act", "Atividades"), ("seq", "Sequência"), ("cmp", "Componentes"), ("cls", "Classes"), ("stm", "Máquina de estados: " + esc(r["states"]["subject"]))]
    return base + [(f"stm-{slug(x['subject'])}", "Máquina de estados: " + esc(x["subject"])) for x in r.get("moreStates", [])]

def folder_readme(r, cards, data):
    c = cards.get(r["id"], {})
    req, hu = c.get("req"), c.get("hu")
    L = [f"# {r['id']} — {esc(r['title'])}", ""]
    L += ["## História de usuário", ""]
    if req:
        L += [f"**{r['id']}:** {strip_id(req['name'])}", ""]
    if hu:
        L += [f"**HU-{r['id']}:** {strip_id(hu['name'])}", ""]
    desc = (hu or {}).get("desc") or (req or {}).get("desc")
    if desc:
        L += ["<details open><summary>Descrição e critérios de aceite (texto do card)</summary>", "", desc.strip(), "", "</details>", ""]
    elif r.get("hu"):
        L += [f"> {esc(r['hu'])}", ""]
    L += [f"Sprint: {r.get('sprint', '—')} · Atores: {', '.join(r.get('actors', [])) or '—'} · Tela: {esc(r.get('screen', '—'))}", ""]
    L += ["## Diagramas", ""]
    for k, label in kinds(r):
        f = f"{r['id']}-{k}"
        L += [f"### {label}", "", f"![{label}]({f}.svg)", "", f"Fonte: [`{f}.puml`]({f}.puml)", ""]
    if r.get("endpoints"):
        L += ["## Endpoints", "", "✅ = existe num controller do `scores-af-web` · ⛔ alvo = previsto no modelo, ainda sem controller (lista em `generator/endpoints_code.json`).", "",
              "| Estado | Método | Rota | Caso de uso | Autorização | Descrição |", "|---|---|---|---|---|---|"]
        L += [f"| {'✅' if implemented(e) else ('—' if implemented(e) is None else '⛔ alvo')} | `{md(e['method'])}` | `{md(e['path'])}` | `{md(e.get('usecase',''))}` | {md(e.get('auth','JWT'))} | {md(e.get('desc',''))} |" for e in r["endpoints"]]
        L += [""]
    if r.get("gateways"):
        L += ["## Pontos de decisão", "", "| Gateway | Regra | Se falso |", "|---|---|---|"]
        L += [f"| {md(g['name'])} | {md(g['rule'])} | {md(g.get('onFalse',''))} |" for g in r["gateways"]]
        L += [""]
    L += ["---", "Gerado por `../generator/generate.py` a partir de `../generator/model/*.py` e `../generator/common.py`; não edite os .puml à mão."]
    return "\n".join(L) + "\n"

def trello_md(data):
    L = [f"# Cards do board Trello «{data['board']}» (extraído em {data['extractedAt']})", "", f"Board: {data['boardUrl']}", ""]
    for lst, cards in data["lists"].items():
        L += [f"## {lst}", ""]
        for c in cards:
            L.append(f"- **{c['name'].strip()}**" + (f" — descrição com {len(c['desc'])} caracteres (ver `trello-board.json` e o README da pasta)" if c.get("desc") else ""))
        L.append("")
    return "\n".join(L) + "\n"

# ------------------------------------------------------------------ catálogos
def catalogs(reqs, outdir):
    lines = ["# Diagramas do Scores: American Football — índice", "",
             "Uma pasta por requisito: `RF1/` … `RF66/` (funcionais; RF1–RF59 com o mesmo número e função do Scores de basquete, D-2; RF60–RF66 só do futebol americano) "
             "e `RNF1/` … `RNF13/` (não funcionais). Cada pasta tem os 5 diagramas — atividades (`act`), sequência (`seq`), componentes (`cmp`), classes (`cls`) e "
             "máquina de estados (`stm`) — em `.puml` (fonte) e `.svg`, e um `README.md` com a história de usuário, os endpoints e os pontos de decisão.", "",
             "Gerados por `generator/generate.py` a partir de `generator/model/*.py` (fonte única, D-7) e do catálogo `generator/common.py`. Nunca edite os `.puml` à mão: "
             "mude o modelo e rode `python3 generator/generate.py`, depois renderize com o PlantUML (`java -jar plantuml.jar -tsvg RF*/*.puml RNF*/*.puml`). "
             "Endpoints ainda sem controller aparecem como ⛔ alvo; `generator/endpoints_code.json` lista os que já existem no `scores-af-web`.", "",
             "| Req | Sprint | Título | Atores | Endpoints | Diagramas |", "|---|---|---|---|---|---|"]
    ep_lines = ["# Catálogo de endpoints da API (por requisito)", "", "✅ implementado no `scores-af-web` · ⛔ alvo (modelo) · — transversal.", "", "| Estado | Método | Rota | Req | Caso de uso | Autorização | Descrição |", "|---|---|---|---|---|---|---|"]
    gw_lines = ["# Catálogo de gateways (pontos de decisão)", "", "| Req | Gateway | Regra | Se falso |", "|---|---|---|---|"]
    ac_lines = ["# Catálogo de atores", "", "| Ator | Descrição | Requisitos |", "|---|---|---|"]
    st_lines = ["# Catálogo de máquinas de estado", "", "| Req | Sujeito | Estados | Transições |", "|---|---|---|---|"]
    actor_map = OrderedDict((a, []) for a in common.ACTORS)
    for r in reqs:
        d = r["id"]
        more = "".join(f" · [stm:{esc(x['subject']).split('.')[0]}]({d}/{r['id']}-stm-{slug(x['subject'])}.svg)" for x in r.get("moreStates", []))
        lines.append(f"| [{r['id']}]({d}/) | {r.get('sprint','—')} | {md(r['title'])} | {md(', '.join(r.get('actors', [])))} | {len(r.get('endpoints', []))} | [act]({d}/{r['id']}-act.svg) · [seq]({d}/{r['id']}-seq.svg) · [cmp]({d}/{r['id']}-cmp.svg) · [cls]({d}/{r['id']}-cls.svg) · [stm]({d}/{r['id']}-stm.svg){more} |")
        for e in r.get("endpoints", []):
            ep_lines.append(f"| {'✅' if implemented(e) else ('—' if implemented(e) is None else '⛔ alvo')} | `{md(e['method'])}` | `{md(e['path'])}` | {r['id']} | `{md(e.get('usecase',''))}` | {md(e.get('auth','JWT'))} | {md(e.get('desc',''))} |")
        for g in r.get("gateways", []):
            gw_lines.append(f"| {r['id']} | {md(g['name'])} | {md(g['rule'])} | {md(g.get('onFalse',''))} |")
        for a in r.get("actors", []):
            actor_map.setdefault(a, []).append(r["id"])
        for s in state_machines(r):
            st_lines.append(f"| {r['id']} | {md(s['subject'])} | {md(', '.join(x['name'] if isinstance(x, dict) else x for x in s['states']))} | {len(s['transitions'])} |")
    for a, rs in actor_map.items():
        ac_lines.append(f"| {md(a)} | {md(common.ACTORS.get(a, a))} | {', '.join(rs)} |")
    for name, content in (("README.md", lines), ("catalogo-endpoints.md", ep_lines), ("catalogo-gateways.md", gw_lines), ("catalogo-atores.md", ac_lines), ("catalogo-estados.md", st_lines)):
        text = "\n".join(content) + "\n"; check_tables(text, name)
        with open(os.path.join(outdir, name), "w", encoding="utf-8") as f: f.write(text)
    with open(os.path.join(outdir, "modelo.json"), "w", encoding="utf-8") as f:
        json.dump(reqs, f, ensure_ascii=False, indent=1, default=str)

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--only", default=""); ap.add_argument("--out", default=os.path.join(HERE, "..")); ap.add_argument("--no-catalogs", action="store_true")
    args = ap.parse_args()
    reqs = load_models(); only = {x.strip() for x in args.only.split(",") if x.strip()}
    base = os.path.abspath(args.out); os.makedirs(base, exist_ok=True)
    cards, data = load_trello()
    missing = [rid for rid in cards if rid not in {r["id"] for r in reqs}]
    if missing: print("AVISO: cards do Trello sem modelo de diagramas:", ", ".join(sorted(missing)))
    for r in reqs:
        for e in r.get("endpoints", []):
            e["implemented"] = implemented(e)
    n = 0
    for r in reqs:
        if only and r["id"] not in only: continue
        d = os.path.join(base, r["id"]); os.makedirs(d, exist_ok=True)
        readme = folder_readme(r, cards, data); check_tables(readme, f"{r['id']}/README.md")
        with open(os.path.join(d, "README.md"), "w", encoding="utf-8") as f: f.write(readme)
        for suffix, fn in (("act", activity), ("seq", sequence), ("cmp", component), ("cls", classes), ("stm", states)):
            with open(os.path.join(d, f"{r['id']}-{suffix}.puml"), "w", encoding="utf-8") as f: f.write(fn(r) + "\n")
            n += 1
        for extra in r.get("moreStates", []):
            with open(os.path.join(d, f"{r['id']}-stm-{slug(extra['subject'])}.puml"), "w", encoding="utf-8") as f: f.write(states(r, extra) + "\n")
            n += 1
    if not args.no_catalogs and not only:
        catalogs(reqs, base)
        if data:
            with open(os.path.join(HERE, "trello-cards.md"), "w", encoding="utf-8") as f: f.write(trello_md(data))
    print(f"{n} diagramas gerados para {len(only) or len(reqs)} requisitos em {base}")

if __name__ == "__main__": main()
