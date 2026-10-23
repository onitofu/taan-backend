package taan.online;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import taan.online.room.view.RoomRuleMapper;

@AnalyzeClasses(packages = "taan.online", importOptions = ImportOption.DoNotIncludeTests.class)
class ChannelArchitectureTest {
    @ArchTest
    static final ArchRule CHANNEL_MODEL_DEPENDENCIES = classes().that().resideInAPackage("taan.online.channel.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.model..",
            "taan.online.character.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule CHANNEL_SERVICE_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.channel.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.service..",
            "taan.online.channel.model..",
            "taan.online.character.model..",
            "taan.online.account.model..",
            "taan.online.channel.logging..",
            "taan.online.logging.."
        );

    @ArchTest
    static final ArchRule CHANNEL_PACKET_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.channel.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "taan.online.channel.protocol.packet..");

    @ArchTest
    static final ArchRule CHANNEL_CODEC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.channel.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.protocol..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule CHANNEL_CONTROLLER_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.channel.controller..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.controller..",
            "taan.online.channel.service..",
            "taan.online.channel.model..",
            "taan.online.channel.protocol.."
        );

    @ArchTest
    static final ArchRule CHANNEL_LAYER_CYCLES = slices().matching("taan.online.channel.(*)..")
        .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule CHANNEL_REPOSITORY_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.channel.repository")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.model..",
            "taan.online.channel.repository"
        );

    @ArchTest
    static final ArchRule CHANNEL_JDBC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.channel.repository.jdbc..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "javax.sql..",
            "taan.online.logging..",
            "taan.online.channel.repository..",
            "taan.online.channel.model.."
        );

    @ArchTest
    static final ArchRule CHANNEL_SERVICE_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.channel.service.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.model..",
            "taan.online.channel.service.model.."
        );

    @ArchTest
    static final ArchRule CHANNEL_CORE_IO_DEPENDENCIES = noClasses().that().resideInAnyPackage(
        "taan.online.channel.model..",
        "taan.online.channel.service..",
        "taan.online.channel.repository"
    ).should().dependOnClassesThat().resideInAnyPackage(
        "java.sql..",
        "javax.sql..",
        "java.io..",
        "java.net..",
        "io.netty.."
    );

    @ArchTest
    static final ArchRule LOBBY_SERVICE_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.lobby.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.model..",
            "taan.online.channel.service..",
            "taan.online.room.service",
            "taan.online.room.service.model..",
            "taan.online.lobby.service..",
            "taan.online.lobby.logging..",
            "taan.online.logging.."
        );

    @ArchTest
    static final ArchRule LOBBY_SERVICE_MODEL_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.lobby.service.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.model..",
            "taan.online.lobby.service.model..",
            "taan.online.room.service.model.."
        );

    @ArchTest
    static final ArchRule LOBBY_CONTROLLER_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.lobby.controller..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.channel.service..",
            "taan.online.channel.model..",
            "taan.online.lobby.controller..",
            "taan.online.lobby.service..",
            "taan.online.lobby.protocol..",
            "taan.online.lobby.view..",
            "taan.online.protocol"
        );

    @ArchTest
    static final ArchRule LOBBY_VIEW_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.lobby.view..")
        .should().onlyDependOnClassesThat(
            resideInAnyPackage(
                "java..",
                "taan.online.lobby.view..",
                "taan.online.account.model..",
                "taan.online.channel.service..",
                "taan.online.channel.model..",
                "taan.online.character.model..",
                "taan.online.lobby.protocol..",
                "taan.online.protocol",
                "taan.online.room.model..",
                "taan.online.room.service.model..",
                "taan.online.room.protocol.packet..",
                "taan.online.lobby.service..",
                "taan.online.lobby.logging..",
                "taan.online.logging.."
            ).or(equivalentTo(RoomRuleMapper.class))
        );

    @ArchTest
    static final ArchRule LOBBY_PACKET_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.lobby.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.lobby.protocol.packet..",
            "taan.online.room.protocol.packet.."
        );

    @ArchTest
    static final ArchRule LOBBY_CODEC_DEPENDENCIES = classes().that()
        .resideInAPackage("taan.online.lobby.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.lobby.protocol..",
            "taan.online.room.protocol..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule LOBBY_CORE_IO_DEPENDENCIES = noClasses().that().resideInAPackage(
        "taan.online.lobby.service.."
    )
        .should().dependOnClassesThat().resideInAnyPackage(
            "java.sql..",
            "javax.sql..",
            "java.io..",
            "java.net..",
            "io.netty.."
        );

    @ArchTest
    static final ArchRule LOBBY_LAYER_CYCLES = slices().matching("taan.online.lobby.(*)..").should().beFreeOfCycles();
}
