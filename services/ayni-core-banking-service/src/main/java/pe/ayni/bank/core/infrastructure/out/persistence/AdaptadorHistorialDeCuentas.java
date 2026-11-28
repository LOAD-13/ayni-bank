package pe.ayni.bank.core.infrastructure.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.VersionDeCuenta;
import pe.ayni.bank.core.domain.port.out.HistorialDeCuentasPort;

/** Las versiones de una cuenta desde las tablas de Envers (HU-15, AYNI-280, ADR-0042). */
@Repository
public class AdaptadorHistorialDeCuentas implements HistorialDeCuentasPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<VersionDeCuenta> versionesDe(UUID cuentaId) {
        List<?> filas = AuditReaderFactory.get(em).createQuery()
                .forRevisionsOfEntity(CuentaEntity.class, false, true)
                .add(AuditEntity.id().eq(cuentaId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return filas.stream().map(f -> version((Object[]) f)).toList();
    }

    private static VersionDeCuenta version(Object[] fila) {
        CuentaEntity cuenta = (CuentaEntity) fila[0];
        RevisionDeAuditoria revision = (RevisionDeAuditoria) fila[1];
        VersionDeCuenta.Tipo tipo = switch ((RevisionType) fila[2]) {
            case ADD -> VersionDeCuenta.Tipo.ALTA;
            case MOD -> VersionDeCuenta.Tipo.CAMBIO;
            case DEL -> VersionDeCuenta.Tipo.BAJA;
        };
        // En una baja Envers solo guarda el id: los demas datos llegan nulos.
        return new VersionDeCuenta(revision.getId(), revision.getMomento(), revision.getActor(), tipo,
                cuenta.getNumero(),
                cuenta.getEstado() == null ? null : EstadoCuenta.valueOf(cuenta.getEstado()),
                cuenta.getMoneda() == null ? null : Moneda.valueOf(cuenta.getMoneda()));
    }
}
