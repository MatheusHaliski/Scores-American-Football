/**
 * Padrão do `config.matcher` do proxy do DevAuthGate. O Next exige o literal estático em `proxy.ts`;
 * esta cópia é a que se testa (tests/brand/metadata.test.mts confere que as duas são iguais).
 * Fica de fora: o próprio gate, a rota que o valida, os assets do Next e arquivos estáticos por extensão
 * (imagens, fontes, modelos 3D, JSON, texto, XML e o manifest PWA).
 */
export const GATE_MATCHER =
  "/((?!gate|api/dev-gate|_next/static|_next/image|favicon\\.ico|.*\\.(?:png|jpg|jpeg|webp|svg|ico|gif|woff2?|ttf|glb|gltf|json|txt|xml|webmanifest)$).*)";

/** O caminho passa pelo gate? (mesma semântica de âncora inteira do matcher do Next) */
export function gateApplies(pathname: string): boolean {
  return new RegExp(`^${GATE_MATCHER}$`).test(pathname);
}
