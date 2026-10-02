package pe.ayni.bank.identity.domain.model;

import java.util.UUID;

/** Record de solicitud para verificar el contacto primario durante el registro (HU-22 / AYNI-130). */
public record SolicitudVerificacionContacto(
        UUID usuarioId,
        TipoDeSegundoFactor tipoContacto,
        String codigo
) {}
