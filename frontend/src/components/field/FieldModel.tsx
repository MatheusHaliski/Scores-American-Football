"use client";
/**
 * Campo 3D reutilizável (bloco 2, refatorado para a partida): gramado com a textura de `field-texture` (linhas, números, hash
 * marks e end zones com a cor e a sigla das franquias), entorno, pylons e traves. Uma malha para o campo inteiro; as traves
 * e os pylons são poucas peças. Usado pela tela inicial (`FieldScene`) e pelo campo da partida (`MatchField3D`).
 */
import { useEffect, useMemo } from "react";
import { FIELD, FIELD_M, GOALPOST_M, YARD_M, yardToX } from "@/lib/field/geometry";
import { FIELD_COLORS, fieldTexture, type FieldPaint } from "./field-texture";

export { FIELD_COLORS } from "./field-texture";

function Goalpost({ x, dir }: { x: number; dir: 1 | -1 }) {
  const r = 0.09;
  const half = GOALPOST_M.width / 2;
  const top = GOALPOST_M.crossbar + GOALPOST_M.upright;
  const offset = 2 * YARD_M; // o poste curvo sai de trás da linha de fundo; o travessão fica em cima dela
  return (
    <group position={[x, 0, 0]}>
      {/* poste: sobe atrás da linha de fundo e faz a curva até o travessão */}
      <mesh position={[dir * offset, GOALPOST_M.crossbar * 0.45, 0]}>
        <cylinderGeometry args={[r * 1.7, r * 1.7, GOALPOST_M.crossbar * 0.9, 10]} />
        <meshStandardMaterial color={FIELD_COLORS.goalpost} metalness={0.3} roughness={0.5} />
      </mesh>
      <mesh position={[dir * offset * 0.5, GOALPOST_M.crossbar * 0.93, 0]} rotation={[0, 0, Math.PI / 2]}>
        <cylinderGeometry args={[r * 1.5, r * 1.5, offset, 10]} />
        <meshStandardMaterial color={FIELD_COLORS.goalpost} metalness={0.3} roughness={0.5} />
      </mesh>
      <mesh position={[0, GOALPOST_M.crossbar, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[r, r, GOALPOST_M.width, 10]} />
        <meshStandardMaterial color={FIELD_COLORS.goalpost} metalness={0.3} roughness={0.5} />
      </mesh>
      {[-half, half].map((z) => (
        <mesh key={z} position={[0, (GOALPOST_M.crossbar + top) / 2, z]}>
          <cylinderGeometry args={[r, r, GOALPOST_M.upright, 10]} />
          <meshStandardMaterial color={FIELD_COLORS.goalpost} metalness={0.3} roughness={0.5} />
        </mesh>
      ))}
    </group>
  );
}

export interface FieldModelProps extends FieldPaint {
  /** material barato (Lambert) para aparelhos fracos */
  cheap?: boolean;
}

export function FieldModel({ ruleset, homeColor, awayColor, homeLabel, awayLabel, labelInk, cheap = false }: FieldModelProps) {
  const tex = useMemo(
    () => fieldTexture({ ruleset, homeColor, awayColor, homeLabel, awayLabel, labelInk }),
    // as tintas são derivadas das cores; a identidade do objeto não importa
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [ruleset, homeColor, awayColor, homeLabel, awayLabel, labelInk?.home, labelInk?.away],
  );
  useEffect(() => () => tex?.dispose(), [tex]);
  const halfW = FIELD_M.width / 2;
  return (
    <group>
      {/* entorno (fora das laterais e das linhas de fundo) */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.02, 0]}>
        <planeGeometry args={[FIELD_M.length + 30, FIELD_M.width + 24]} />
        <meshLambertMaterial color={FIELD_COLORS.apron} />
      </mesh>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]}>
        <planeGeometry args={[FIELD_M.length, FIELD_M.width]} />
        {cheap ? <meshLambertMaterial map={tex ?? undefined} color={tex ? "#ffffff" : FIELD_COLORS.turf} /> : <meshStandardMaterial map={tex ?? undefined} color={tex ? "#ffffff" : FIELD_COLORS.turf} roughness={0.95} />}
      </mesh>
      {/* pylons nos quatro cantos de cada end zone */}
      {[0, FIELD.playYd, -FIELD.endZoneYd, FIELD.playYd + FIELD.endZoneYd].flatMap((yd) =>
        [-halfW, halfW].map((z) => (
          <mesh key={`p${yd}:${z}`} position={[yardToX(yd), 0.23, z]}>
            <boxGeometry args={[0.1, 0.46, 0.1]} />
            <meshLambertMaterial color={FIELD_COLORS.pylon} />
          </mesh>
        )),
      )}
      <Goalpost x={-FIELD_M.length / 2} dir={-1} />
      <Goalpost x={FIELD_M.length / 2} dir={1} />
    </group>
  );
}

/** Faixa fina sobre o gramado (linhas de transmissão, traço do último lance). */
export function GroundStripe({ x, z = 0, length, width, color, y = 0.02, opacity = 1 }: { x: number; z?: number; length: number; width: number; color: string; y?: number; opacity?: number }) {
  return (
    <mesh position={[x, y, z]} rotation={[-Math.PI / 2, 0, 0]} renderOrder={2}>
      <planeGeometry args={[length, width]} />
      <meshBasicMaterial color={color} transparent={opacity < 1} opacity={opacity} depthWrite={false} toneMapped={false} />
    </mesh>
  );
}
