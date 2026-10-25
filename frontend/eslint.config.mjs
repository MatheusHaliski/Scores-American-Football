import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

// Next 16 tirou o `next lint` e passou a exportar flat config: sem FlatCompat (herdado do basquete).
const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    // Regras do React Compiler que chegaram com o eslint-config-next 16. O projeto não usa o compilador, e as cenas
    // R3F mutam de propósito objetos do three vindos de hooks. Ficam como aviso até a revisão dos pontos (herdado do basquete).
    rules: {
      "react-hooks/set-state-in-effect": "warn",
      "react-hooks/immutability": "warn",
      "react-hooks/refs": "warn",
    },
  },
  globalIgnores(["node_modules/**", ".next/**", "out/**", "build/**", "next-env.d.ts"]),
]);

export default eslintConfig;
