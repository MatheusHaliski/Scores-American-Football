"use client";
import { useState, type DragEvent } from "react";
import type { DepthIssue, DepthSlot } from "@/lib/api/types";
import { MAX_DEPTH } from "@/lib/api/types";
import { addToSlot, isAvailable, isOutOfPosition, moveInSlot, overallAt, removeFromSlot, reorder, ROLE_NATURALS, type DepthPlayerInfo, type Draft } from "@/lib/career/depth";
import { useT, useTd } from "@/i18n/provider";
import { cn } from "@/lib/utils/cn";
import { TierChip } from "@/components/roster/PlayerCardCompact";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { TextField } from "@/components/ui/Field";
import { IconAlert, IconArrowDown, IconArrowUp, IconClose, IconPlus } from "@/components/ui/icons";

export interface DepthChartBoardProps {
  slots: DepthSlot[];
  draft: Draft;
  onChange: (d: Draft) => void;
  players: Map<string, DepthPlayerInfo>;
  known: Map<string, number>;
  issues: DepthIssue[];
  /** chamado ao escolher alguém no seletor (o editor busca o overall por posição para o aviso de fora de posição) */
  onPick?: (playerId: string) => void;
  disabled?: boolean;
}

/**
 * Depth chart de uma unidade, vaga a vaga (posições e papéis): profundidade, overall na vaga, aviso de fora de posição e de
 * indisponível; reordena arrastando (HTML5) ou pelas setas (teclado), tira e acrescenta pelo seletor do elenco.
 */
export function DepthChartBoard({ slots, draft, onChange, players, known, issues, onPick, disabled }: DepthChartBoardProps) {
  const t = useT("roster");
  const td = useTd();
  const [drag, setDrag] = useState<{ slot: string; index: number } | null>(null);
  const [over, setOver] = useState<string | null>(null);
  const [picking, setPicking] = useState<DepthSlot | null>(null);

  const slotName = (s: DepthSlot) => (s.role ? td("enums", `slot.${s.slot}`) : td("enums", `position.${s.slot}`));

  return (
    <>
      <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
        {slots.map((s) => {
          const ids = draft[s.slot] ?? [];
          const slotErrors = issues.filter((i) => i.slot === s.slot && i.code === "DEPTH_CHART_INCOMPLETE");
          return (
            <section key={s.slot} aria-label={slotName(s)} className={cn("min-w-0 rounded-[var(--radius-lg)] border bg-arena-2 p-3", slotErrors.length ? "border-bad/60" : "border-border")}>
              <header className="mb-2 flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="flex flex-wrap items-baseline gap-x-2 font-heading font-semibold">
                    <span className="text-led">{td("enums", `slotShort.${s.slot}`)}</span>
                    <span className="truncate text-[0.85rem] text-text-soft">{slotName(s)}</span>
                  </h3>
                  <div className="mt-0.5 flex flex-wrap gap-1">
                    {s.role && <Badge tone="info" size="xs">{t("depth.role")}</Badge>}
                    <Badge size="xs">{s.minStarters > 0 ? t("depth.min", { n: s.minStarters }) : t("depth.optional")}</Badge>
                    {s.role && <span className="text-[0.68rem] text-text-dim">{(ROLE_NATURALS[s.slot] ?? []).join(" · ")}</span>}
                  </div>
                </div>
                <Button variant="ghost" size="sm" icon={<IconPlus size={16} />} onClick={() => setPicking(s)} disabled={disabled || ids.length >= MAX_DEPTH} aria-label={t("depth.addTo", { slot: slotName(s) })} className="shrink-0">
                  <span className="hidden sm:inline">{t("depth.add")}</span>
                </Button>
              </header>
              <ol
                className={cn("grid gap-1.5 rounded-[var(--radius-md)] transition-colors", over === s.slot && "bg-orange/10")}
                onDragOver={(e: DragEvent) => {
                  if (drag?.slot === s.slot) {
                    e.preventDefault();
                    setOver(s.slot);
                  }
                }}
                onDragLeave={() => setOver(null)}
              >
                {ids.length === 0 && <li className="rounded-[var(--radius-md)] border border-dashed border-border-strong px-3 py-3 text-center text-[0.8rem] text-text-dim">{t("depth.empty")}</li>}
                {ids.map((id, i) => {
                  const p = players.get(id);
                  const avail = isAvailable(p);
                  const oop = p ? avail && isOutOfPosition(p, s, known) : false;
                  const at = p ? overallAt(p, s, known) : null;
                  const name = p?.name ?? id.slice(0, 8);
                  return (
                    <li
                      key={id}
                      draggable={!disabled}
                      onDragStart={(e: DragEvent) => {
                        e.dataTransfer.setData("text/plain", id);
                        e.dataTransfer.effectAllowed = "move";
                        setDrag({ slot: s.slot, index: i });
                      }}
                      onDragEnd={() => {
                        setDrag(null);
                        setOver(null);
                      }}
                      onDrop={(e: DragEvent) => {
                        e.preventDefault();
                        setOver(null);
                        if (drag && drag.slot === s.slot && drag.index !== i) onChange(reorder(draft, s.slot, drag.index, i));
                        setDrag(null);
                      }}
                      className={cn("flex min-w-0 cursor-grab items-center gap-2 rounded-[var(--radius-md)] border bg-pitch px-2 py-1.5 active:cursor-grabbing", !avail ? "border-bad/60" : oop ? "border-warn/50" : "border-border")}
                      title={t("depth.drag")}
                    >
                      <span className={cn("grid size-6 shrink-0 place-items-center rounded-full font-display text-[0.75rem]", i < Math.max(1, s.minStarters) ? "bg-orange text-white" : "bg-arena-4 text-text-soft")}>{i + 1}</span>
                      {p && <TierChip tier={p.tier} overall={at ?? p.overall} size="sm" />}
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[0.88rem] leading-tight">{name}</span>
                        <span className="flex flex-wrap items-center gap-x-1.5 text-[0.7rem] text-text-dim">
                          {p && (
                            <span>
                              {p.position} #{p.jerseyNumber}
                            </span>
                          )}
                          {p && at !== null && at !== p.overall && <span>{t("depth.natural", { n: p.overall })}</span>}
                          {!avail && <span className="text-[var(--bad-text)]">{t("depth.unavailable")}</span>}
                          {oop && (
                            <span className="inline-flex items-center gap-0.5 text-warn" title={t("depth.oopHint")}>
                              <IconAlert size={12} /> {t("depth.oop")}
                            </span>
                          )}
                        </span>
                      </span>
                      <span className="flex shrink-0 items-center">
                        <button type="button" className="touch grid place-items-center rounded text-text-muted hover:bg-arena-3 hover:text-text disabled:opacity-30" onClick={() => onChange(moveInSlot(draft, s.slot, i, -1))} disabled={disabled || i === 0} aria-label={t("depth.up", { name })}>
                          <IconArrowUp size={16} />
                        </button>
                        <button type="button" className="touch grid place-items-center rounded text-text-muted hover:bg-arena-3 hover:text-text disabled:opacity-30" onClick={() => onChange(moveInSlot(draft, s.slot, i, 1))} disabled={disabled || i === ids.length - 1} aria-label={t("depth.down", { name })}>
                          <IconArrowDown size={16} />
                        </button>
                        <button type="button" className="touch grid place-items-center rounded text-text-muted hover:bg-bad/20 hover:text-[var(--bad-text)] disabled:opacity-30" onClick={() => onChange(removeFromSlot(draft, s.slot, id))} disabled={disabled} aria-label={t("depth.remove", { name, slot: slotName(s) })}>
                          <IconClose size={16} />
                        </button>
                      </span>
                    </li>
                  );
                })}
              </ol>
              {slotErrors.length > 0 && <p className="mt-2 text-[0.78rem] text-[var(--bad-text)]">{t("depth.incomplete", { min: s.minStarters })}</p>}
            </section>
          );
        })}
      </div>
      <PlayerPicker
        slot={picking}
        slotName={picking ? slotName(picking) : ""}
        taken={picking ? (draft[picking.slot] ?? []) : []}
        players={players}
        known={known}
        onClose={() => setPicking(null)}
        onPick={(id) => {
          if (!picking) return;
          onChange(addToSlot(draft, picking.slot, id, MAX_DEPTH));
          onPick?.(id);
          setPicking(null);
        }}
      />
    </>
  );
}

