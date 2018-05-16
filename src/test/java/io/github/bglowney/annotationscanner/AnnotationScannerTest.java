package io.github.bglowney.annotationscanner;

import io.github.bglowney.annotationscanner.test.TestAnnotation;
import io.github.bglowney.annotationscanner.test.TestClass;
import io.github.bglowney.annotationscanner.test.TestClass2;
import lombok.val;
import org.junit.Assert;
import org.junit.Test;

public class AnnotationScannerTest {

    static final String TEST_PACKAGE = "io.github.bglowney.annotationscanner.test";
    static final String TEST_PACKAGE_2 = "io.github.bglowney.annotationscanner.test2";
    static final String TEST_PACKAGE_3 = "io.github.bglowney.annotationscanner.test2.test3";

    @Test
    public void testScanForClass() {
        val results = AnnotationScanner.of(TEST_PACKAGE)
            .withTypeAnnotations(TestAnnotation.class)
            .scan();

        Assert.assertEquals(1, results.size());

        for (val result: results) {

            Assert.assertEquals(TestAnnotation.class, result.getAnnotation().annotationType());
            Assert.assertEquals(TestClass.class, result.getClazz());
            Assert.assertEquals(TestClass.class, result.getAnnotatedElement());

        }
    }

    @Test
    public void testScanForConstructor() throws Exception {
        val results = AnnotationScanner.of(TEST_PACKAGE)
            .withConstructorAnnotations(TestAnnotation.class)
            .scan();

        Assert.assertEquals(1, results.size());

        for (val result: results) {

            Assert.assertEquals(TestAnnotation.class, result.getAnnotation().annotationType());
            Assert.assertEquals(TestClass.class, result.getClazz());
            Assert.assertEquals(TestClass.class.getConstructor(), result.getAnnotatedElement());

        }
    }

    @Test
    public void testScanForMethod() throws Exception {
        val results = AnnotationScanner.of(TEST_PACKAGE)
            .withMethodAnnotations(TestAnnotation.class)
            .scan();

        Assert.assertEquals(1, results.size());

        for (val result: results) {

            Assert.assertEquals(TestAnnotation.class, result.getAnnotation().annotationType());
            Assert.assertEquals(TestClass.class, result.getClazz());
            Assert.assertEquals(TestClass.class.getMethod("test"), result.getAnnotatedElement());

        }
    }

    @Test
    public void testScanForField() throws Exception {
        val results = AnnotationScanner.of(TEST_PACKAGE)
            .withFieldAnnotations(TestAnnotation.class)
            .scan();

        Assert.assertEquals(1, results.size());

        for (val result: results) {

            Assert.assertEquals(TestAnnotation.class, result.getAnnotation().annotationType());
            Assert.assertEquals(TestClass.class, result.getClazz());
            Assert.assertEquals(TestClass.class.getDeclaredField("test"), result.getAnnotatedElement());

        }
    }

    @Test
    public void testScanForTypeAndAnnotation() throws Exception {
        val results = AnnotationScanner.of(TEST_PACKAGE)
            .withTypeAndAnnotation(TestClass.class, TestAnnotation.class)
            .scan();

        Assert.assertEquals(1, results.size());

        for (val result: results) {

            Assert.assertEquals(TestAnnotation.class, result.getAnnotation().annotationType());
            Assert.assertEquals(TestClass.class, result.getClazz());
            Assert.assertEquals(TestClass.class, result.getAnnotatedElement());

        }
    }

    @Test
    public void testIncludePackageContentByDefault() {
        val results = AnnotationScanner.of(TEST_PACKAGE)
            .withTypeAnnotations(TestAnnotation.class)
            .includePackageContentByDefault(true)
            .scan();

        Assert.assertEquals(3, results.size());

        for (val result: results) {

            if (TestClass.class.equals(result.getClazz())) {
                Assert.assertEquals(TestAnnotation.class, result.getAnnotation().annotationType());
                Assert.assertEquals(TestClass.class, result.getClazz());
                Assert.assertEquals(TestClass.class, result.getAnnotatedElement());
            } else if (
                TestClass2.class.equals(result.getClazz())
                || TestAnnotation.class.equals(result.getClazz())
                ) {
                Assert.assertFalse(result.isMatch());
            } else {
                throw new AssertionError("Unexpected class found in scanned results");
            }

        }
    }

    @Test
    public void testScanMultiplePackages() {

        val results = AnnotationScanner.of(TEST_PACKAGE_2, TEST_PACKAGE_3)
            .includePackageContentByDefault(true)
            .scan();

        Assert.assertEquals(2, results.size());
    }

}
