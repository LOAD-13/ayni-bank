package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;

/** Implementa {@link RegistroDeIdempotenciaPort} sobre JPA. */
@Repository
public class AdaptadorRegistroDeIdempotencia implements RegistroDeIdempotenciaPort {

    private final OperacionIdempotenteJpaRepository repositorio;
    private final Clock reloj;

    AdaptadorRegistroDeIdempotencia(OperacionIdempotenteJpaRepository repositorio,
                                    Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public Optional<UUID> resultadoDe(UUID claveDeIdempotencia) {
        return repositorio.findById(claveDeIdempotencia)
                .map(OperacionIdempotenteEntity::getResultadoId);
    }

    @Override
    public void recordar(UUID claveDeIdempotencia, UUID resultado) {
        repositorio.save(new OperacionIdempotenteEntity(
                claveDeIdempotencia, resultado, reloj.instant()));
    }
}
