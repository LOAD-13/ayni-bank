package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;

/**
 * Implementa {@link RepositorioDeMetodoSegundoFactorPort} sobre JPA.
 *
 * <p>El secreto TOTP se cifra en reposo con AES-256-GCM antes de escribirse en la base de
 * datos y se descifra al reconstituir la entidad de dominio (ADR-0025).
 */
@Component
public class AdaptadorRepositorioDeMetodoSegundoFactor implements RepositorioDeMetodoSegundoFactorPort {

    private final MetodoSegundoFactorJpaRepository jpaRepository;
    private final CifradorDeDatosPort cifrador;

    public AdaptadorRepositorioDeMetodoSegundoFactor(MetodoSegundoFactorJpaRepository jpaRepository,
                                                     CifradorDeDatosPort cifrador) {
        this.jpaRepository = jpaRepository;
        this.cifrador = cifrador;
    }

    @Override
    public Optional<MetodoDeSegundoFactor> buscarPorUsuarioYTipo(UUID usuarioId, TipoDeSegundoFactor tipo) {
        return jpaRepository.findByUsuarioIdAndTipo(usuarioId, tipo)
                .map(this::aDominio);
    }

    @Override
    public List<MetodoDeSegundoFactor> listarPorUsuario(UUID usuarioId) {
        return jpaRepository.findByUsuarioId(usuarioId).stream()
                .map(this::aDominio)
                .toList();
    }

    @Override
    public MetodoDeSegundoFactor guardar(MetodoDeSegundoFactor metodo) {
        String secretoCifrado = metodo.secreto() != null ? cifrador.cifrar(metodo.secreto()) : null;
        MetodoSegundoFactorEntity entity = new MetodoSegundoFactorEntity(
                metodo.id(),
                metodo.usuarioId(),
                metodo.tipo(),
                secretoCifrado,
                metodo.creadoEn(),
                metodo.confirmadoEn()
        );
        jpaRepository.save(entity);
        return metodo;
    }

    private MetodoDeSegundoFactor aDominio(MetodoSegundoFactorEntity entity) {
        String secretoEnClaro = entity.getSecreto() != null ? cifrador.descifrar(entity.getSecreto()) : null;
        return MetodoDeSegundoFactor.reconstituir(
                entity.getId(),
                entity.getUsuarioId(),
                entity.getTipo(),
                secretoEnClaro,
                entity.getCreadoEn(),
                entity.getConfirmadoEn()
        );
    }
}

