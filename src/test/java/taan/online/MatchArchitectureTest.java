package taan.online;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "taan.online", importOptions = ImportOption.DoNotIncludeTests.class)
class MatchArchitectureTest {
    @ArchTest
    static final ArchRule SCENARIO_STAGING = noClasses().that().resideInAnyPackage(
        "taan.online.bootstrap..",
        "taan.online.server.."
    ).should().dependOnClassesThat().haveNameMatching(
        "taan\\.online\\.match\\.controller\\.ValidateScenarioPacketHandler"
    );

    @ArchTest
    static final ArchRule PACKETS = classes().that().resideInAPackage("taan.online.match.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "taan.online.match.protocol.packet..");

    @ArchTest
    static final ArchRule CODECS = classes().that().resideInAPackage("taan.online.match.protocol.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.match.protocol..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule MODELS = classes().that().resideInAPackage("taan.online.match.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.match.model..",
            "taan.online.room.model..",
            "taan.online.character.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule MODEL_IO = noClasses().that().resideInAPackage("taan.online.match.model..")
        .should().dependOnClassesThat().resideInAnyPackage("java.io..", "java.net..", "java.sql..", "org.slf4j..");

    @ArchTest
    static final ArchRule SERVICES = classes().that().resideInAPackage("taan.online.match.service..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.match.service..",
            "taan.online.match.model..",
            "taan.online.match.logging..",
            "taan.online.logging..",
            "taan.online.room.service..",
            "taan.online.room.model..",
            "taan.online.character.model..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule SERVICE_MODELS = classes().that().resideInAPackage("taan.online.match.service.model..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.match.service.model..",
            "taan.online.match.model..",
            "taan.online.room.model..",
            "taan.online.room.service.model.."
        );

    @ArchTest
    static final ArchRule CONTROLLERS = classes().that().resideInAPackage("taan.online.match.controller..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.match.controller..",
            "taan.online.match.service..",
            "taan.online.match.protocol..",
            "taan.online.match.view..",
            "taan.online.room.service..",
            "taan.online.protocol.."
        );

    @ArchTest
    static final ArchRule VIEWS = classes().that().resideInAPackage("taan.online.match.view..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.match.view..",
            "taan.online.match.service..",
            "taan.online.match.protocol..",
            "taan.online.protocol..",
            "taan.online.match.model..",
            "taan.online.room.model..",
            "taan.online.room.protocol..",
            "taan.online.character.model..",
            "taan.online.character.controller..",
            "taan.online.character.protocol.packet..",
            "taan.online.account.model.."
        );

    @ArchTest
    static final ArchRule TERMINAL_OWNER = classes().that().haveSimpleName("MatchTerminationDelivery")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.match\\.service\\.(MatchRegistry|MatchTerminationDelivery|MatchTerminationDispatch)"
        );

    @ArchTest
    static final ArchRule NO_REVERSE_DEPENDENCY = noClasses().that().resideInAPackage("taan.online.room..")
        .should().dependOnClassesThat().resideInAPackage("taan.online.match..");

    @ArchTest
    static final ArchRule NO_ROOM_CALLBACK = noClasses().that().resideInAPackage("taan.online.match..")
        .should().dependOnClassesThat().haveFullyQualifiedName("taan.online.room.service.RoomRegistry");

    @ArchTest
    static final ArchRule MUTABLE_MATCH = classes().that().haveFullyQualifiedName("taan.online.match.model.Match")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.match\\.(model\\.Match|model\\.MatchTurnPolicy"
                    + "|service\\.MatchRegistry|service\\.MatchRuntime"
                    + "|service\\.MatchTurnExecution|service\\.MatchItemExecution|service\\.MatchMovementExecution"
                    + "|service\\.MatchLootExecution|service\\.MatchOutcomeExecution"
                    + "|service\\.model\\.MatchSnapshot)"
        );

    @ArchTest
    static final ArchRule MAILBOX_OWNER = classes().that().haveSimpleName("MatchMailbox")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.match\\.service\\.(MatchRuntime|MatchMailbox)"
        );

    @ArchTest
    static final ArchRule RUNTIME_OWNER = classes().that().haveSimpleName("MatchRuntime")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.match\\.service\\."
                + "(MatchRuntime|MatchRegistry|MatchBootstrapDelivery|MatchTerminationDispatch"
                + "|MatchScenarioExecution|MatchTurnExecution|MatchMovementExecution|MatchItemExecution"
                + "|MatchLootExecution|MatchOutcomeExecution)"
        );

    @ArchTest
    static final ArchRule ROOM_MATCH_OWNER = classes().that().haveSimpleName("RoomMatchOperations")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.room\\.service\\."
                + "(RoomRegistry|RoomMatchOperations|RoomDepartureOperations|RoomMatchCoordinator)"
        );

    @ArchTest
    static final ArchRule RETURN_BARRIER_OWNER = classes().that().haveSimpleName("RoomMatchReturnBarrier")
        .should().onlyBeAccessed().byClassesThat().haveNameMatching(
            "taan\\.online\\.room\\.service\\.(RoomMatchOperations|RoomMatchReturnBarrier)"
        );

    @ArchTest
    static final ArchRule OPTION_PACKETS = classes().that().resideInAPackage("taan.online.protocol.option.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "taan.online.protocol.option.packet..");

    @ArchTest
    static final ArchRule OPTION_CODECS = classes().that().resideInAPackage("taan.online.protocol.option.codec..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "taan.online.protocol..");

    @ArchTest
    static final ArchRule OPTION_LAYER_CYCLES = slices().matching("taan.online.protocol.option.(*)..")
        .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule MATCH_LAYER_CYCLES = slices().matching("taan.online.match.(*)..")
        .should().beFreeOfCycles();
}
