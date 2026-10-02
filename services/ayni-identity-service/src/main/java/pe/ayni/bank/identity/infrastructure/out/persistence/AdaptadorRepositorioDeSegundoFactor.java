package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;

/**
 * Implementa {@link RepositorioDeSegundoFactorPort} sobre JPA.
 *
 * <p>El cifrado del secreto ocurre aqui: el dominio razona sobre el secreto TOTP, no sobre
 * su criptograma. Que este cifrado en reposo es una decision de infraestructura.
 */
@Repository
public class AdaptadorRepositorioDeSegundoFactor implements RepositorioDeSegundoFactorPort {

    private final SegundoFactorJpaRepository repositorio;
    private final CifradorDeDatosPort cifrador;

    AdaptadorRepositorioDeSegundoFactor(SegundoFactorJpaRepository repositorio,
                                        CifradorDeDatosPort cifrador) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
    }

    @Override
    public Optional<SegundoFactor> buscarPorUsuario(UUID usuarioId) {
        return repositorio.findById(usuarioId).map(fila -> SegundoFactor.reconstituir(
                fila.getUsuarioId(),
                new SecretoTotp(cifrador.descifrar(fila.getSecreto())),
                fila.getCreadoEn(),
                fila.getConfirmadoEn()));
    }

    @Override
    public SegundoFactor guardar(SegundoFactor segundoFactor) {
        repositorio.save(new SegundoFactorEntity(
                segundoFactor.usuarioId(),
                cifrador.cifrar(segundoFactor.secreto().valor()),
                segundoFactor.creadoEn(),
                segundoFactor.confirmadoEn()));

        return segundoFactor;
    }
}
