package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.EstadoDeTarjeta;
import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;
import pe.ayni.bank.core.infrastructure.out.crypto.CifradorDePan;

/**
 * Implementa {@link RepositorioDeTarjetasPort} sobre JPA (HU-09). Cifra el PAN al guardar y lo
 * descifra al leer: fuera de este adaptador el dominio ve un {@link Pan} y la base, un
 * criptograma. Al emitir la tarjeta crea tambien su fila de controles (HU-10).
 */
@Repository
public class AdaptadorRepositorioDeTarjetas implements RepositorioDeTarjetasPort {

    private final TarjetaJpaRepository tarjetas;
    private final CifradorDePan cifrador;
    private final JdbcTemplate jdbc;

    AdaptadorRepositorioDeTarjetas(TarjetaJpaRepository tarjetas, CifradorDePan cifrador, JdbcTemplate jdbc) {
        this.tarjetas = tarjetas;
        this.cifrador = cifrador;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Tarjeta> deTitular(UUID usuarioId) {
        return tarjetas.findByUsuarioId(usuarioId).map(this::aDominio);
    }

    @Override
    public boolean existePan(Pan pan) {
        return tarjetas.existsByPanHuella(cifrador.huella(pan));
    }

    @Override
    public void guardar(Tarjeta t) {
        tarjetas.saveAndFlush(new TarjetaEntity(t.id(), t.cuentaId(), t.usuarioId(), cifrador.cifrar(t.pan()),
                cifrador.huella(t.pan()), t.pan().ultimos4(), t.venceEn().atEndOfMonth(), t.estado().name(),
                t.emitidaEn()));
        jdbc.update("INSERT INTO core.control_tarjeta (tarjeta_id) VALUES (?) ON CONFLICT DO NOTHING", t.id());
    }

    private Tarjeta aDominio(TarjetaEntity e) {
        return new Tarjeta(e.id(), e.cuentaId(), e.usuarioId(), cifrador.descifrar(e.panCifrado()),
                YearMonth.from(e.venceEn()), EstadoDeTarjeta.valueOf(e.estado()), e.emitidaEn());
    }
}