function PlayerPicker({ slot, slotName, taken, players, known, onClose, onPick }: { slot: DepthSlot | null; slotName: string; taken: string[]; players: Map<string, DepthPlayerInfo>; known: Map<string, number>; onClose: () => void; onPick: (id: string) => void }) {
  const t = useT("roster");
  const [q, setQ] = useState("");
  const [lastSlot, setLastSlot] = useState<string | null>(null);
  if ((slot?.slot ?? null) !== lastSlot) {
    setLastSlot(slot?.slot ?? null);
    setQ("");
  }
  const list = slot
    ? [...players.values()].filter((p) => isAvailable(p) && !taken.includes(p.id) && (!q.trim() || `${p.name} ${p.position} ${p.jerseyNumber}`.toLocaleLowerCase().includes(q.trim().toLocaleLowerCase())))
    : [];
  const natural = (p: DepthPlayerInfo) => (slot ? !isOutOfPosition(p, slot, known) || p.position === slot.position : false);
  const naturals = list.filter(natural).sort((a, b) => b.overall - a.overall);
  const others = list.filter((p) => !natural(p)).sort((a, b) => a.position.localeCompare(b.position) || b.overall - a.overall);
  const row = (p: DepthPlayerInfo) => (
    <li key={p.id}>
      <button type="button" onClick={() => onPick(p.id)} className="flex w-full min-w-0 items-center gap-2 rounded-[var(--radius-md)] px-2 py-1.5 text-left hover:bg-arena-3">
        <TierChip tier={p.tier} overall={p.overall} size="sm" />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[0.9rem]">{p.name}</span>
          <span className="block text-[0.72rem] text-text-dim">
            {p.position}
            {p.secondaryPosition && ` / ${p.secondaryPosition}`} · #{p.jerseyNumber}
          </span>
        </span>
        <IconPlus size={16} className="shrink-0 text-text-dim" />
      </button>
    </li>
  );
  return (
    <Dialog open={!!slot} onClose={onClose} title={t("depth.pick.title", { slot: slotName })} size="sm">
      <div className="grid gap-3">
        <TextField label={t("depth.pick.search")} value={q} onChange={(e) => setQ(e.target.value)} type="search" data-autofocus />
        {list.length === 0 && <p className="text-sm text-text-muted">{t("depth.pick.none")}</p>}
        {naturals.length > 0 && (
          <div>
            <h4 className="mb-1 font-heading text-[0.7rem] uppercase tracking-[0.14em] text-led">{t("depth.pick.natural")}</h4>
            <ul className="grid gap-0.5">{naturals.map(row)}</ul>
          </div>
        )}
        {others.length > 0 && (
          <div>
            <h4 className="mb-1 font-heading text-[0.7rem] uppercase tracking-[0.14em] text-text-dim">{t("depth.pick.others")}</h4>
            <ul className="grid gap-0.5">{others.map(row)}</ul>
          </div>
        )}
      </div>
    </Dialog>
  );
}
