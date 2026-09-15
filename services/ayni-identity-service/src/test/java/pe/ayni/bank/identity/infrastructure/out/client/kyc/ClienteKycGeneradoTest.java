package pe.ayni.bank.identity.infrastructure.out.client.kyc;

import org.junit.jupiter.api.Test;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.VerificationApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.VerificationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteKycGeneradoTest {

    @Test
    void el_cliente_openapi_del_kyc_service_se_genera_en_tiempo_de_build() {
        assertThat(new VerificationApi()).isNotNull();
        assertThat(new VerificationRequest()
                .anversoDocumentKey(java.util.UUID.randomUUID())
                .reversoDocumentKey(java.util.UUID.randomUUID())).isNotNull();
    }
}
