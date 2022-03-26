package com.buschmais.jqassistant.plugin.osgi.test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.buschmais.jqassistant.core.report.api.model.Result;
import com.buschmais.jqassistant.core.rule.api.model.Constraint;
import com.buschmais.jqassistant.core.shared.map.MapBuilder;
import com.buschmais.jqassistant.plugin.java.api.model.PackageDescriptor;
import com.buschmais.jqassistant.plugin.java.api.model.TypeDescriptor;
import com.buschmais.jqassistant.plugin.java.api.scanner.JavaScope;
import com.buschmais.jqassistant.plugin.java.test.AbstractJavaPluginIT;
import com.buschmais.jqassistant.plugin.osgi.test.api.data.Request;
import com.buschmais.jqassistant.plugin.osgi.test.api.data.Response;
import com.buschmais.jqassistant.plugin.osgi.test.api.service.Service;
import com.buschmais.jqassistant.plugin.osgi.test.impl.Activator;
import com.buschmais.jqassistant.plugin.osgi.test.impl.ServiceImpl;
import com.buschmais.jqassistant.plugin.osgi.test.impl.a.UsedPublicClass;
import com.buschmais.jqassistant.plugin.osgi.test.impl.b.UnusedPublicClass;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static com.buschmais.jqassistant.core.report.api.model.Result.Status.FAILURE;
import static com.buschmais.jqassistant.core.report.api.model.Result.Status.SUCCESS;
import static com.buschmais.jqassistant.core.test.matcher.ConstraintMatcher.constraint;
import static com.buschmais.jqassistant.core.test.matcher.ResultMatcher.result;
import static com.buschmais.jqassistant.plugin.java.test.matcher.PackageDescriptorMatcher.packageDescriptor;
import static com.buschmais.jqassistant.plugin.java.test.matcher.TypeDescriptorMatcher.typeDescriptor;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasValue;
import static org.hamcrest.core.IsCollectionContaining.hasItem;

/**
 * Contains tests regarding manifest files.
 */
class OsgiBundleIT extends AbstractJavaPluginIT {

