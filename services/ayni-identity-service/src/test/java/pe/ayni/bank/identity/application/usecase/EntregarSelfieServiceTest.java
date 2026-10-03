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
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;

/** HU-03 · la selfie entregada se registra y pasa a un operador para el cotejo facial. */
class EntregarSelfieServiceTest {

    private DoblesDeHu02 d;
    private EntregarSelfieService servicio;

    @BeforeEach
    void prepararEscenario() {
        d = new DoblesDeHu02();
        servicio = new EntregarSelfieService(d.solicitudes, d.documentos, d.almacen, d.fallos, DoblesDeHu02.RELOJ);
    }

    @Test
    @DisplayName("la selfie queda registrada con su hash y la solicitud pasa a revision con aviso al titular")
    void laSelfieQuedaRegistradaYPasaARevision() {
        String clave = d.subir(TipoDeDocumentoKyc.SELFIE);

        EstadoDelPasoKyc estado = servicio.entregar(d.solicitudId, clave);

        assertThat(estado).isEqualTo(EstadoDelPasoKyc.EN_REVISION_MANUAL);
        DocumentoKyc selfie = d.documentos.ultimoDe(d.solicitudId, TipoDeDocumentoKyc.SELFIE).orElseThrow();
        assertThat(selfie.hashSha256()).isEqualTo(d.almacen.calcularHash(clave));
        assertThat(d.solicitudes.enRevisionManual).containsExactly(d.solicitudId);
        assertThat(d.notificador.avisadosDeRevisionManual).containsExactly("ana.quispe@example.pe");
    }

    @Test
    @DisplayName("una clave de otro lado o de otra solicitud no se acepta como selfie")
    void unaClaveAjenaNoSeAcepta() {
        String anverso = d.subir(TipoDeDocumentoKyc.ANVERSO);

        assertThatThrownBy(() -> servicio.entregar(d.solicitudId, anverso))
                .isInstanceOf(DocumentoNoSubidoException.class);
        assertThat(d.solicitudes.enRevisionManual).isEmpty();
    }

    @Test
    @DisplayName("una selfie que no se llego a subir no se registra")
    void unaSelfieNoSubidaNoSeRegistra() {
        String clave = "kyc/" + d.solicitudId + "/" + TipoDeDocumentoKyc.SELFIE.prefijoDeObjeto() + "nada.jpg";

        assertThatThrownBy(() -> servicio.entregar(d.solicitudId, clave))
                .isInstanceOf(DocumentoNoSubidoException.class);
        assertThat(d.documentos.guardados).isEmpty();
    }

    @Test
    @DisplayName("una solicitud sin titular no existe para este paso")
    void unaSolicitudSinTitularNoExiste() {
        assertThatThrownBy(() -> servicio.entregar(UUID.randomUUID(), "kyc/x/SELFIE-1.jpg"))
                .isInstanceOf(SolicitudNoExisteException.class);
    }
}
