"use client";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import { CAREER_NAME, COACH_NAME, COACH_STYLES, DIFFICULTIES, type ClubOption, type CoachStyle, type LeagueSummary, type WorldSummary } from "@/lib/api/types";
import type { Difficulty } from "@/lib/api/types";
import { useQuery } from "@/lib/hooks/useQuery";
import { fromCents, iso2, NATIONALITIES } from "@/lib/career/format";
import { useT, useTd } from "@/i18n/provider";
import { useFormat } from "@/i18n/format";
import { cn } from "@/lib/utils/cn";
import { useCareer } from "@/store/career";
import { useSession } from "@/store/session";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { RequireAuth } from "@/components/shell/RequireAuth";
import { ClubBadge } from "@/components/career/ClubBadge";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Segmented, SelectField, TextField } from "@/components/ui/Field";
import { ErrorState, InlineAlert, PageSkeleton, Skeleton } from "@/components/ui/States";
import { IconCheck, IconChevronLeft, IconChevronRight, IconSearch } from "@/components/ui/icons";

const STEPS = ["league", "club", "coach", "review"] as const;
type Step = (typeof STEPS)[number];

function Stepper({ step }: { step: number }) {
  const t = useT("career");
  return (
    <ol className="hide-scrollbar mb-6 flex items-center gap-1 overflow-x-auto sm:gap-2" aria-label={t("new.steps")}>
      {STEPS.map((s, i) => (
        <li key={s} className="flex shrink-0 items-center gap-1 sm:gap-2">
          <span className={cn("grid size-7 place-items-center rounded-full border-2 font-display text-sm", i < step ? "border-good bg-good text-white" : i === step ? "border-orange-deep bg-orange text-white" : "border-border-strong bg-arena-3 text-text-dim")} aria-current={i === step ? "step" : undefined}>
            {i < step ? <IconCheck size={14} /> : i + 1}
          </span>
          <span className={cn("font-heading text-[0.8rem] uppercase tracking-[0.1em]", i === step ? "text-text" : "text-text-dim", i !== step && "hidden sm:inline")}>{t(`new.step.${s}`)}</span>
          {i < STEPS.length - 1 && <span className="h-px w-4 bg-border-strong sm:w-8" aria-hidden />}
        </li>
      ))}
    </ol>
  );
}

function LeagueCard({ league, world, selected, onSelect }: { league: LeagueSummary; world: WorldSummary; selected: boolean; onSelect: () => void }) {
  const t = useT("career");
  const tc = useT("common");
  const f = useFormat();
  return (
    <button type="button" onClick={onSelect} aria-pressed={selected} disabled={!league.structureValid} className={cn("w-full rounded-[var(--radius-lg)] border bg-arena-2 p-4 text-left transition-colors disabled:opacity-50", selected ? "border-rim shadow-orange" : "border-border hover:border-border-strong")}>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="wood" size="xs">{league.shortName}</Badge>
        <Badge size="xs">{t("new.league.ruleset", { ruleset: tc(`ruleset.${league.ruleset === "COLLEGE" ? "COLLEGE" : "PRO"}`) })}</Badge>
      </div>
      <div className="mt-2 font-heading text-lg font-semibold">{league.name}</div>
      <div className="text-sm text-text-muted">{t("new.league.teams", { n: league.teamCount, conferences: league.conferenceCount, divisions: league.divisionsPerConference })}</div>
      <div className="mt-1 text-[0.8rem] text-text-dim">
        {t("new.league.world", { name: world.name })} · {t("new.league.cap", { value: f.money(fromCents(league.salaryCap)) })}
      </div>
    </button>
  );
}

