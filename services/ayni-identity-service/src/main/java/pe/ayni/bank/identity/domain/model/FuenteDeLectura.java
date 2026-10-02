package pe.ayni.bank.identity.domain.model;

/**
 * De donde salieron los datos del DNI.
 *
 * <p>{@code MRZ} es la zona de lectura mecanica del reverso, con digitos verificadores:
 * si validan, la lectura es fiable. {@code HEURISTICA_ANVERSO} es texto libre del anverso,
 * sin forma de comprobar que el OCR leyo bien. {@code TITULAR} son los datos que la persona
 * confirmo o corrigio en pantalla. Ver ADR-0015 y ADR-0026.
 */
public enum FuenteDeLectura {
    MRZ,
    HEURISTICA_ANVERSO,
    TITULAR
}
