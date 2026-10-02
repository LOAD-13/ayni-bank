package pe.ayni.bank.core.application.usecase;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.CuentaDuplicadaException;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/**
 * Orquesta HU-05: abre la cuenta de ahorro cuando el onboarding queda aprobado.
 *
 * <p><strong>Todo ocurre en una sola transaccion</strong>, y eso incluye escribir el evento
 * en la bandeja de salida. Es el criterio de aceptacion y tambien la unica forma de que el
 * escenario 4 —RabbitMQ caido— no deje el sistema incoherente: o se crea la cuenta y queda
 * el evento pendiente, o no pasa ninguna de las dos cosas.
 */
@Service
public class AbrirCuentaDeAhorroService implements AbrirCuentaDeAhorroUseCase {

    private static final Logger log = LoggerFactory.getLogger(AbrirCuentaDeAhorroService.class);

    private static final String AGREGADO = "Cuenta";
    private static final String EVENTO = "CuentaAperturada";
    private static final String CONCEPTO_DE_APERTURA = "Apertura de cuenta de ahorro";

    private final RepositorioDeCuentasPort cuentas;
    private final PublicadorDeEventosPort eventos;
    private final RegistroDeIdempotenciaPort idempotencia;
    private final short productoDeAhorro;
    private final Clock reloj;

    public AbrirCuentaDeAhorroService(
            RepositorioDeCuentasPort cuentas,
            PublicadorDeEventosPort eventos,
            RegistroDeIdempotenciaPort idempotencia,
            @Value("${ayni.productos.ahorro-soles}") short productoDeAhorro,
            Clock reloj) {
        this.cuentas = cuentas;
        this.eventos = eventos;
        this.idempotencia = idempotencia;
        this.productoDeAhorro = productoDeAhorro;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Cuenta abrirPara(UUID usuarioId, UUID solicitudId, Moneda moneda) {
        // 1. Idempotencia. El mismo evento puede llegar dos veces —RabbitMQ entrega «al
        //    menos una vez»—, y la segunda tiene que devolver la cuenta que ya se creo en
        //    lugar de crear otra o de fallar.
        Optional<UUID> yaHecha = idempotencia.resultadoDe(solicitudId);
        if (yaHecha.isPresent()) {
            log.info("Evento repetido: la solicitud {} ya abrio una cuenta.", solicitudId);
            return cuentas.buscarActivaDe(usuarioId, moneda)
                    .orElseThrow(() -> new IllegalStateException(
                            "La solicitud figura procesada pero su cuenta no aparece."));
        }

        // 2. Una sola cuenta activa por titular y moneda. Escenario 2 de HU-05. La
        //    comprobacion se hace aqui para poder responder con un error con sentido, pero
        //    quien de verdad impide el duplicado es el indice unico parcial de la base: dos
        //    peticiones simultaneas superarian esta linea a la vez.
        if (cuentas.existeCuentaActiva(usuarioId, moneda)) {
            throw new CuentaDuplicadaException(moneda);
        }

        // 3. El correlativo lo entrega la base, no la aplicacion.
        NumeroDeCuenta numero = NumeroDeCuenta.de(moneda, cuentas.siguienteCorrelativo());

        Cuenta cuenta = cuentas.guardar(Cuenta.abrir(
                UUID.randomUUID(), usuarioId, productoDeAhorro, numero, moneda,
                reloj.instant()));

        // 4. El evento va a la bandeja de salida dentro de esta misma transaccion. No sale
        //    a RabbitMQ desde aqui: de eso se encarga el publicador, despues del COMMIT.
        eventos.registrar(AGREGADO, cuenta.id(), EVENTO, new CuentaAperturada(
                cuenta.id(), usuarioId, solicitudId,
                cuenta.numero().valor(), cuenta.cci().valor(),
                cuenta.moneda().name(), CONCEPTO_DE_APERTURA));

        idempotencia.recordar(solicitudId, cuenta.id());

        // Ni el numero ni el CCI en el log: identifican al titular ante terceros y con
        // ellos se le puede transferir dinero.
        log.info("Cuenta de ahorro aperturada. cuentaId={} usuarioId={} moneda={}",
                cuenta.id(), usuarioId, moneda);

        return cuenta;
    }

    /**
     * Carga del evento {@code CuentaAperturada}.
     *
     * <p>Lleva el numero y el CCI porque quien lo consume —la notificacion que avisa al
     * cliente— los necesita. Es un evento interno que viaja por la red privada del
     * despliegue, no una respuesta publica.
     */
    public record CuentaAperturada(UUID cuentaId, UUID usuarioId, UUID solicitudId,
                                   String numero, String cci, String moneda,
                                   String concepto) {
    }
}
