package pe.ayni.bank.notification.application.usecase;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pe.ayni.bank.notification.domain.model.EstadoDeNotificacion;
import pe.ayni.bank.notification.domain.model.IntentoDeEnvio;
import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.Notificacion;
import pe.ayni.bank.notification.domain.model.PoliticaDeReintentos;
import pe.ayni.bank.notification.domain.model.ResultadoDelDespacho;
import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.in.DespacharNotificacionesUseCase;
import pe.ayni.bank.notification.domain.port.out.CatalogoDePlantillasPort;
import pe.ayni.bank.notification.domain.port.out.EnviadorDeCorreoPort;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeNotificacionesPort;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeTitularesPort;

/**
 * Envia lo pendiente y anota cada intento.
 *
 * <p>Sin transaccion propia a proposito: cada notificacion se guarda por separado despues de
 * su envio, de modo que un correo ya entregado no se pierde porque falle el siguiente.
 *
 * <p>Un titular que todavia no esta en la proyeccion cuenta como fallo y se reintenta:
 * el evento de identity y el de core-banking viajan por colas distintas y pueden llegar
 * en cualquier orden.
 */
@Service
public class DespacharNotificacionesService implements DespacharNotificacionesUseCase {

    private static final Logger log = LoggerFactory.getLogger(DespacharNotificacionesService.class);

    /** Cuantas se envian por pasada. El resto espera a la siguiente, a los pocos segundos. */
    static final int LOTE = 50;

    private final RepositorioDeNotificacionesPort notificaciones;
    private final RepositorioDeTitularesPort titulares;
    private final CatalogoDePlantillasPort plantillas;
    private final EnviadorDeCorreoPort enviador;
    private final PoliticaDeReintentos politica;
    private final Clock reloj;

    public DespacharNotificacionesService(RepositorioDeNotificacionesPort notificaciones,
                                          RepositorioDeTitularesPort titulares,
                                          CatalogoDePlantillasPort plantillas, EnviadorDeCorreoPort enviador,
                                          PoliticaDeReintentos politica, Clock reloj) {
        this.notificaciones = notificaciones;
        this.titulares = titulares;
        this.plantillas = plantillas;
        this.enviador = enviador;
        this.politica = politica;
        this.reloj = reloj;
    }

    @Override
    public ResultadoDelDespacho despacharPendientes() {
        int enviadas = 0;
        int reprogramadas = 0;
        int fallidas = 0;
        for (Notificacion n : notificaciones.pendientesHasta(reloj.instant(), LOTE)) {
            IntentoDeEnvio intento = intentar(n);
            notificaciones.actualizar(n, intento);
            if (n.estado() == EstadoDeNotificacion.ENVIADA) {
                enviadas++;
            } else if (n.estado() == EstadoDeNotificacion.FALLIDA) {
                fallidas++;
                log.warn("Notificacion {} fallida tras {} intentos: {}", n.id(), n.intentos(), intento.detalle());
            } else {
                reprogramadas++;
            }
        }
        return new ResultadoDelDespacho(enviadas, reprogramadas, fallidas);
    }

    private IntentoDeEnvio intentar(Notificacion n) {
        try {
            Titular titular = titulares.buscar(n.usuarioId())
                    .orElseThrow(() -> new IllegalStateException("El titular aun no esta en la proyeccion"));
            MensajeRedactado mensaje = plantillas.deCorreo(n.tipo())
                    .orElseThrow(() -> new IllegalStateException("No hay plantilla activa para " + n.tipo()))
                    .rellenar(n.datos());
            enviador.enviar(titular, mensaje);
            return n.marcarEnviada(reloj.instant());
        } catch (RuntimeException e) {
            // Cualquier fallo del proveedor o de los datos se anota y se reintenta: dejar que
            // la excepcion suba cortaria el lote y las demas notificaciones esperarian.
            return n.registrarFallo(e.getClass().getSimpleName() + ": " + e.getMessage(), reloj.instant(),
                    politica);
        }
    }
}
