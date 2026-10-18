package taan.online;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "taan.online", importOptions = ImportOption.DoNotIncludeTests.class)
class RoomArchitectureTest {
    @ArchTest
    static final ArchRule CONTROL_OPERATIONS_OWNER = classes().that().haveFullyQualifiedName(
        "taan.online.room.service.RoomControlOperations"
    ).should().onlyBeAccessed().byClassesThat().haveNameMatching(
        "taan\\.online\\.room\\.service\\.(RoomRegistry|RoomControlOperations)"
    );

    @ArchTest
    static final ArchRule START_OPERATIONS_OWNER = classes().that().haveSimpleName("RoomStartOperations")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.room\\.service\\."
                + "(RoomRegistry|RoomStartOperations|RoomMatchOperations|RoomDepartureOperations)"
        );

    @ArchTest
    static final ArchRule EXPULSION_OPERATIONS_OWNER = classes().that().haveSimpleName("RoomExpulsionOperations")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.room\\.service\\.(RoomRegistry|RoomExpulsionOperations)"
        );

    @ArchTest
    static final ArchRule PASSWORD_ENCAPSULATION = noClasses().that().resideInAnyPackage(
        "taan.online.room.service.model..",
        "taan.online.room.protocol..",
        "taan.online.room.view..",
        "taan.online.lobby.."
    ).should().dependOnClassesThat().haveSimpleName("RoomPassword");

    @ArchTest
    static final ArchRule PACKET_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.room.protocol.packet..",
            "taan.online.character.protocol.packet..",
            "taan.online.protocol.option.packet.."
        );

    @ArchTest
    static final ArchRule CODEC_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.room.protocol..",
            "taan.online.character.protocol..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule MODEL_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.room.model..",
            "taan.online.character.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule SERVICE_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.room.service..",
            "taan.online.room.model..",
            "taan.online.room.logging..",
            "taan.online.logging..",
            "taan.online.channel.service..",
            "taan.online.channel.model..",
            "taan.online.character.model..",
            "taan.online.character.repository",
            "taan.online.inventory.model..",
            "taan.online.inventory.service..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule SERVICE_MODEL_DEPENDENCIES = classes().that().resideInAPackage(
        "taan.online.room.service.model.."
    ).should().onlyDependOnClassesThat().resideInAnyPackage(
        "java..",
        "taan.online.room.service.model..",
        "taan.online.room.model.."
    );

    @ArchTest
    static final ArchRule CONTROLLER_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.controller..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.logging..",
            "taan.online.room.logging..",
            "taan.online.room.controller..",
            "taan.online.room.service..",
            "taan.online.room.model..",
            "taan.online.room.protocol..",
            "taan.online.room.view..",
            "taan.online.channel.service..",
            "taan.online.character.controller..",
            "taan.online.character.model..",
            "taan.online.character.protocol.packet..",
            "taan.online.account.model..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule VIEW_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.view..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.room.view..",
            "taan.online.room.service..",
            "taan.online.room.model..",
            "taan.online.room.protocol..",
            "taan.online.protocol",
            "taan.online.protocol.connection.codec",
            "taan.online.protocol.connection.packet",
            "taan.online.character.controller..",
            "taan.online.character.model..",
            "taan.online.character.protocol.packet..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule CATALOG_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.logging..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "org.slf4j.event..",
            "taan.online.logging",
            "taan.online.room.logging.."
        );

    @ArchTest
    static final ArchRule CORE_IO_DEPENDENCIES = noClasses().that().resideInAnyPackage(
        "taan.online.room.model..",
        "taan.online.room.service.."
    ).should().dependOnClassesThat().resideInAnyPackage(
        "java.sql..",
        "javax.sql..",
        "java.net..",
        "java.io..",
        "java.nio.channels..",
        "java.nio.file.."
    );

    @ArchTest
    static final ArchRule LAYER_CYCLES = slices().matching("taan.online.room.(*)..").should().beFreeOfCycles();
}
