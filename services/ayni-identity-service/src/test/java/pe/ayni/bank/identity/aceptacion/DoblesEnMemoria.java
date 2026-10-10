package pe.ayni.bank.identity.aceptacion;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DatosDeclarados;
import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.EvaluacionDeCaptura;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.ObjetoAlmacenado;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRegistroPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeVerificacionKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDocumentosKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;
import pe.ayni.bank.identity.domain.port.out.VerificadorKycPort;

/**
 * Adaptadores en memoria para las pruebas de aceptacion.
 *
 * <p>Se agrupan aqui, y no dentro de los pasos, porque son clases con estado que los pasos
 * consultan constantemente; tenerlas sueltas convertiria el fichero de pasos en un muro donde
 * lo que se comprueba queda enterrado bajo lo que simula.
 *
 * <p>Publicos porque las pruebas unitarias de los casos de uso de HU-02 usan los mismos: un
 * doble por puerto, en vez de uno distinto en cada prueba que se desincroniza al cambiar el
 * puerto.
 */
public final class DoblesEnMemoria {

    private DoblesEnMemoria() {
    }

    public static final class Usuarios implements RepositorioDeUsuariosPort {
        public final List<Usuario> guardados = new ArrayList<>();
        public final List<String> correosExistentes = new ArrayList<>();

        @Override
        public boolean existeCorreo(CorreoElectronico correo) {
            return correosExistentes.contains(correo.valor());
        }

        @Override
        public Optional<Usuario> buscarPorCorreo(CorreoElectronico correo) {
            return guardados.stream().filter(u -> u.correo().equals(correo)).findFirst();
        }

        @Override
        public Optional<Usuario> buscarPorId(UUID id) {
            return guardados.stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Usuario guardar(Usuario usuario) {
            // Guardar uno que ya existe lo sustituye, como hace la base (HU-21 cambia la contrasena).
            guardados.removeIf(u -> u.id().equals(usuario.id()));
            guardados.add(usuario);
            return usuario;
        }
    }

    public static final class Solicitudes implements RepositorioDeSolicitudesPort {
        public final Map<UUID, UUID> reales = new HashMap<>();
        public final List<UUID> senuelos = new ArrayList<>();
        public final List<UUID> aprobadas = new ArrayList<>();
        public final List<IdentidadDeclarada> identidades = new ArrayList<>();
        public final Map<UUID, IdentidadDeclarada> identidadPorSolicitud = new HashMap<>();
        public final Map<UUID, Map<TipoDeDocumentoKyc, Integer>> intentosKyc = new HashMap<>();
        public final List<UUID> enRevisionManual = new ArrayList<>();
        public final List<UUID> documentosCargados = new ArrayList<>();

        @Override
        public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
            UUID id = UUID.randomUUID();
            reales.put(id, usuarioId);
            identidades.add(identidad);
            if (identidad != null) {
                identidadPorSolicitud.put(id, identidad);
            }
            return id;
        }

        @Override
        public UUID abrirSenuelo() {
            UUID id = UUID.randomUUID();
            senuelos.add(id);
            return id;
        }

        @Override
        public Optional<UUID> titularDe(UUID solicitudId) {
            return Optional.ofNullable(reales.get(solicitudId));
        }

        @Override
        public void marcarAprobada(UUID solicitudId) {
            aprobadas.add(solicitudId);
        }

        /** Tope de 3, igual que la restriccion de la tabla (V8). */
        @Override
        public int registrarIntentoFallidoDeKyc(UUID solicitudId, TipoDeDocumentoKyc lado) {
            return intentosKyc.computeIfAbsent(solicitudId, id -> new EnumMap<>(TipoDeDocumentoKyc.class))
                    .merge(lado, 1, (actual, uno) -> Math.min(actual + uno, 3));
        }

        public int intentosDe(UUID solicitudId, TipoDeDocumentoKyc lado) {
            return intentosKyc.getOrDefault(solicitudId, Map.of()).getOrDefault(lado, 0);
        }

        @Override
        public void marcarEnRevisionManual(UUID solicitudId) {
            enRevisionManual.add(solicitudId);
        }

        @Override
        public boolean estaEnRevisionManual(UUID solicitudId) {
            return enRevisionManual.contains(solicitudId);
        }

        @Override
        public Optional<DatosDeclarados> datosDeclaradosDe(UUID solicitudId) {
            return Optional.ofNullable(identidadPorSolicitud.get(solicitudId))
                    .map(identidad -> new DatosDeclarados(identidad.nombres(), identidad.apellidos(),
                            identidad.documento().tipo(), identidad.documento().numero(),
                            identidad.fechaNacimiento().valor()));
        }

        @Override
        public void marcarDocumentoCargado(UUID solicitudId) {
            documentosCargados.add(solicitudId);
        }

        @Override
        public Optional<String> nombreDePilaDe(UUID usuarioId) {
            return Optional.of("Ana");
        }
    }

    /**
     * Deriva de verdad no: eso lo prueba {@code CifradorArgon2idTest}. Aqui solo hace falta
     * que el resultado tenga la forma de una derivacion Argon2id y poder contar cuantas
     * veces se llamo, que es lo que comprueba la paridad de tiempos del escenario 2.
     */
    public static final class Cifrador implements CifradorDeContrasenasPort {
        public int vecesQueSeCifro;

