package pe.ayni.bank.identity.aceptacion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRegistroPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Adaptadores en memoria para las pruebas de aceptacion.
 *
 * <p>Se agrupan aqui, y no dentro de los pasos, porque son cuatro clases con estado que los
 * pasos consultan constantemente; tenerlas sueltas convertiria el fichero de pasos en un
 * muro donde lo que se comprueba queda enterrado bajo lo que simula.
 */
final class DoblesEnMemoria {

    private DoblesEnMemoria() {
    }

    static final class Usuarios implements RepositorioDeUsuariosPort {
        final List<Usuario> guardados = new ArrayList<>();
        final List<String> correosExistentes = new ArrayList<>();

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
            guardados.add(usuario);
            return usuario;
        }
    }

    static final class Solicitudes implements RepositorioDeSolicitudesPort {
        final Map<UUID, UUID> reales = new HashMap<>();
        final List<UUID> senuelos = new ArrayList<>();
        final List<UUID> aprobadas = new ArrayList<>();
        final List<IdentidadDeclarada> identidades = new ArrayList<>();

        @Override
        public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
            UUID id = UUID.randomUUID();
            reales.put(id, usuarioId);
            identidades.add(identidad);
            return id;
        }

        @Override
        public UUID abrirSenuelo() {
            UUID id = UUID.randomUUID();
            senuelos.add(id);
            return id;
        }

        @Override
        public java.util.Optional<UUID> titularDe(UUID solicitudId) {
            return java.util.Optional.ofNullable(reales.get(solicitudId));
        }

        @Override
        public void marcarAprobada(UUID solicitudId) {
            aprobadas.add(solicitudId);
        }

        @Override
        public java.util.Optional<String> nombreDePilaDe(UUID usuarioId) {
            return java.util.Optional.of("Ana");
        }
    }

    /**
     * Deriva de verdad no: eso lo prueba {@code CifradorArgon2idTest}. Aqui solo hace falta
     * que el resultado tenga la forma de una derivacion Argon2id y poder contar cuantas
     * veces se llamo, que es lo que comprueba la paridad de tiempos del escenario 2.
     */
    static final class Cifrador implements CifradorDeContrasenasPort {
        int vecesQueSeCifro;

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

    static final class Notificador implements NotificadorDeRegistroPort {
        final List<String> bienvenidas = new ArrayList<>();
        final List<String> avisosDeIntento = new ArrayList<>();

        @Override
        public void enviarBienvenida(CorreoElectronico correo) {
            bienvenidas.add(correo.valor());
        }

        @Override
        public void avisarIntentoDeRegistroSobreCuentaExistente(CorreoElectronico correo) {
            avisosDeIntento.add(correo.valor());
        }
    }
}
