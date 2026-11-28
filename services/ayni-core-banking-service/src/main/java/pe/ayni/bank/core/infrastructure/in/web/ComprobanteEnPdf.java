package pe.ayni.bank.core.infrastructure.in.web;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.draw.LineSeparator;

import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;

/**
 * El comprobante de HU-07 en PDF.
 *
 * <p>Lo genera el banco y no el navegador: el documento que el cliente guarda o reenvia es
 * siempre el mismo, con la hora de Lima y las cuentas enmascaradas igual que en pantalla.
 * No lleva saldo: un comprobante acredita el movimiento, no el estado de la cuenta, y el
 * saldo de hoy no es el de entonces.
 *
 * <p>Las fuentes estandar del PDF (Helvetica) usan la codificacion WinAnsi, que incluye las
 * tildes, la ene y el punto medio: no hace falta incrustar una fuente.
 */
final class ComprobanteEnPdf {

    private static final Color AZUL = new Color(6, 68, 117);
    private static final Color AZUL_FONDO = new Color(243, 248, 252);
    private static final Color DORADO = new Color(197, 158, 65);
    private static final Color EXITO = new Color(15, 122, 82);
    private static final Color GRIS = new Color(84, 96, 110);
    private static final Color LINEA = new Color(220, 228, 236);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy, HH:mm:ss", Locale.forLanguageTag("es-PE")).withZone(LIMA);
    private static final byte[] LOGO = leerLogo();

    private ComprobanteEnPdf() {
    }

    static byte[] generar(Comprobante c) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A5, 40, 40, 32, 32);
        PdfWriter.getInstance(documento, salida);
        documento.addTitle("Comprobante " + c.movimientoId());
        documento.addAuthor("Ayni Bank");
        documento.open();

        Image logo = logo();
        logo.scaleToFit(104, 80);
        logo.setAlignment(Element.ALIGN_CENTER);
        documento.add(logo);
        documento.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 4)));
        documento.add(new LineSeparator(1.2f, 22, DORADO, Element.ALIGN_CENTER, 0));

        documento.add(centrado(titulo(c), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, AZUL), 14, 2));
        documento.add(centrado("Operación realizada con éxito",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, EXITO), 0, 12));

        PdfPCell bloque = new PdfPCell();
        bloque.setBorder(PdfPCell.NO_BORDER);
        bloque.setBackgroundColor(AZUL_FONDO);
        bloque.setPadding(12);
        bloque.addElement(centrado("Importe", FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS), 0, 2));
        bloque.addElement(centrado(importe(c), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, AZUL), 0, 4));
        PdfPTable importe = new PdfPTable(1);
        importe.setWidthPercentage(100);
        importe.addCell(bloque);
        importe.setSpacingAfter(12);
        documento.add(importe);

        PdfPTable tabla = new PdfPTable(new float[] {2, 3});
        tabla.setWidthPercentage(100);
        fila(tabla, "Fecha y hora (Lima)", FECHA.format(c.registradoEn()));
        fila(tabla, "Cuenta de origen", legible(c.cuentaOrigen()));
        fila(tabla, "Cuenta de destino", legible(c.cuentaDestino()));
        fila(tabla, "Concepto", c.concepto() == null || c.concepto().isBlank()
                ? "Transferencia" : legible(c.concepto()));
        fila(tabla, "Nº de operación", c.movimientoId().toString());
        documento.add(tabla);

        documento.add(centrado("Ayni Bank · Banca 100 % digital · Hoy por ti, mañana por mí.\n"
                + "Proyecto académico de la Universidad Tecnológica del Perú. Este documento no requiere firma.",
                FontFactory.getFont(FontFactory.HELVETICA, 7, GRIS), 22, 0));

        documento.close();
        return salida.toByteArray();
    }

    private static String titulo(Comprobante c) {
        boolean recibido = c.sentido() == TipoDeAsiento.ABONO;
        return switch (c.tipo()) {
            case TRANSFERENCIA -> recibido ? "Transferencia recibida" : "Constancia de transferencia";
            case DEPOSITO_SIMULADO -> "Constancia de depósito";
            case INTERES -> "Constancia de abono de intereses";
            case CONVERSION -> "Constancia de cambio de moneda";
        };
    }

    private static String importe(Comprobante c) {
        String simbolo = "PEN".equals(c.importe().moneda().name()) ? "S/ " : "US$ ";
        return simbolo + c.importe().importe().toPlainString();
    }

    /**
     * «**********0031» como «•••• 0031», igual que en la banca. Se recorre el texto en lugar de usar una expresión
     * regular: un patrón con repetición sin tope tenía backtracking superlineal (Sonar S8786).
     */
    static String legible(String texto) {
        StringBuilder salida = new StringBuilder(texto.length());
        int i = 0;
        while (i < texto.length()) {
            int fin = i;
            while (fin < texto.length() && texto.charAt(fin) == '*') {
                fin++;
            }
            if (fin - i >= 4 && fin + 4 <= texto.length() && texto.substring(fin, fin + 4).chars().allMatch(Character::isDigit)) {
                salida.append("•••• ");
                i = fin;
            } else {
                salida.append(texto, i, Math.max(fin, i + 1));
                i = Math.max(fin, i + 1);
            }
        }
        return salida.toString().replaceFirst("^Deposito", "Depósito").replaceFirst("^Devolucion", "Devolución");
    }

    private static Paragraph centrado(String texto, Font fuente, float antes, float despues) {
        Paragraph parrafo = new Paragraph(texto, fuente);
        parrafo.setAlignment(Element.ALIGN_CENTER);
        parrafo.setSpacingBefore(antes);
        parrafo.setSpacingAfter(despues);
        return parrafo;
    }

    private static void fila(PdfPTable tabla, String clave, String valor) {
        tabla.addCell(celda(clave, FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS), Element.ALIGN_LEFT));
        tabla.addCell(celda(valor, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9), Element.ALIGN_RIGHT));
    }

    private static PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBorder(PdfPCell.BOTTOM);
        celda.setBorderColor(LINEA);
        celda.setHorizontalAlignment(alineacion);
        celda.setPaddingTop(7);
        celda.setPaddingBottom(7);
        return celda;
    }

    private static Image logo() {
        try {
            return Image.getInstance(LOGO);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] leerLogo() {
        try (InputStream logo = ComprobanteEnPdf.class.getResourceAsStream("/marca/logo.png")) {
            if (logo == null) {
                throw new IllegalStateException("Falta el logo del comprobante (/marca/logo.png)");
            }
            return logo.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
