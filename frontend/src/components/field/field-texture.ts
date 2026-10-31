/**
 * Textura do gramado desenhada num canvas (sem imagem externa): faixas de 5 jardas, end zones nas cores das franquias com a
 * sigla, linhas de jarda, laterais e de fundo, números a cada 10 (com a seta para o gol mais perto), hash marks das regras e
 * as marcas junto às laterais. Uma malha só para o campo inteiro, em vez de centenas de faixas (cabe no celular).
 *
 * O canvas cobre as 120 × 53⅓ jardas: x da esquerda para a direita = jarda absoluta −10 a 110 (a casa à esquerda); y de cima
 * para baixo = do lado −z (longe da câmera de TV) ao lado +z. Toda medida vem de `lib/field/geometry`.
 */
import { CanvasTexture, SRGBColorSpace } from "three";
import type { Ruleset } from "@/lib/api/meta";
import { FIELD, HASH_FROM_SIDELINE_YD, hashYards, yardLines } from "@/lib/field/geometry";

/** Cores do campo: as mesmas dos tokens CSS (src/styles/tokens.css), que o WebGL não lê. */
export const FIELD_COLORS = {
  turf: "#2f6b33",
  turfStripe: "#357a3a",
  line: "#f7f5ee",
  firstDown: "#f2d21b",
  scrimmage: "#2f7fe0",
  endZoneHome: "#b8451a",
  endZoneAway: "#1f3f73",
  pylon: "#ff7a1a",
  goalpost: "#f3c623",
  apron: "#24542a",
} as const;

export interface FieldPaint {
  ruleset: Ruleset;
  homeColor?: string | null;
  awayColor?: string | null;
  homeLabel?: string | null;
  awayLabel?: string | null;
  labelInk?: { home: string; away: string };
}

/** Pixels por jarda: ~3 mil pixels no comprimento (as linhas de 4 polegadas ficam com ~3 px). */
export const PX_PER_YD = 26;

