/* Cálculos de los simuladores de productos de la landing.
 *
 * Son referenciales: sirven para que la persona entienda el producto, no constituyen una oferta
 * y nunca tocan un saldo. Por eso, y solo aquí, se calcula con `number` (las potencias
 * fraccionarias no tienen versión exacta) y se redondea a céntimos al final. */

/** Tasa efectiva de un periodo a partir de la TEA, con año comercial de 360 días. */
export function tasaDelPeriodo(teaPorcentaje: number, dias: number): number {
  return Math.pow(1 + teaPorcentaje / 100, dias / 360) - 1;
}

const centimos = (valor: number) => Math.round(valor * 100) / 100;

/** Cuota fija mensual (método francés), como en los préstamos y las compras en cuotas. */
export function cuotaMensual(capital: number, teaPorcentaje: number, meses: number) {
  const tem = tasaDelPeriodo(teaPorcentaje, 30);
  const cuota = centimos(
    tem === 0 ? capital / meses : (capital * tem) / (1 - Math.pow(1 + tem, -meses)),
  );
  // El total sale de la cuota ya redondeada: es la cuenta que la persona puede rehacer.
  const total = centimos(cuota * meses);
  return { cuota, intereses: centimos(total - capital), total, tem: tem * 100 };
}

/** Lo que gana un depósito a plazo fijo al vencimiento. */
export function interesDeDeposito(capital: number, teaPorcentaje: number, dias: number) {
  const interes = capital * tasaDelPeriodo(teaPorcentaje, dias);
  return { interes: centimos(interes), total: centimos(capital + interes) };
}

/**
 * Valor de una inversión con un aporte inicial y aportes mensuales, año a año, a una
 * rentabilidad anual supuesta (no garantizada).
 */
export function proyeccionDeInversion(
  inicial: number,
  mensual: number,
  anios: number,
  rentabilidadAnual: number,
): { anio: number; aportado: number; valor: number }[] {
  const tem = tasaDelPeriodo(rentabilidadAnual, 30);
  let valor = inicial;
  const filas = [];
  for (let mes = 1; mes <= anios * 12; mes++) {
    valor = valor * (1 + tem) + mensual;
    if (mes % 12 === 0) {
      filas.push({
        anio: mes / 12,
        aportado: centimos(inicial + mensual * mes),
        valor: centimos(valor),
      });
    }
  }
  return filas;
}

/** IGV vigente en el Perú. */
export const IGV = 0.18;

/** Una factura: el valor de venta y el IGV que se le suma. */
export function factura(valorDeVenta: number) {
  const igv = centimos(valorDeVenta * IGV);
  return { valorDeVenta: centimos(valorDeVenta), igv, total: centimos(valorDeVenta + igv) };
}

/** Lo que queda de las ventas cobradas con QR después de la comisión. */
export function cobrosConQr(ventas: number, comisionPorcentaje: number) {
  const comision = centimos((ventas * comisionPorcentaje) / 100);
  return { comision, neto: centimos(ventas - comision) };
}

/** «1250.5» → «1250.50», para pasarlo a `formatearImporte`. */
export function aTexto(valor: number): string {
  return centimos(valor).toFixed(2);
}
