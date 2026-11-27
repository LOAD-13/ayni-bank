package pe.ayni.bank.core.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;

class ComprobanteEnPdfTest {

    private static String texto(TipoDeAsiento sentido) throws Exception {
        Comprobante c = new Comprobante(UUID.fromString("6f9a2c10-4b1e-4d7a-9c3e-2a1b0c9d8e7f"),
                TipoDeMovimiento.TRANSFERENCIA, Dinero.de("150.50", Moneda.PEN),
                "**********0030", "**********0031", "Cena · a **********0031",
                Instant.parse("2026-11-19T22:52:00Z"), Dinero.de("850.00", Moneda.PEN), sentido);
        byte[] pdf = ComprobanteEnPdf.generar(c);
        try (PdfReader lector = new PdfReader(pdf)) {
            assertThat(lector.getNumberOfPages()).isEqualTo(1);
            return new PdfTextExtractor(lector).getTextFromPage(1);
        }
    }

    @Test
    @DisplayName("El PDF trae logo, tildes, el importe, la hora de Lima y las cuentas enmascaradas")
    void contenido() throws Exception {
        String texto = texto(TipoDeAsiento.CARGO);

        assertThat(texto).contains("Constancia de transferencia", "Operación realizada con éxito",
                "S/ 150.50", "19 de noviembre de 2026, 17:52:00", "0031", "Nº de operación",
                "6f9a2c10-4b1e-4d7a-9c3e-2a1b0c9d8e7f");
        // Un comprobante no muestra el saldo: el de hoy no es el de entonces.
        assertThat(texto).doesNotContain("850.00").doesNotContain("**********");
    }

    @Test
    @DisplayName("Quien recibe la transferencia ve «Transferencia recibida»")
    void recibida() throws Exception {
        assertThat(texto(TipoDeAsiento.ABONO)).contains("Transferencia recibida");
    }

    @Test
    @DisplayName("Los asteriscos del enmascarado se leen como en la banca")
    void cuentaLegible() {
        assertThat(ComprobanteEnPdf.legible("Cena · a **********0031")).isEqualTo("Cena · a •••• 0031");
        assertThat(ComprobanteEnPdf.legible("Deposito simulado")).isEqualTo("Depósito simulado");
    }
}
