package pe.ayni.bank.core.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RevisionDeAuditoriaTest {

    @AfterEach
    void sinPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static RevisionDeAuditoria revisionCon(String cabecera) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        if (cabecera != null) {
            peticion.addHeader("X-Ayni-Usuario", cabecera);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
        RevisionDeAuditoria revision = new RevisionDeAuditoria();
        new RevisionDeAuditoria.ActorDeLaRevision().newRevision(revision);
        return revision;
    }

    @Test
    void el_actor_es_el_usuario_de_la_sesion() {
        UUID usuario = UUID.randomUUID();
        RevisionDeAuditoria revision = revisionCon(usuario.toString());

        assertThat(revision.getActor()).isEqualTo(usuario);
        assertThat(revision.getId()).isNull();
        assertThat(revision.getMomento()).isNull();
    }

    @Test
    void sin_cabecera_o_con_algo_que_no_es_un_uuid_no_hay_actor() {
        assertThat(revisionCon(null).getActor()).isNull();
        assertThat(revisionCon("no-es-un-uuid").getActor()).isNull();
    }

    @Test
    void fuera_de_una_peticion_no_hay_actor() {
        RevisionDeAuditoria revision = new RevisionDeAuditoria();
        new RevisionDeAuditoria.ActorDeLaRevision().newRevision(revision);

        assertThat(revision.getActor()).isNull();
    }
}
