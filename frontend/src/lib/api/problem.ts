/**
 * Corpo de erro da API (RFC 7807 + {@code code, message, details, correlationId, locale}), o mesmo em todo caminho do backend.
 * O formato é conferido contra a amostra gravada pela API (docs/validation/golden/problem-401.json, D-5).
 */
export interface ApiProblem {
  status: number;
  code: string;
  message: string;
  details: Record<string, unknown>;
  correlationId: string | null;
  locale: string | null;
}

export class ApiError extends Error {
  readonly problem: ApiProblem;
  constructor(problem: ApiProblem) {
    super(`${problem.code} (${problem.status})`);
    this.name = "ApiError";
    this.problem = problem;
  }
  get code(): string {
    return this.problem.code;
  }
}

/** Erro de rede (sem resposta) ou de contrato (resposta fora do formato). */
export const NETWORK = "NETWORK";
export const UNKNOWN = "UNKNOWN";

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v);
const str = (v: unknown): string | null => (typeof v === "string" && v.length > 0 ? v : null);

/** Lê o corpo de erro; campos ausentes viram valores neutros, nunca lança. `status` vem da resposta HTTP. */
export function problemFromBody(status: number, body: unknown): ApiProblem {
  const b = isRecord(body) ? body : {};
  return {
    status,
    code: str(b.code) ?? UNKNOWN,
    message: str(b.message) ?? str(b.detail) ?? "",
    details: isRecord(b.details) ? b.details : {},
    correlationId: str(b.correlationId),
    locale: str(b.locale),
  };
}
