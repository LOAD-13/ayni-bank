package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import pe.ayni.bank.identity.application.usecase.GenerarDesafioCodigoService;
import pe.ayni.bank.identity.application.usecase.GenerarUrlDeSubidaService;
import pe.ayni.bank.identity.application.usecase.IniciarSesionService;
import pe.ayni.bank.identity.application.usecase.SeleccionarSegundoFactorService;
import pe.ayni.bank.identity.application.usecase.VerificarContactoRegistroService;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.ComandoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.DesafioExpiradoException;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.DocumentoDeIdentidad;
import pe.ayni.bank.identity.domain.model.FechaDeNacimiento;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.MaximoIntentosDesafioExcedidoException;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.RefreshToken;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SegundoFactorNoVerificadoException;
import pe.ayni.bank.identity.domain.model.SesionIniciada;
import pe.ayni.bank.identity.domain.model.SolicitudVerificacionContacto;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDocumento;
import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.EmisorDeTokensDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSeguridadPort;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSesionesPort;

/** Pasos de {@code segundo-factor-a-eleccion.feature} · HU-22 (AYNI-124). */
public class PasosDeSegundoFactor {

    private static final String CONTRASENA_CLARA = "Cont!rasena2026#";
    private static final String SECRETO_FIJO = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final String CODIGO_TOTP_VALIDO = "246810";
    private static final HuellaDeCliente CLIENTE = new HuellaDeCliente("190.12.4.7", "Mozilla/5.0");

    private RelojMovil reloj;
    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.Almacen almacen;
    private DoblesEnMemoria.Cifrador cifrador;
    private MetodosEnMemoria metodos;
    private SegundosFactoresEnMemoria segundosFactores;
    private DesafiosEnMemoria desafios;
    private NotificadorSegundoFactorEnMemoria notificador;
    private TotpEnMemoria totp;

    private SeleccionarSegundoFactorService seleccionarService;
    private GenerarDesafioCodigoService generarDesafioService;
    private VerificarContactoRegistroService verificarContactoService;
    private GenerarUrlDeSubidaService generarUrlService;
    private IniciarSesionService iniciarSesionService;

    private Usuario solicitante;
    private UUID solicitudId;
    private MetodoDeSegundoFactor ultimoMetodo;
    private Throwable ultimoFallo;
    private DesafioAbierto desafioLogin;
    private SesionIniciada sesionIniciada;

    @Before
    public void preparar() {
        usuarios = new DoblesEnMemoria.Usuarios();
        solicitudes = new DoblesEnMemoria.Solicitudes();
        almacen = new DoblesEnMemoria.Almacen();
        cifrador = new DoblesEnMemoria.Cifrador();
        metodos = new MetodosEnMemoria();
        segundosFactores = new SegundosFactoresEnMemoria();
        desafios = new DesafiosEnMemoria();
        notificador = new NotificadorSegundoFactorEnMemoria();
        totp = new TotpEnMemoria();
        ultimoMetodo = null;
        ultimoFallo = null;
        desafioLogin = null;
        sesionIniciada = null;
    }

    @Dado("que el reloj del segundo factor marca el {int} de octubre de {int} a las {int}:{int}")
    public void queElRelojMarca(int dia, int anio, int hora, int minuto) {
        reloj = new RelojMovil(
                Instant.parse("%d-10-%02dT%02d:%02d:00Z".formatted(anio, dia, hora, minuto)));

        generarDesafioService = new GenerarDesafioCodigoService(
                desafios, usuarios, notificador, reloj, solicitudes);
        seleccionarService = new SeleccionarSegundoFactorService(
                metodos, totp, reloj, solicitudes, segundosFactores, usuarios, generarDesafioService);
        verificarContactoService = new VerificarContactoRegistroService(
                desafios, metodos, reloj, totp, segundosFactores, solicitudes);
        generarUrlService = new GenerarUrlDeSubidaService(
                solicitudes, almacen, metodos, segundosFactores);

        ControlesEnMemoria controles = new ControlesEnMemoria();
        SesionesEnMemoria sesiones = new SesionesEnMemoria();
        EmisorEnMemoria emisor = new EmisorEnMemoria();
        PistaDeAuditoriaPort auditoria = (tipo, u, c) -> { };
        NotificadorDeSeguridadPort notifSeguridad = new NotificadorDeSeguridadPort() {
            @Override
            public void avisarIngresoPausado(CorreoElectronico correo) {
            }

            @Override
            public void avisarSesionCerradaPorSeguridad(CorreoElectronico correo) {
            }
        };

        iniciarSesionService = new IniciarSesionService(
                usuarios, segundosFactores, controles, sesiones, cifrador, totp, emisor,
                auditoria, notifSeguridad, reloj, desafios, metodos, generarDesafioService);
    }

