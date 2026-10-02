package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;

/** Implementa {@link PistaDeAuditoriaPort} sobre JPA. */
@Repository
public class AdaptadorPistaDeAuditoria implements PistaDeAuditoriaPort {

    private final EventoAuditoriaJpaRepository repositorio;
    private final Clock reloj;

    AdaptadorPistaDeAuditoria(EventoAuditoriaJpaRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public void registrar(TipoDeEventoDeAcceso tipo, UUID usuarioId,
                          HuellaDeCliente cliente) {
        repositorio.save(new EventoAuditoriaEntity(
                tipo.name(), usuarioId, cliente.ip(), cliente.agenteDeUsuario(),
                reloj.instant()));
    }
}
