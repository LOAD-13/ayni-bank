/* Pagina de espera del andamiaje.
 *
 * La landing publica real (HU con su diseno aprobado en el prototipo) llega en
 * su propia historia. Lo que esta pagina demuestra hoy es que la cadena de
 * tokens funciona de punta a punta: `docs/marca/design-tokens.md` ->
 * `src/styles/tokens.css` -> utilidades de Tailwind -> pagina renderizada.
 * Ni un solo hexadecimal escrito aqui. */

export default function Home() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-6 px-6 py-16">
      <p className="text-caption font-semibold tracking-widest text-dorado-700 uppercase">
        Banco peruano
      </p>

      <h1 className="text-h1 font-bold text-azul-700 sm:text-display">Ayni Bank</h1>

      <p className="text-body text-gris-700">
        Banca 100 % digital para personas naturales en Peru. Sin comision de mantenimiento y con
        cuenta remunerada de devengo diario.
      </p>

      <div className="rounded-md border border-azul-200 bg-azul-050 p-6 shadow-sm">
        <h2 className="text-h3 font-semibold text-azul-800">Aplicacion en construccion</h2>
        <p className="mt-2 text-small text-gris-700">
          El andamiaje esta listo. Las pantallas del prototipo se implementan historia a historia,
          empezando por el registro.
        </p>
      </div>
    </main>
  );
}
