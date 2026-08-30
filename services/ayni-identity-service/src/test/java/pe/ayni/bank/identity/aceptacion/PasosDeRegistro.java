package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.identity.application.usecase.RegistrarVisitanteService;
import pe.ayni.bank.identity.domain.model.ComandoDeRegistro;
import pe.ayni.bank.identity.domain.model.ConsentimientoNoOtorgadoException;
import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;
import pe.ayni.bank.identity.domain.model.ResultadoDeRegistro;

/** Pasos de {@code registro-de-usuario.feature} · HU-01. */
public class PasosDeRegistro {

    private static final String CONTRASENA_VALIDA = "Cont!rasena2026#";
    private static final String CELULAR = "987654321";
    private static final String NOMBRES = "Ana Lucia";
    private static final String APELLIDOS = "Quispe Mendoza";
    private static final String DNI = "45678912";
    private static final LocalDate NACIMIENTO = LocalDate.of(1998, 3, 14);

    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.Cifrador cifrador;
    private DoblesEnMemoria.Notificador notificador;
    private RegistrarVisitanteService servicio;
    private Clock reloj;

    private ResultadoDeRegistro resultado;
    private Throwable fallo;

    @Before
    public void prepararEscenario() {
        usuarios = new DoblesEnMemoria.Usuarios();
        solicitudes = new DoblesEnMemoria.Solicitudes();
        cifrador = new DoblesEnMemoria.Cifrador();
        notificador = new DoblesEnMemoria.Notificador();
        resultado = null;
        fallo = null;
    }

    // ─── Antecedentes ──────────────────────────────────────────────────────

    @Dado("que hoy es el {int} de agosto de {int}")
    public void queHoyEs(int dia, int anio) {
        reloj = Clock.fixed(LocalDate.of(anio, 8, dia).atStartOfDay(ZoneOffset.UTC).toInstant(),
                ZoneOffset.UTC);
        servicio = new RegistrarVisitanteService(
                usuarios, solicitudes, cifrador, notificador, reloj);
    }

    @Dado("que no hay ninguna cuenta registrada con el correo {string}")
    public void queNoHayCuentaCon(String correo) {
        usuarios.correosExistentes.remove(correo);
    }

    @Dado("que ya existe una cuenta con el correo {string}")
    public void queYaExisteUnaCuentaCon(String correo) {
        usuarios.correosExistentes.add(correo);
    }

    // ─── Acciones ──────────────────────────────────────────────────────────

    @Cuando("me registro con el correo {string} y la contraseña {string}")
    public void meRegistroCon(String correo, String contrasena) {
        ejecutar(correo, contrasena, NACIMIENTO, true);
    }

    @Cuando("intento registrarme con la contraseña {string}")
    public void intentoRegistrarmeConLaContrasena(String contrasena) {
        ejecutar("ana.quispe@example.pe", contrasena, NACIMIENTO, true);
    }

    @Cuando("me registro sin aceptar el tratamiento de mis datos personales")
    public void meRegistroSinAceptar() {
        ejecutar("ana.quispe@example.pe", CONTRASENA_VALIDA, NACIMIENTO, false);
    }

    @Cuando("me registro declarando que nací el {int} de enero de {int}")
    public void meRegistroDeclarandoQueNaci(int dia, int anio) {
        ejecutar("ana.quispe@example.pe", CONTRASENA_VALIDA,
                LocalDate.of(anio, 1, dia), true);
    }

    private void ejecutar(String correo, String contrasena, LocalDate nacimiento,
                          boolean aceptaTerminos) {
        ComandoDeRegistro comando = new ComandoDeRegistro(
                NOMBRES, APELLIDOS, "DNI", DNI, nacimiento,
                correo, CELULAR, contrasena, aceptaTerminos);

        fallo = catchThrowable(() -> resultado = servicio.registrar(comando));
    }

    // ─── Comprobaciones ────────────────────────────────────────────────────

    @Entonces("el sistema crea mi usuario en estado {string}")
    public void creaElUsuarioEnEstado(String estado) {
        assertThat(fallo).isNull();
        assertThat(usuarios.guardados).hasSize(1);
        assertThat(usuarios.guardados.get(0).estado())
                .isEqualTo(EstadoUsuario.valueOf(estado));
    }

