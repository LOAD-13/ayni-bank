package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.ComandoDeRegistro;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;
import pe.ayni.bank.identity.domain.model.ResultadoDeRegistro;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.RegistrarVisitanteUseCase;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRegistroPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;
import pe.ayni.bank.identity.domain.service.PoliticaDeContrasena;

/**
 * Orquesta HU-01. Las reglas viven en el dominio; aqui solo se decide el orden y la
 * frontera transaccional.
 *
 * <p>Ver ADR-0008 para la decision de antienumeracion.
 */
@Service
public class RegistrarVisitanteService implements RegistrarVisitanteUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarVisitanteService.class);

    /**
     * Version de los terminos que se acepta al registrarse. Se guarda con cada
     * consentimiento: si los terminos cambian, hay que poder saber cuales acepto cada
     * persona, no solo que acepto algo.
     */
    private static final String VERSION_DE_TERMINOS = "v1";

    private final RepositorioDeUsuariosPort usuarios;
    private final RepositorioDeSolicitudesPort solicitudes;
    private final CifradorDeContrasenasPort cifrador;
    private final NotificadorDeRegistroPort notificador;
    private final Clock reloj;

    public RegistrarVisitanteService(RepositorioDeUsuariosPort usuarios,
                                     RepositorioDeSolicitudesPort solicitudes,
                                     CifradorDeContrasenasPort cifrador,
                                     NotificadorDeRegistroPort notificador,
                                     Clock reloj) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        this.cifrador = cifrador;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public ResultadoDeRegistro registrar(ComandoDeRegistro comando) {
        // 1. Validaciones de formato y politica. Estas SI producen errores explicitos:
        //    decirle a alguien que su contrasena necesita un simbolo no revela nada
        //    sobre quien tiene cuenta en Ayni.
        List<RequisitoDeContrasena> incumplidos =
                PoliticaDeContrasena.evaluar(comando.contrasena());
        if (!incumplidos.isEmpty()) {
            throw new ContrasenaInvalidaException(incumplidos);
        }

        Instant momento = reloj.instant();
        Consentimiento consentimiento =
                Consentimiento.otorgar(comando.aceptaTerminos(), momento, VERSION_DE_TERMINOS);

        CorreoElectronico correo = new CorreoElectronico(comando.correo());
        Celular celular = new Celular(comando.celular());

        // 2. A partir de aqui, la respuesta es identica exista o no el correo.
        if (usuarios.existeCorreo(correo)) {
            return responderSinDelatarQueLaCuentaExiste(correo, comando.contrasena());
        }

        ContrasenaCifrada contrasena = cifrador.cifrar(comando.contrasena());
        Usuario usuario = usuarios.guardar(Usuario.registrar(
                UUID.randomUUID(), correo, celular, contrasena, consentimiento, momento));

        UUID solicitudId = solicitudes.abrirPara(usuario.id());
        notificador.enviarBienvenida(correo);

        // El correo va enmascarado. Un log de aplicacion lo leen operaciones, soporte y
        // cualquiera con acceso a Loki; el correo es dato personal segun la Ley N.o 29733.
        log.info("Registro completado. usuarioId={} correo={}",
                usuario.id(), correo.enmascarado());

        return ResultadoDeRegistro.aceptado(solicitudId);
    }

    /**
     * Escenario 2 de HU-01: el correo ya esta registrado.
     *
     * <p>No se crea ningun usuario, pero <strong>si se deriva la contrasena igualmente</strong>.
     * Sin esa derivacion la respuesta llegaria en unos pocos milisegundos, frente a las
     * decenas que cuesta Argon2id en el camino normal, y esa diferencia de tiempo es un
     * oraculo tan util como devolver un 409: basta cronometrar. Devolver el mismo cuerpo
     * por un canal y delatarlo por el otro no protege nada.
     *
     * <p>Al titular legitimo si se le avisa por correo. Es el unico que tiene derecho a
     * saber que alguien intento registrarse con su direccion.
     */
    private ResultadoDeRegistro responderSinDelatarQueLaCuentaExiste(
            CorreoElectronico correo, String contrasenaEnClaro) {

        cifrador.cifrar(contrasenaEnClaro);

        UUID senuelo = solicitudes.abrirSenuelo();
        notificador.avisarIntentoDeRegistroSobreCuentaExistente(correo);

        log.info("Intento de registro sobre una cuenta existente. correo={} senuelo={}",
                correo.enmascarado(), senuelo);

        return ResultadoDeRegistro.aceptado(senuelo);
    }
}
