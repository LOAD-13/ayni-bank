package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Taller de la sesión 30 (AYNI-321): con el reloj y el azar inyectados por constructor, el servicio
 * se prueba sin base de datos, con el repositorio y el notificador (servicio externo) simulados
 * por Mockito, y la prueba sabe de antemano qué código se va a generar.
 */
@ExtendWith(MockitoExtension.class)
class GenerarDesafioCodigoConDependenciasInyectadasTest {

    private static final Instant AHORA = Instant.parse("2026-11-19T15:00:00Z");
    private static final long SEMILLA = 2026L;

    @Mock
    private RepositorioDeDesafioPorCodigoPort desafios;
    @Mock
    private RepositorioDeUsuariosPort usuarios;
    @Mock
    private NotificadorDeSegundoFactorPort notificador;
    @Mock
    private RepositorioDeSolicitudesPort solicitudes;

    private GenerarDesafioCodigoService servicio;
    private final UUID anaId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        servicio = new GenerarDesafioCodigoService(desafios, usuarios, notificador,
                Clock.fixed(AHORA, ZoneOffset.UTC), new Random(SEMILLA), solicitudes);
    }

    /** El código que el servicio va a sacar con la misma semilla. */
    private static String codigoEsperado() {
        return String.format("%06d", new Random(SEMILLA).nextInt(1_000_000));
    }

    private Usuario ana() {
        return Usuario.reconstituir(anaId, new CorreoElectronico("ana.quispe@ejemplo.pe"),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$v=19$m=16,t=2,p=1$c2FsdA$aGFzaA"),
                EstadoUsuario.ACTIVO, Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
    }

    @Test
    @DisplayName("Envía por correo exactamente el código generado y guarda solo su hash")
    void enviaElCodigoExacto() {
        when(solicitudes.titularDe(anaId)).thenReturn(Optional.of(anaId));
        when(usuarios.buscarPorId(anaId)).thenReturn(Optional.of(ana()));
        when(desafios.guardar(any())).thenAnswer(i -> i.getArgument(0));

        servicio.generar(anaId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        verify(notificador).enviarCodigoPorCorreo(new CorreoElectronico("ana.quispe@ejemplo.pe"), codigoEsperado());
        ArgumentCaptor<DesafioPorCodigo> guardado = ArgumentCaptor.forClass(DesafioPorCodigo.class);
        verify(desafios).guardar(guardado.capture());
        assertThat(guardado.getValue().hashCodigo())
                .isEqualTo(GenerarDesafioCodigoService.calcularHashSha256(anaId, codigoEsperado()))
                .doesNotContain(codigoEsperado());
    }

    @Test
    @DisplayName("Invalida el desafío pendiente anterior antes de guardar el nuevo")
    void invalidaElAnterior() {
        DesafioPorCodigo anterior = DesafioPorCodigo.generar(UUID.randomUUID(), anaId,
                TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash-anterior", AHORA.minusSeconds(60));
        when(solicitudes.titularDe(anaId)).thenReturn(Optional.of(anaId));
        when(usuarios.buscarPorId(anaId)).thenReturn(Optional.of(ana()));
        when(desafios.buscarUltimoPendiente(anaId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(anterior));
        when(desafios.guardar(any())).thenAnswer(i -> i.getArgument(0));

        servicio.generar(anaId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        ArgumentCaptor<DesafioPorCodigo> guardados = ArgumentCaptor.forClass(DesafioPorCodigo.class);
        verify(desafios, org.mockito.Mockito.times(2)).guardar(guardados.capture());
        List<DesafioPorCodigo> orden = guardados.getAllValues();
        assertThat(orden.get(0).id()).isEqualTo(anterior.id());
        assertThat(orden.get(0).hashCodigo()).isEqualTo("hash-anterior");
        assertThat(orden.get(1).id()).isNotEqualTo(anterior.id());
    }

    @Test
    @DisplayName("Ante una solicitud señuelo responde un desafío efímero sin guardar ni enviar nada")
    void senuelo() {
        UUID senuelo = UUID.randomUUID();
        when(solicitudes.titularDe(senuelo)).thenReturn(Optional.empty());
        when(usuarios.buscarPorId(senuelo)).thenReturn(Optional.empty());

        var resultado = servicio.generar(senuelo, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThat(resultado.codigo6Digitos()).isEqualTo(codigoEsperado());
        verify(desafios, never()).guardar(any());
        verifyNoInteractions(notificador);
    }
}
