package com.conxist;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build when a module boundary or a Clean Architecture dependency rule is broken. */
class ArchitectureTest {

    private static final String[] OUTER_RING = {
            "org.springframework..", "jakarta..", "org.apache..", "..adapter..", "..config.."};

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.conxist");

    @Test
    void modules_only_use_each_others_published_api_and_have_no_cycles() {
        ApplicationModules.of(ConxistApplication.class).verify();
    }

    @Test
    void domain_depends_on_nothing_outside_the_inner_ring() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(OUTER_RING)
                .orShould().dependOnClassesThat().resideInAPackage("..application..")
                .check(classes);
    }

    @Test
    void use_cases_do_not_depend_on_frameworks_or_adapters() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(OUTER_RING)
                .check(classes);
    }

    @Test
    void adapters_get_settings_as_plain_values_not_from_config() {
        noClasses().that().resideInAPackage("..adapter..")
                .should().dependOnClassesThat().resideInAPackage("..config..")
                .check(classes);
    }

    @Test
    void published_module_apis_are_plain_java() {
        noClasses().that().resideInAnyPackage("com.conxist.platform", "com.conxist.sources", "com.conxist.capture")
                .should().dependOnClassesThat().resideInAnyPackage(OUTER_RING)
                .check(classes);
    }
}
