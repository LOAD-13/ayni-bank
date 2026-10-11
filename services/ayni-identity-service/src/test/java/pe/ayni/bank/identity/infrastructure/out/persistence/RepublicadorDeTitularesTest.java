package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.PublicadorDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.infrastructure.config.ConfiguracionDeMensajeria;
import pe.ayni.bank.identity.infrastructure.out.messaging.PublicadorDeSolicitudesPorAmqp;
import pe.ayni.bank.identity.infrastructure.out.messaging.PublicadorDeSolicitudesPorAmqp.TitularActivado;

/** HU-13: el evento de titular activado y su republicacion de una sola vez. */
class RepublicadorDeTitularesTest {

    private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");

    @Test
    @DisplayName("republica a cada titular activo con su correo y su nombre de pila")
    void republica() {
        UsuarioJpaRepository usuarios = mock(UsuarioJpaRepository.class);
        RepositorioDeSolicitudesPort solicitudes = mock(RepositorioDeSolicitudesPort.class);
        PublicadorDeSolicitudesPort publicador = mock(PublicadorDeSolicitudesPort.class);
        UUID ana = UUID.randomUUID();
        when(usuarios.findByEstado("ACTIVO")).thenReturn(List.of(new UsuarioEntity(ana, "ana@example.pe",
                "987654321", "$argon2id$x", "ACTIVO", AHORA, "v1", AHORA, AHORA)));
        when(solicitudes.nombreDePilaDe(ana)).thenReturn(Optional.of("Ana"));

        new RepublicadorDeTitulares(usuarios, solicitudes, publicador).run(null);

        verify(publicador).anunciarTitularActivado(ana, new CorreoElectronico("ana@example.pe"), "Ana");
    }

    @Test
    @DisplayName("el evento sale a identity.titular.activado y no deja el correo en una traza")
    void evento() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        UUID ana = UUID.randomUUID();
        var evento = ArgumentCaptor.forClass(Object.class);

        new PublicadorDeSolicitudesPorAmqp(rabbit, java.time.Clock.fixed(AHORA, java.time.ZoneOffset.UTC))
                .anunciarTitularActivado(ana, new CorreoElectronico("ana@example.pe"), "Ana");

        verify(rabbit).convertAndSend(eq(ConfiguracionDeMensajeria.EXCHANGE),
                eq(ConfiguracionDeMensajeria.CLAVE_TITULAR_ACTIVADO), evento.capture());
        assertThat(evento.getValue()).isEqualTo(new TitularActivado(ana, "ana@example.pe", "Ana", AHORA.toString()));
        assertThat(evento.getValue().toString()).doesNotContain("ana@example.pe");
    }
}
