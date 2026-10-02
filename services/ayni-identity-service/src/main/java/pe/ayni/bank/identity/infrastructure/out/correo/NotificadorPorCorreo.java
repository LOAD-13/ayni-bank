package pe.ayni.bank.identity.infrastructure.out.correo;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRegistroPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSeguridadPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeVerificacionKycPort;

/**
 * Notificaciones al cliente por correo real, en produccion.
 *
 * <p>Sustituye a los notificadores provisionales que solo dejaban rastro en el log. Un
 * solo adaptador implementa los cuatro puertos porque los cuatro hacen lo mismo —componer
 * una plantilla y enviarla— y repartirlo en cuatro clases solo duplicaria el envio.
 *
 * <p>El SMS no tiene canal todavia: se registra en el log enmascarado y el cliente puede
 * elegir correo o aplicacion autenticadora como segundo factor.
 *
 * <p>Ningun codigo, enlace ni correo completo va al log: solo el destinatario enmascarado.
 */
@Component
@Profile("prod")
public class NotificadorPorCorreo implements NotificadorDeRegistroPort,
        NotificadorDeSegundoFactorPort, NotificadorDeSeguridadPort, NotificadorDeVerificacionKycPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPorCorreo.class);

    private final EnviadorDeCorreo enviador;
    private final String urlPublica;

    public NotificadorPorCorreo(EnviadorDeCorreo enviador,
                                @Value("${ayni.web.url-publica}") String urlPublica) {
        this.enviador = enviador;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    @Override
    public void enviarBienvenida(CorreoElectronico correo, UUID solicitudId) {
        String enlace = urlPublica + "/registro/dni-anverso?solicitudId=" + solicitudId;
        enviar(correo, PlantillaDeCorreo.BIENVENIDA, enlace);
    }

    @Override
    public void avisarIntentoDeRegistroSobreCuentaExistente(CorreoElectronico correo) {
        enviar(correo, PlantillaDeCorreo.INTENTO_DE_REGISTRO, urlPublica + "/ingresar");
    }

    @Override
    public void enviarCodigoPorCorreo(CorreoElectronico correo, String codigo) {
        enviar(correo, PlantillaDeCorreo.CODIGO_DE_VERIFICACION, codigo);
    }

    @Override
    public void enviarCodigoPorSms(Celular celular, String codigo) {
        log.info("SMS sin canal configurado: no se envia. destinatario={}", celular.enmascarado());
    }

    @Override
    public void avisarIngresoPausado(CorreoElectronico correo) {
        enviar(correo, PlantillaDeCorreo.INGRESO_PAUSADO, urlPublica + "/ingresar");
    }

    @Override
    public void avisarSesionCerradaPorSeguridad(CorreoElectronico correo) {
        enviar(correo, PlantillaDeCorreo.SESION_CERRADA, urlPublica + "/ingresar");
    }

    @Override
    public void avisarEnRevisionManual(CorreoElectronico correo) {
        enviar(correo, PlantillaDeCorreo.REVISION_MANUAL, urlPublica);
    }

    /**
     * Un fallo de envio no rompe la operacion que lo origino: el registro o el ingreso ya
     * ocurrieron. Se registra para poder reintentarlo, sin datos personales.
     */
    private void enviar(CorreoElectronico correo, PlantillaDeCorreo plantilla, String dato) {
        try {
            enviador.enviar(correo.valor(), plantilla.asunto(), plantilla.html(dato), plantilla.texto(dato));
            log.info("Correo enviado. plantilla={} destinatario={}", plantilla, correo.enmascarado());
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el correo. plantilla={} destinatario={} causa={}",
                    plantilla, correo.enmascarado(), e.getClass().getSimpleName());
        }
    }
}
