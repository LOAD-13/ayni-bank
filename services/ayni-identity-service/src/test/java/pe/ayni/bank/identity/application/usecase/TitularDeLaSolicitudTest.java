package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/** La utilidad extraída en el taller de la sesión 29 (AYNI-321): titular real y solicitud señuelo. */
class TitularDeLaSolicitudTest {

    private final RepositorioDeSolicitudesPort solicitudes = mock(RepositorioDeSolicitudesPort.class);
    private final RepositorioDeUsuariosPort usuarios = mock(RepositorioDeUsuariosPort.class);
    private final UUID recibido = UUID.randomUUID();

    @Test
    @DisplayName("Si la solicitud tiene titular, el usuario real es el titular")
    void conTitular() {
        UUID titular = UUID.randomUUID();
        when(solicitudes.titularDe(recibido)).thenReturn(Optional.of(titular));

        assertThat(TitularDeLaSolicitud.de(recibido, solicitudes, usuarios))
                .isEqualTo(new TitularDeLaSolicitud(titular, false));
        verifyNoInteractions(usuarios);
    }

    @Test
    @DisplayName("Sin titular ni usuario con ese identificador, es un señuelo")
    void senuelo() {
        when(solicitudes.titularDe(recibido)).thenReturn(Optional.empty());
        when(usuarios.buscarPorId(recibido)).thenReturn(Optional.empty());

        assertThat(TitularDeLaSolicitud.de(recibido, solicitudes, usuarios))
                .isEqualTo(new TitularDeLaSolicitud(recibido, true));
    }

    @Test
    @DisplayName("Sin titular pero con un usuario que existe, es ese usuario y no un señuelo")
    void usuarioExistente() {
        when(solicitudes.titularDe(recibido)).thenReturn(Optional.empty());
        when(usuarios.buscarPorId(recibido)).thenReturn(Optional.of(mock(Usuario.class)));

        assertThat(TitularDeLaSolicitud.de(recibido, solicitudes, usuarios))
                .isEqualTo(new TitularDeLaSolicitud(recibido, false));
    }

    @Test
    @DisplayName("Sin repositorio de solicitudes nunca hay señuelo")
    void sinRepositorios() {
        assertThat(TitularDeLaSolicitud.de(recibido, null, null))
                .isEqualTo(new TitularDeLaSolicitud(recibido, false));
    }
}
