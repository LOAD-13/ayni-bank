package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.CapturasIncompletasException;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.ResultadoDeExtraccion;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;

/** HU-02 · lectura de los datos del DNI (escenario 1). */
class ExtraerDatosDelDniServiceTest {

    private DoblesDeHu02 d;
    private ExtraerDatosDelDniService servicio;
    private String anverso;
    private String reverso;

    @BeforeEach
    void prepararEscenario() {
        d = new DoblesDeHu02();
        servicio = new ExtraerDatosDelDniService(d.solicitudes, d.documentos, d.almacen, d.verificador, d.fallos);

        EvaluarCapturaDeDniService evaluar = new EvaluarCapturaDeDniService(
                d.solicitudes, d.documentos, d.almacen, d.verificador, d.fallos, DoblesDeHu02.RELOJ);
        anverso = d.subir(TipoDeDocumentoKyc.ANVERSO);
        reverso = d.subir(TipoDeDocumentoKyc.REVERSO);
        evaluar.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, anverso);
        evaluar.evaluar(d.solicitudId, TipoDeDocumentoKyc.REVERSO, reverso);
    }

    @Test
    @DisplayName("lee los datos, los guarda como lectura del OCR y los devuelve para confirmarlos")
    void leeYGuardaLosDatos() {
        LecturaDelDni lectura = new LecturaDelDni(DoblesDeHu02.datosDeAna(), FuenteDeLectura.MRZ, true);
        d.verificador.lectura = Optional.of(lectura);

        ResultadoDeExtraccion resultado = servicio.extraer(d.solicitudId);

        assertThat(resultado).isEqualTo(ResultadoDeExtraccion.leida(lectura));
        assertThat(d.documentos.ultimaLecturaOcrDe(d.solicitudId)).contains(lectura);
    }

    @Test
    @DisplayName("si el OCR no lee el documento, cuenta un intento del reverso y pide repetirlo")
    void unDocumentoIlegibleCuentaComoIntentoDelReverso() {
        ResultadoDeExtraccion resultado = servicio.extraer(d.solicitudId);

        assertThat(resultado.estado()).isEqualTo(EstadoDelPasoKyc.RECHAZADO);
        assertThat(resultado.intentosRestantes()).isEqualTo(2);
        assertThat(d.solicitudes.intentosDe(d.solicitudId, TipoDeDocumentoKyc.REVERSO)).isEqualTo(1);
    }

    @Test
    @DisplayName("si una foto cambio despues de evaluarse, no se lee: va a revision manual")
    void unaFotoAlteradaDeriva() {
        d.almacen.subir(reverso, "otra-imagen".getBytes(StandardCharsets.UTF_8));
        d.verificador.lectura = Optional.of(new LecturaDelDni(DoblesDeHu02.datosDeAna(), FuenteDeLectura.MRZ, true));

        ResultadoDeExtraccion resultado = servicio.extraer(d.solicitudId);

        assertThat(resultado).isEqualTo(ResultadoDeExtraccion.enRevisionManual());
        assertThat(d.verificador.extraccionesPedidas).isZero();
        assertThat(d.notificador.avisadosDeRevisionManual).containsExactly("ana.quispe@example.pe");
    }

    @Test
    @DisplayName("con kyc-service caido se difiere, sin gastar intentos")
    void conElServicioCaidoSeDifiere() {
        d.verificador.caido = true;

        assertThat(servicio.extraer(d.solicitudId)).isEqualTo(ResultadoDeExtraccion.diferida());
        assertThat(d.solicitudes.intentosDe(d.solicitudId, TipoDeDocumentoKyc.REVERSO)).isZero();
    }

    @Test
    @DisplayName("sin las dos fotos aceptadas no hay nada que leer")
    void sinLasDosFotosNoSeLee() {
        DoblesDeHu02 vacio = new DoblesDeHu02();
        ExtraerDatosDelDniService sinFotos = new ExtraerDatosDelDniService(
                vacio.solicitudes, vacio.documentos, vacio.almacen, vacio.verificador, vacio.fallos);

        assertThatThrownBy(() -> sinFotos.extraer(vacio.solicitudId))
                .isInstanceOf(CapturasIncompletasException.class);
    }
}
