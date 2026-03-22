package com.core.plugin.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the plugin JAR for all classes under a given package prefix.
 * Used for reflective auto-registration of commands, services, and listeners.
 */
public final class ClassUtil {

    private ClassUtil() {}

    /**
     * Find all concrete (non-abstract, non-interface) classes under the given package
     * that are assignable to the specified supertype.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<Class<? extends T>> findSubclasses(String packagePrefix, Class<T> supertype, ClassLoader classLoader) {
        List<Class<? extends T>> results = new ArrayList<>();
        String pathPrefix = packagePrefix.replace('.', '/');

        try {
            File jarFile = new File(ClassUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jarFile.getName().endsWith(".jar")) {
                // Running from IDE — scan class files on disk
                return scanDirectory(jarFile, pathPrefix, packagePrefix, supertype, classLoader);
            }

            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(pathPrefix) || !name.endsWith(".class")) continue;
                    if (name.contains("$")) continue; // skip inner classes

                    String className = name.replace('/', '.').replace(".class", "");
                    tryAddClass(className, supertype, classLoader, results);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to scan classpath for " + packagePrefix, e);
        }

        return results;
    }

    private static <T> List<Class<? extends T>> scanDirectory(File root, String pathPrefix, String packagePrefix,
                                                               Class<T> supertype, ClassLoader classLoader) {
        List<Class<? extends T>> results = new ArrayList<>();
        File packageDir = new File(root, pathPrefix);
        if (!packageDir.exists()) return results;
        scanDirectoryRecursive(packageDir, packagePrefix, supertype, classLoader, results);
        return results;
    }

    private static <T> void scanDirectoryRecursive(File dir, String currentPackage, Class<T> supertype,
                                                    ClassLoader classLoader, List<Class<? extends T>> results) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, currentPackage + "." + file.getName(), supertype, classLoader, results);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String className = currentPackage + "." + file.getName().replace(".class", "");
                tryAddClass(className, supertype, classLoader, results);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void tryAddClass(String className, Class<T> supertype,
                                         ClassLoader classLoader, List<Class<? extends T>> results) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            if (supertype.isAssignableFrom(clazz)
                    && !clazz.isInterface()
                    && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                results.add((Class<? extends T>) clazz);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // Skip classes that can't be loaded
        }
    }
}
