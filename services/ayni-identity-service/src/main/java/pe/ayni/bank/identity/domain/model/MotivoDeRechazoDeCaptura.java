package pe.ayni.bank.identity.domain.model;

/**
 * Por que se rechaza la foto de un lado del DNI (HU-02, escenarios 2 y 3).
 *
 * <p>Se informa uno solo, el que conviene corregir primero; el orden lo decide kyc-service.
 * El solicitante necesita saber que repetir, no la lista completa de medidas.
 */
public enum MotivoDeRechazoDeCaptura {
    NO_ES_DNI,
    ENCUADRE,
    DESENFOQUE,
    REFLEJO,
    ILUMINACION
}
