import { fileURLToPath } from "node:url";

import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    coverage: {
      provider: "v8",
      reporter: ["text", "lcov"],
      // Se mide lo que tiene lógica. El layout y los ficheros de tipos son
      // declaraciones: cubrirlos sube el porcentaje sin decir nada sobre la
      // calidad de las pruebas.
      include: ["src/**/*.{ts,tsx}"],
      exclude: [
        "src/**/*.{test,spec}.{ts,tsx}",
        "src/**/*.d.ts",
        "src/app/layout.tsx",
        "src/styles/**",
        // Andamiaje de pruebas, no producto. Medir su cobertura mide las
        // pruebas de las pruebas.
        "src/test/**",
      ],
    },
  },
});
