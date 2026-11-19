import type { Metadata } from "next";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";

export const metadata: Metadata = {
  title: "Trabaja con nosotros",
  description:
    "Cómo construimos Ayni Bank: Scrum, revisión de código, pruebas automáticas y accesibilidad desde el primer día.",
};

const PRACTICAS = [
  [
    "Sprints de dos semanas",
    "Scrum con planificación, revisión y retrospectiva. Cada historia tiene criterios de aceptación escritos antes de programar.",
  ],
  [
    "Nada entra sin revisión",
    "Cada cambio llega por pull request, lo revisa otra persona del equipo y solo se fusiona con la integración continua en verde.",
  ],
  [
    "Pruebas en cada cambio",
    "Unitarias, de arquitectura, de aceptación con Cucumber y de navegador con Playwright, más análisis de calidad en SonarCloud.",
  ],
  [
    "Accesible desde el inicio",
    "WCAG 2.1 AA verificado en cada pantalla con axe, y pruebas con personas reales en celular y computadora.",
  ],
] as const;

const TECNOLOGIA = [
  "Java 21 y Spring Boot",
  "Arquitectura hexagonal",
  "Next.js y React",
  "Python y FastAPI",
  "PostgreSQL",
  "RabbitMQ",
  "Docker",
  "AWS",
  "GitHub Actions",
] as const;

export default function TrabajaConNosotros() {
  return (
    <PaginaPublica
      titulo="Construimos el banco que nos hubiera gustado tener."
      bajada="Hoy no tenemos vacantes abiertas: Ayni es un proyecto de seis personas. Pero nuestro código y nuestra forma de trabajar están a la vista."
    >
      <Seccion titulo="Cómo trabajamos">
        <dl className="grid gap-5 sm:grid-cols-2">
          {PRACTICAS.map(([titulo, detalle]) => (
            <div key={titulo} className="rounded-[20px] border border-azul-200 bg-blanco p-7">
              <dt className="text-h3 font-bold text-azul-900">{titulo}</dt>
              <dd className="mt-2 text-body leading-[1.6] text-gris-700">{detalle}</dd>
            </div>
          ))}
        </dl>
      </Seccion>

      <Seccion titulo="Con qué lo construimos" fondo="blanco">
        <ul className="flex flex-wrap gap-3">
          {TECNOLOGIA.map((t) => (
            <li
              key={t}
              className="rounded-full border border-azul-200 bg-azul-050 px-4 py-2 text-small font-semibold text-azul-800"
            >
              {t}
            </li>
          ))}
        </ul>
      </Seccion>

      <Seccion
        titulo="Mira el código"
        bajada="El repositorio es público: arquitectura, decisiones registradas y cada pull request con su revisión."
      >
        <a
          href="https://github.com/LOAD-13/ayni-bank"
          className="inline-flex min-h-[48px] items-center rounded-full bg-azul-900 px-7 text-body font-bold text-blanco hover:bg-azul-800"
        >
          Ver Ayni Bank en GitHub <span aria-hidden="true">&nbsp;→</span>
        </a>
      </Seccion>
    </PaginaPublica>
  );
}
