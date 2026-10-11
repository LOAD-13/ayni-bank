package pe.ayni.bank.notification.domain.port.in;

import pe.ayni.bank.notification.domain.model.Titular;

/** Incorpora o actualiza a un titular en la proyeccion local. Repetirlo no hace dano. */
public interface RegistrarTitularUseCase {

    void registrar(Titular titular);
}
