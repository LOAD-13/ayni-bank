package pe.ayni.bank.notification.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.notification.application.usecase.DespacharNotificacionesService;
import pe.ayni.bank.notification.application.usecase.RegistrarAvisosService;
import pe.ayni.bank.notification.application.usecase.RegistrarTitularService;
import pe.ayni.bank.notification.domain.model.EstadoDeNotificacion;
import pe.ayni.bank.notification.domain.model.IntentoDeEnvio;
import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.Notificacion;
import pe.ayni.bank.notification.domain.model.Plantilla;
import pe.ayni.bank.notification.domain.model.PoliticaDeReintentos;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;
import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.out.CatalogoDePlantillasPort;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeNotificacionesPort;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeTitularesPort;
import pe.ayni.bank.notification.infrastructure.in.messaging.EscuchadorDeMovimientos;
import pe.ayni.bank.notification.infrastructure.in.messaging.TraductorDeMovimientos.DepositoSimuladoRegistrado;
import pe.ayni.bank.notification.infrastructure.in.messaging.TraductorDeMovimientos.TransferenciaRealizada;

/** Pasos de {@code notificacion-de-movimientos.feature} · HU-13. */
public class PasosDeNotificacionDeMovimientos {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Los mismos textos que siembran V1 y V2. */
    private static final Map<TipoDeAviso, Plantilla> PLANTILLAS = Map.of(
            TipoDeAviso.TRANSFERENCIA_ENVIADA, new Plantilla("Transferencia enviada",
                    "Enviaste {{moneda}} {{importe}} desde tu cuenta {{cuenta}} a la cuenta {{contraparte}} el "
                            + "{{fecha}}. Tu saldo disponible es {{moneda}} {{saldo}}. Operación {{operacion}}."),
            TipoDeAviso.TRANSFERENCIA_RECIBIDA, new Plantilla("Recibiste una transferencia",
                    "Recibiste {{moneda}} {{importe}} en tu cuenta {{cuenta}} desde la cuenta {{contraparte}} el "
                            + "{{fecha}}. Tu saldo disponible es {{moneda}} {{saldo}}. Operación {{operacion}}."),
            TipoDeAviso.DEPOSITO_RECIBIDO, new Plantilla("Recibiste un depósito",
                    "Recibiste un depósito de {{moneda}} {{importe}} en tu cuenta {{cuenta}} el {{fecha}}. "
                            + "Tu saldo disponible es {{moneda}} {{saldo}}. Operación {{operacion}}."));

    private Instant ahora;
    private final Map<String, UUID> personas = new HashMap<>();
    private final Map<UUID, Titular> titulares = new HashMap<>();
    private final Map<UUID, Notificacion> notificaciones = new HashMap<>();
    private final List<IntentoDeEnvio> intentos = new ArrayList<>();
    private final List<String[]> correos = new ArrayList<>();
    private PoliticaDeReintentos politica = new PoliticaDeReintentos(Duration.ofSeconds(30), 5);
    private boolean proveedorCaido;
    private Runnable ultimoMensaje;

    @Before
    public void reiniciar() {
        personas.clear();
        titulares.clear();
        notificaciones.clear();
        intentos.clear();
        correos.clear();
        proveedorCaido = false;
    }

    // ─── Dobles ──────────────────────────────────────────────────────────────

    private Clock reloj() {
        return Clock.fixed(ahora, ZoneOffset.UTC);
    }

    private final RepositorioDeTitularesPort repoTitulares = new RepositorioDeTitularesPort() {
        @Override
        public void guardar(Titular titular, Instant actualizadoEn) {
            titulares.put(titular.usuarioId(), titular);
        }

        @Override
        public Optional<Titular> buscar(UUID usuarioId) {
            return Optional.ofNullable(titulares.get(usuarioId));
        }
    };

    /** Imita la restriccion UNIQUE (evento_id, usuario_id, plantilla) de V2. */
    private final RepositorioDeNotificacionesPort repoNotificaciones = new RepositorioDeNotificacionesPort() {
        @Override
        public boolean registrarSiEsNueva(Notificacion n) {
            boolean existe = notificaciones.values().stream().anyMatch(o -> o.eventoId().equals(n.eventoId())
                    && o.usuarioId().equals(n.usuarioId()) && o.tipo() == n.tipo());
            if (!existe) {
                notificaciones.put(n.id(), n);
            }
            return !existe;
        }

        @Override
        public List<Notificacion> pendientesHasta(Instant hasta, int limite) {
            return notificaciones.values().stream()
                    .filter(n -> n.estado() == EstadoDeNotificacion.PENDIENTE && !n.proximoIntentoEn().isAfter(hasta))
                    .limit(limite)
                    .toList();
        }

        @Override
        public void actualizar(Notificacion n, IntentoDeEnvio intento) {
            intentos.add(intento);
        }
    };

    private final CatalogoDePlantillasPort catalogo = tipo -> Optional.of(PLANTILLAS.get(tipo));

    private EscuchadorDeMovimientos escuchador() {
        return new EscuchadorDeMovimientos(new RegistrarAvisosService(repoNotificaciones, reloj()));
    }

