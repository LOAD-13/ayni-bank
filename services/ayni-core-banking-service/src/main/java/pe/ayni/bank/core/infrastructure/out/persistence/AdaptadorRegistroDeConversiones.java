package pe.ayni.bank.core.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeConversionesPort;

/**
 * La fila que une las dos patas de una conversion (HU-12). Los asientos viven en el libro mayor;
 * aqui solo se guarda la union y el tipo aplicado, y al leer se rehacen las patas desde el libro.
 */
@Repository
public class AdaptadorRegistroDeConversiones implements RegistroDeConversionesPort {

    private final ConversionJpaRepository filas;
    private final LibroMayorPort libro;

    AdaptadorRegistroDeConversiones(ConversionJpaRepository filas, LibroMayorPort libro) {
        this.filas = filas;
        this.libro = libro;
    }

    @Override
    public void registrar(Conversion c) {
        filas.save(new ConversionEntity(c.id(), c.usuarioId(), c.salida().id(), c.entrada().id(),
                c.importeOrigen().moneda().name(), c.importeOrigen().importe(),
                c.importeDestino().moneda().name(), c.importeDestino().importe(),
                c.tipo().solesPorDolar(), c.tipo().obtenidoEn(), c.tipo().enCache(), c.registradaEn()));
    }

    @Override
    public Optional<Conversion> buscarPorSalida(UUID movimientoSalidaId) {
        return filas.findByMovimientoSalidaId(movimientoSalidaId).map(f -> new Conversion(f.getId(),
                f.getUsuarioId(), pata(f.getMovimientoSalidaId()), pata(f.getMovimientoEntradaId()),
                new TipoDeCambioAplicado(f.getTipoAplicado(), f.getTipoObtenidoEn(), f.isTipoEnCache()),
                f.getRegistradaEn()));
    }

    private Movimiento pata(UUID movimientoId) {
        List<Asiento> asientos = libro.asientosDelMovimiento(movimientoId);
        return new Movimiento(movimientoId, TipoDeMovimiento.CONVERSION,
                unico(asientos, TipoDeAsiento.CARGO), unico(asientos, TipoDeAsiento.ABONO));
    }

    private static Asiento unico(List<Asiento> asientos, TipoDeAsiento tipo) {
        return asientos.stream().filter(a -> a.tipo() == tipo).findFirst().orElseThrow(() ->
                new IllegalStateException("La pata de la conversion no tiene " + tipo));
    }
}
