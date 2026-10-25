import { NextResponse, type NextRequest } from "next/server";
import { GATE_COOKIE, GATE_PATH, gateEnabled, verifyGateToken } from "@/lib/devgate/token";

/**
 * DevAuthGate: nenhuma tela (inclusive login e cadastro) abre sem passar pelo gate (Google + PIN).
 * Sem cookie válido → /gate?next=<caminho pedido>. Configuração em `.env.example` (DEV_GATE_*).
 */
export async function proxy(req: NextRequest) {
  if (!gateEnabled()) return NextResponse.next();
  const claims = await verifyGateToken(req.cookies.get(GATE_COOKIE)?.value, process.env.DEV_GATE_SECRET);
  if (claims) return NextResponse.next();
  const url = req.nextUrl.clone();
  url.pathname = GATE_PATH;
  url.search = "";
  url.searchParams.set("next", `${req.nextUrl.pathname}${req.nextUrl.search}`);
  return NextResponse.redirect(url);
}

export const config = {
  // tudo, menos o próprio gate, a rota que o valida e arquivos estáticos (assets do Next, imagens, fontes, ícones,
  // manifest PWA). Literal exigido pelo Next; cópia testada em lib/devgate/matcher.ts.
  matcher: ["/((?!gate|api/dev-gate|_next/static|_next/image|favicon\\.ico|.*\\.(?:png|jpg|jpeg|webp|svg|ico|gif|woff2?|ttf|glb|gltf|json|txt|xml|webmanifest)$).*)"],
};
