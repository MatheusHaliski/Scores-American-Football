/**
 * Corpo de erro da API (RFC 7807 + {@code code, message, details, correlationId, locale}), o mesmo em todo caminho do backend.
 * O formato é conferido contra as amostras gravadas pela API (docs/validation/golden/problem-401.json e problem-429.json, D-5).
 */
export type ProblemParams = Record<string, string | number | boolean | null>;

export interface ApiProblem {
  status: number;
  code: string;
  message: string;
  details: Record<string, unknown>;
  correlationId: string | null;
  locale: string | null;
  /** só no modo mock: argumentos da mensagem `errors:<code>` (o servidor já manda `message` traduzida) */
  params?: ProblemParams;
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
  get status(): number {
    return this.problem.status;
  }
  /** Erros por campo do 400 VALIDATION (`details.fields`: campo → mensagem). */
  get fields(): Record<string, string> {
    const f = this.problem.details.fields;
    if (!isRecord(f)) return {};
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(f)) if (typeof v === "string") out[k] = v;
    return out;
  }
  /** Segundos até poder tentar de novo (429/503: `details.retryAfterSeconds`), ou null. */
  get retryAfterSeconds(): number | null {
    const v = this.problem.details.retryAfterSeconds;
    return typeof v === "number" && Number.isFinite(v) ? v : null;
  }

  /** Erro com um código do catálogo `errors:` (mock e validações locais), no mesmo formato do backend. */
  static of(status: number, code: string, params?: ProblemParams, fields?: Record<string, string>): ApiError {
    return new ApiError({ status, code, message: "", details: fields ? { fields } : {}, correlationId: null, locale: null, params });
  }
  static badRequest(code: string, params?: ProblemParams, fields?: Record<string, string>): ApiError {
    return ApiError.of(400, code, params, fields);
  }
  static notFound(what: string): ApiError {
    return ApiError.of(404, `notFound.${what}`);
  }
  static conflict(code: string, params?: ProblemParams): ApiError {
    return ApiError.of(409, code, params);
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

/** Falha de rede como `ApiError` NETWORK (status 0). */
export function networkError(): ApiError {
  return new ApiError({ status: 0, code: NETWORK, message: "", details: {}, correlationId: null, locale: null });
}
