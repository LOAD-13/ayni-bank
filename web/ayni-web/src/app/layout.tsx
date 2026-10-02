import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";

import "./globals.css";

// Inter con subconjunto latino, que es el que cubre el castellano. `swap`
// muestra el texto con la fuente del sistema mientras Inter carga: nunca deja
// la pagina en blanco esperando una tipografia.
const inter = Inter({
  subsets: ["latin"],
  display: "swap",
  variable: "--fuente-inter",
});

export const metadata: Metadata = {
  title: {
    default: "Ayni Bank",
    template: "%s · Ayni Bank",
  },
  description:
    "Banca 100 % digital para personas naturales en Peru. Sin comision de mantenimiento y con cuenta remunerada de devengo diario.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  // No se fija maximumScale ni userScalable: impedir el zoom rompe el criterio
  // 1.4.4 de WCAG 2.1 AA, que exige poder ampliar el texto hasta el 200 %.
  themeColor: "#064475",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    // `lang="es"` no es decorativo: es lo que permite al lector de pantalla
    // elegir la voz correcta. Criterio 3.1.1 de WCAG.
    <html lang="es" className={inter.variable}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
