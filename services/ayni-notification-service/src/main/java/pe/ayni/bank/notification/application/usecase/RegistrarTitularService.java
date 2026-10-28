package pe.ayni.bank.notification.application.usecase;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.in.RegistrarTitularUseCase;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeTitularesPort;

@Service
public class RegistrarTitularService implements RegistrarTitularUseCase {

    private final RepositorioDeTitularesPort titulares;
    private final Clock reloj;

    public RegistrarTitularService(RepositorioDeTitularesPort titulares, Clock reloj) {
        this.titulares = titulares;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void registrar(Titular titular) {
        titulares.guardar(titular, reloj.instant());
    }
}
