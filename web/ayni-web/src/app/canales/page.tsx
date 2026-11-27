import {
  BookOpenText,
  CircleHelp,
  Mail,
  MessageSquareText,
  ShieldAlert,
  Smartphone,
} from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";

export const metadata: Metadata = {
  title: "Canales de atención",
  description:
    "Dónde te atiende Ayni Bank: la banca por internet, el centro de ayuda y el libro de reclamaciones.",
};

const CANALES = [
  {
    Icono: Smartphone,
    titulo: "Banca por internet",
    detalle:
      "Desde tu celular o computadora: consulta, transfiere, congela tu tarjeta y encuentra la ayuda de cada pantalla sin salir de la banca.",
    accion: { texto: "Ingresar a mi banca", href: "/ingresar" },
  },
  {
    Icono: CircleHelp,
    titulo: "Centro de ayuda",
    detalle:
      "Respuestas sobre la apertura, las transferencias, los intereses y la seguridad de tu cuenta.",
    accion: { texto: "Ver preguntas frecuentes", href: "/ayuda" },
  },
  {
    Icono: BookOpenText,
    titulo: "Libro de reclamaciones",
    detalle:
      "Si algo no salió como debía, regístralo. Te damos un número de reclamo y una constancia por correo, y te respondemos en un plazo máximo de 15 días hábiles.",
    accion: { texto: "Registrar un reclamo", href: "/libro-de-reclamaciones" },
  },
  {
    Icono: MessageSquareText,
    titulo: "Tu opinión",
    detalle:
      "Dentro de la banca, en «Ayuda», puedes contarnos qué tal te va con la banca en un cuestionario breve.",
    accion: { texto: "Ir a la banca", href: "/ingresar" },
  },
];

export default function CanalesDeAtencion() {
  return (
    <PaginaPublica
      titulo="Canales de atención"
      bajada="Ayni es un banco 100 % digital: sin agencias y sin colas. Te atendemos donde ya estás, en la web y en tu celular."
    >
      <Seccion titulo="Dónde te atendemos">
        <ul className="grid gap-5 md:grid-cols-2">
          {CANALES.map(({ Icono, titulo, detalle, accion }) => (
            <li
              key={titulo}
              className="flex flex-col rounded-[20px] border border-azul-200 bg-blanco p-7"
            >
              <span
                aria-hidden="true"
                className="flex h-12 w-12 items-center justify-center rounded-xl bg-azul-800 text-blanco"
              >
                <Icono className="h-6 w-6" />
              </span>
              <h3 className="mt-5 text-h3 font-bold text-azul-900">{titulo}</h3>
              <p className="mt-2 flex-1 text-body leading-[1.6] text-gris-700">{detalle}</p>
              <Link
                href={accion.href}
                className="mt-5 inline-flex min-h-[44px] w-fit items-center font-semibold text-azul-600 hover:underline"
              >
                {accion.texto} <span aria-hidden="true">&nbsp;→</span>
              </Link>
            </li>
          ))}
        </ul>
      </Seccion>

      <Seccion titulo="Cómo reconocer que somos nosotros" fondo="blanco">
        <div className="grid gap-5 md:grid-cols-2">
          <div className="flex gap-4 rounded-[20px] bg-azul-050 p-6">
            <Mail aria-hidden="true" className="mt-1 h-5 w-5 shrink-0 text-azul-700" />
            <p className="text-body leading-[1.6] text-gris-700">
              Nuestros correos llegan desde{" "}
              <strong className="text-gris-900">no-responder@ayni.pe</strong>: avisos de apertura,
              códigos de verificación y constancias. Es una dirección solo de envío; para
              escribirnos usa los canales de arriba.
            </p>
          </div>
          <div className="flex gap-4 rounded-[20px] bg-dorado-100 p-6">
            <ShieldAlert aria-hidden="true" className="mt-1 h-5 w-5 shrink-0 text-dorado-800" />
            <p className="text-body leading-[1.6] text-gris-900">
              <strong>Nunca te pediremos tu contraseña ni tus códigos</strong>, ni por correo, ni
              por teléfono, ni por redes. Si alguien lo hace en nombre de Ayni, no los des: cambia
              tu contraseña y congela tu tarjeta desde la banca.
            </p>
          </div>
        </div>
      </Seccion>

      <Seccion
        id="redes"
        titulo="Redes sociales"
        bajada="Hoy atendemos solo por la web y la banca, donde cada consulta queda registrada y protegida. Las cuentas oficiales de Ayni en redes se anunciarán en la Sala de prensa; desconfía de cualquier perfil que diga atender en nuestro nombre."
      >
        <Link
          href="/prensa"
          className="inline-flex min-h-[44px] items-center font-semibold text-azul-600 hover:underline"
        >
          Ir a la Sala de prensa <span aria-hidden="true">&nbsp;→</span>
        </Link>
      </Seccion>
    </PaginaPublica>
  );
}
