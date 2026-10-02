package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de Spring Data. No sale de este paquete: fuera se conoce el puerto. */
interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, UUID> {

    boolean existsByCorreo(String correo);

    Optional<UsuarioEntity> findByCorreo(String correo);
}
