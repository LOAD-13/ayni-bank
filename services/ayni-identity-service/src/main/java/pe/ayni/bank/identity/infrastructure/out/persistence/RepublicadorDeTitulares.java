package pe.ayni.bank.identity.infrastructure.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.port.out.PublicadorDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/**
 * HU-13: vuelve a anunciar a todos los titulares activos al arrancar.
 *
 * <p>notification-service conoce a los titulares por el evento que se publica al aprobar
 * una solicitud. Los que se aprobaron antes de que existiera ese evento no estan en su
 * proyeccion. Este proceso los anuncia otra vez; la proyeccion los inserta o actualiza, asi
 * que repetirlo no hace dano.
 *
 * <p>Apagado por defecto. Se enciende una vez, en el despliegue que estrena las
 * notificaciones, con {@code AYNI_REPUBLICAR_TITULARES=true}, y se vuelve a apagar.
 */
@Component
@ConditionalOnProperty(name = "ayni.notificaciones.republicar-titulares", havingValue = "true")
public class RepublicadorDeTitulares implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RepublicadorDeTitulares.class);

    private final UsuarioJpaRepository usuarios;
    private final RepositorioDeSolicitudesPort solicitudes;
    private final PublicadorDeSolicitudesPort publicador;

    RepublicadorDeTitulares(UsuarioJpaRepository usuarios, RepositorioDeSolicitudesPort solicitudes,
                            PublicadorDeSolicitudesPort publicador) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        this.publicador = publicador;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var activos = usuarios.findByEstado(EstadoUsuario.ACTIVO.name());
        activos.forEach(u -> publicador.anunciarTitularActivado(u.getId(), new CorreoElectronico(u.getCorreo()),
                solicitudes.nombreDePilaDe(u.getId()).orElse("")));
        log.info("Titulares activos republicados para notification-service: {}", activos.size());
    }
}
