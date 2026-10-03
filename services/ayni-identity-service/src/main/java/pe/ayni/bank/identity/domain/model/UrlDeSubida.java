package pe.ayni.bank.identity.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Formulario pre-firmado con el que el navegador sube un documento directo al almacen.
 *
 * <p>Es una politica POST, no una URL PUT: la politica fija el tipo de contenido y el
 * tamano maximo, y el almacen rechaza la subida si no los cumple. Una URL PUT firmada no
 * puede limitar el tamano, y el limite de 5 MB quedaria solo en manos del navegador.
 *
 * @param url destino del formulario (el bucket)
 * @param campos campos que el formulario debe enviar tal cual, antes del archivo
 * @param claveDeObjeto la clave con la que quedara guardado; el navegador la devuelve al
 *        pedir la evaluacion de la foto
 */
public record UrlDeSubida(String url, Map<String, String> campos, String claveDeObjeto, Instant expiraEn) {

    public UrlDeSubida {
        campos = Map.copyOf(campos);
    }
}