    @Y("que el solicitante {string} completó el paso 1 con celular {string}")
    public void queElSolicitanteCompletoPaso1(String correo, String celular) {
        solicitante = Usuario.registrar(
                UUID.randomUUID(),
                new CorreoElectronico(correo),
                new Celular(celular),
                cifrador.cifrar(CONTRASENA_CLARA),
                Consentimiento.otorgar(true, reloj.instant(), "v1"),
                reloj.instant());
        usuarios.guardar(solicitante);
        IdentidadDeclarada identidad = new IdentidadDeclarada(
                "Ana", "Quispe",
                new DocumentoDeIdentidad(TipoDocumento.DNI, "45678912"),
                FechaDeNacimiento.de(LocalDate.of(1995, 5, 10), LocalDate.of(2026, 10, 12)));
        solicitudId = solicitudes.abrirPara(solicitante.id(), identidad);
    }

    @Cuando("el solicitante elige el método de segundo factor {string}")
    @Dado("que el solicitante eligió el método de segundo factor {string}")
    public void eligeElMetodo(String tipo) {
        ultimoMetodo = seleccionarService.seleccionarMetodo(
                solicitudId, TipoDeSegundoFactor.valueOf(tipo));
    }

    @Cuando("el solicitante intenta elegir el método de segundo factor {string}")
    public void intentaElegirElMetodo(String tipo) {
        try {
            ultimoMetodo = seleccionarService.seleccionarMetodo(
                    solicitudId, TipoDeSegundoFactor.valueOf(tipo));
            ultimoFallo = null;
        } catch (RuntimeException e) {
            ultimoFallo = e;
        }
    }

    @Entonces("se inscribe el método {string} con un secreto TOTP único")
    public void seInscribeElMetodoConSecreto(String tipo) {
        assertThat(ultimoMetodo).isNotNull();
        assertThat(ultimoMetodo.tipo()).isEqualTo(TipoDeSegundoFactor.valueOf(tipo));
        assertThat(ultimoMetodo.secreto()).isEqualTo(SECRETO_FIJO);
        assertThat(ultimoMetodo.estaConfirmado()).isFalse();
    }

    @Y("cuando confirma el contacto del registro con un código TOTP válido de 6 dígitos")
    public void confirmaConCodigoTotpValido() {
        boolean verificado = verificarContactoService.verificarContacto(
                new SolicitudVerificacionContacto(
                        solicitudId, TipoDeSegundoFactor.APP_AUTENTICADORA, CODIGO_TOTP_VALIDO));
        assertThat(verificado).isTrue();
    }

    @Entonces("el método {string} queda confirmado y puede avanzar al paso 3 del DNI")
    public void elMetodoQuedaConfirmadoYPuedeAvanzar(String tipo) {
        MetodoDeSegundoFactor guardado = metodos.buscarPorUsuarioYTipo(
                solicitante.id(), TipoDeSegundoFactor.valueOf(tipo)).orElseThrow();
        assertThat(guardado.estaConfirmado()).isTrue();

        UrlDeSubida url = generarUrlService.generar(solicitudId, TipoDeDocumentoKyc.ANVERSO, "jpg");
        assertThat(url).isNotNull();
    }

    @Entonces("se envía un código de verificación de 6 dígitos a {string}")
    public void seEnviaCodigoDeSeisDigitos(String correo) {
        assertThat(notificador.ultimoDestinatario).isEqualTo(correo);
        assertThat(notificador.ultimoCodigo).matches("^\\d{6}$");
    }

    @Y("el solicitante no puede avanzar al paso 3 del DNI antes de verificar su contacto")
    public void noPuedeAvanzarAlPaso3SinVerificar() {
        assertThatThrownBy(() ->
                generarUrlService.generar(solicitudId, TipoDeDocumentoKyc.ANVERSO, "jpg"))
                .isInstanceOf(SegundoFactorNoVerificadoException.class);
    }

    @Cuando("confirma el contacto del registro con el código enviado por correo")
    public void confirmaConElCodigoEnviadoPorCorreo() {
        boolean verificado = verificarContactoService.verificarContacto(
                new SolicitudVerificacionContacto(
                        solicitudId, TipoDeSegundoFactor.CORREO_ELECTRONICO, notificador.ultimoCodigo));
        assertThat(verificado).isTrue();
    }

    @Entonces("la elección de {string} se rechaza explicando que el envío por SMS estará disponible próximamente")
    public void laEleccionDeSmsSeRechaza(String tipo) {
        assertThat(ultimoFallo)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(tipo);
    }

    @Cuando("intenta confirmar el contacto con el código incorrecto {string} tres veces")
    public void intentaConfirmarConCodigoIncorrectoTresVeces(String codigoIncorrecto) {
        for (int intento = 1; intento <= 3; intento++) {
            try {
                verificarContactoService.verificarContacto(
                        new SolicitudVerificacionContacto(
                                solicitudId, TipoDeSegundoFactor.CORREO_ELECTRONICO, codigoIncorrecto));
            } catch (CodigoDesafioInvalidoException | MaximoIntentosDesafioExcedidoException e) {
                ultimoFallo = e;
            }
        }
    }

