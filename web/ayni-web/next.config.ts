import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // `standalone` emite un servidor autocontenido con solo las dependencias que
  // el build alcanza. Es lo que permite que la imagen final no lleve
  // node_modules entero: menos superficie que escanear y menos que descargar en
  // la Raspberry Pi.
  output: "standalone",

  // La cabecera delata la version del framework a cualquiera que mire la
  // respuesta. No aporta nada y facilita el trabajo del atacante.
  poweredByHeader: false,

  // El navegador nunca habla directamente con los servicios: todo pasa por el
  // gateway, que es donde viven la validacion del JWT, el CORS y el rate
  // limiting. Ver §3.4 del documento de diseno.
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",
  },
};

export default nextConfig;
