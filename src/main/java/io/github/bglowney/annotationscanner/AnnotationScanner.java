package io.github.bglowney.annotationscanner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.util.Arrays.asList;

/**
 * A utility class for scanning annotated types on the classpath
 */
public class AnnotationScanner {

    protected boolean includePackageContentsByDefault = false;
    protected final Set<String> packages;
    protected final Set<AnnotatedType> annotatedTypes = new HashSet<>();
    protected final Set<Class<? extends Annotation>> typeAnnotations = new HashSet<>();
    protected final Set<Class<? extends Annotation>> methodAnnotations = new HashSet<>();
    protected final Set<Class<? extends Annotation>> fieldAnnotations = new HashSet<>();
    protected final Set<Class<? extends Annotation>> constructorAnnotations = new HashSet<>();
    protected ClassLoader classLoaderToUse;

    @Data
    @EqualsAndHashCode
    protected static class AnnotatedType {
        final Class<?> type;
        final Class<? extends Annotation> annotation;
    }

    /**
     * Indicate if all types in the packages to scan should be returned in the results regardless of if a match is made. By
     * default only matches are returned
     *
     * For each returned result, if the class indeed matches the search criteria, then calling {@link ScannerResult#isMatch()}
     * will indicate if the result matched the search criteria
     *
     * @see ScannerResult#isMatch()
     *
     * @param includePackageContentsByDefault - set to true if all types in the packages to scan should be returned
     * @return this AnnotationScanner for method chaining
     */
    public final AnnotationScanner includePackageContentByDefault(boolean includePackageContentsByDefault) {
        this.includePackageContentsByDefault = includePackageContentsByDefault;
        return this;
    }

    /**
     * Optionally set any type and annotation to scan for. Classes that both extend from this type
     * and include the supplied annotation will be returned as a match in the scanned results
     *
     * @param type - the type to scan for
     * @param annotation - the type annotation to scan for
     * @return this AnnotationScanner for method chaining
     */
    public final AnnotationScanner withTypeAndAnnotation(Class<?> type, Class<? extends Annotation> annotation) {
        annotatedTypes.add(new AnnotatedType(type, annotation));
        return this;
    }

    /**
     * Optionally set any constructor annotations to scan for. Classes with at least one constructor with at least one
     * of the supplied annotations will be returned as a match in the scanned results
     *
     * Both the class's declared constructors and its inherited public constructors will be scanned
     *
     * @param annotations - the constructor annotations to scan for
     * @return this AnnotationScanner for method chaining
     */
    @SafeVarargs
    public final AnnotationScanner withTypeAnnotations(Class<? extends Annotation>...annotations) {
        typeAnnotations.addAll(asList(annotations));
        return this;
    }

    /**
     * Optionally set any method annotations to scan for. Classes with at least one method with at least one
     * of the supplied annotations will be returned as a match in the scanned results
     *
     * Both the class's declared methods and its inherited public methods will be scanned
     *
     * @param annotations - the method annotations to scan for
     * @return this AnnotationScanner for method chaining
     */
    @SafeVarargs
    public final AnnotationScanner withMethodAnnotations(Class<? extends Annotation>...annotations) {
        methodAnnotations.addAll(asList(annotations));
        return this;
    }

    /**
     * Optionally set any field annotations to scan for. Classes with at least one field with at least one
     * of the supplied annotations will be returned as a match in the scanned results
     *
     * @param annotations - the field annotations to scan for
     * @return this AnnotationScanner for method chaining
     */
    @SafeVarargs
    public final AnnotationScanner withFieldAnnotations(Class<? extends Annotation>...annotations) {
        fieldAnnotations.addAll(asList(annotations));
        return this;
    }

    /**
     * Optionally set any constructor annotations to scan for. Classes with at least one constructor with at least one
     * of the supplied annotations will be returned as a match in the scanned results
     *
     * @param annotations - the constructor annotations to scan for
     * @return this AnnotationScanner for method chaining
     */
    @SafeVarargs
    public final AnnotationScanner withConstructorAnnotations(Class<? extends Annotation>...annotations) {
        constructorAnnotations.addAll(asList(annotations));
        return this;
    }

    /**
     * Optionally set the classloader to use for scanning classes. If this method is not set, then the Thread context class loader
     * will be used.
     *
     * @see Thread#getContextClassLoader()
     *
     * @param classLoaderToUse - the class loader to use
     * @return this AnnotationScanner for method chaining
     */
    public AnnotationScanner withClassLoader(ClassLoader classLoaderToUse) {
        this.classLoaderToUse = classLoaderToUse;
        return this;
    }

