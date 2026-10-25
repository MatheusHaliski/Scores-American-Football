"use client";
/**
 * Campo 3D do esqueleto (bloco 2): grama listrada de 5 em 5 jardas, end zones nas cores dos dois times, linhas de jarda,
 * hash marks das regras escolhidas, pylons, traves e as linhas de scrimmage (azul) e de primeira descida (amarela).
 * Toda medida vem de `lib/field/geometry` (testada). Render sob demanda: sem animação, a cena só redesenha quando a câmera
 * se move.
 */
import { Canvas } from "@react-three/fiber";
import { OrbitControls } from "@react-three/drei";
import { useMemo } from "react";
import type { Ruleset } from "@/lib/api/meta";
import { FIELD, FIELD_M, GOALPOST_M, YARD_M, downMarkers, hashYards, hashZ, yardLines, yardToX } from "@/lib/field/geometry";

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
} as const;

const LINE_W = (4 / 36) * YARD_M; // 4 polegadas
const LIFT = 0.01; // acima da grama, para não piscar (z-fighting)

function Stripe({ x, z = 0, length, width, color, y = LIFT }: { x: number; z?: number; length: number; width: number; color: string; y?: number }) {
  return (
    <mesh position={[x, y, z]} rotation={[-Math.PI / 2, 0, 0]}>
      <planeGeometry args={[length, width]} />
      <meshStandardMaterial color={color} roughness={0.95} />
    </mesh>
  );
}

function Goalpost({ x, dir }: { x: number; dir: 1 | -1 }) {
  const base = 3 * YARD_M; // poste curvo recuado atrás da linha de fundo, simplificado
  const r = 0.09;
  const half = GOALPOST_M.width / 2;
  const top = GOALPOST_M.crossbar + GOALPOST_M.upright;
  const mat = <meshStandardMaterial color={FIELD_COLORS.goalpost} metalness={0.3} roughness={0.5} />;
  return (
    <group position={[x + dir * (base / 3), 0, 0]}>
      <mesh position={[0, GOALPOST_M.crossbar / 2, 0]}>
        <cylinderGeometry args={[r * 1.6, r * 1.6, GOALPOST_M.crossbar, 12]} />
        {mat}
      </mesh>
      <mesh position={[0, GOALPOST_M.crossbar, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[r, r, GOALPOST_M.width, 12]} />
        {mat}
      </mesh>
      {[-half, half].map((z) => (
        <mesh key={z} position={[0, (GOALPOST_M.crossbar + top) / 2, z]}>
          <cylinderGeometry args={[r, r, GOALPOST_M.upright, 12]} />
          {mat}
        </mesh>
      ))}
    </group>
  );
}

function Field({ ruleset, ballOn, toGo }: { ruleset: Ruleset; ballOn: number; toGo: number }) {
  const lines = useMemo(() => yardLines(), []);
  const hashes = useMemo(() => hashYards(), []);
  const hz = hashZ(ruleset);
  const markers = downMarkers(ballOn, toGo);
  const halfW = FIELD_M.width / 2;
  const hashLen = (2 / 3) * YARD_M; // 2 pés
  const ezX = (FIELD.playYd / 2 + FIELD.endZoneYd / 2) * YARD_M;

  return (
    <group>
      {/* grama: faixas alternadas de 5 jardas */}
      {Array.from({ length: FIELD.playYd / 5 }, (_, i) => (
        <Stripe key={i} x={yardToX(i * 5 + 2.5)} length={5 * YARD_M} width={FIELD_M.width} color={i % 2 ? FIELD_COLORS.turfStripe : FIELD_COLORS.turf} y={0} />
      ))}
      <Stripe x={-ezX} length={FIELD_M.endZone} width={FIELD_M.width} color={FIELD_COLORS.endZoneHome} y={0} />
      <Stripe x={ezX} length={FIELD_M.endZone} width={FIELD_M.width} color={FIELD_COLORS.endZoneAway} y={0} />
      {/* arredores (fora das linhas laterais e de fundo) */}
      <Stripe x={0} length={FIELD_M.length + 12} width={FIELD_M.width + 12} color={FIELD_COLORS.turf} y={-0.02} />

      {/* linhas de jarda, laterais e de fundo */}
      {lines.map((l) => (
        <Stripe key={l.absYd} x={l.x} length={l.goal ? LINE_W * 2 : LINE_W} width={FIELD_M.width} color={FIELD_COLORS.line} />
      ))}
      {[-halfW, halfW].map((z) => (
        <Stripe key={z} x={0} z={z} length={FIELD_M.length} width={LINE_W * 3} color={FIELD_COLORS.line} />
      ))}
      {[-FIELD_M.length / 2, FIELD_M.length / 2].map((x) => (
        <Stripe key={x} x={x} length={LINE_W * 3} width={FIELD_M.width} color={FIELD_COLORS.line} />
      ))}

      {/* hash marks das regras escolhidas e as marcas junto às laterais */}
      {hashes.flatMap((yd) =>
        [-hz, hz, -(halfW - YARD_M / 3), halfW - YARD_M / 3].map((z) => (
          <Stripe key={`${yd}:${z}`} x={yardToX(yd)} z={z} length={LINE_W} width={hashLen} color={FIELD_COLORS.line} />
        )),
      )}

      {/* scrimmage (azul) e primeira descida (amarela) */}
      <Stripe x={markers.scrimmageX} length={LINE_W * 2.5} width={FIELD_M.width} color={FIELD_COLORS.scrimmage} y={LIFT * 2} />
      {!markers.goalToGo && <Stripe x={markers.firstDownX} length={LINE_W * 2.5} width={FIELD_M.width} color={FIELD_COLORS.firstDown} y={LIFT * 2} />}

      {/* pylons nos quatro cantos de cada end zone */}
      {[0, FIELD.playYd, -FIELD.endZoneYd, FIELD.playYd + FIELD.endZoneYd].flatMap((yd) =>
        [-halfW, halfW].map((z) => (
          <mesh key={`p${yd}:${z}`} position={[yardToX(yd), 0.23, z]}>
            <boxGeometry args={[0.1, 0.46, 0.1]} />
            <meshStandardMaterial color={FIELD_COLORS.pylon} />
          </mesh>
        )),
      )}

      <Goalpost x={-FIELD_M.length / 2} dir={-1} />
      <Goalpost x={FIELD_M.length / 2} dir={1} />
    </group>
  );
}

export interface FieldSceneProps {
  ruleset?: Ruleset;
  /** jarda absoluta da bola (0–100) para as linhas de transmissão */
  ballOn?: number;
  toGo?: number;
  label: string;
}

export default function FieldScene({ ruleset = "PRO", ballOn = 25, toGo = 10, label }: FieldSceneProps) {
  return (
    <div role="img" aria-label={label} className="h-full w-full">
      <Canvas frameloop="demand" dpr={[1, 2]} camera={{ position: [0, 64, 86], fov: 38, near: 0.5, far: 500 }} gl={{ antialias: true }}>
        <color attach="background" args={["#0a0805"]} />
        <hemisphereLight args={["#fff4e0", "#1a2a14", 0.9]} />
        <directionalLight position={[30, 60, 20]} intensity={1.6} />
        <Field ruleset={ruleset} ballOn={ballOn} toGo={toGo} />
        <OrbitControls enablePan={false} minDistance={25} maxDistance={150} maxPolarAngle={Math.PI / 2.15} target={[0, 0, 0]} />
      </Canvas>
    </div>
  );
}
