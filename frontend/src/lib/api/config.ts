/** `NEXT_PUBLIC_API_MODE=mock|live` (padrão mock: o front roda sem servidor) e a base da API REST. */
export type ApiMode = "mock" | "live";

export const API_MODE: ApiMode = process.env.NEXT_PUBLIC_API_MODE === "live" ? "live" : "mock";
export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";
