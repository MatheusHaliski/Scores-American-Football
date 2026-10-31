"use client";
/**
 * Os 22 atletas do campo 3D com instancing: uma malha por peça do corpo (capacete, grade, rosto, ombreiras, tronco, calça,
 * coxas, canelas, chuteiras, braços, números) para todos os atletas, e a cada quadro só as matrizes mudam (sem render do
 * React). As cores (camisa, calça, meião, capacete, pele, tinta do número) mudam por instância quando o roteiro troca.
 * LOD simples: de longe (câmera de cima) somem o rosto, a grade e os números, que nem apareceriam.
 */
import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useMemo, useRef } from "react";
import {
  BoxGeometry,
  BufferGeometry,
  CapsuleGeometry,
  CircleGeometry,
  Color,
  CylinderGeometry,
  InstancedBufferAttribute,
  InstancedMesh,
  Matrix4,
  MeshBasicMaterial,
  MeshLambertMaterial,
  MeshStandardMaterial,
  PlaneGeometry,
  SphereGeometry,
  TorusGeometry,
  Vector3,
  type Material,
} from "three";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import { athleteMatrices, PART_COUNT, PART_NAMES, poseOf, worldYaw, type PartName } from "@/lib/match/scene/athlete";
import { offenseToWorld } from "@/lib/field/world";
import { sampleTrack } from "@/lib/match/scene/track";
import { numberAtlas, numberCell } from "@/components/field/field-texture";
import type { StageRef } from "./stage";

const N = 22;

export interface TeamKit {
  jersey: string;
  pants: string;
  socks: string;
  helmet: string;
  numberInk: string;
}

/** Tons de pele variados (determinísticos pela vaga e pelo número). */
const SKIN = ["#f1c9a5", "#d9a77c", "#b97a4f", "#8d5a36", "#6a4025", "#e0b38d"];

function partGeometry(part: PartName): BufferGeometry {
  switch (part) {
    case "torso": {
      const g = new CylinderGeometry(0.24, 0.2, 0.5, 12);
      g.scale(0.68, 1, 1);
      return g;
    }
    case "pads": {
      const g = new SphereGeometry(1, 14, 8);
      g.scale(0.2, 0.12, 0.33);
      return g;
    }
    case "pelvis": {
      const g = new CylinderGeometry(0.2, 0.17, 0.24, 10);
      g.scale(0.75, 1, 1);
      return g;
    }
    case "thigh":
      return new CapsuleGeometry(0.088, 0.36, 3, 8);
    case "shin":
      return new CapsuleGeometry(0.065, 0.38, 3, 8);
    case "shoe":
      return new BoxGeometry(0.27, 0.09, 0.11);
    case "upperArm":
      return new CapsuleGeometry(0.07, 0.2, 3, 8);
    case "forearm":
      return new CapsuleGeometry(0.056, 0.22, 3, 8);
    case "helmet": {
      const g = new SphereGeometry(0.165, 16, 12);
      g.scale(1.1, 1, 0.96);
      return g;
    }
    case "face":
      return new SphereGeometry(0.12, 10, 8);
    case "facemask": {
      // duas barras horizontais em arco na frente do rosto e uma vertical no meio
      const bar = (y: number) => {
        const t = new TorusGeometry(0.155, 0.013, 5, 14, Math.PI);
        t.rotateX(Math.PI / 2);
        t.rotateY(Math.PI / 2);
        t.translate(0, y, 0);
        return t;
      };
      const mid = new CylinderGeometry(0.012, 0.012, 0.11, 5);
      mid.translate(0.155, -0.045, 0);
      return mergeGeometries([bar(0), bar(-0.08), mid]);
    }
    case "numberFront":
    case "numberBack": {
      const g = new PlaneGeometry(0.22, 0.22);
      g.setAttribute("aCell", new InstancedBufferAttribute(new Float32Array(N * 2), 2));
      return g;
    }
  }
}

function numberMaterial(): Material {
  const tex = numberAtlas();
  const m = new MeshBasicMaterial({ map: tex ?? undefined, transparent: true, alphaTest: 0.35, depthWrite: true, toneMapped: false });
  m.onBeforeCompile = (shader) => {
    shader.vertexShader = "attribute vec2 aCell;\n" + shader.vertexShader.replace("#include <uv_vertex>", "#include <uv_vertex>\n#ifdef USE_MAP\n vMapUv = vMapUv * 0.1 + aCell;\n#endif");
  };
  m.customProgramCacheKey = () => "jersey-number-atlas";
  return m;
}

const SHADOW_GEO = new CircleGeometry(0.45, 16);

