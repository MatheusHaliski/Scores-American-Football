import { cn } from "@/lib/utils/cn";
import { inkOn } from "@/lib/career/format";

/** Escudo simples da franquia: as duas cores e a sigla (o escudo 3D com capacete chega no bloco 8). */
export function ClubBadge({ club, size = 40, className }: { club: { shortName: string; name?: string; primaryColor: string; secondaryColor: string }; size?: number; className?: string }) {
  return (
    <span
      role="img"
      aria-label={club.name ?? club.shortName}
      className={cn("inline-grid shrink-0 place-items-center rounded-[28%] border-2 font-display leading-none", className)}
      style={{ width: size, height: size, background: club.primaryColor, borderColor: club.secondaryColor, color: inkOn(club.primaryColor), fontSize: Math.max(10, size * 0.32) }}
    >
      {club.shortName}
    </span>
  );
}