function ClubButton({ club, selected, onSelect }: { club: ClubOption; selected: boolean; onSelect: () => void }) {
  const t = useT("career");
  return (
    <button type="button" onClick={onSelect} aria-pressed={selected} className={cn("flex w-full min-w-0 items-center gap-3 rounded-[var(--radius-md)] border p-2.5 text-left transition-colors", selected ? "border-rim bg-orange/10" : "border-border bg-pitch hover:border-border-strong")}>
      <ClubBadge club={club} size={40} />
      <span className="min-w-0 flex-1">
        <span className="block truncate font-heading font-semibold leading-tight">{club.name}</span>
        <span className="block truncate text-[0.75rem] text-text-dim">
          {t("new.club.prestige", { n: club.prestige })}
          {club.expectedWins != null && ` · ${t("new.club.expected", { n: club.expectedWins })}`}
        </span>
      </span>
      {selected && <IconCheck size={18} className="shrink-0 text-led" />}
    </button>
  );
}

function Wizard() {
  const t = useT("career");
  const tc = useT("common");
  const td = useTd();
  const f = useFormat();
  const router = useRouter();
  const params = useSearchParams();
  const user = useSession((s) => s.user);
  const setActive = useCareer((s) => s.setActiveCareer);
  const slotParam = Number(params.get("slot"));
  const slot = Number.isInteger(slotParam) && slotParam >= 1 && slotParam <= 5 ? slotParam : null;

  const [step, setStep] = useState(0);
  const [worldId, setWorldId] = useState<string | null>(null);
  const [pickedLeague, setLeague] = useState<LeagueSummary | null>(null);
  const [club, setClub] = useState<ClubOption | null>(null);
  const [filter, setFilter] = useState("");
  const [coach, setCoach] = useState({ name: user?.displayName ?? "", nationality: "BRA", style: "OFFENSIVE" as CoachStyle });
  const [name, setName] = useState("");
  const [difficulty, setDifficulty] = useState<Difficulty>("NORMAL");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // só mundos modelo começam carreira; o padrão vem primeiro
  const worlds = useQuery(async () => (await api.worlds.list()).filter((w) => w.kind === "TEMPLATE").sort((a, b) => Number(b.defaultTemplate) - Number(a.defaultTemplate)), []);
  const world = worlds.data?.find((w) => w.id === worldId) ?? worlds.data?.[0] ?? null;
  const leagues = useQuery(() => api.worlds.leagues(world!.id), [world?.id], !!world);
  // liga única já vem escolhida
  const league = pickedLeague ?? (leagues.data?.length === 1 ? leagues.data[0] : null);
  const clubs = useQuery(() => api.worlds.leagueClubs(world!.id, league!.id), [world?.id, league?.id], !!world && !!league);

  const defaultName = club ? t("new.defaultName", { club: club.shortName, year: new Date().getFullYear() }) : "";
  const careerName = (name.trim() || defaultName).replace(/\s+/g, " ");
  const nameError = careerName.length < CAREER_NAME.min || careerName.length > CAREER_NAME.max ? t("new.validation.length", { min: CAREER_NAME.min, max: CAREER_NAME.max }) : null;
  const coachError = coach.name.trim().length < COACH_NAME.min || coach.name.trim().length > COACH_NAME.max ? t("new.validation.length", { min: COACH_NAME.min, max: COACH_NAME.max }) : null;

  const canNext: Record<Step, boolean> = { league: !!league, club: !!club, coach: !coachError, review: !nameError };
  const current = STEPS[step];

  const filtered = useMemo(() => {
    const q = filter.trim().toLocaleLowerCase();
    if (!clubs.data) return null;
    const match = (c: ClubOption) => !q || `${c.name} ${c.city} ${c.shortName}`.toLocaleLowerCase().includes(q);
    return {
      conferences: clubs.data.conferences.map((c) => ({ ...c, divisions: c.divisions.map((d) => ({ ...d, clubs: d.clubs.filter(match) })).filter((d) => d.clubs.length) })).filter((c) => c.divisions.length),
      unassigned: clubs.data.unassigned.filter(match),
    };
  }, [clubs.data, filter]);

  async function submit() {
    if (!club || !league || !world || nameError || coachError) return;
    setError(null);
    setSaving(true);
    try {
      const created = await api.careers.create({ name: careerName, worldId: world.id, leagueId: league.id, clubId: club.id, coachName: coach.name.trim(), coachNationality: coach.nationality, coachStyle: coach.style, difficulty, slot });
      setActive(created.id);
      router.push(`/c/${created.id}`);
    } catch (e) {
      setError(describeError(e));
      setSaving(false);
    }
  }

  return (
    <>
      <PageHeader eyebrow={t("new.eyebrow")} title={t("new.title")} description={t("new.description")} back={{ href: "/careers", label: t("new.back") }} />
      <Stepper step={step} />

      {current === "league" && (
        <section aria-label={t("new.step.league")} className="grid gap-3">
          {worlds.loading || leagues.loading ? (
            <Skeleton className="h-28" />
          ) : worlds.error || leagues.error ? (
            <ErrorState message={worlds.error ?? leagues.error} onRetry={() => void (worlds.error ? worlds.refetch() : leagues.refetch())} />
          ) : (
            <>
              {(worlds.data?.length ?? 0) > 1 && (
                <SelectField label={t("new.world")} value={world?.id ?? ""} onChange={(e) => { setWorldId(e.target.value); setLeague(null); setClub(null); }}>
                  {worlds.data?.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
                </SelectField>
              )}
              {world && leagues.data?.length ? (
                leagues.data.map((l) => <LeagueCard key={l.id} league={l} world={world} selected={league?.id === l.id} onSelect={() => { if (league?.id !== l.id) setClub(null); setLeague(l); }} />)
              ) : (
                <InlineAlert tone="warn">{t("new.noLeague")}</InlineAlert>
              )}
            </>
          )}
        </section>
      )}

      {current === "club" && (
        <section aria-label={t("new.step.club")} className="grid gap-4">
          <TextField label={t("new.club.filter")} value={filter} onChange={(e) => setFilter(e.target.value)} type="search" placeholder={t("new.club.filterPlaceholder")} />
          {clubs.loading ? (
            <div className="grid gap-3 sm:grid-cols-2">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-16" />)}</div>
          ) : clubs.error || !filtered ? (
            <ErrorState message={clubs.error} onRetry={clubs.refetch} />
          ) : filtered.conferences.length === 0 && filtered.unassigned.length === 0 ? (
            <p className="flex items-center gap-2 text-text-muted"><IconSearch size={18} /> {t("new.club.none")}</p>
          ) : (
            <>
              {filtered.conferences.map((conf) => (
                <section key={conf.id ?? conf.name} aria-label={conf.name} className="grid gap-3">
                  <h2 className="font-heading text-sm uppercase tracking-[0.16em] text-led">
                    {conf.name} <span className="text-text-dim">· {conf.shortName}</span>
                  </h2>
                  <div className="grid gap-3 md:grid-cols-2">
                    {conf.divisions.map((d) => (
                      <div key={d.id ?? d.name} className="min-w-0 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-3">
                        <h3 className="mb-2 font-heading text-[0.8rem] uppercase tracking-[0.12em] text-text-muted">{d.name}</h3>
                        <div className="grid gap-2">
                          {d.clubs.map((c) => <ClubButton key={c.id} club={c} selected={club?.id === c.id} onSelect={() => setClub(c)} />)}
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              ))}
              {filtered.unassigned.length > 0 && (
                <div className="grid gap-2 sm:grid-cols-2">{filtered.unassigned.map((c) => <ClubButton key={c.id} club={c} selected={club?.id === c.id} onSelect={() => setClub(c)} />)}</div>
              )}
            </>
          )}
        </section>
      )}

      {current === "coach" && (
        <section aria-label={t("new.step.coach")} className="grid gap-4 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-4 sm:grid-cols-2">
          <TextField label={t("new.coach.name")} value={coach.name} maxLength={COACH_NAME.max} onChange={(e) => setCoach({ ...coach, name: e.target.value })} error={coach.name ? coachError : null} hint={t("new.validation.length", { min: COACH_NAME.min, max: COACH_NAME.max })} />
          <SelectField label={t("new.coach.nationality")} value={coach.nationality} onChange={(e) => setCoach({ ...coach, nationality: e.target.value })}>
            {NATIONALITIES.map((n) => <option key={n} value={n}>{f.region(iso2(n))}</option>)}
          </SelectField>
          <fieldset className="sm:col-span-2">
            <legend className="font-heading text-[0.8rem] uppercase tracking-[0.12em] text-text-muted">{t("new.coach.style")}</legend>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              {COACH_STYLES.map((code) => (
                <button key={code} type="button" onClick={() => setCoach({ ...coach, style: code })} aria-pressed={coach.style === code} className={cn("rounded-[var(--radius-md)] border p-3 text-left transition-colors", coach.style === code ? "border-rim bg-orange/10" : "border-border bg-pitch hover:border-border-strong")}>
                  <span className="block font-heading font-semibold">{td("enums", `coachStyle.${code}`)}</span>
                  <span className="block text-[0.8rem] text-text-dim">{td("enums", `coachStyle.${code}.hint`)}</span>
                </button>
              ))}
            </div>
          </fieldset>
        </section>
      )}

      {current === "review" && club && league && (
        <section aria-label={t("new.step.review")} className="grid gap-5 rounded-[var(--radius-lg)] border border-border bg-arena-2 p-4">
          <div className="flex items-center gap-3">
            <ClubBadge club={club} size={56} />
            <div className="min-w-0">
              <div className="eyebrow">{t("new.club.selected")}</div>
              <div className="truncate font-heading text-xl font-semibold">{club.name}</div>
              <div className="truncate text-sm text-text-muted">{league.name}</div>
            </div>
          </div>
          <TextField label={t("new.name")} value={name} placeholder={defaultName} maxLength={CAREER_NAME.max} onChange={(e) => setName(e.target.value)} error={name ? nameError : null} hint={t("new.nameHint", { min: CAREER_NAME.min, max: CAREER_NAME.max })} />
          <Segmented label={t("new.difficulty")} value={difficulty} onChange={setDifficulty} options={DIFFICULTIES.map((d) => ({ value: d, label: td("enums", `difficulty.${d}`) }))} />
          <p className="rounded-[var(--radius-md)] border border-border bg-pitch p-3 text-sm text-text-muted">
            {t("new.summary", { club: club.name, league: league.name, coach: coach.name.trim(), style: td("enums", `coachStyle.${coach.style}`), difficulty: td("enums", `difficulty.${difficulty}`) })}
            {slot != null && ` ${t("new.slot", { n: slot })}`}
          </p>
          {error && <InlineAlert tone="bad">{error}</InlineAlert>}
        </section>
      )}

      <div className="mt-6 flex justify-between gap-2">
        <Button variant="ghost" icon={<IconChevronLeft size={18} />} onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0 || saving}>
          {tc("actions.back")}
        </Button>
        {step < STEPS.length - 1 ? (
          <Button iconRight={<IconChevronRight size={18} />} onClick={() => setStep((s) => s + 1)} disabled={!canNext[current]}>
            {tc("actions.continue")}
          </Button>
        ) : (
          <Button variant="led" size="lg" onClick={() => void submit()} loading={saving} disabled={!canNext.review} icon={<IconCheck size={18} />}>
            {t("new.create")}
          </Button>
        )}
      </div>
    </>
  );
}

/** RF34 — nova carreira: liga do mundo padrão, franquia por conferência e divisão, head coach e estilo, dificuldade. */
export default function NewCareerPage() {
  return (
    <RequireAuth>
      <AppShell>
        <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6 sm:py-10">
          <Suspense fallback={<PageSkeleton rows={1} />}>
            <Wizard />
          </Suspense>
        </div>
      </AppShell>
    </RequireAuth>
  );
}
