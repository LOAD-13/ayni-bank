package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.TipoDeCambioConocido;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTipoDeCambioPort;

/** Implementa {@link RepositorioDeTipoDeCambioPort} sobre la tabla {@code tipo_cambio}. */
@Repository
public class AdaptadorRepositorioDeTipoDeCambio implements RepositorioDeTipoDeCambioPort {

    private final TipoCambioJpaRepository filas;

    AdaptadorRepositorioDeTipoDeCambio(TipoCambioJpaRepository filas) {
        this.filas = filas;
    }

    @Override
    public Optional<TipoDeCambioConocido> obtenidoDesde(Instant desde) {
        return filas.obtenidosDesde(desde, Limit.of(1)).stream().findFirst()
                .map(AdaptadorRepositorioDeTipoDeCambio::aDominio);
    }

    @Override
    public Optional<TipoDeCambioConocido> ultimoConocido() {
        return filas.masRecientes(Limit.of(1)).stream().findFirst()
                .map(AdaptadorRepositorioDeTipoDeCambio::aDominio);
    }

    // Transaccion propia: el upsert es una consulta @Modifying y las de Spring Data corren en
    // transacciones de solo lectura salvo que el metodo diga otra cosa.
    @Override
    @Transactional
    public void guardar(TipoDeCambioOficial tipo, Instant obtenidoEn) {
        filas.guardar(tipo.fecha(), tipo.compra(), tipo.venta(), tipo.fuente(), obtenidoEn);
    }

    private static TipoDeCambioConocido aDominio(TipoCambioEntity fila) {
        return new TipoDeCambioConocido(new TipoDeCambioOficial(fila.getFecha(), fila.getReferenciaCompra(),
                fila.getReferenciaVenta(), fila.getFuente()), fila.getObtenidoEn());
    }
}
