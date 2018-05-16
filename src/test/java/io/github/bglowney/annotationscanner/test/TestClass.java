package io.github.bglowney.annotationscanner.test;

@TestAnnotation
public class TestClass {

    @TestAnnotation
    private Object test;

    @TestAnnotation
    public TestClass() {
    }

    @TestAnnotation
    public void test() {}
}
