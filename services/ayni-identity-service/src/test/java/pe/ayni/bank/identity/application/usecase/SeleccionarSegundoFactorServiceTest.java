package pe.ayni.bank.identity.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeleccionarSegundoFactorServiceTest {

    private RepositorioEnMemoria repositorio;
    private GeneradorTotpEnMemoria generadorTotp;
    private SeleccionarSegundoFactorService servicio;

    @BeforeEach
    void setUp() {
        repositorio = new RepositorioEnMemoria();
        generadorTotp = new GeneradorTotpEnMemoria();
        servicio = new SeleccionarSegundoFactorService(repositorio, generadorTotp);
    }

    @Test
    @DisplayName("Seleccionar metodo CORREO_ELECTRONICO registra un nuevo metodo sin secreto TOTP")
    void testSeleccionarMetodoCorreo() {
        UUID usuarioId = UUID.randomUUID();

        MetodoDeSegundoFactor metodo = servicio.seleccionarMetodo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertNotNull(metodo.id());
        assertEquals(usuarioId, metodo.usuarioId());
        assertEquals(TipoDeSegundoFactor.CORREO_ELECTRONICO, metodo.tipo());
        assertNull(metodo.secreto());
        assertFalse(metodo.estaConfirmado());
    }

    @Test
    @DisplayName("Seleccionar metodo APP_AUTENTICADORA genera y guarda un nuevo secreto TOTP")
    void testSeleccionarMetodoTotp() {
        UUID usuarioId = UUID.randomUUID();

        MetodoDeSegundoFactor metodo = servicio.seleccionarMetodo(usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA);

        assertNotNull(metodo.id());
        assertEquals(usuarioId, metodo.usuarioId());
        assertEquals(TipoDeSegundoFactor.APP_AUTENTICADORA, metodo.tipo());
        assertEquals("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", metodo.secreto());
        assertFalse(metodo.estaConfirmado());
    }

    @Test
    @DisplayName("Seleccionar un metodo ya existente devuelve el metodo previamente registrado sin duplicarlo")
    void testSeleccionarMetodoExistente() {
        UUID usuarioId = UUID.randomUUID();

        MetodoDeSegundoFactor primero = servicio.seleccionarMetodo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);
        MetodoDeSegundoFactor segundo = servicio.seleccionarMetodo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertEquals(primero.id(), segundo.id());
        assertEquals(1, repositorio.metodos.size());
    }

    private static class RepositorioEnMemoria implements RepositorioDeMetodoSegundoFactorPort {
        final List<MetodoDeSegundoFactor> metodos = new ArrayList<>();

        @Override
        public Optional<MetodoDeSegundoFactor> buscarPorUsuarioYTipo(UUID usuarioId, TipoDeSegundoFactor tipo) {
            return metodos.stream()
                    .filter(m -> m.usuarioId().equals(usuarioId) && m.tipo() == tipo)
                    .findFirst();
        }

        @Override
        public List<MetodoDeSegundoFactor> listarPorUsuario(UUID usuarioId) {
            return metodos.stream().filter(m -> m.usuarioId().equals(usuarioId)).toList();
        }

        @Override
        public MetodoDeSegundoFactor guardar(MetodoDeSegundoFactor metodo) {
            metodos.removeIf(m -> m.id().equals(metodo.id()));
            metodos.add(metodo);
            return metodo;
        }
    }

    private static class GeneradorTotpEnMemoria implements GeneradorDeTotpPort {
        @Override
        public SecretoTotp generarSecreto() {
            return new SecretoTotp("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
        }

        @Override
        public boolean verificar(SecretoTotp secreto, pe.ayni.bank.identity.domain.model.CodigoTotp codigo, java.time.Instant momento) {
            return true;
        }

        @Override
        public String uriDeAprovisionamiento(SecretoTotp secreto, CorreoElectronico correo) {
            return "otpauth://totp/Ayni:" + correo.valor() + "?secret=" + secreto.valor() + "&issuer=Ayni";
        }
    }
}
