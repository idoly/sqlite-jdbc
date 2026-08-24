package io.github.idoly.sqlite.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "io.github.idoly.sqlite",
        importOptions = {ImportOption.OnlyIncludeTests.class})
class TestCodingRulesTest {

    @ArchTest
    public static final ArchRule no_junit_assertions =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("org.junit.jupiter.api.Assertions");
}