export function drawField(ctx: CanvasRenderingContext2D, p: FieldPaint): void {
  const S = PX_PER_YD;
  const W = FIELD.lengthYd * S;
  const H = FIELD.widthYd * S;
  const X = (absYd: number) => (absYd + FIELD.endZoneYd) * S;
  ctx.fillStyle = FIELD_COLORS.turf;
  ctx.fillRect(0, 0, W, H);
  for (let i = 0; i < FIELD.playYd / 5; i += 2) {
    ctx.fillStyle = FIELD_COLORS.turfStripe;
    ctx.fillRect(X(i * 5 + 5), 0, 5 * S, H);
  }
  // end zones
  ctx.fillStyle = p.homeColor || FIELD_COLORS.endZoneHome;
  ctx.fillRect(0, 0, X(0), H);
  ctx.fillStyle = p.awayColor || FIELD_COLORS.endZoneAway;
  ctx.fillRect(X(FIELD.playYd), 0, FIELD.endZoneYd * S, H);
  const label = (text: string | null | undefined, cx: number, rot: number, ink: string) => {
    if (!text) return;
    ctx.save();
    ctx.translate(cx, H / 2);
    ctx.rotate(rot);
    ctx.fillStyle = ink;
    ctx.globalAlpha = 0.92;
    ctx.font = `700 ${Math.round(6.5 * S)}px Oswald, "Arial Narrow", Arial, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(text.toUpperCase(), 0, 0, H * 0.82);
    ctx.restore();
  };
  // a sigla com o alto das letras para a linha de fundo (lê de pé para a câmera atrás do ataque que vai para aquela end zone)
  label(p.homeLabel, X(-FIELD.endZoneYd / 2), -Math.PI / 2, p.labelInk?.home ?? FIELD_COLORS.line);
  label(p.awayLabel, X(FIELD.playYd + FIELD.endZoneYd / 2), Math.PI / 2, p.labelInk?.away ?? FIELD_COLORS.line);

  const lineW = Math.max(2, (4 / 36) * S);
  ctx.fillStyle = FIELD_COLORS.line;
  // laterais e linhas de fundo (6 pés de largura na regra; aqui mais finas)
  const border = lineW * 2.4;
  ctx.fillRect(0, 0, W, border);
  ctx.fillRect(0, H - border, W, border);
  ctx.fillRect(0, 0, border, H);
  ctx.fillRect(W - border, 0, border, H);
  for (const l of yardLines()) ctx.fillRect(X(l.absYd) - (l.goal ? lineW : lineW / 2), 0, l.goal ? lineW * 2 : lineW, H);
  // hash marks das regras e as marcas a 1 jarda das laterais (2 pés cada)
  const hash = HASH_FROM_SIDELINE_YD[p.ruleset] * S;
  const mark = (2 / 3) * S;
  for (const yd of hashYards()) {
    const x = X(yd) - lineW / 2;
    for (const y of [hash, H - hash - mark, S, H - S - mark]) ctx.fillRect(x, y, lineW, mark);
  }
  // números (6 pés de altura, o topo a 9 jardas da lateral) e a seta para o gol mais perto
  ctx.font = `600 ${Math.round(2 * S * 1.05)}px Oswald, "Arial Narrow", Arial, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  const numY = 10 * S;
  for (const l of yardLines()) {
    if (l.label == null) continue;
    const x = X(l.absYd);
    const arrowDir = l.absYd < 50 ? -1 : l.absYd > 50 ? 1 : 0;
    for (const far of [false, true]) {
      ctx.save();
      ctx.translate(x, far ? numY : H - numY);
      if (far) ctx.rotate(Math.PI);
      ctx.fillStyle = FIELD_COLORS.line;
      ctx.globalAlpha = 0.95;
      ctx.fillText(String(l.label), 0, 0);
      if (arrowDir !== 0) {
        // lado de quem lê: no lado de longe o canvas está girado 180°
        const a = far ? -arrowDir : arrowDir;
        const ax = a * 1.75 * S;
        ctx.beginPath();
        ctx.moveTo(ax + a * 0.45 * S, -0.55 * S);
        ctx.lineTo(ax + a * 0.95 * S, -0.55 * S + 0.25 * S);
        ctx.lineTo(ax + a * 0.45 * S, -0.55 * S + 0.5 * S);
        ctx.closePath();
        ctx.fill();
      }
      ctx.restore();
    }
  }
}

/** Textura pronta (cor sRGB, anisotropia para a câmera rasante da TV). */
export function fieldTexture(p: FieldPaint, anisotropy = 8): CanvasTexture | null {
  if (typeof document === "undefined") return null;
  const canvas = document.createElement("canvas");
  canvas.width = FIELD.lengthYd * PX_PER_YD;
  canvas.height = Math.round(FIELD.widthYd * PX_PER_YD);
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  drawField(ctx, p);
  const tex = new CanvasTexture(canvas);
  tex.colorSpace = SRGBColorSpace;
  tex.anisotropy = anisotropy;
  return tex;
}

/** Atlas dos números de camisa 0–99 (10 × 10 células), brancos sobre transparente: a cor vem do material de cada time. */
export function numberAtlas(): CanvasTexture | null {
  if (typeof document === "undefined") return null;
  const cell = 96;
  const canvas = document.createElement("canvas");
  canvas.width = cell * 10;
  canvas.height = cell * 10;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  ctx.fillStyle = "#ffffff";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.font = `700 ${Math.round(cell * 0.78)}px Oswald, "Arial Narrow", Arial, sans-serif`;
  for (let n = 0; n < 100; n++) {
    const cx = (n % 10) * cell + cell / 2;
    const cy = Math.floor(n / 10) * cell + cell / 2;
    ctx.fillText(String(n), cx, cy + cell * 0.04, cell * 0.92);
  }
  const tex = new CanvasTexture(canvas);
  tex.colorSpace = SRGBColorSpace;
  return tex;
}

/** Célula do número no atlas, como deslocamento de UV (o atlas é 10 × 10; a linha 0 do canvas é o alto da textura). */
export function numberCell(n: number): [number, number] {
  const col = n % 10;
  const row = Math.floor(n / 10);
  return [col / 10, 1 - (row + 1) / 10];
}
