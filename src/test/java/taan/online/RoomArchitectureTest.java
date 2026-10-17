package taan.online;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "taan.online", importOptions = ImportOption.DoNotIncludeTests.class)
class RoomArchitectureTest {
    @ArchTest
    static final ArchRule PACKET_DEPENDENCIES = classes().that().resideInAPackage("taan.online.room.protocol.packet..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "taan.online.room.protocol.packet..",
            "taan.online.character.protocol.packet.."
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
    static final ArchRule LAYER_CYCLES = slices().matching("taan.online.room.(*)..").should().beFreeOfCycles();
}
