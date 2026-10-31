"use client";
/**
 * Campo 3D da tela inicial (bloco 2): o campo reutilizável (`FieldModel`: gramado, linhas, números, hash marks das regras,
 * end zones, pylons e traves) com as linhas de scrimmage (azul) e de primeira descida (amarela). Toda medida vem de
 * `lib/field/geometry` (testada). Render sob demanda: sem animação, a cena só redesenha quando a câmera se move.
 */
import { Canvas } from "@react-three/fiber";
import { OrbitControls } from "@react-three/drei";
import type { Ruleset } from "@/lib/api/meta";
import { FIELD_M, YARD_M, downMarkers } from "@/lib/field/geometry";
import { FIELD_COLORS, FieldModel, GroundStripe } from "./FieldModel";

export { FIELD_COLORS } from "./FieldModel";

const LINE_W = (4 / 36) * YARD_M;

export interface FieldSceneProps {
  ruleset?: Ruleset;
  /** jarda absoluta da bola (0–100) para as linhas de transmissão */
  ballOn?: number;
  toGo?: number;
  label: string;
}

export default function FieldScene({ ruleset = "PRO", ballOn = 25, toGo = 10, label }: FieldSceneProps) {
  const markers = downMarkers(ballOn, toGo);
  return (
    <div role="img" aria-label={label} className="h-full w-full">
      <Canvas frameloop="demand" dpr={[1, 2]} camera={{ position: [0, 64, 86], fov: 38, near: 0.5, far: 500 }} gl={{ antialias: true }}>
        <color attach="background" args={["#0a0805"]} />
        <hemisphereLight args={["#fff4e0", "#1a2a14", 0.9]} />
        <directionalLight position={[30, 60, 20]} intensity={1.6} />
        <FieldModel ruleset={ruleset} />
        <GroundStripe x={markers.scrimmageX} length={LINE_W * 2.5} width={FIELD_M.width} color={FIELD_COLORS.scrimmage} />
        {!markers.goalToGo && <GroundStripe x={markers.firstDownX} length={LINE_W * 2.5} width={FIELD_M.width} color={FIELD_COLORS.firstDown} />}
        <OrbitControls enablePan={false} minDistance={25} maxDistance={150} maxPolarAngle={Math.PI / 2.15} target={[0, 0, 0]} />
      </Canvas>
    </div>
  );
}
