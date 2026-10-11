package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.CodigoDeConfirmacionIncorrectoException;
import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;
import pe.ayni.bank.identity.domain.model.ConfirmacionNoDisponibleException;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CuentaBloqueadaException;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.OperacionAConfirmar;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SinSegundoFactorException;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.ConfirmacionEmitida;
import pe.ayni.bank.identity.domain.model.ConfirmacionIniciada;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.VerificarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeConfirmacionesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;

/** HU-07 · ADR-0031: la confirmacion de una operacion con el segundo factor. */
class ConfirmarOperacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
    private static final HuellaDeCliente CLIENTE = new HuellaDeCliente("190.12.4.7", "Mozilla/5.0");
    private static final SecretoTotp SECRETO = new SecretoTotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    private static final String BUENO = "123456";

    private final UUID ana = UUID.randomUUID();
    private final OperacionAConfirmar operacion =
            new OperacionAConfirmar("00111000000001", new BigDecimal("200.00"), "PEN", UUID.randomUUID());

    private final Map<UUID, ConfirmacionDeOperacion> guardadas = new HashMap<>();
    private final Map<UUID, ControlDeAcceso> controlesGuardados = new HashMap<>();
    private final List<TipoDeEventoDeAcceso> eventos = new ArrayList<>();
    private final RepositorioDeMetodoSegundoFactorPort metodos = mock(RepositorioDeMetodoSegundoFactorPort.class);
    private final RepositorioDeSegundoFactorPort segundos = mock(RepositorioDeSegundoFactorPort.class);
    private final GenerarDesafioCodigoUseCase generar = mock(GenerarDesafioCodigoUseCase.class);
    private final VerificarDesafioCodigoUseCase verificarCodigo = mock(VerificarDesafioCodigoUseCase.class);
    private final GeneradorDeTotpPort totp = mock(GeneradorDeTotpPort.class);
    private final Reloj reloj = new Reloj();
    private ConfirmarOperacionService servicio;

    @BeforeEach
    void preparar() {
        RepositorioDeConfirmacionesPort repo = new RepositorioDeConfirmacionesPort() {
            @Override
            public void guardar(ConfirmacionDeOperacion c) {
                guardadas.put(c.id(), c);
            }

            @Override
            public Optional<ConfirmacionDeOperacion> buscar(UUID id) {
                return Optional.ofNullable(guardadas.get(id));
            }
        };
        RepositorioDeControlDeAccesoPort controles = new RepositorioDeControlDeAccesoPort() {
            @Override
            public ControlDeAcceso cargar(UUID usuarioId) {
                return controlesGuardados.getOrDefault(usuarioId, ControlDeAcceso.limpio(usuarioId));
            }

            @Override
            public void guardar(ControlDeAcceso control) {
                controlesGuardados.put(control.usuarioId(), control);
            }
        };
        servicio = new ConfirmarOperacionService(repo, metodos, segundos, totp, generar, verificarCodigo,
                controles, (tipo, usuario, cliente) -> eventos.add(tipo), c -> "jwt-" + c.huella(), reloj);
        when(metodos.listarPorUsuario(ana)).thenReturn(List.of());
        when(segundos.buscarPorUsuario(ana))
                .thenReturn(Optional.of(SegundoFactor.inscribir(ana, SECRETO, AHORA).confirmar(AHORA)));
        when(totp.verificar(eq(SECRETO), any(), any()))
                .thenAnswer(i -> BUENO.equals(i.<CodigoTotp>getArgument(1).valor()));
    }

    @Test
    @DisplayName("con la app autenticadora no se envia nada y el codigo correcto emite el token de esta operacion")
    void appAutenticadora() {
        ConfirmacionIniciada iniciada = servicio.iniciar(ana, operacion, CLIENTE);

        assertThat(iniciada.metodo()).isEqualTo(TipoDeSegundoFactor.APP_AUTENTICADORA);
        assertThat(iniciada.expiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        verify(generar, never()).generar(any(), any());

        ConfirmacionEmitida emitida = servicio.verificar(ana, iniciada.confirmacionId(), BUENO, CLIENTE);

        assertThat(emitida.token()).isEqualTo("jwt-" + operacion.huella());
        assertThat(emitida.toString()).doesNotContain("jwt-");
        assertThat(eventos).containsExactly(TipoDeEventoDeAcceso.OPERACION_CONFIRMADA);
    }

    @Test
    @DisplayName("con el correo como metodo se envia el codigo y se verifica contra ese desafio")
    void correo() {
        when(metodos.listarPorUsuario(ana)).thenReturn(List.of(MetodoDeSegundoFactor.inscribir(
                UUID.randomUUID(), ana, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, AHORA)));
        DesafioPorCodigo desafio = mock(DesafioPorCodigo.class);
        UUID desafioId = UUID.randomUUID();
        when(desafio.id()).thenReturn(desafioId);
        when(generar.generar(ana, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(new ResultadoGeneracionDesafio(desafio, "654321"));
        when(verificarCodigo.verificar(desafioId, "654321")).thenReturn(true);
        when(verificarCodigo.verificar(desafioId, "000000")).thenThrow(new CodigoDesafioInvalidoException());

        ConfirmacionIniciada iniciada = servicio.iniciar(ana, operacion, CLIENTE);
        assertThat(iniciada.metodo()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThatThrownBy(() -> servicio.verificar(ana, iniciada.confirmacionId(), "000000", CLIENTE))
                .isInstanceOf(CodigoDeConfirmacionIncorrectoException.class);
        assertThat(servicio.verificar(ana, iniciada.confirmacionId(), "654321", CLIENTE).token()).isNotBlank();
    }

    @Test
    @DisplayName("tres codigos incorrectos agotan la confirmacion y cada fallo cuenta para la pausa del ingreso")
    void tresIntentos() {
        UUID id = servicio.iniciar(ana, operacion, CLIENTE).confirmacionId();

        for (int restantes = 2; restantes >= 0; restantes--) {
            int esperado = restantes;
            assertThatThrownBy(() -> servicio.verificar(ana, id, "999999", CLIENTE))
                    .isInstanceOf(CodigoDeConfirmacionIncorrectoException.class)
                    .extracting(e -> ((CodigoDeConfirmacionIncorrectoException) e).intentosRestantes())
                    .isEqualTo(esperado);
        }
        assertThatThrownBy(() -> servicio.verificar(ana, id, BUENO, CLIENTE))
                .isInstanceOf(ConfirmacionNoDisponibleException.class);
        assertThat(controlesGuardados.get(ana).fallosConsecutivos()).isEqualTo(3);
        assertThat(eventos).containsOnly(TipoDeEventoDeAcceso.CONFIRMACION_FALLIDA);
    }

    @Test
    @DisplayName("abrir confirmaciones nuevas no permite seguir probando: al sexto fallo se pausa todo")
    void sinFuerzaBruta() {
        for (int i = 0; i < 2; i++) {
            UUID id = servicio.iniciar(ana, operacion, CLIENTE).confirmacionId();
            for (int j = 0; j < 3; j++) {
                assertThatThrownBy(() -> servicio.verificar(ana, id, "999999", CLIENTE))
                        .isInstanceOf(CodigoDeConfirmacionIncorrectoException.class);
            }
        }
        assertThatThrownBy(() -> servicio.iniciar(ana, operacion, CLIENTE))
                .isInstanceOf(CuentaBloqueadaException.class);
    }

    @Test
    @DisplayName("una confirmacion se usa una vez, caduca a los 5 minutos y no sirve a otro usuario")
    void usoUnicoCaducidadYTitular() {
        UUID id = servicio.iniciar(ana, operacion, CLIENTE).confirmacionId();
        servicio.verificar(ana, id, BUENO, CLIENTE);
        assertThatThrownBy(() -> servicio.verificar(ana, id, BUENO, CLIENTE))
                .isInstanceOf(ConfirmacionNoDisponibleException.class);

        UUID otra = servicio.iniciar(ana, operacion, CLIENTE).confirmacionId();
        assertThatThrownBy(() -> servicio.verificar(UUID.randomUUID(), otra, BUENO, CLIENTE))
                .isInstanceOf(ConfirmacionNoDisponibleException.class);
        reloj.avanzar(Duration.ofMinutes(5));
        assertThatThrownBy(() -> servicio.verificar(ana, otra, BUENO, CLIENTE))
                .isInstanceOf(ConfirmacionNoDisponibleException.class);
        assertThatThrownBy(() -> servicio.verificar(ana, UUID.randomUUID(), BUENO, CLIENTE))
                .isInstanceOf(ConfirmacionNoDisponibleException.class);
    }

    @Test
    @DisplayName("un codigo mal formado cuenta como fallo, sin romper")
    void codigoMalFormado() {
        UUID id = servicio.iniciar(ana, operacion, CLIENTE).confirmacionId();
        assertThatThrownBy(() -> servicio.verificar(ana, id, "abc", CLIENTE))
                .isInstanceOf(CodigoDeConfirmacionIncorrectoException.class);
        assertThatThrownBy(() -> servicio.verificar(ana, id, null, CLIENTE))
                .isInstanceOf(CodigoDeConfirmacionIncorrectoException.class);
    }

    @Test
    @DisplayName("sin un segundo factor confirmado no se puede confirmar nada")
    void sinSegundoFactor() {
        when(segundos.buscarPorUsuario(ana)).thenReturn(Optional.of(SegundoFactor.inscribir(ana, SECRETO, AHORA)));
        assertThatThrownBy(() -> servicio.iniciar(ana, operacion, CLIENTE))
                .isInstanceOf(SinSegundoFactorException.class);
    }

    private static final class Reloj extends Clock {
        private Instant ahora = AHORA;

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
