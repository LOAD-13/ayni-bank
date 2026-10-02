package pe.ayni.bank.identity.infrastructure.out.client.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.DocumentosApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.EvaluacionRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.ExtraccionRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.LadoDelDocumento;

class ClienteKycGeneradoTest {

    @Test
    void el_cliente_openapi_del_kyc_service_se_genera_en_tiempo_de_build() {
        assertThat(new DocumentosApi()).isNotNull();
        assertThat(new EvaluacionRequest().documentKey("kyc/a/anverso-b.jpg").lado(LadoDelDocumento.ANVERSO))
                .isNotNull();
        assertThat(new ExtraccionRequest().anversoDocumentKey("a").reversoDocumentKey("b")).isNotNull();
    }
}
