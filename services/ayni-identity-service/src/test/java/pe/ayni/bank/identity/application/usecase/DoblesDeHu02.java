package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import pe.ayni.bank.identity.aceptacion.DoblesEnMemoria;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.DocumentoDeIdentidad;
import pe.ayni.bank.identity.domain.model.FechaDeNacimiento;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDocumento;
import pe.ayni.bank.identity.domain.model.Usuario;

/**
 * El montaje comun de las pruebas de los casos de uso de HU-02: una solicitud real de Ana
 * Quispe, que declaro su DNI al registrarse, y los dobles en memoria de cada puerto.
 */
final class DoblesDeHu02 {

    static final Instant AHORA = Instant.parse("2026-09-12T10:15:30Z");
    static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    static final String DNI = "44556677";
    static final LocalDate NACIMIENTO = LocalDate.of(1990, 5, 15);

    final DoblesEnMemoria.Solicitudes solicitudes = new DoblesEnMemoria.Solicitudes();
    final DoblesEnMemoria.Usuarios usuarios = new DoblesEnMemoria.Usuarios();
    final DoblesEnMemoria.NotificadorDeVerificacionKyc notificador = new DoblesEnMemoria.NotificadorDeVerificacionKyc();
    final DoblesEnMemoria.DocumentosKyc documentos = new DoblesEnMemoria.DocumentosKyc();
    final DoblesEnMemoria.Almacen almacen = new DoblesEnMemoria.Almacen();
    final DoblesEnMemoria.VerificadorKyc verificador = new DoblesEnMemoria.VerificadorKyc();
    final GestionarFalloDeVerificacionKycService fallos =
            new GestionarFalloDeVerificacionKycService(solicitudes, usuarios, notificador);
    final UUID solicitudId;

    DoblesDeHu02() {
        Usuario titular = Usuario.registrar(UUID.randomUUID(),
                new CorreoElectronico("ana.quispe@example.pe"), new Celular("987654321"),
                new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(titular);
        solicitudId = solicitudes.abrirPara(titular.id(), new IdentidadDeclarada("Ana Lucía", "Quispe Mamani",
                new DocumentoDeIdentidad(TipoDocumento.DNI, DNI),
                FechaDeNacimiento.de(NACIMIENTO, LocalDate.of(2026, 9, 12))));
    }

    /** Una clave como las que firma GenerarUrlDeSubidaService, ya subida al almacen. */
    String subir(TipoDeDocumentoKyc lado) {
        String clave = "kyc/" + solicitudId + "/" + lado.prefijoDeObjeto() + UUID.randomUUID() + ".jpg";
        almacen.subir(clave, ("foto-" + clave).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return clave;
    }

    static DatosDelDni datosDeAna() {
        return new DatosDelDni(DNI, "ANA LUCIA", "QUISPE MAMANI", NACIMIENTO, "F", LocalDate.of(2021, 8, 20));
    }
}
