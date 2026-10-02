package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.DocumentoNoSubidoException;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.EvaluacionDeCaptura;
import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeCaptura;
import pe.ayni.bank.identity.domain.model.ResultadoDeCaptura;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;

/** HU-02 · evaluacion de la foto de cada lado del DNI (escenarios 1 a 5). */
class EvaluarCapturaDeDniServiceTest {

    private DoblesDeHu02 d;
    private EvaluarCapturaDeDniService servicio;

    @BeforeEach
    void prepararEscenario() {
        d = new DoblesDeHu02();
        servicio = new EvaluarCapturaDeDniService(d.solicitudes, d.documentos, d.almacen, d.verificador, d.fallos,
                DoblesDeHu02.RELOJ);
    }

    @Test
    @DisplayName("una foto aceptada queda registrada con su referencia, hash, tipo y tamano")
    void unaFotoAceptadaSeRegistra() {
        String clave = d.subir(TipoDeDocumentoKyc.ANVERSO);

        ResultadoDeCaptura resultado = servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, clave);

        assertThat(resultado).isEqualTo(ResultadoDeCaptura.aceptada());
        DocumentoKyc documento = d.documentos.ultimoDe(d.solicitudId, TipoDeDocumentoKyc.ANVERSO).orElseThrow();
        assertThat(documento.claveDeObjeto()).isEqualTo(clave);
        assertThat(documento.hashSha256()).isEqualTo(d.almacen.calcularHash(clave));
        assertThat(documento.tipoDeContenido()).isEqualTo("image/jpeg");
        assertThat(documento.tamanoBytes()).isPositive();
        assertThat(documento.subidoEn()).isEqualTo(DoblesDeHu02.AHORA);
    }

    @Test
    @DisplayName("escenario 2: algo que no es un DNI se rechaza, no se guarda y se borra del almacen")
    void unObjetoQueNoEsUnDniSeRechazaYSeBorra() {
        String clave = d.subir(TipoDeDocumentoKyc.ANVERSO);
        d.verificador.evaluaciones.add(EvaluacionDeCaptura.rechazada(MotivoDeRechazoDeCaptura.NO_ES_DNI));

        ResultadoDeCaptura resultado = servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, clave);

        assertThat(resultado.estado()).isEqualTo(EstadoDelPasoKyc.RECHAZADO);
        assertThat(resultado.motivo()).isEqualTo(MotivoDeRechazoDeCaptura.NO_ES_DNI);
        assertThat(resultado.intentosRestantes()).isEqualTo(2);
        assertThat(d.documentos.guardados).isEmpty();
        assertThat(d.almacen.objetos).doesNotContainKey(clave);
    }

    @Test
    @DisplayName("escenario 3: la calidad insuficiente devuelve el motivo concreto")
    void laCalidadInsuficienteDevuelveElMotivo() {
        String clave = d.subir(TipoDeDocumentoKyc.REVERSO);
        d.verificador.evaluaciones.add(EvaluacionDeCaptura.rechazada(MotivoDeRechazoDeCaptura.REFLEJO));

        ResultadoDeCaptura resultado = servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.REVERSO, clave);

        assertThat(resultado.motivo()).isEqualTo(MotivoDeRechazoDeCaptura.REFLEJO);
    }

    @Test
    @DisplayName("escenario 4: el tercer rechazo del mismo lado deriva a revision manual")
    void elTercerRechazoDeriva() {
        for (int i = 0; i < 3; i++) {
            d.verificador.evaluaciones.add(EvaluacionDeCaptura.rechazada(MotivoDeRechazoDeCaptura.DESENFOQUE));
        }

        servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, d.subir(TipoDeDocumentoKyc.ANVERSO));
        servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, d.subir(TipoDeDocumentoKyc.ANVERSO));
        ResultadoDeCaptura tercero = servicio.evaluar(
                d.solicitudId, TipoDeDocumentoKyc.ANVERSO, d.subir(TipoDeDocumentoKyc.ANVERSO));

        assertThat(tercero).isEqualTo(ResultadoDeCaptura.enRevisionManual());
        assertThat(d.solicitudes.estaEnRevisionManual(d.solicitudId)).isTrue();
        assertThat(d.notificador.avisadosDeRevisionManual).containsExactly("ana.quispe@example.pe");
    }

    @Test
    @DisplayName("escenario 4: una cuarta captura sobre una solicitud derivada ni siquiera se evalua")
    void unaCuartaCapturaNoSeEvalua() {
        d.solicitudes.marcarEnRevisionManual(d.solicitudId);

        ResultadoDeCaptura resultado = servicio.evaluar(
                d.solicitudId, TipoDeDocumentoKyc.ANVERSO, d.subir(TipoDeDocumentoKyc.ANVERSO));

        assertThat(resultado).isEqualTo(ResultadoDeCaptura.enRevisionManual());
        assertThat(d.verificador.evaluacionesPedidas).isZero();
    }

    @Test
    @DisplayName("escenario 5: con kyc-service caido, la solicitud se difiere a revision y la foto se conserva")
    void conElServicioCaidoSeDifiere() {
        String clave = d.subir(TipoDeDocumentoKyc.ANVERSO);
        d.verificador.caido = true;

        ResultadoDeCaptura resultado = servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, clave);

        assertThat(resultado).isEqualTo(ResultadoDeCaptura.diferida());
        assertThat(d.solicitudes.estaEnRevisionManual(d.solicitudId)).isTrue();
        assertThat(d.solicitudes.intentosKyc).doesNotContainKey(d.solicitudId);
        // El operador que revise el caso necesita la foto.
        assertThat(d.documentos.guardados).hasSize(1);
        assertThat(d.almacen.objetos).containsKey(clave);
    }

    @Test
    @DisplayName("una clave de otra solicitud o de otro lado no se acepta como propia")
    void unaClaveAjenaSeRechaza() {
        String deOtraSolicitud = "kyc/" + UUID.randomUUID() + "/anverso-" + UUID.randomUUID() + ".jpg";
        String delOtroLado = d.subir(TipoDeDocumentoKyc.REVERSO);

        assertThatThrownBy(() -> servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, deOtraSolicitud))
                .isInstanceOf(DocumentoNoSubidoException.class);
        assertThatThrownBy(() -> servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, delOtroLado))
                .isInstanceOf(DocumentoNoSubidoException.class);
        assertThat(d.verificador.evaluacionesPedidas).isZero();
    }

    @Test
    @DisplayName("una clave que no se llego a subir no se evalua")
    void unaClaveSinSubirSeRechaza() {
        String clave = "kyc/" + d.solicitudId + "/anverso-" + UUID.randomUUID() + ".jpg";

        assertThatThrownBy(() -> servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.ANVERSO, clave))
                .isInstanceOf(DocumentoNoSubidoException.class);
    }

    @Test
    @DisplayName("una foto de mas de 5 MB se borra y no se evalua, aunque haya esquivado la politica")
    void unaFotoDemasiadoGrandeSeBorra() {
        String clave = "kyc/" + d.solicitudId + "/reverso-" + UUID.randomUUID() + ".jpg";
        d.almacen.subir(clave, new byte[(int) TipoDeDocumentoKyc.TAMANO_MAXIMO_BYTES + 1]);

        assertThatThrownBy(() -> servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.REVERSO, clave))
                .isInstanceOf(DocumentoNoSubidoException.class)
                .hasMessageContaining("5 MB");
        assertThat(d.almacen.objetos).doesNotContainKey(clave);
        assertThat(d.verificador.evaluacionesPedidas).isZero();
    }

    @Test
    @DisplayName("un senuelo o una solicitud inexistente no evaluan nada")
    void unSenueloNoEvalua() {
        UUID senuelo = d.solicitudes.abrirSenuelo();

        assertThatThrownBy(() -> servicio.evaluar(senuelo, TipoDeDocumentoKyc.ANVERSO, "kyc/x"))
                .isInstanceOf(SolicitudNoExisteException.class);
    }

    @Test
    @DisplayName("la selfie no es un lado del DNI")
    void laSelfieNoSeEvaluaAqui() {
        assertThatThrownBy(() -> servicio.evaluar(d.solicitudId, TipoDeDocumentoKyc.SELFIE, "kyc/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