    private DespacharNotificacionesService despachador() {
        return new DespacharNotificacionesService(repoNotificaciones, repoTitulares, catalogo,
                (Titular destino, MensajeRedactado m) -> {
                    if (proveedorCaido) {
                        throw new IllegalStateException("SES no responde");
                    }
                    correos.add(new String[] {destino.correo(), m.asunto(), m.texto()});
                }, politica, reloj());
    }

    private List<String[]> correosDe(String correo) {
        return correos.stream().filter(c -> c[0].equals(correo)).toList();
    }

    private Notificacion unica() {
        assertThat(notificaciones).hasSize(1);
        return notificaciones.values().iterator().next();
    }

    // ─── Pasos ──────────────────────────────────────────────────────────────

    @Dado("que el reloj marca el {int} de octubre de {int} a las {int}:{int} en Lima")
    public void reloj(int dia, int anio, int hora, int minuto) {
        ahora = LocalDateTime.of(anio, 10, dia, hora, minuto).atZone(LIMA).toInstant();
    }

    @Y("que {string} es titular con el correo {string}")
    public void titular(String nombre, String correo) {
        UUID id = UUID.randomUUID();
        personas.put(nombre, id);
        new RegistrarTitularService(repoTitulares, reloj()).registrar(new Titular(id, correo, nombre));
    }

    @Dado("que el proveedor de correo está caído y se permiten {int} intentos con una espera base de {int} segundos")
    public void proveedorCaido(int maximo, int base) {
        proveedorCaido = true;
        politica = new PoliticaDeReintentos(Duration.ofSeconds(base), maximo);
    }

    @Cuando("core-banking publica que {string} transfirió PEN {word} a {string} dejando saldos de {word} y {word}")
    public void transferencia(String origen, String importe, String destino, String saldoOrigen,
                              String saldoDestino) {
        var evento = new TransferenciaRealizada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), importe,
                "PEN", personas.get(origen), personas.get(destino), "****4321", "****8765", saldoOrigen,
                saldoDestino, ahora.toString());
        String messageId = UUID.randomUUID().toString();
        ultimoMensaje = () -> escuchador().alRealizarseUnaTransferencia(evento, messageId);
        ultimoMensaje.run();
    }

    @Cuando("core-banking publica un depósito de PEN {word} para {string} con saldo resultante {word}")
    public void deposito(String importe, String titular, String saldo) {
        var evento = new DepositoSimuladoRegistrado(UUID.randomUUID(), UUID.randomUUID(), importe, "PEN",
                personas.get(titular), "****4321", saldo, ahora.toString());
        String messageId = UUID.randomUUID().toString();
        ultimoMensaje = () -> escuchador().alRegistrarseUnDeposito(evento, messageId);
        ultimoMensaje.run();
    }

    @Y("el mismo mensaje llega otra vez")
    public void otraVez() {
        ultimoMensaje.run();
    }

    @Y("pasa el despachador")
    public void pasaElDespachador() {
        despachador().despacharPendientes();
    }

    @Cuando("pasan {int} segundos y pasa el despachador")
    public void pasan(int segundos) {
        ahora = ahora.plusSeconds(segundos);
        pasaElDespachador();
    }

    @Entonces("{string} recibe un correo {string} que dice {string}")
    public void recibe(String correo, String asunto, String texto) {
        assertThat(correosDe(correo)).singleElement().satisfies(c -> {
            assertThat(c[1]).isEqualTo(asunto);
            assertThat(c[2]).contains(texto);
        });
    }

    @Y("el correo de {string} dice {string} y {string}")
    public void dice(String correo, String uno, String otro) {
        assertThat(correosDe(correo)).singleElement().satisfies(c -> assertThat(c[2]).contains(uno, otro));
    }

    @Y("ningún correo contiene un número de cuenta completo")
    public void sinCuentasCompletas() {
        // Los numeros de cuenta tienen 14 digitos; el codigo de operacion, 8 caracteres como mucho.
        assertThat(correos).isNotEmpty().allSatisfy(c -> assertThat(c[2]).doesNotContainPattern("\\d{10,}"));
    }

    @Entonces("{string} recibe exactamente {int} correo(s)")
    public void exactamente(String correo, int cuantos) {
        assertThat(correosDe(correo)).hasSize(cuantos);
    }

    @Entonces("el aviso queda pendiente para dentro de {int} segundos")
    public void pendiente(int segundos) {
        Notificacion n = unica();
        assertThat(n.estado()).isEqualTo(EstadoDeNotificacion.PENDIENTE);
        assertThat(n.proximoIntentoEn()).isEqualTo(ahora.plusSeconds(segundos));
    }

    @Entonces("el aviso queda fallido con {int} intentos registrados")
    public void fallido(int cuantos) {
        assertThat(unica().estado()).isEqualTo(EstadoDeNotificacion.FALLIDA);
        assertThat(intentos).hasSize(cuantos).noneMatch(IntentoDeEnvio::enviado);
    }
}
