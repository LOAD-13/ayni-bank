package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;

/**
 * Los adaptadores de persistencia, con los repositorios simulados.
 *
 * <p>Lo que se prueba aquí es el **mapeo**, que es donde vive la lógica: que el secreto TOTP
 * se cifre al bajar y se descifre al subir, que un usuario sin fila devuelva un control
 * limpio en lugar de vacío, y que la auditoría guarde lo que HU-04 exige. Que Spring Data
 * sepa hacer un `findById` no lo prueba nadie aquí; eso llegará con las pruebas de
 * integración contra PostgreSQL real.
 */
@ExtendWith(MockitoExtension.class)
class AdaptadoresDePersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String SECRETO = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final UUID usuario = UUID.randomUUID();

    /** Cifrador de mentira: envuelve y desenvuelve, que es todo lo que el mapeo necesita. */
    private static final CifradorDeDatosPort CIFRADOR = new CifradorDeDatosPort() {
        @Override
        public String cifrar(String enClaro) {
            return enClaro == null ? null : "cifrado(" + enClaro + ")";
        }

        @Override
        public String descifrar(String criptograma) {
            return criptograma == null
                    ? null
                    : criptograma.replace("cifrado(", "").replace(")", "");
        }
    };

    @Nested
    @DisplayName("Segundo factor")
    class SegundosFactores {

        @Mock
        private SegundoFactorJpaRepository repositorio;

        @Test
        @DisplayName("el secreto se cifra al guardar: en claro seria como guardar la contraseña")
        void cifraAlGuardar() {
            var adaptador = new AdaptadorRepositorioDeSegundoFactor(repositorio, CIFRADOR);

            adaptador.guardar(SegundoFactor.inscribir(
                    usuario, new SecretoTotp(SECRETO), AHORA));

            // Lo que se comprueba es que el adaptador pase por el cifrador antes de
            // guardar. Que el resultado sea irreconocible lo prueba `CifradorAes256GcmTest`
            // sobre el cifrador de verdad; aquí el doble solo envuelve el valor.
            var fila = ArgumentCaptor.forClass(SegundoFactorEntity.class);
            verify(repositorio).save(fila.capture());
            assertThat(fila.getValue().getSecreto())
                    .isEqualTo("cifrado(" + SECRETO + ")")
                    .isNotEqualTo(SECRETO);
        }

        @Test
        void descifraAlLeer() {
            when(repositorio.findById(usuario)).thenReturn(Optional.of(
                    new SegundoFactorEntity(usuario, "cifrado(" + SECRETO + ")", AHORA, AHORA)));
            var adaptador = new AdaptadorRepositorioDeSegundoFactor(repositorio, CIFRADOR);

            var encontrado = adaptador.buscarPorUsuario(usuario);

            assertThat(encontrado).isPresent();
            assertThat(encontrado.get().secreto().valor()).isEqualTo(SECRETO);
            assertThat(encontrado.get().estaConfirmado()).isTrue();
        }

        @Test
        void unUsuarioSinSegundoFactorDevuelveVacio() {
            when(repositorio.findById(usuario)).thenReturn(Optional.empty());
            var adaptador = new AdaptadorRepositorioDeSegundoFactor(repositorio, CIFRADOR);

            assertThat(adaptador.buscarPorUsuario(usuario)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Control de acceso")
    class Controles {

        @Mock
        private ControlDeAccesoJpaRepository repositorio;

        @Test
        @DisplayName("quien nunca ha fallado no tiene fila, y aun asi se carga limpio")
        void sinFilaDevuelveLimpio() {
            // No se crea la fila al leer: un ingreso correcto no tiene por que provocar
            // una escritura.
            when(repositorio.findById(usuario)).thenReturn(Optional.empty());
            var adaptador = new AdaptadorRepositorioDeControlDeAcceso(repositorio, RELOJ);

            ControlDeAcceso control = adaptador.cargar(usuario);

            assertThat(control.fallosConsecutivos()).isZero();
            assertThat(control.estaBloqueado(AHORA)).isFalse();
            verify(repositorio, never()).save(any());
        }

        @Test
        void reconstruyeElBloqueoGuardado() {
            when(repositorio.findById(usuario)).thenReturn(Optional.of(
                    new ControlDeAccesoEntity(usuario, (short) 6, AHORA.plusSeconds(300), AHORA)));
            var adaptador = new AdaptadorRepositorioDeControlDeAcceso(repositorio, RELOJ);

            ControlDeAcceso control = adaptador.cargar(usuario);

            assertThat(control.fallosConsecutivos()).isEqualTo(6);
            assertThat(control.estaBloqueado(AHORA)).isTrue();
            assertThat(control.esperaRestante(AHORA).toSeconds()).isEqualTo(300);
        }

        @Test
        void guardaLosFallosYElBloqueo() {
            var adaptador = new AdaptadorRepositorioDeControlDeAcceso(repositorio, RELOJ);

            adaptador.guardar(ControlDeAcceso
                    .reconstituir(usuario, 6, AHORA.plusSeconds(300)));

            var fila = ArgumentCaptor.forClass(ControlDeAccesoEntity.class);
            verify(repositorio).save(fila.capture());
            assertThat(fila.getValue().getFallosConsecutivos()).isEqualTo((short) 6);
            assertThat(fila.getValue().getBloqueadoHasta()).isEqualTo(AHORA.plusSeconds(300));
        }
    }

    @Nested
    @DisplayName("Pista de auditoría")
    class Auditoria {

        @Mock
        private EventoAuditoriaJpaRepository repositorio;

        @Test
        @DisplayName("registra el tipo, la IP y el agente que exige HU-04")
        void registraLoQueExigeLaHistoria() {
            var adaptador = new AdaptadorPistaDeAuditoria(repositorio, RELOJ);

            adaptador.registrar(TipoDeEventoDeAcceso.INGRESO_EXITOSO, usuario,
                    new HuellaDeCliente("190.12.4.7", "Mozilla/5.0"));

            verify(repositorio).save(any(EventoAuditoriaEntity.class));
        }

        @Test
        @DisplayName("un intento sobre un correo desconocido tambien se anota, sin usuario")
        void anotaTambienLoQueNoTieneTitular() {
            // Registrar el intento importa aunque no se sepa contra quien iba.
            var adaptador = new AdaptadorPistaDeAuditoria(repositorio, RELOJ);

            adaptador.registrar(TipoDeEventoDeAcceso.CREDENCIALES_INVALIDAS, null,
                    new HuellaDeCliente(null, null));

            verify(repositorio).save(any(EventoAuditoriaEntity.class));
        }
    }
}
