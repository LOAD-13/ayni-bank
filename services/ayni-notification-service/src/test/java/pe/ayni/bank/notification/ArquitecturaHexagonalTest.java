package pe.ayni.bank.notification;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * La arquitectura hexagonal no es una recomendacion: es una restriccion verificada.
 * Si alguna de estas reglas se viola, el build falla y el Pull Request queda bloqueado.
 *
 * Ver ADR-0002 y CODESTYLE.md
 */
@AnalyzeClasses(packages = "pe.ayni.bank.notification", importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArquitecturaHexagonalTest {

    @ArchTest
    static final ArchRule el_dominio_no_conoce_el_framework =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "javax.persistence..",
                            "com.fasterxml.jackson..")
                    .allowEmptyShould(true)
                    .because("el dominio debe poder probarse sin levantar Spring ni una base de datos");

    @ArchTest
    static final ArchRule el_dominio_no_depende_de_la_infraestructura =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure..", "..application..")
                    .allowEmptyShould(true)
                    .because("las dependencias apuntan hacia dentro, nunca hacia fuera");

    @ArchTest
    static final ArchRule las_capas_se_respetan =
            layeredArchitecture().consideringAllDependencies()
                    .layer("Dominio").definedBy("..domain..")
                    .layer("Aplicacion").definedBy("..application..")
                    .layer("Infraestructura").definedBy("..infrastructure..")
                    .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacion", "Infraestructura")
                    .whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer()
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule los_puertos_de_salida_se_nombran_Port =
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                    .that().resideInAPackage("..domain.port.out..")
                    .should().haveSimpleNameEndingWith("Port")
                    .allowEmptyShould(true)
                    .because("CODESTYLE.md fija la nomenclatura de los puertos");

    @ArchTest
    static final ArchRule los_casos_de_uso_se_nombran_UseCase =
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                    .that().resideInAPackage("..domain.port.in..")
                    .should().haveSimpleNameEndingWith("UseCase")
                    .allowEmptyShould(true)
                    .because("CODESTYLE.md fija la nomenclatura de los casos de uso");
}
