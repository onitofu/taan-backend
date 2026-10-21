package taan.online;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "taan.online", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule PROTOCOL_STATE_MACHINE_DEPENDENCIES = classes().that().haveNameMatching(
        "taan\\.online\\.server\\.(ProtocolStateMachine|ProtocolPhase|ProtocolEvent|ProtocolDecision)"
    ).should().onlyDependOnClassesThat().haveNameMatching(
        "(?:\\[L)?(?:java\\.(?:lang\\..*|util\\.[^.]+)|taan\\.online\\.server\\."
            + "(?:ProtocolStateMachine|ProtocolPhase|ProtocolEvent|ProtocolDecision));?"
    );

    @ArchTest
    static final ArchRule SESSION_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.session.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.session.model..",
            "taan.online.character.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule SESSION_SERVICE_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.session.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.session.model..",
            "taan.online.session.service..",
            "taan.online.account.model..",
            "taan.online.account.service",
            "taan.online.account.service.model..",
            "taan.online.account.repository",
            "taan.online.character.service",
            "taan.online.character.repository",
            "taan.online.logging..",
            "taan.online.session.logging..",
            "taan.online.character.model..",
            "taan.online.channel.service",
            "taan.online.channel.model..",
            "taan.online.inventory.service.."
        );

    @ArchTest
    static final ArchRule SESSION_SERVICE_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.session.service.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.session.service.model..",
            "taan.online.session.model..",
            "taan.online.character.model..",
            "taan.online.account.model..",
            "taan.online.account.service.model.."
        );

    @ArchTest
    static final ArchRule SESSION_PACKET_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.session.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.session.protocol.packet..",
            "taan.online.character.protocol.packet.."
        );

    @ArchTest
    static final ArchRule SESSION_CODEC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.session.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.protocol..",
            "taan.online.session.protocol..",
            "taan.online.character.protocol..",
            "taan.online.inventory.model..",
            "taan.online.inventory.protocol.packet.."
        );

    @ArchTest
    static final ArchRule SESSION_CONTROLLER_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.session.controller..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.protocol",
            "taan.online.logging..",
            "taan.online.session.controller..",
            "taan.online.session.model..",
            "taan.online.session.service..",
            "taan.online.session.protocol..",
            "taan.online.session.logging..",
            "taan.online.account.model..",
            "taan.online.account.service.model..",
            "taan.online.character.model..",
            "taan.online.character.controller..",
            "taan.online.character.service..",
            "taan.online.character.protocol.packet.."
        );

    @ArchTest
    static final ArchRule SESSION_LAYER_CYCLES = slices().matching("taan.online.session.(*)..")
        .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule ACCOUNT_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "taan.online.account.model..");

    @ArchTest
    static final ArchRule ACCOUNT_SERVICE_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.logging..",
            "taan.online.account.logging..",
            "taan.online.session.logging..",
            "taan.online.account.model..",
            "taan.online.account.service..",
            "taan.online.account.repository"
        );

    @ArchTest
    static final ArchRule ACCOUNT_SERVICE_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.service.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.account.model..",
            "taan.online.account.service.model.."
        );

    @ArchTest
    static final ArchRule ACCOUNT_REPOSITORY_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.repository")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.account.model..",
            "taan.online.account.repository"
        );

    @ArchTest
    static final ArchRule ACCOUNT_CORE_IO_DEPENDENCIES = noClasses().that().resideInAnyPackage(
        "taan.online.account.model..",
        "taan.online.account.service..",
        "taan.online.account.repository"
    ).should().dependOnClassesThat().resideInAnyPackage(
        "java.sql..",
        "javax.sql..",
        "java.net..",
        "java.io..",
        "java.nio.channels..",
        "java.nio.file.."
    );

    @ArchTest
    static final ArchRule ACCOUNT_JDBC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.repository.jdbc..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "javax.sql..",
            "taan.online.account.model..",
            "taan.online.account.repository",
            "taan.online.account.repository.jdbc..",
            "taan.online.logging.."
        );

    @ArchTest
    static final ArchRule ACCOUNT_CONTROLLER_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.controller..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.logging..",
            "taan.online.account.logging..",
            "taan.online.session.logging..",
            "taan.online.account.model..",
            "taan.online.account.controller..",
            "taan.online.account.service..",
            "taan.online.account.protocol.packet.."
        );

    @ArchTest
    static final ArchRule ACCOUNT_PACKET_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "taan.online.account.protocol.packet..");

    @ArchTest
    static final ArchRule ACCOUNT_CODEC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.account.protocol..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule ACCOUNT_SECURITY_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.account.security..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "com.password4j..",
            "taan.online.account.model..",
            "taan.online.account.service..",
            "taan.online.account.security.."
        );

    @ArchTest
    static final ArchRule LOG_CATALOG_DEPENDENCIES = classes().that().resideInAnyPackage(
        "taan.online.account.logging..",
        "taan.online.character.logging..",
        "taan.online.channel.logging..",
        "taan.online.lobby.logging..",
        "taan.online.room.logging..",
        "taan.online.match.logging..",
        "taan.online.session.logging..",
        "taan.online.bootstrap.logging..",
        "taan.online.server.logging.."
    ).should().onlyDependOnClassesThat().resideInAnyPackage(
        "java..",
        "org.slf4j.event..",
        "taan.online.logging",
        "taan.online.account.logging..",
        "taan.online.character.logging..",
        "taan.online.channel.logging..",
        "taan.online.lobby.logging..",
        "taan.online.room.logging..",
        "taan.online.match.logging..",
        "taan.online.session.logging..",
        "taan.online.bootstrap.logging..",
        "taan.online.server.logging.."
    );

    @ArchTest
    static final ArchRule SHARED_LOGGING_DEPENDENCIES = classes().that().resideInAPackage("taan.online.logging..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "org.slf4j..", "taan.online.logging..");

    @ArchTest
    static final ArchRule LOGGING_BACKEND_DEPENDENCIES = noClasses().that().resideOutsideOfPackages(
        "taan.online.logging..",
        "taan.online.bootstrap.."
    ).should().dependOnClassesThat().resideInAnyPackage("org.slf4j", "org.slf4j.spi..", "ch.qos.logback..");

    @ArchTest
    static final ArchRule NETWORK_DEPENDENCIES = noClasses().that().resideInAPackage("taan.online.network..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "taan.online.server..",
            "taan.online.room..",
            "taan.online.lobby..",
            "taan.online.account..",
            "taan.online.inventory..",
            "taan.online.channel..",
            "taan.online.character..",
            "taan.online.session..",
            "taan.online.persistence..",
            "taan.online.bootstrap.."
        );

    @ArchTest
    static final ArchRule PROTOCOL_DEPENDENCIES = noClasses().that().resideInAPackage("taan.online.protocol..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "taan.online.network..",
            "taan.online.server..",
            "taan.online.match..",
            "taan.online.room..",
            "taan.online.lobby..",
            "taan.online.account..",
            "taan.online.inventory..",
            "taan.online.channel..",
            "taan.online.character..",
            "taan.online.session..",
            "taan.online.persistence..",
            "taan.online.bootstrap..",
            "io.netty.."
        );

    @ArchTest
    static final ArchRule SERVER_DEPENDENCIES = noClasses().that().resideInAPackage("taan.online.server..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "taan.online.persistence..",
            "taan.online.match.model..",
            "taan.online.match.service..",
            "taan.online.match.repository..",
            "taan.online.bootstrap..",
            "taan.online.account.model..",
            "taan.online.account.service..",
            "taan.online.account.repository..",
            "taan.online.account.security..",
            "taan.online.session.model..",
            "taan.online.session.service..",
            "taan.online.character.model..",
            "taan.online.character.service..",
            "taan.online.character.repository..",
            "taan.online.inventory.model..",
            "taan.online.inventory.repository..",
            "taan.online.channel.model..",
            "taan.online.channel.service..",
            "taan.online.room.model..",
            "taan.online.room.service..",
            "taan.online.room.repository..",
            "java.sql..",
            "javax.sql.."
        );

    @ArchTest
    static final ArchRule CHARACTER_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.character.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule CHARACTER_SERVICE_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.logging..",
            "taan.online.character.logging..",
            "taan.online.channel.logging..",
            "taan.online.lobby.logging..",
            "taan.online.room.logging..",
            "taan.online.character.model..",
            "taan.online.inventory.model..",
            "taan.online.account.model..",
            "taan.online.character.service..",
            "taan.online.character.repository"
        );

    @ArchTest
    static final ArchRule CHARACTER_SERVICE_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.service.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.character.service.model..",
            "taan.online.inventory.model..",
            "taan.online.character.model.."
        );

    @ArchTest
    static final ArchRule CHARACTER_REPOSITORY_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.repository")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.character.repository",
            "taan.online.inventory.model..",
            "taan.online.character.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule CHARACTER_JDBC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.repository.jdbc..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "javax.sql..",
            "taan.online.logging..",
            "taan.online.account.model..",
            "taan.online.character.model..",
            "taan.online.character.repository..",
            "taan.online.inventory.model..",
            "taan.online.inventory.repository.jdbc..",
            "taan.online.inventory.repository",
            "taan.online.account.model..",
            "taan.online.logging.."
        );

    @ArchTest
    static final ArchRule CHARACTER_CONTROLLER_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.controller..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.account.model..",
            "taan.online.protocol",
            "taan.online.character.controller..",
            "taan.online.character.model..",
            "taan.online.character.service..",
            "taan.online.inventory.model..",
            "taan.online.inventory.protocol.packet..",
            "taan.online.character.protocol..",
            "taan.online.inventory.controller.."
        );

    @ArchTest
    static final ArchRule CHARACTER_PACKET_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.character.protocol.packet..",
            "taan.online.inventory.protocol.packet.."
        );

    @ArchTest
    static final ArchRule CHARACTER_CODEC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.character.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.protocol..",
            "taan.online.character.protocol..",
            "taan.online.inventory.protocol.."
        );

    @ArchTest
    static final ArchRule CHARACTER_CORE_IO_DEPENDENCIES = noClasses().that().resideInAnyPackage(
        "taan.online.character.model..",
        "taan.online.character.service..",
        "taan.online.character.repository"
    ).should().dependOnClassesThat().resideInAnyPackage(
        "java.sql..",
        "javax.sql..",
        "java.net..",
        "java.io..",
        "java.nio.channels..",
        "java.nio.file.."
    );

    @ArchTest
    static final ArchRule CHARACTER_LAYER_CYCLES = slices().matching("taan.online.character.(*)..")
        .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule DOMAIN_WIRE_CONVERSIONS = noMethods().that().areDeclaredInClassesThat()
        .resideInAnyPackage("taan.online.account.model..", "taan.online.character.model..")
        .should().haveNameMatching("wireValue|fromWire");

    @ArchTest
    static final ArchRule MODULE_CYCLES = slices().matching("taan.online.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule ACCOUNT_LAYER_CYCLES = slices().matching("taan.online.account.(*)..")
        .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule INVENTORY_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.inventory.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.inventory.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule INVENTORY_PACKET_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.inventory.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "taan.online.inventory.protocol.packet..");

    @ArchTest
    static final ArchRule INVENTORY_CODEC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.inventory.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.inventory.protocol..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule INVENTORY_JDBC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.inventory.repository.jdbc..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "javax.sql..",
            "taan.online.inventory.model..",
            "taan.online.inventory.repository.jdbc..",
            "taan.online.inventory.repository",
            "taan.online.account.model..",
            "taan.online.logging.."
        );

    @ArchTest
    static final ArchRule INVENTORY_LAYER_CYCLES = slices().matching("taan.online.inventory.(*)..")
        .should().beFreeOfCycles();

}