    @Getter
    @EqualsAndHashCode
    public static class ScannerResult<T,A extends Annotation> {
        private final Class<?> clazz;
        private final T annotatedElement;
        private final A annotation;
        private final boolean isMatch;

        public ScannerResult(Class<?> clazz, T annotatedElement, A annotation) {
            this(clazz, annotatedElement, annotation, true);
        }

        public ScannerResult(Class<?> clazz, T annotatedElement, A annotation, boolean isMatch) {
            this.clazz = clazz;
            this.annotatedElement = annotatedElement;
            this.annotation = annotation;
            this.isMatch = isMatch;
        }

        /**
         * If {@link AnnotationScanner} is built with {@link AnnotationScanner#includePackageContentByDefault(boolean)} invoked
         * with true, then all members of the target package will be returned in the results, even if they
         * do not match the scanning criteria.
         *
         * Calling this method will indicate if this result was actually a match or if it is only included
         * in the results because it is a member of the target package.
         *
         * If {@link AnnotationScanner#includePackageContentByDefault(boolean)} is not invoked, or is invoked
         * with false, then this method will always return true because only matches will be included.
         *
         * @return true if this result represents a match
         */
        public boolean isMatch() {
            return isMatch;
        }
    }

    protected AnnotationScanner(Set<String> packages) {
        this.packages = packages;
    }

    /**
     * If no class from a package has ever been loaded then that package will not be
     * returned by the classloader
     */
    // TODO: find a way to scan all sub packages, this will require scanning all jars
//    @SneakyThrows({NoSuchMethodException.class, IllegalAccessException.class, InvocationTargetException.class})
//    protected Set<String> subPackages(String packaj) {
//        val subPackages = new HashSet<String>();
//        if (!(classLoaderToUse instanceof URLClassLoader)) {
//            throw new IllegalStateException("Cannot scan for subpackages. Classloader is not an instance of URLClassloader");
//        }
//        URL[] urls = ((URLClassLoader) classLoaderToUse).getURLs();
//        for (val p: Package.getPackages()) {
//            if (!p.getName().equals(packaj) && p.getName().startsWith(packaj + "."))
//                subPackages.add(p.getName());
//        }
//        return subPackages;
//    }

    /**
     * Scan for results matching the criteria of this AnnotationScanner
     *
     * The caller should be prepared to catch any exceptions encountered when scanning classes. This is very unlikely
     * unless the AnnotationScanner is invoked with a custom {@link ClassLoader}
     *
     * @see AnnotationScanner#withClassLoader(ClassLoader)
     *
     * @return a Set containing the results (if any)
     */
    @SneakyThrows({IOException.class, URISyntaxException.class, ClassNotFoundException.class})
    public Set<ScannerResult<?,? extends Annotation>> scan() {
        if (this.classLoaderToUse == null)
            this.classLoaderToUse = Thread.currentThread().getContextClassLoader();

        val classNames = new HashSet<String>();

        for (val packaj : packages) {
            classNames.addAll(getClassNamesFromPackage(packaj));
            // TODO: support scanning subpackages
//            for (val subPackage: subPackages(packaj))
//                classNames.addAll(getClassNamesFromPackage(subPackage));
        }

        val results = new HashSet<ScannerResult<?,?>>();
        for (val className : classNames) {
            boolean matched = false;
            val clazz = Class.forName(className, true, classLoaderToUse);

            for (val annotatedType: annotatedTypes) {
                val annotationInstance = clazz.getAnnotation(annotatedType.getAnnotation());
                if (annotationInstance != null && annotatedType.getType().isAssignableFrom(clazz)) {
                    results.add(new ScannerResult<>(clazz, clazz, annotationInstance));
                    matched = true;
                }
            }

            // scan for annotated types
            for (val annotationClass: typeAnnotations) {
                val annotationInstance = clazz.getAnnotation(annotationClass);
                if (annotationInstance != null) {
                    results.add(new ScannerResult<>(clazz, clazz, annotationInstance));
                    matched = true;
                }
            }
            // scan for annotated methods
            for (val annotationClass: methodAnnotations) {
                val allMethods = new HashSet<Method>();
                allMethods.addAll(asList(clazz.getMethods()));
                allMethods.addAll(asList(clazz.getDeclaredMethods()));

                for (val method: allMethods) {
                    val annotationInstance = method.getAnnotation(annotationClass);
                    if (annotationInstance != null) {
                        results.add(new ScannerResult<>(clazz, method, annotationInstance));
                        matched = true;
                    }
                }
            }

            // scan for annotated fields
            for (val annotationClass: fieldAnnotations) {
                val allFields = new HashSet<Field>();
                allFields.addAll(asList(clazz.getFields()));
                allFields.addAll(asList(clazz.getDeclaredFields()));

                for (val field : allFields) {
                    val annotationInstance = field.getAnnotation(annotationClass);
                    if (annotationInstance != null) {
                        results.add(new ScannerResult<>(clazz, field, annotationInstance));
                        matched = true;
                    }
                }
            }
            // scan for annotated constructors
            for (val annotationClass: constructorAnnotations) {
                val allConstructors = new HashSet<Constructor>();
                allConstructors.addAll(asList(clazz.getConstructors()));
                allConstructors.addAll(asList(clazz.getDeclaredConstructors()));

                for (val constructor: allConstructors) {
                    val annotationInstance = constructor.getAnnotation(annotationClass);
                    if (annotationInstance != null) {
                        results.add(new ScannerResult<>(clazz, constructor, annotationInstance));
                        matched = true;
                    }
                }
            }

            if (!matched && includePackageContentsByDefault)
                results.add(new ScannerResult<>(clazz, clazz, null, false));
        }

        return results;
    }