    @Entonces("el tercer intento se rechaza por superar el máximo de 3 intentos permitidos")
    public void elTercerIntentoSeRechazaPorMaximoIntentos() {
        assertThat(ultimoFallo).isInstanceOf(MaximoIntentosDesafioExcedidoException.class);
    }

    @Cuando("solicita reenviar el código de verificación al correo")
    public void solicitaReenviarCodigo() {
        generarDesafioService.generar(solicitudId, TipoDeSegundoFactor.CORREO_ELECTRONICO);
    }

    @Y("transcurren {int} minutos en el reloj del segundo factor")
    public void transcurrenMinutos(int minutos) {
        reloj.avanzar(Duration.ofMinutes(minutos));
    }

    @Y("intenta confirmar el contacto con el nuevo código enviado por correo")
    public void intentaConfirmarConElNuevoCodigo() {
        try {
            verificarContactoService.verificarContacto(
                    new SolicitudVerificacionContacto(
                            solicitudId, TipoDeSegundoFactor.CORREO_ELECTRONICO, notificador.ultimoCodigo));
            ultimoFallo = null;
        } catch (RuntimeException e) {
            ultimoFallo = e;
        }
    }

    @Entonces("la confirmación se rechaza indicando que el código ha expirado")
    public void laConfirmacionSeRechazaPorExpirado() {
        assertThat(ultimoFallo).isInstanceOf(DesafioExpiradoException.class);
    }

    @Dado("que el solicitante eligió y confirmó el método {string} en el registro")
    public void eligioYConfirmoEnElRegistro(String tipo) {
        eligeElMetodo(tipo);
        confirmaConElCodigoEnviadoPorCorreo();
    }

    @Cuando("inicia sesión con el correo {string} y su contraseña")
    public void iniciaSesionConCorreoYContrasena(String correo) {
        desafioLogin = iniciarSesionService.presentarCredenciales(
                new ComandoDeIngreso(correo, CONTRASENA_CLARA, CLIENTE));
    }

    @Entonces("el primer paso del ingreso abre un desafío de tipo {string} y envía un código al correo")
    public void elPrimerPasoAbreDesafioYEnviaCodigo(String tipoEsperado) {
        assertThat(desafioLogin).isNotNull();
        assertThat(desafioLogin.requiereInscripcion()).isFalse();
        assertThat(desafioLogin.tipoMetodo()).isEqualTo(TipoDeSegundoFactor.valueOf(tipoEsperado));
        assertThat(notificador.ultimoCodigo).matches("^\\d{6}$");
    }

    @Cuando("presenta ese código de 6 dígitos en el segundo paso del ingreso")
    public void presentaEseCodigoEnElSegundoPaso() {
        sesionIniciada = iniciarSesionService.verificarSegundoFactor(
                new ComandoDeSegundoFactor(
                        desafioLogin.desafioId(), new CodigoTotp(notificador.ultimoCodigo), CLIENTE));
    }

    @Entonces("obtiene una sesión válida con su token de acceso")
    public void obtieneUnaSesionValida() {
        assertThat(sesionIniciada).isNotNull();
        assertThat(sesionIniciada.tokenDeAcceso()).isNotBlank();
    }

    // ─── Dobles en memoria para HU-22 ──────────────────────────────────────