    @Y("el sistema guarda mi contraseña derivada con Argon2id, nunca en claro")
    public void guardaLaContrasenaDerivada() {
        assertThat(usuarios.guardados.get(0).contrasena().valor())
                .doesNotContain(CONTRASENA_VALIDA)
                .startsWith("$argon2id$");
    }

    @Y("el sistema abre una solicitud de onboarding ligada a mi usuario")
    public void abreLaSolicitud() {
        assertThat(solicitudes.senuelos).isEmpty();
        assertThat(solicitudes.reales).containsKey(resultado.solicitudId());
        assertThat(solicitudes.reales.get(resultado.solicitudId()))
                .isEqualTo(usuarios.guardados.get(0).id());
    }

    @Y("el sistema guarda mis datos de identidad para contrastarlos después con el OCR")
    public void guardaLaIdentidadDeclarada() {
        assertThat(solicitudes.identidades).hasSize(1);
        var identidad = solicitudes.identidades.get(0);
        assertThat(identidad.nombres()).isEqualTo(NOMBRES);
        assertThat(identidad.documento().numero()).isEqualTo(DNI);
        assertThat(identidad.fechaNacimiento().valor()).isEqualTo(NACIMIENTO);
    }

    @Y("el sistema me envía el correo de bienvenida")
    public void envaLaBienvenida() {
        assertThat(notificador.bienvenidas).containsExactly("ana.quispe@example.pe");
        assertThat(notificador.avisosDeIntento).isEmpty();
    }

    @Entonces("el sistema no crea ningún usuario duplicado")
    public void noCreaUsuarioDuplicado() {
        assertThat(usuarios.guardados).isEmpty();
    }

    @Y("la respuesta es indistinguible de un registro correcto")
    public void laRespuestaEsIndistinguible() {
        assertThat(fallo).isNull();
        assertThat(resultado.estado()).isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
        assertThat(resultado.mensaje()).isEqualTo(ResultadoDeRegistro.MENSAJE_NEUTRO);
        assertThat(resultado.solicitudId()).isNotNull();
    }

    @Y("el sistema deriva la contraseña igualmente, para que el cronómetro no delate la cuenta")
    public void derivaLaContrasenaIgualmente() {
        assertThat(cifrador.vecesQueSeCifro).isEqualTo(1);
    }

    @Y("el sistema avisa por correo al titular legítimo")
    public void avisaAlTitular() {
        assertThat(notificador.avisosDeIntento).containsExactly("ana.quispe@example.pe");
        assertThat(notificador.bienvenidas).isEmpty();
    }

    @Y("el sistema no guarda ningún dato personal de ese intento")
    public void noGuardaDatosDelIntento() {
        assertThat(solicitudes.senuelos).hasSize(1);
        assertThat(solicitudes.identidades).isEmpty();
    }

    @Entonces("el sistema rechaza el registro indicando que falta {string}")
    public void rechazaIndicandoElRequisito(String requisito) {
        assertThat(fallo)
                .isInstanceOfSatisfying(ContrasenaInvalidaException.class, e ->
                        assertThat(e.incumplidos())
                                .contains(RequisitoDeContrasena.valueOf(requisito)));
    }

    @Entonces("el sistema rechaza el registro por falta de consentimiento")
    public void rechazaPorFaltaDeConsentimiento() {
        assertThat(fallo).isInstanceOf(ConsentimientoNoOtorgadoException.class);
    }

    @Entonces("el sistema rechaza el registro por no alcanzar la mayoría de edad")
    public void rechazaPorMenorDeEdad() {
        assertThat(fallo)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18");
    }

    @Y("el sistema no crea ningún usuario")
    public void noCreaNingunUsuario() {
        assertThat(usuarios.guardados).isEmpty();
    }

    @Y("el sistema no abre ninguna solicitud de onboarding")
    public void noAbreNingunaSolicitud() {
        assertThat(solicitudes.reales).isEmpty();
        assertThat(solicitudes.senuelos).isEmpty();
    }
}
