package pe.ayni.bank.identity.domain.model;

/** Lo que el almacen sabe de un objeto ya subido, sin descargarlo. */
public record ObjetoAlmacenado(long tamanoBytes, String tipoDeContenido) {
}
