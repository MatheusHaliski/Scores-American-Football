"use client";
import { useState } from "react";
import type { AdvanceMode } from "@/lib/api/match-types";
import { useT } from "@/i18n/provider";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { IconForward, IconPlay, IconSkip, IconTarget, IconTimeout, IconFlag } from "@/components/ui/icons";

export interface MatchControlsProps {
  canAdvance: boolean;
  busy: boolean;
  timeoutsLeft: number;
  canTimeout: boolean;
  onAdvance: (mode: AdvanceMode) => void;
  onTimeout: () => void;
}

/**
 * Avanço da partida pelos modos do servidor (D-67): próxima jogada, fim do drive, fim do quarto, próxima decisão e simular até o fim
 * (com confirmação). Todos param na próxima chamada de jogada do manager e no fim. Tempo técnico com a bola morta.
 */
export function MatchControls({ canAdvance, busy, timeoutsLeft, canTimeout, onAdvance, onTimeout }: MatchControlsProps) {
  const t = useT("match");
  const [confirm, setConfirm] = useState(false);
  const off = !canAdvance || busy;
  return (
    <div className="flex flex-col gap-2 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-3" role="group" aria-label={t("live.controls.aria")}>
      <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
        <Button variant="led" size="lg" icon={<IconPlay size={18} />} onClick={() => onAdvance("PLAY")} disabled={off} loading={busy} className="col-span-2 sm:col-span-1">{t("live.controls.PLAY")}</Button>
        <Button variant="secondary" icon={<IconForward size={16} />} onClick={() => onAdvance("DRIVE")} disabled={off}>{t("live.controls.DRIVE")}</Button>
        <Button variant="secondary" icon={<IconFlag size={16} />} onClick={() => onAdvance("QUARTER")} disabled={off}>{t("live.controls.QUARTER")}</Button>
        <Button variant="secondary" icon={<IconTarget size={16} />} onClick={() => onAdvance("TO_DECISION")} disabled={off}>{t("live.controls.TO_DECISION")}</Button>
        <Button variant="outline" icon={<IconTimeout size={16} />} onClick={onTimeout} disabled={!canTimeout || busy || timeoutsLeft <= 0}>{t("live.controls.timeout", { n: timeoutsLeft })}</Button>
        <Button variant="ghost" icon={<IconSkip size={16} />} onClick={() => setConfirm(true)} disabled={off} className="col-span-2 border border-dashed border-border-strong sm:col-span-1 sm:ml-auto">{t("live.controls.FULL")}</Button>
      </div>
      <p className="text-[0.75rem] text-text-dim">{t("live.controls.hint")}</p>
      <ConfirmDialog open={confirm} onClose={() => setConfirm(false)} onConfirm={() => { setConfirm(false); onAdvance("FULL"); }} title={t("live.controls.fullConfirm.title")} body={t("live.controls.fullConfirm.body")} confirmLabel={t("live.controls.fullConfirm.confirm")} />
    </div>
  );
}
