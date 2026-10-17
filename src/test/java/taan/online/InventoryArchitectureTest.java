package taan.online;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "taan.online", importOptions = ImportOption.DoNotIncludeTests.class)
class InventoryArchitectureTest {
    @ArchTest
    static final ArchRule REPOSITORY_DEPENDENCIES = classes().that().resideInAPackage(
        "taan.online.inventory.repository"
    )
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.account.model..",
            "taan.online.inventory.repository",
            "taan.online.inventory.model.."
        );

    @ArchTest
    static final ArchRule SERVICE_DEPENDENCIES = classes().that().resideInAPackage("taan.online.inventory.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.account.model..",
            "taan.online.inventory.service..",
            "taan.online.inventory.repository",
            "taan.online.inventory.model..",
            "taan.online.inventory.logging..",
            "taan.online.logging.."
        );

    @ArchTest
    static final ArchRule SERVICE_MODEL_DEPENDENCIES = classes().that().resideInAPackage(
        "taan.online.inventory.service.model.."
    )
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.account.model..",
            "taan.online.inventory.model..",
            "taan.online.inventory.service.model.."
        );

    @ArchTest
    static final ArchRule CONTROLLER_DEPENDENCIES = classes().that().resideInAPackage(
        "taan.online.inventory.controller.."
    )
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.inventory.controller..",
            "taan.online.inventory.protocol..",
            "taan.online.inventory.model..",
            "taan.online.inventory.service.."
        );

    @ArchTest
    static final ArchRule CATALOG_DEPENDENCIES = classes().that().resideInAPackage("taan.online.inventory.logging..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "org.slf4j.event..",
            "taan.online.logging",
            "taan.online.inventory.logging.."
        );

}
