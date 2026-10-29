package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.ControlesDeTarjeta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoDeTarjeta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;
import pe.ayni.bank.core.infrastructure.out.crypto.CifradorDePan;

/**
 * Implementa {@link RepositorioDeTarjetasPort} sobre JPA (HU-09 y HU-10). Cifra el PAN al
 * guardar y lo descifra al leer: fuera de este adaptador el dominio ve un {@link Pan} y la base,
 * un criptograma. Al emitir la tarjeta crea tambien su fila de controles (HU-10).
 */
@Repository
public class AdaptadorRepositorioDeTarjetas implements RepositorioDeTarjetasPort {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

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

    @Override
    public ControlesDeTarjeta controlesDe(Tarjeta tarjeta, Instant ahora) {
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT limite_diario, compras_por_internet FROM core.control_tarjeta WHERE tarjeta_id = ?",
                tarjeta.id());
        BigDecimal limite = (BigDecimal) fila.get("limite_diario");
        boolean internet = Boolean.TRUE.equals(fila.get("compras_por_internet"));

        LocalDate hoyEnLima = ahora.atZone(LIMA).toLocalDate();
        Instant inicioDelDia = hoyEnLima.atStartOfDay(LIMA).toInstant();
        Instant finDelDia = hoyEnLima.plusDays(1).atStartOfDay(LIMA).toInstant();

        BigDecimal consumido = jdbc.queryForObject("""
                SELECT COALESCE(SUM(importe), 0)
                FROM core.consumo_tarjeta
                WHERE tarjeta_id = ? AND registrado_en >= ? AND registrado_en < ?""",
                BigDecimal.class,
                tarjeta.id(), Timestamp.from(inicioDelDia), Timestamp.from(finDelDia));

        return new ControlesDeTarjeta(
                tarjeta.estado(),
                tarjeta.pan().ultimos4(),
                Moneda.PEN,
                new Dinero(limite, Moneda.PEN),
                new Dinero(consumido == null ? BigDecimal.ZERO : consumido, Moneda.PEN),
                internet);
    }

    @Override
    public void actualizarEstado(UUID tarjetaId, EstadoDeTarjeta estado) {
        jdbc.update("UPDATE core.tarjeta SET estado = ? WHERE id = ?", estado.name(), tarjetaId);
    }

    @Override
    public void actualizarControles(UUID tarjetaId, Dinero limiteDiario,
                                    boolean comprasPorInternet, Instant ahora) {
        jdbc.update("""
                UPDATE core.control_tarjeta
                SET limite_diario = ?, compras_por_internet = ?, actualizado_en = ?
                WHERE tarjeta_id = ?""",
                limiteDiario.importe(), comprasPorInternet, Timestamp.from(ahora), tarjetaId);
    }

    @Override
    public void registrarConsumo(UUID tarjetaId, UUID movimientoId, Dinero importe,
                                 boolean porInternet, String comercio, Instant registradoEn) {
        jdbc.update("""
                INSERT INTO core.consumo_tarjeta
                    (id, tarjeta_id, movimiento_id, importe, moneda, por_internet, comercio, registrado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), tarjetaId, movimientoId,
                importe.importe(), importe.moneda().name(),
                porInternet, comercio, Timestamp.from(registradoEn));
    }

    private Tarjeta aDominio(TarjetaEntity e) {
        return new Tarjeta(e.id(), e.cuentaId(), e.usuarioId(), cifrador.descifrar(e.panCifrado()),
                YearMonth.from(e.venceEn()), EstadoDeTarjeta.valueOf(e.estado()), e.emitidaEn());
    }
}
