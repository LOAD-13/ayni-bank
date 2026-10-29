package pe.ayni.bank.core.infrastructure.interbancaria;

import org.springframework.http.HttpStatus;

/**
 * Una transferencia interbancaria que no se puede hacer, con lo que la API responde: el estado
 * HTTP, un codigo estable para la web y un detalle que dice al cliente que paso y que hacer.
 * Nunca un 500 con el mensaje de una excepcion de Java.
 */
public class RechazoInterbancarioException extends RuntimeException {

    public enum Motivo {
        CCI_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY,
                "El codigo interbancario no es valido. Revisa los 20 digitos del CCI."),
        CCI_PROPIO(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ese CCI es de una cuenta Ayni. Usa Transferir para enviarle dinero al instante."),
        IMPORTE_FUERA_DE_RANGO(HttpStatus.UNPROCESSABLE_ENTITY,
                "El importe debe estar entre S/ 1.00 y S/ 5 000.00, con hasta dos decimales."),
        SALDO_INSUFICIENTE(HttpStatus.UNPROCESSABLE_ENTITY,
                "No tienes saldo suficiente para esta transferencia."),
        CONCEPTO_DEMASIADO_LARGO(HttpStatus.UNPROCESSABLE_ENTITY,
                "El concepto admite hasta 140 caracteres."),
        ALIAS_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY,
                "Escribe un nombre para el beneficiario, de hasta 80 caracteres."),
        SIN_CUENTA(HttpStatus.NOT_FOUND,
                "Todavia no tienes una cuenta en soles activa."),
        NO_ENCONTRADA(HttpStatus.NOT_FOUND,
                "No encontramos esa transferencia entre las tuyas.");

        private final HttpStatus estado;
        private final String detalle;

        Motivo(HttpStatus estado, String detalle) {
            this.estado = estado;
            this.detalle = detalle;
        }

        public HttpStatus estado() {
            return estado;
        }

        public String detalle() {
            return detalle;
        }
    }

    private final Motivo motivo;

    public RechazoInterbancarioException(Motivo motivo) {
        super(motivo.detalle());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
