package sk.knizat.tennisclub.architecture;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Architecture rules from ARCHITECTURE.md section 7 (enforced on production classes only).
 */
@AnalyzeClasses(packages = "sk.knizat.tennisclub", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "sk.knizat.tennisclub";

    /** Rule 1: no dependency on Spring Data at all. */
    @ArchTest
    static final ArchRule no_spring_data_dependency = noClasses()
            .should().dependOnClassesThat().resideInAPackage("org.springframework.data..")
            .because("Spring Data is forbidden; DAO layer uses EntityManager + JPQL only");

    /** Rule 2: Spring Data JPA is not even on the classpath. */
    @Test
    void should_notHaveJpaRepositoryOnClasspath_when_dependenciesResolved() {
        assertThatThrownBy(() -> Class.forName("org.springframework.data.jpa.repository.JpaRepository"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    /** Rule 3: layered architecture controller -> service -> dao. */
    @ArchTest
    static final ArchRule layers_are_respected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy(ROOT + ".controller..")
            .layer("Service").definedBy(ROOT + ".service..")
            .layer("Dao").definedBy(ROOT + ".dao..")
            .layer("Config").definedBy(ROOT + ".config..")
            .layer("Security").definedBy(ROOT + ".security..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Config", "Security")
            .whereLayer("Dao").mayOnlyBeAccessedByLayers("Service", "Config")
            .allowEmptyShould(true);

    /** Rule 3 (second half): controllers never touch dao or entity packages. */
    @ArchTest
    static final ArchRule controllers_do_not_touch_dao_or_entities = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..dao..", "..entity..")
            .allowEmptyShould(true);

    /** Rule 4: public controller methods have no entity types in their signatures. */
    @ArchTest
    static final ArchRule controller_signatures_have_no_entities = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .and().arePublic()
            .should().haveRawReturnType(com.tngtech.archunit.base.DescribedPredicate.describe(
                    "reside in entity package", c -> c.getPackageName().startsWith(ROOT + ".entity")))
            .orShould().haveRawParameterTypes(com.tngtech.archunit.base.DescribedPredicate.describe(
                    "contain a type from entity package",
                    types -> types.stream().anyMatch(t -> t.getPackageName().startsWith(ROOT + ".entity"))))
            .allowEmptyShould(true);

    /** Rule 5a: @Transactional (Spring or Jakarta) only in the service package. */
    @ArchTest
    static final ArchRule transactional_only_in_service = noClasses()
            .that().resideOutsideOfPackage("..service..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    org.springframework.transaction.annotation.Transactional.class.getName())
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    jakarta.transaction.Transactional.class.getName())
            .allowEmptyShould(true);

    /** Rule 5b: EntityManager is used only in the dao package. */
    @ArchTest
    static final ArchRule entity_manager_only_in_dao = noClasses()
            .that().resideOutsideOfPackage("..dao..")
            .should().dependOnClassesThat().areAssignableTo(EntityManager.class)
            .allowEmptyShould(true);

    /** Soft delete only: nothing in production code may call EntityManager.remove. */
    @ArchTest
    static final ArchRule entity_manager_remove_is_never_called = noClasses()
            .should().callMethod(EntityManager.class, "remove", Object.class)
            .because("all deletions are soft deletes (BaseEntity.markDeleted)");

    /** Rule 6a: every *DaoImpl extends AbstractDao. */
    @ArchTest
    static final ArchRule dao_impls_extend_abstract_dao = classes()
            .that().haveSimpleNameEndingWith("DaoImpl")
            .should().beAssignableTo(ROOT + ".dao.AbstractDao")
            .allowEmptyShould(true);

    /** Rule 6b: every @Entity (wherever it is placed) extends BaseEntity and lives in the entity package. */
    @ArchTest
    static final ArchRule entities_extend_base_entity = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().beAssignableTo(ROOT + ".entity.BaseEntity")
            .andShould().resideInAPackage(ROOT + ".entity")
            .allowEmptyShould(true);
}
