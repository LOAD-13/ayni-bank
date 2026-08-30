package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;

/** Implementa {@link RepositorioDeControlDeAccesoPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeControlDeAcceso
        implements RepositorioDeControlDeAccesoPort {

    private final ControlDeAccesoJpaRepository repositorio;
    private final Clock reloj;

    AdaptadorRepositorioDeControlDeAcceso(ControlDeAccesoJpaRepository repositorio,
                                          Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    /**
     * Devuelve el control limpio si el usuario no tiene fila.
     *
     * <p>No se crea la fila en la lectura: quien nunca ha fallado no necesita ocupar
     * espacio en esta tabla, y crearla al leer convertiria un ingreso correcto en una
     * escritura innecesaria.
     */
    @Override
    public ControlDeAcceso cargar(UUID usuarioId) {
        return repositorio.findById(usuarioId)
                .map(fila -> ControlDeAcceso.reconstituir(
                        fila.getUsuarioId(),
                        fila.getFallosConsecutivos(),
                        fila.getBloqueadoHasta()))
                .orElseGet(() -> ControlDeAcceso.limpio(usuarioId));
    }

    @Override
    public void guardar(ControlDeAcceso control) {
        repositorio.save(new ControlDeAccesoEntity(
                control.usuarioId(),
                (short) control.fallosConsecutivos(),
                control.bloqueadoHasta(),
                reloj.instant()));
    }
}