    /**
     * Verifies the concept "osgi-bundle:Bundle".
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void bundle() throws Exception {
        scanClassPathResource(JavaScope.CLASSPATH, "/META-INF/MANIFEST.MF");
        assertThat(applyConcept("osgi-bundle:Bundle").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        assertThat(
            query(
                "MATCH (bundle:Osgi:Bundle) WHERE bundle.bundleSymbolicName='com.buschmais.jqassistant.plugin.osgi.test' and bundle.bundleVersion='0.1.0' RETURN bundle")
                .getColumn("bundle").size(), equalTo(1));
        store.commitTransaction();
    }

    /**
     * Verifies the concept "osgi-bundle:ExportPackage".
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void exportedPackages() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        assertThat(applyConcept("osgi-bundle:ExportPackage").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        List<PackageDescriptor> packages = query("MATCH (b:Osgi:Bundle)-[:EXPORTS]->(p:Package) RETURN p").getColumn("p");
        assertThat(packages.size(), equalTo(2));
        assertThat(packages, hasItems(packageDescriptor(Request.class.getPackage()), packageDescriptor(Service.class.getPackage())));
        store.commitTransaction();
    }

    /**
     * Verifies the uniqueness of concept "osgi-bundle:ExportPackage" with keeping existing properties.
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void exportedPackagesUnique() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        store.beginTransaction();
        // create existing relations with and without properties
        Map<String, Object> params = MapBuilder.<String, Object> builder().entry("package", Service.class.getPackage().getName()).build();
        assertThat(query("MATCH (a:Artifact {fqn:'artifact'}), (p:Package) WHERE p.fqn=$package MERGE (a)-[r:EXPORTS {prop: 'value'}]->(p) RETURN r", params).getColumn("r").size(), equalTo(1));
        params = MapBuilder.<String, Object> builder().entry("package", Request.class.getPackage().getName()).build();
        assertThat(query("MATCH (a:Artifact {fqn:'artifact'}), (p:Package) WHERE p.fqn=$package MERGE (a)-[r:EXPORTS]->(p) RETURN r", params).getColumn("r").size(), equalTo(1));
        verifyUniqueRelation("EXPORTS", 2);
        store.commitTransaction();
        assertThat(applyConcept("osgi-bundle:ExportPackage").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        verifyUniqueRelation("EXPORTS", 2);
        store.commitTransaction();
    }

    /**
     * Verifies the concept "osgi-bundle:ImportPackage".
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void importedPackages() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        query("create (:File:Directory:Java:Package{fqn:'org.junit'})");
        assertThat(applyConcept("osgi-bundle:ImportPackage").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        List<PackageDescriptor> packages = query("MATCH (b:Osgi:Bundle)-[:IMPORTS]->(p:Java:Package) RETURN p").getColumn("p");
        assertThat(packages.size(), equalTo(1));
        assertThat(packages, hasItems(packageDescriptor("org.junit")));
        store.commitTransaction();
    }

    /**
     * Verifies the uniqueness of concept "osgi-bundle:ImportPackage" with keeping existing properties.
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void importedPackagesUnique() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        store.beginTransaction();
        // create existing relations with property
        query("create (:File:Directory:Package{fqn:'org.junit'})");
        assertThat(query("MATCH (a:Artifact {fqn:'artifact'}), (p:Package {fqn:'org.junit'}) MERGE (a)-[r:IMPORTS {prop: 'value'}]->(p) RETURN r").getColumn("r").size(), equalTo(1));
        verifyUniqueRelation("IMPORTS", 1);
        store.commitTransaction();
        assertThat(applyConcept("osgi-bundle:ImportPackage").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        verifyUniqueRelation("IMPORTS", 1);
        store.commitTransaction();
    }

    /**
     * Verifies the concept "osgi-bundle:Activator".
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void activator() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        assertThat(applyConcept("osgi-bundle:Activator").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        List<TypeDescriptor> activators = query("MATCH (a:Class)-[:ACTIVATES]->(b:Osgi:Bundle) RETURN a").getColumn("a");
        assertThat(activators.size(), equalTo(1));
        assertThat(activators, hasItems(typeDescriptor(Activator.class)));
        store.commitTransaction();
    }

    /**
     * Verifies the uniqueness of concept "osgi-bundle:Activator" with keeping existing properties.
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void activatorUnique() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        store.beginTransaction();
        // create existing relations with property
        Map<String, Object> params = MapBuilder.<String, Object> builder().entry("activator", Activator.class.getName()).build();
        assertThat(query("MATCH (a:Artifact {fqn:'artifact'}), (c:Class) WHERE c.fqn=$activator MERGE (c)-[r:ACTIVATES {prop: 'value'}]->(a) RETURN r", params).getColumn("r").size(), equalTo(1));
        verifyUniqueRelation("ACTIVATES", 1);
        store.commitTransaction();
        assertThat(applyConcept("osgi-bundle:Activator").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        verifyUniqueRelation("ACTIVATES", 1);
        store.commitTransaction();
    }

    /**
     * Verifies the concept "osgi-bundle:InternalType".
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void internalType() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        removeTestClass();
        assertThat(applyConcept("osgi-bundle:InternalType").getStatus(), equalTo(SUCCESS));
        store.beginTransaction();
        List<TypeDescriptor> internalTypes = query("MATCH (t:Type:Internal) RETURN t").getColumn("t");
        assertThat(
            internalTypes,
            hasItems(typeDescriptor(Activator.class), typeDescriptor(UsedPublicClass.class), typeDescriptor(UnusedPublicClass.class),
                     typeDescriptor(ServiceImpl.class)));
        store.commitTransaction();
    }

    /**
     * Verifies the constraint "osgi-bundle:UnusedInternalType".
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void unusedInternalType() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        removeTestClass();
        assertThat(validateConstraint("osgi-bundle:UnusedInternalType").getStatus(), equalTo(FAILURE));
        store.beginTransaction();
        Matcher<Constraint> constraintMatcher = constraint("osgi-bundle:UnusedInternalType");
        Collection<Result<Constraint>> constraintViolations = reportPlugin.getConstraintResults().values();

        // The explicitly given type information for Matcher#not() is required to allow
        // us to compile this class with JDK 8u31
        // Oliver B. Fischer, 25th May 2015
        assertThat(constraintViolations, hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(UnusedPublicClass.class))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(ServiceImpl.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Request.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Response.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Service.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(UsedPublicClass.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Activator.class)))))));
        store.commitTransaction();
    }

    /**
     * Verifies the constraint "osgi-bundle:InternalTypeMustNotBePublic".
     *
     * @throws IOException
     *             If the test fails.
     */
    @Test
    void internalTypeMustNotBePublic() throws Exception {
        scanClassPathDirectory(getClassesDirectory(Service.class));
        removeTestClass();
        assertThat(validateConstraint("osgi-bundle:InternalTypeMustNotBePublic").getStatus(), equalTo(FAILURE));
        store.beginTransaction();
        Matcher<Constraint> constraintMatcher = constraint("osgi-bundle:InternalTypeMustNotBePublic");
        Collection<Result<Constraint>> constraintViolations = reportPlugin.getConstraintResults().values();
        assertThat(constraintViolations, hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(UnusedPublicClass.class))))));
        assertThat(constraintViolations, hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(ServiceImpl.class))))));

        // The explicitly given type information for Matcher#not() is required to allow
        // us to compile this class with JDK 8u31
        // Oliver B. Fischer, 25th May 2015
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Request.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Response.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Service.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(UsedPublicClass.class)))))));
        assertThat(constraintViolations, Matchers.<Iterable<? super Result<Constraint>>>not(hasItem(result(constraintMatcher, hasItem(hasValue(typeDescriptor(Activator.class)))))));
        store.commitTransaction();
    }

    /**
     * Verifies a unique relation with property. An existing transaction is assumed.
     * @param relationName The name of the relation.
     * @param total The total of relations with the given name.
     */
    private void verifyUniqueRelation(String relationName, int total) {
        assertThat(query("MATCH ()-[r:" + relationName + " {prop: 'value'}]->() RETURN r").getColumn("r").size(), equalTo(1));
        assertThat(query("MATCH ()-[r:" + relationName + "]->() RETURN r").getColumn("r").size(), equalTo(total));
    }
}