    /**
     * Find the first such method of the target class (either declared or inherited) that is annotated with the provided annotation
     * and that returns the provided return type. This search is greedy.
     *
     * If no such method is found {@link Optional#empty()} is returned
     *
     * @param target - this class's methods will be scanned
     * @param annotationClass - methods with this annotation will be considered
     * @param returnType - methods returning this type will be considered
     * @return a {@link Optional} maybe containing the first method found matching the search criteria
     */
    public static Optional<Method> annotatedMethodWithReturnType(Class<?> target, Class<? extends Annotation> annotationClass, Class<?> returnType) {
        val allMethods = new HashSet<Method>();
        allMethods.addAll(asList(target.getDeclaredMethods()));
        allMethods.addAll(asList(target.getMethods()));
        for (val method: allMethods) {
            val annotationInstance = method.getAnnotation(annotationClass);
            if (annotationInstance != null && method.getReturnType().equals(returnType))
                return Optional.of(method);
        }

        return Optional.empty();
    }

    /**
     * Create and return a new AnnotationScanner to scan the provided packages.
     * Note: Subpackages must be listed explicitly.
     *
     * Packages names should be formatted with '.' as the namespace delimiter rather thant '/'
     *
     * For example
     *
     * <pre>
     *     AnnotationScanner.of("io.github.bglowney.annotationscanner")
     *      .includePackageContentByDefault(true)
     *      .scan()
     * </pre>
     *
     * will return all types in the "io.github.bglowney.annotationscanner" package
     *
     * @param packages - the top level packages to scan
     * @return a new AnnotationScanner
     */
    public static AnnotationScanner of(String... packages) {
        return new AnnotationScanner(new HashSet<>(asList(packages)));
    }

    // it appears that if no classes in a package are ever used
    // then the jvm may not know about the package
    // We must reference at least one class in the package
    protected Set<String> getClassNamesFromPackage(String packageName) throws IOException, URISyntaxException {
        val names = new HashSet<String>();

        val directoryPackageName = packageName.replace(".", File.separator);
        val urls = classLoaderToUse.getResources(directoryPackageName);
        while(urls.hasMoreElements()) {
            val packageURL = urls.nextElement();

            if ("jar".equals(packageURL.getProtocol())) {
                Enumeration<JarEntry> jarEntries;
                String entryName;

                // build jar file name, then loop through zipped entries
                String jarFileName = URLDecoder.decode(packageURL.getFile(), "UTF-8");
                jarFileName = jarFileName.substring(5, jarFileName.indexOf("!"));

                val jf = new JarFile(jarFileName);
                jarEntries = jf.entries();
                while (jarEntries.hasMoreElements()) {
                    entryName = jarEntries.nextElement().getName();
                    if (!entryName.endsWith(".class")) continue;
                    entryName = entryName.substring(0, entryName.lastIndexOf('.'));
                    entryName = entryName.replace('/', '.');
                    names.add(entryName);
                }

                // loop through files in classpath
            } else {
                val uri = new URI(packageURL.toString());
                val folder = new File(uri.getPath());
                val files = folder.listFiles();
                if (files == null) continue;
                String entryName;
                for (val file : files) {
                    entryName = file.getName();
                    if (!entryName.endsWith(".class")) continue;
                    entryName = packageName + "." + entryName.substring(0, entryName.lastIndexOf('.'));
                    entryName = entryName.replace('/', '.');
                    names.add(entryName);
                }
            }
        }
        return names;
    }

}
