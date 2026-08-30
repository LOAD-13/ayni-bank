package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.RefreshToken;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSesionesPort;

/** Implementa {@link RepositorioDeSesionesPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeSesiones implements RepositorioDeSesionesPort {

    private final DesafioJpaRepository desafios;
    private final RefreshTokenJpaRepository tokens;
    private final Clock reloj;

    AdaptadorRepositorioDeSesiones(DesafioJpaRepository desafios,
                                   RefreshTokenJpaRepository tokens,
                                   Clock reloj) {
        this.desafios = desafios;
        this.tokens = tokens;
        this.reloj = reloj;
    }

    @Override
    public void guardarDesafio(DesafioDeSegundoFactor desafio) {
        desafios.save(new DesafioEntity(desafio.id(), desafio.usuarioId(),
                reloj.instant(), desafio.expiraEn()));
    }

    @Override
    public Optional<DesafioDeSegundoFactor> buscarDesafio(UUID desafioId) {
        return desafios.findByIdAndConsumidoEnIsNull(desafioId)
                .map(fila -> new DesafioDeSegundoFactor(
                        fila.getId(), fila.getUsuarioId(), fila.getExpiraEn()));
    }

    @Override
    @Transactional
    public void consumirDesafio(UUID desafioId) {
        desafios.findById(desafioId).ifPresent(fila -> fila.consumir(reloj.instant()));
    }

    @Override
    public RefreshToken guardarToken(RefreshToken token) {
        tokens.save(new RefreshTokenEntity(
                token.id(), token.familiaId(), token.usuarioId(), token.huella(),
                token.emitidoEn(), token.expiraEn(), token.consumidoEn()));

        return token;
    }

    @Override
    public Optional<RefreshToken> buscarTokenPorHuella(String huella) {
        return tokens.findByHuellaAndInvalidadoEnIsNull(huella)
                .map(fila -> RefreshToken.reconstituir(
                        fila.getId(), fila.getFamiliaId(), fila.getUsuarioId(),
                        fila.getHuella(), fila.getEmitidoEn(), fila.getExpiraEn(),
                        fila.getConsumidoEn()));
    }

    @Override
    @Transactional
    public void invalidarFamilia(UUID familiaId) {
        tokens.invalidarFamilia(familiaId, reloj.instant());
    }
}
