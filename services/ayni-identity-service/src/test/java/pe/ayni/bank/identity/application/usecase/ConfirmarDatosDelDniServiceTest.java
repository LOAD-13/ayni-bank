package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.CapturasIncompletasException;
import pe.ayni.bank.identity.domain.model.DatosConfirmados;
import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;

/** HU-02 · el titular confirma o corrige lo leido, y se contrasta con lo declarado (ADR-0009). */
class ConfirmarDatosDelDniServiceTest {

    private static final LocalDate EMISION = LocalDate.of(2021, 8, 20);

    private DoblesDeHu02 d;
    private ConfirmarDatosDelDniService servicio;

    @BeforeEach
    void prepararEscenario() {
        d = new DoblesDeHu02();
        servicio = new ConfirmarDatosDelDniService(d.solicitudes, d.documentos, d.fallos);
    }

    private void leido(DatosDelDni datos, FuenteDeLectura fuente, boolean confiable) {
        d.documentos.guardarLectura(d.solicitudId, new LecturaDelDni(datos, fuente, confiable));
    }

    @Test
    @DisplayName("si lo leido coincide con lo declarado, la solicitud pasa a DOCUMENTO_CARGADO")
    void loConfirmadoCoincideConLoDeclarado() {
        leido(DoblesDeHu02.datosDeAna(), FuenteDeLectura.MRZ, true);

        EstadoDelPasoKyc estado = servicio.confirmar(d.solicitudId, new DatosConfirmados(
                null, "ANA LUCIA", "QUISPE MAMANI", DoblesDeHu02.NACIMIENTO, "F", EMISION));

        // "Ana Lucía" declarada, "ANA LUCIA" impresa en el DNI: no es una discrepancia.
        assertThat(estado).isEqualTo(EstadoDelPasoKyc.ACEPTADO);
        assertThat(d.solicitudes.documentosCargados).containsExactly(d.solicitudId);
        assertThat(d.documentos.lecturasDe(d.solicitudId)).extracting(LecturaDelDni::fuente)
                .containsExactly(FuenteDeLectura.MRZ, FuenteDeLectura.TITULAR);
    }

    @Test
    @DisplayName("el titular corrige un nombre mal leido del anverso y se acepta")
    void elTitularCorrigeUnaLecturaNoFiable() {
        leido(new DatosDelDni(DoblesDeHu02.DNI, "ANA LUClA", "QUISPE MAMANl", DoblesDeHu02.NACIMIENTO, "F", null),
                FuenteDeLectura.HEURISTICA_ANVERSO, false);

        EstadoDelPasoKyc estado = servicio.confirmar(d.solicitudId, new DatosConfirmados(
                null, "Ana Lucia", "Quispe Mamani", DoblesDeHu02.NACIMIENTO, "F", EMISION));

        assertThat(estado).isEqualTo(EstadoDelPasoKyc.ACEPTADO);
    }

    @Test
    @DisplayName("si lo confirmado no coincide con lo declarado, va a revision manual")
    void unaDiscrepanciaConLoDeclaradoDeriva() {
        leido(new DatosDelDni("11223344", "LUIS", "ROJAS", LocalDate.of(1985, 1, 2), "M", EMISION),
                FuenteDeLectura.MRZ, true);

        EstadoDelPasoKyc estado = servicio.confirmar(d.solicitudId, new DatosConfirmados(
                null, "LUIS", "ROJAS", LocalDate.of(1985, 1, 2), "M", EMISION));

        assertThat(estado).isEqualTo(EstadoDelPasoKyc.EN_REVISION_MANUAL);
        assertThat(d.solicitudes.documentosCargados).isEmpty();
        assertThat(d.notificador.avisadosDeRevisionManual).containsExactly("ana.quispe@example.pe");
    }

    @Test
    @DisplayName("cambiar el numero de una lectura del MRZ verificada deriva aunque coincida con lo declarado")
    void contradecirAlMrzDeriva() {
        // El MRZ, con sus digitos verificadores, leyo otro numero: si el titular lo "corrige"
        // para que coincida con lo que declaro, no corrige al OCR, contradice al documento.
        leido(new DatosDelDni("99887766", "ANA LUCIA", "QUISPE MAMANI", DoblesDeHu02.NACIMIENTO, "F", EMISION),
                FuenteDeLectura.MRZ, true);

        EstadoDelPasoKyc estado = servicio.confirmar(d.solicitudId, new DatosConfirmados(
                DoblesDeHu02.DNI, "ANA LUCIA", "QUISPE MAMANI", DoblesDeHu02.NACIMIENTO, "F", EMISION));

        assertThat(estado).isEqualTo(EstadoDelPasoKyc.EN_REVISION_MANUAL);
    }

    @Test
    @DisplayName("sin una lectura previa no hay nada que confirmar")
    void sinLecturaNoSeConfirma() {
        DatosConfirmados confirmados = new DatosConfirmados(
                null, "ANA", "QUISPE", DoblesDeHu02.NACIMIENTO, "F", EMISION);

        assertThatThrownBy(() -> servicio.confirmar(d.solicitudId, confirmados))
                .isInstanceOf(CapturasIncompletasException.class);
    }

    @Test
    @DisplayName("un numero corregido que no son ocho digitos se rechaza como dato invalido")
    void unNumeroInvalidoSeRechaza() {
        leido(DoblesDeHu02.datosDeAna(), FuenteDeLectura.MRZ, true);
        DatosConfirmados confirmados = new DatosConfirmados(
                "A1234567", "ANA", "QUISPE", DoblesDeHu02.NACIMIENTO, "F", EMISION);

        assertThatThrownBy(() -> servicio.confirmar(d.solicitudId, confirmados))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una solicitud ya derivada no admite confirmacion")
    void unaSolicitudDerivadaNoSeConfirma() {
        d.solicitudes.marcarEnRevisionManual(d.solicitudId);

        EstadoDelPasoKyc estado = servicio.confirmar(d.solicitudId, new DatosConfirmados(
                null, "ANA", "QUISPE", DoblesDeHu02.NACIMIENTO, "F", EMISION));

        assertThat(estado).isEqualTo(EstadoDelPasoKyc.EN_REVISION_MANUAL);
    }
}