    private static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration duracion) {
            this.ahora = this.ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static final class MetodosEnMemoria implements RepositorioDeMetodoSegundoFactorPort {
        private final List<MetodoDeSegundoFactor> guardados = new ArrayList<>();

        @Override
        public Optional<MetodoDeSegundoFactor> buscarPorUsuarioYTipo(
                UUID usuarioId, TipoDeSegundoFactor tipo) {
            return guardados.stream()
                    .filter(m -> m.usuarioId().equals(usuarioId) && m.tipo() == tipo)
                    .findFirst();
        }

        @Override
        public List<MetodoDeSegundoFactor> listarPorUsuario(UUID usuarioId) {
            return guardados.stream().filter(m -> m.usuarioId().equals(usuarioId)).toList();
        }

        @Override
        public MetodoDeSegundoFactor guardar(MetodoDeSegundoFactor metodo) {
            guardados.removeIf(m -> m.id().equals(metodo.id()));
            guardados.add(metodo);
            return metodo;
        }
    }

    private static final class SegundosFactoresEnMemoria implements RepositorioDeSegundoFactorPort {
        private final Map<UUID, SegundoFactor> guardados = new HashMap<>();

        @Override
        public Optional<SegundoFactor> buscarPorUsuario(UUID usuarioId) {
            return Optional.ofNullable(guardados.get(usuarioId));
        }

        @Override
        public SegundoFactor guardar(SegundoFactor segundoFactor) {
            guardados.put(segundoFactor.usuarioId(), segundoFactor);
            return segundoFactor;
        }
    }

    private static final class DesafiosEnMemoria implements RepositorioDeDesafioPorCodigoPort {
        private final List<DesafioPorCodigo> guardados = new ArrayList<>();

        @Override
        public DesafioPorCodigo guardar(DesafioPorCodigo desafio) {
            guardados.removeIf(d -> d.id().equals(desafio.id()));
            guardados.add(desafio);
            return desafio;
        }

        @Override
        public Optional<DesafioPorCodigo> buscarPorId(UUID id) {
            return guardados.stream().filter(d -> d.id().equals(id)).findFirst();
        }

        @Override
        public Optional<DesafioPorCodigo> buscarUltimoPendiente(
                UUID usuarioId, TipoDeSegundoFactor tipoFactor) {
            for (int i = guardados.size() - 1; i >= 0; i--) {
                DesafioPorCodigo d = guardados.get(i);
                if (d.usuarioId().equals(usuarioId)
                        && d.tipoFactor() == tipoFactor
                        && !d.estaVerificado()) {
                    return Optional.of(d);
                }
            }
            return Optional.empty();
        }
    }

    private static final class NotificadorSegundoFactorEnMemoria
            implements NotificadorDeSegundoFactorPort {
        private String ultimoDestinatario;
        private String ultimoCodigo;

        @Override
        public void enviarCodigoPorCorreo(CorreoElectronico correo, String codigo) {
            this.ultimoDestinatario = correo.valor();
            this.ultimoCodigo = codigo;
        }

        @Override
        public void enviarCodigoPorSms(Celular celular, String codigo) {
            this.ultimoDestinatario = celular.valor();
            this.ultimoCodigo = codigo;
        }
    }

    private static final class TotpEnMemoria implements GeneradorDeTotpPort {
        @Override
        public SecretoTotp generarSecreto() {
            return new SecretoTotp(SECRETO_FIJO);
        }

        @Override
        public boolean verificar(SecretoTotp secreto, CodigoTotp codigo, Instant momento) {
            return SECRETO_FIJO.equals(secreto.valor())
                    && CODIGO_TOTP_VALIDO.equals(codigo.valor());
        }

        @Override
        public String uriDeAprovisionamiento(SecretoTotp secreto, CorreoElectronico correo) {
            return "otpauth://totp/Ayni:" + correo.valor() + "?secret=" + secreto.valor();
        }
    }

    private static final class ControlesEnMemoria implements RepositorioDeControlDeAccesoPort {
        private final Map<UUID, ControlDeAcceso> controles = new HashMap<>();

        @Override
        public ControlDeAcceso cargar(UUID usuarioId) {
            return controles.getOrDefault(usuarioId, ControlDeAcceso.limpio(usuarioId));
        }

        @Override
        public void guardar(ControlDeAcceso control) {
            controles.put(control.usuarioId(), control);
        }
    }

    private static final class SesionesEnMemoria implements RepositorioDeSesionesPort {
        private final Map<UUID, DesafioDeSegundoFactor> desafios = new HashMap<>();
        private final List<RefreshToken> tokens = new ArrayList<>();

        @Override
        public void guardarDesafio(DesafioDeSegundoFactor desafio) {
            desafios.put(desafio.id(), desafio);
        }

        @Override
        public Optional<DesafioDeSegundoFactor> buscarDesafio(UUID desafioId) {
            return Optional.ofNullable(desafios.get(desafioId));
        }

        @Override
        public void consumirDesafio(UUID desafioId) {
            desafios.remove(desafioId);
        }

        @Override
        public RefreshToken guardarToken(RefreshToken token) {
            tokens.add(token);
            return token;
        }

        @Override
        public Optional<RefreshToken> buscarTokenPorHuella(String huella) {
            return tokens.stream().filter(t -> t.huella().equals(huella)).findFirst();
        }

        @Override
        public void invalidarFamilia(UUID familiaId) {
        }

        @Override
        public void invalidarSesionesDe(UUID usuarioId) {
        }
    }

    private static final class EmisorEnMemoria implements EmisorDeTokensDeAccesoPort {
        private int contador;

        @Override
        public String emitir(Usuario usuario, Instant momento, Instant expiraEn) {
            return "jwt-" + usuario.id();
        }

        @Override
        public TokenDeRenovacion generarTokenDeRenovacion() {
            String claro = "renovacion-" + (++contador);
            return new TokenDeRenovacion(claro, huellaDe(claro));
        }

        @Override
        public String huellaDe(String tokenEnClaro) {
            return "sha256:" + tokenEnClaro;
        }
    }
}
