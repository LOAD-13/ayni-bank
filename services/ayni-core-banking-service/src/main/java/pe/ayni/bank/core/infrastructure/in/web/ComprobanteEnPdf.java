package pe.ayni.bank.core.infrastructure.in.web;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;

/**
 * El comprobante de HU-07 en PDF.
 *
 * <p>Lo genera el banco y no el navegador: el documento que el cliente guarda o reenvia es
 * siempre el mismo, con la hora de Lima y las cuentas enmascaradas igual que en pantalla.
 * No lleva saldo: un comprobante acredita el movimiento, no el estado de la cuenta, y el
 * saldo de hoy no es el de entonces.
 */
final class ComprobanteEnPdf {

    private static final Color AZUL = new Color(6, 68, 117);
    private static final Color GRIS = new Color(107, 119, 133);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", new Locale("es", "PE")).withZone(LIMA);

    private ComprobanteEnPdf() {
    }

    static byte[] generar(Comprobante c) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A5, 36, 36, 36, 36);
        PdfWriter.getInstance(documento, salida);
        documento.addTitle("Comprobante " + c.movimientoId());
        documento.addAuthor("Ayni Bank");
        documento.open();

        documento.add(new Paragraph("AYNI Bank", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, AZUL)));
        Paragraph titulo = new Paragraph(c.tipo() == TipoDeMovimiento.TRANSFERENCIA
                ? "Constancia de transferencia" : "Constancia de deposito",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
        titulo.setSpacingBefore(10);
        titulo.setSpacingAfter(12);
        documento.add(titulo);

        PdfPTable tabla = new PdfPTable(new float[] {2, 3});
        tabla.setWidthPercentage(100);
        fila(tabla, "Numero de operacion", c.movimientoId().toString());
        fila(tabla, "Fecha y hora (Lima)", FECHA.format(c.registradoEn()));
        fila(tabla, "Importe", "S/ " + c.importe().importe().toPlainString() + " " + c.importe().moneda().name());
        fila(tabla, "Cuenta de origen", c.cuentaOrigen());
        fila(tabla, "Cuenta de destino", c.cuentaDestino());
        fila(tabla, "Concepto", c.concepto() == null || c.concepto().isBlank() ? "Transferencia" : c.concepto());
        documento.add(tabla);

        Paragraph pie = new Paragraph("Ayni Bank · Banca 100 % digital. Proyecto academico de la Universidad "
                + "Tecnologica del Peru. Este documento no requiere firma.",
                FontFactory.getFont(FontFactory.HELVETICA, 7, GRIS));
        pie.setSpacingBefore(18);
        pie.setAlignment(Element.ALIGN_CENTER);
        documento.add(pie);

        documento.close();
        return salida.toByteArray();
    }

    private static void fila(PdfPTable tabla, String clave, String valor) {
        tabla.addCell(celda(clave, FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS)));
        tabla.addCell(celda(valor, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
    }

    private static PdfPCell celda(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBorder(PdfPCell.BOTTOM);
        celda.setBorderColor(new Color(220, 228, 236));
        celda.setPaddingTop(6);
        celda.setPaddingBottom(6);
        return celda;
    }
}
