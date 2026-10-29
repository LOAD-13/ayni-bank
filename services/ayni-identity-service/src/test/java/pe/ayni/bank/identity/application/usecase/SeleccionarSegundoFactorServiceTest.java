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

    @Test
    @DisplayName("Seleccionar metodo SMS se rechaza porque no esta habilitado en esta version")
    void testSeleccionarMetodoSmsRechazado() {
        UUID usuarioId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> servicio.seleccionarMetodo(usuarioId, TipoDeSegundoFactor.SMS));
        assertTrue(ex.getMessage().contains("SMS"));
    }

    @Test
    @DisplayName("Antienumeracion: solicitud senuelo devuelve metodo efimero y sincroniza TOTP / desafio correo cuando existe titular")
    void testSolicitudSenueloYSincronizacionCompleta() {
        pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort solicitudes = org.mockito.Mockito.mock(
                pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort.class);
        pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort segundosFactores = org.mockito.Mockito.mock(
                pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort.class);
        pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort usuarios = org.mockito.Mockito.mock(
                pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort.class);
        pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase generarDesafio = org.mockito.Mockito.mock(
                pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase.class);

        SeleccionarSegundoFactorService completo = new SeleccionarSegundoFactorService(
                repositorio, generadorTotp, java.time.Clock.systemUTC(),
                solicitudes, segundosFactores, usuarios, generarDesafio);

        // 1. Solicitud senuelo (sin titular ni usuario en BD)
        UUID senueloId = UUID.randomUUID();
        org.mockito.Mockito.when(solicitudes.titularDe(senueloId)).thenReturn(Optional.empty());
        org.mockito.Mockito.when(usuarios.buscarPorId(senueloId)).thenReturn(Optional.empty());

        MetodoDeSegundoFactor senueloTotp = completo.seleccionarMetodo(senueloId, TipoDeSegundoFactor.APP_AUTENTICADORA);
        MetodoDeSegundoFactor senueloCorreo = completo.seleccionarMetodo(senueloId, TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertNotNull(senueloTotp.secreto());
        assertNull(senueloCorreo.secreto());
        assertTrue(repositorio.metodos.isEmpty());

        // 2. Solicitud real con titular: sincroniza SegundoFactor TOTP y dispara desafio por correo
        UUID solicitudId = UUID.randomUUID();
        UUID titularId = UUID.randomUUID();
        org.mockito.Mockito.when(solicitudes.titularDe(solicitudId)).thenReturn(Optional.of(titularId));
        org.mockito.Mockito.when(segundosFactores.buscarPorUsuario(titularId)).thenReturn(Optional.empty());
        org.mockito.Mockito.when(usuarios.buscarPorId(titularId)).thenReturn(Optional.of(
                org.mockito.Mockito.mock(pe.ayni.bank.identity.domain.model.Usuario.class)));

        MetodoDeSegundoFactor realTotp = completo.seleccionarMetodo(solicitudId, TipoDeSegundoFactor.APP_AUTENTICADORA);
        assertEquals(titularId, realTotp.usuarioId());
        org.mockito.Mockito.verify(segundosFactores).guardar(org.mockito.Mockito.any());

        MetodoDeSegundoFactor realCorreo = completo.seleccionarMetodo(solicitudId, TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertEquals(titularId, realCorreo.usuarioId());
        org.mockito.Mockito.verify(generarDesafio).generar(titularId, TipoDeSegundoFactor.CORREO_ELECTRONICO);
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
            return "otpauth://totp/Ayni:" + correo.valor() + "&secret=" + secreto.valor() + "&issuer=Ayni";
        }
    }
}