export function Athletes({ stage, kits, cheap }: { stage: StageRef; kits: { home: TeamKit; away: TeamKit; homeClubId: string }; cheap: boolean }) {
  const meshes = useRef<Partial<Record<PartName, InstancedMesh | null>>>({});
  const shadows = useRef<InstancedMesh>(null);
  const camera = useThree((s) => s.camera);
  const geos = useMemo(() => Object.fromEntries(PART_NAMES.map((p) => [p, partGeometry(p)])) as Record<PartName, BufferGeometry>, []);
  const materials = useMemo(() => {
    const body = (rough = 0.7) => (cheap ? new MeshLambertMaterial() : new MeshStandardMaterial({ roughness: rough, metalness: 0 }));
    const num = numberMaterial();
    return {
      torso: body(0.75), pads: body(0.6), pelvis: body(0.8), thigh: body(0.8), shin: body(0.8), shoe: body(0.6), upperArm: body(0.75), forearm: body(0.7),
      helmet: cheap ? new MeshLambertMaterial() : new MeshStandardMaterial({ roughness: 0.25, metalness: 0.15 }),
      facemask: body(0.5), face: body(0.8), numberFront: num, numberBack: num,
    } as Record<PartName, Material>;
  }, [cheap]);
  useEffect(
    () => () => {
      for (const g of Object.values(geos)) g.dispose();
      const seen = new Set<Material>();
      for (const m of Object.values(materials)) {
        if (seen.has(m)) continue;
        seen.add(m);
        (m as MeshBasicMaterial).map?.dispose();
        m.dispose();
      }
    },
    [geos, materials],
  );
  const shadowMat = useMemo(() => new MeshBasicMaterial({ color: "#000000", transparent: true, opacity: 0.32, depthWrite: false }), []);
  useEffect(() => () => shadowMat.dispose(), [shadowMat]);

  // matrizes de trabalho (reaproveitadas a cada quadro)
  const work = useMemo(() => Object.fromEntries(PART_NAMES.map((p) => [p, Array.from({ length: PART_COUNT[p] }, () => new Matrix4())])) as Record<PartName, Matrix4[]>, []);
  const painted = useRef<unknown>(null);
  const tmp = useMemo(() => ({ m: new Matrix4(), zero: new Matrix4().makeScale(0, 0, 0), c: new Color(), v: new Vector3() }), []);

  useFrame(() => {
    const st = stage.current;
    const script = st?.script;
    const all = meshes.current;
    const visible = !!script;
    for (const p of PART_NAMES) if (all[p]) all[p]!.visible = visible;
    if (shadows.current) shadows.current.visible = visible;
    if (!script || !st) return;
    const t = st.time();
    const numbers = st.numbers;
    // cores e números: só quando o roteiro (e, com ele, quem é ataque e defesa) troca
    const sig = `${script.offenseClubId}|${script.slots.map((s) => s.side + s.role).join(",")}|${(numbers ?? []).join(",")}|${kits.home.jersey}|${kits.away.jersey}`;
    if (painted.current !== sig) {
      painted.current = sig;
      for (let i = 0; i < N; i++) {
        const club = script.slots[i].side === "O" ? script.offenseClubId : script.defenseClubId;
        const kit = club === kits.homeClubId ? kits.home : kits.away;
        const skin = SKIN[(i * 7 + (numbers?.[i] ?? i)) % SKIN.length];
        const set = (p: PartName, color: string) => {
          const mesh = all[p];
          if (!mesh) return;
          for (let k = 0; k < PART_COUNT[p]; k++) mesh.setColorAt(i * PART_COUNT[p] + k, tmp.c.set(color));
        };
        set("torso", kit.jersey);
        set("pads", kit.jersey);
        set("upperArm", kit.jersey);
        set("pelvis", kit.pants);
        set("thigh", kit.pants);
        set("shin", kit.socks);
        set("shoe", "#161616");
        set("forearm", skin);
        set("face", skin);
        set("helmet", kit.helmet);
        set("facemask", "#c8c8c8");
        set("numberFront", kit.numberInk);
        set("numberBack", kit.numberInk);
        const n = numbers?.[i];
        for (const p of ["numberFront", "numberBack"] as const) {
          const attr = geos[p].getAttribute("aCell") as InstancedBufferAttribute;
          const [u, v] = numberCell(n ?? 0);
          attr.setXY(i, u, v);
          attr.needsUpdate = true;
        }
      }
      for (const p of PART_NAMES) if (all[p]?.instanceColor) all[p]!.instanceColor!.needsUpdate = true;
    }
    // LOD: longe da câmera, sem os detalhes pequenos
    const [lx, lz] = offenseToWorld(script.los, script.dir, 0, 0);
    const far = camera.position.distanceTo(tmp.v.set(lx, 0, lz)) > 78;
    for (const p of ["face", "facemask", "numberFront", "numberBack"] as const) if (all[p]) all[p]!.visible = !far;
    for (let i = 0; i < N; i++) {
      const s = sampleTrack(script.tracks[i], t, script.slots[i].side === "O" ? 0 : Math.PI);
      const pose = poseOf(s);
      const [x, z] = offenseToWorld(script.los, script.dir, s.d, s.w);
      athleteMatrices(pose, x, z, worldYaw(s.face, script.dir), 1, work);
      for (const p of PART_NAMES) {
        const mesh = all[p];
        if (!mesh) continue;
        const hide = (p === "numberFront" || p === "numberBack") && numbers?.[i] == null;
        for (let k = 0; k < PART_COUNT[p]; k++) mesh.setMatrixAt(i * PART_COUNT[p] + k, hide ? tmp.zero : work[p][k]);
      }
      if (shadows.current) {
        tmp.m.makeRotationX(-Math.PI / 2).setPosition(x, 0.015, z);
        shadows.current.setMatrixAt(i, tmp.m);
      }
    }
    for (const p of PART_NAMES) if (all[p]) all[p]!.instanceMatrix.needsUpdate = true;
    if (shadows.current) shadows.current.instanceMatrix.needsUpdate = true;
  });

  return (
    <group>
      {PART_NAMES.map((p) => (
        <instancedMesh
          key={`${p}-${cheap}`}
          ref={(m) => {
            meshes.current[p] = m;
            // cor por instância: cria o buffer na hora (setColorAt precisa dele antes do primeiro desenho)
            if (m && !m.instanceColor) for (let k = 0; k < m.count; k++) m.setColorAt(k, new Color("#ffffff"));
          }}
          args={[geos[p], materials[p], N * PART_COUNT[p]]}
          frustumCulled={false}
        />
      ))}
      <instancedMesh ref={shadows} args={[SHADOW_GEO, shadowMat, N]} frustumCulled={false} renderOrder={1} />
    </group>
  );
}
