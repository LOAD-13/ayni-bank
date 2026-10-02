package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.DatosDeclarados;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDocumento;
import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/** Implementa {@link RepositorioDeSolicitudesPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeSolicitudes implements RepositorioDeSolicitudesPort {

    /**
     * Un expediente de onboarding sin terminar caduca a los siete dias. Sin caducidad, la
     * tabla acumula indefinidamente datos personales de gente que nunca completo el alta,
     * lo que contradice el principio de minimizacion de la Ley N.o 29733.
     */
    private static final Duration VIGENCIA = Duration.ofDays(7);

    private static final String ESTADO_INICIAL = "INICIADA";
    private static final String ESTADO_EN_REVISION_MANUAL = "EN_REVISION_MANUAL";

    /** Coincide con los CHECK de V8: el contador de un lado nunca pasa de 3. */
    private static final short MAXIMO_DE_INTENTOS_POR_LADO = 3;
    private static final short PRIMER_PASO = 1;

    private final SolicitudJpaRepository repositorio;
    private final CifradorDeDatosPort cifrador;
    private final Clock reloj;

    AdaptadorRepositorioDeSolicitudes(SolicitudJpaRepository repositorio,
                                      CifradorDeDatosPort cifrador,
                                      Clock reloj) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
        this.reloj = reloj;
    }

    @Override
    public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
        SolicitudOnboardingEntity solicitud = nueva(usuarioId);

        // El cifrado ocurre aqui y no en el dominio: el dominio razona sobre el numero de
        // documento, no sobre su criptograma. Que este cifrado en reposo es una decision
        // de infraestructura, y aqui es donde se cruza esa frontera.
        solicitud.declarar(
                identidad.nombres(),
                identidad.apellidos(),
                identidad.documento().tipo().name(),
                cifrador.cifrar(identidad.documento().numero()),
                identidad.documento().ultimos4(),
                identidad.fechaNacimiento().valor());

        return repositorio.save(solicitud).getId();
    }

    @Override
    public UUID abrirSenuelo() {
        // Se persiste de verdad, con usuario_id nulo. Devolver un UUID inventado sin
        // escribir nada haria que la siguiente peticion sobre esa solicitud respondiera
        // «no existe», y ahi se acabaria la indistinguibilidad. Ver ADR-0008.
        return repositorio.save(nueva(null)).getId();
    }

    @Override
    public Optional<UUID> titularDe(UUID solicitudId) {
        return repositorio.findById(solicitudId).map(SolicitudOnboardingEntity::getUsuarioId);
    }

    @Override
    @Transactional
    public void marcarAprobada(UUID solicitudId) {
        repositorio.findById(solicitudId)
                .orElseThrow(() -> new IllegalStateException(
                        "Se intento aprobar una solicitud inexistente."))
                .aprobar(reloj.instant());
    }

    @Override
    @Transactional
    public int registrarIntentoFallidoDeKyc(UUID solicitudId, TipoDeDocumentoKyc lado) {
        return repositorio.findById(solicitudId)
                .orElseThrow(() -> new IllegalStateException(
                        "Se intento registrar un fallo de KYC sobre una solicitud inexistente."))
                .incrementarIntentosDeVerificacionKyc(
                        lado == TipoDeDocumentoKyc.ANVERSO, MAXIMO_DE_INTENTOS_POR_LADO, reloj.instant());
    }

    @Override
    @Transactional
    public void marcarEnRevisionManual(UUID solicitudId) {
        repositorio.findById(solicitudId)
                .orElseThrow(() -> new IllegalStateException(
                        "Se intento derivar a revision manual una solicitud inexistente."))
                .derivarARevisionManual(reloj.instant());
    }

    @Override
    public boolean estaEnRevisionManual(UUID solicitudId) {
        return repositorio.findById(solicitudId)
                .map(solicitud -> ESTADO_EN_REVISION_MANUAL.equals(solicitud.getEstado()))
                .orElse(false);
    }

    /**
     * Descifra el numero aqui, en la frontera, igual que se cifro al guardarlo: el dominio
     * compara numeros, no criptogramas. Vacio en los senuelos, que no guardan datos.
     */
    @Override
    public Optional<DatosDeclarados> datosDeclaradosDe(UUID solicitudId) {
        return repositorio.findById(solicitudId)
                .filter(solicitud -> solicitud.getDocumentoDeclarado() != null)
                .map(solicitud -> new DatosDeclarados(
                        solicitud.getNombresDeclarados(),
                        solicitud.getApellidosDeclarados(),
                        TipoDocumento.valueOf(solicitud.getTipoDocumentoDeclarado()),
                        cifrador.descifrar(solicitud.getDocumentoDeclarado()),
                        solicitud.getFechaNacimientoDeclarada()));
    }

    @Override
    @Transactional
    public void marcarDocumentoCargado(UUID solicitudId) {
        repositorio.findById(solicitudId)
                .orElseThrow(() -> new IllegalStateException(
                        "Se intento marcar el documento de una solicitud inexistente."))
                .marcarDocumentoCargado(reloj.instant());
    }

    @Override
    public Optional<String> nombreDePilaDe(UUID usuarioId) {
        return repositorio.findFirstByUsuarioIdOrderByCreadaEnDesc(usuarioId)
                .map(SolicitudOnboardingEntity::getNombresDeclarados)
                .filter(nombres -> !nombres.isBlank())
                .map(nombres -> nombres.split(" ")[0]);
    }

    private SolicitudOnboardingEntity nueva(UUID usuarioId) {
        Instant ahora = reloj.instant();
        return new SolicitudOnboardingEntity(
                UUID.randomUUID(), usuarioId, ESTADO_INICIAL, PRIMER_PASO,
                ahora, ahora, ahora.plus(VIGENCIA));
    }
}