        @Override
        public ContrasenaCifrada cifrar(String contrasenaEnClaro) {
            vecesQueSeCifro++;
            return new ContrasenaCifrada(
                    "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$" + contrasenaEnClaro.hashCode());
        }

        @Override
        public boolean coincide(String contrasenaEnClaro, ContrasenaCifrada cifrada) {
            return cifrar(contrasenaEnClaro).valor().equals(cifrada.valor());
        }
    }

    public static final class Notificador implements NotificadorDeRegistroPort {
        public final List<String> bienvenidas = new ArrayList<>();
        public final List<String> avisosDeIntento = new ArrayList<>();

        @Override
        public void enviarBienvenida(CorreoElectronico correo, java.util.UUID solicitudId) {
            bienvenidas.add(correo.valor());
        }

        @Override
        public void avisarIntentoDeRegistroSobreCuentaExistente(CorreoElectronico correo) {
            avisosDeIntento.add(correo.valor());
        }
    }

    public static final class NotificadorDeVerificacionKyc implements NotificadorDeVerificacionKycPort {
        public final List<String> avisadosDeRevisionManual = new ArrayList<>();

        @Override
        public void avisarEnRevisionManual(CorreoElectronico correo) {
            avisadosDeRevisionManual.add(correo.valor());
        }
    }

    /** Fotos aceptadas y lecturas del DNI, en el orden en que se guardaron. */
    public static final class DocumentosKyc implements RepositorioDeDocumentosKycPort {
        public final List<DocumentoKyc> guardados = new ArrayList<>();
        public final Map<UUID, List<LecturaDelDni>> lecturas = new HashMap<>();

        @Override
        public void guardar(DocumentoKyc documento) {
            guardados.add(documento);
        }

        @Override
        public Optional<DocumentoKyc> ultimoDe(UUID solicitudId, TipoDeDocumentoKyc tipo) {
            return guardados.stream()
                    .filter(d -> d.solicitudId().equals(solicitudId) && d.tipo() == tipo)
                    .reduce((primero, segundo) -> segundo);
        }

        @Override
        public void guardarLectura(UUID solicitudId, LecturaDelDni lectura) {
            lecturas.computeIfAbsent(solicitudId, id -> new ArrayList<>()).add(lectura);
        }

        @Override
        public Optional<LecturaDelDni> ultimaLecturaOcrDe(UUID solicitudId) {
            return lecturas.getOrDefault(solicitudId, List.of()).stream()
                    .filter(l -> l.fuente() != FuenteDeLectura.TITULAR)
                    .reduce((primera, segunda) -> segunda);
        }

        public List<LecturaDelDni> lecturasDe(UUID solicitudId) {
            return lecturas.getOrDefault(solicitudId, List.of());
        }
    }

    /** MinIO en memoria: el hash es el SHA-256 real del contenido guardado. */
    public static final class Almacen implements AlmacenDeDocumentosPort {
        public final Map<String, byte[]> objetos = new HashMap<>();
        public final List<String> eliminados = new ArrayList<>();

        public void subir(String clave, byte[] contenido) {
            objetos.put(clave, contenido);
        }

        @Override
        public UrlDeSubida generarUrlDeSubida(String claveDeObjeto, String tipoDeContenido, long tamanoMaximoBytes) {
            return new UrlDeSubida("http://minio.local/ayni-kyc-documentos",
                    Map.of("key", claveDeObjeto, "Content-Type", tipoDeContenido), claveDeObjeto,
                    Instant.parse("2026-09-12T10:20:30Z"));
        }

        @Override
        public String calcularHash(String claveDeObjeto) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(objetos.get(claveDeObjeto)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Optional<ObjetoAlmacenado> describir(String claveDeObjeto) {
            return Optional.ofNullable(objetos.get(claveDeObjeto))
                    .map(contenido -> new ObjetoAlmacenado(contenido.length, "image/jpeg"));
        }

        @Override
        public void eliminar(String claveDeObjeto) {
            objetos.remove(claveDeObjeto);
            eliminados.add(claveDeObjeto);
        }
    }

    /**
     * kyc-service simulado. Responde con la siguiente evaluacion de la cola (o acepta si esta
     * vacia) y con la lectura configurada; con {@code caido} lanza lo mismo que el adaptador
     * real tras agotar Resilience4j.
     */
    public static final class VerificadorKyc implements VerificadorKycPort {
        public final Deque<EvaluacionDeCaptura> evaluaciones = new ArrayDeque<>();
        public Optional<LecturaDelDni> lectura = Optional.empty();
        public boolean caido;
        public int evaluacionesPedidas;
        public int extraccionesPedidas;

        @Override
        public EvaluacionDeCaptura evaluar(String claveDeObjeto, TipoDeDocumentoKyc lado) {
            evaluacionesPedidas++;
            fallarSiEstaCaido();
            return evaluaciones.isEmpty() ? EvaluacionDeCaptura.aprobada() : evaluaciones.poll();
        }

        @Override
        public Optional<LecturaDelDni> extraer(String claveAnverso, String claveReverso) {
            extraccionesPedidas++;
            fallarSiEstaCaido();
            return lectura;
        }

        private void fallarSiEstaCaido() {
            if (caido) {
                throw new KycServiceNoDisponibleException("kyc-service no respondio.",
                        new java.net.SocketTimeoutException("Read timed out"));
            }
        }
    }
}
