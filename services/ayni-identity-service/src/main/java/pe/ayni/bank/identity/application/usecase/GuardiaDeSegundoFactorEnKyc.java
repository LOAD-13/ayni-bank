package pe.ayni.bank.identity.application.usecase;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SegundoFactorNoVerificadoException;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;

/**
 * HU-22 (AYNI-130) · Impide avanzar a los pasos de captura y lectura del KYC hasta que el
 * titular haya verificado su segundo factor en el paso 2 de 6.
 */
final class GuardiaDeSegundoFactorEnKyc {

    private GuardiaDeSegundoFactorEnKyc() {
    }

    static void exigirVerificado(UUID titularId,
                                 RepositorioDeMetodoSegundoFactorPort metodosSegundoFactor,
                                 RepositorioDeSegundoFactorPort segundosFactores) {
        if (metodosSegundoFactor == null && segundosFactores == null) {
            return;
        }
        boolean verificadoPorMetodo = metodosSegundoFactor != null
                && metodosSegundoFactor.listarPorUsuario(titularId).stream()
                        .anyMatch(MetodoDeSegundoFactor::estaConfirmado);
        boolean verificadoPorTotp = segundosFactores != null
                && segundosFactores.buscarPorUsuario(titularId)
                        .filter(SegundoFactor::estaConfirmado)
                        .isPresent();
        if (!verificadoPorMetodo && !verificadoPorTotp) {
            throw new SegundoFactorNoVerificadoException();
        }
    }
}
