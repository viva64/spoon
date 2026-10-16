/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */

package spoon.support.visitor.bytecode;

import spoon.Launcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

final class ClassPathBytes {

    private static final Map<Path, Optional<ZipFile>> OPENED_JARS = new ConcurrentHashMap<>();
    private static final Optional<Jrt> JRT = Jrt.open();

    private final List<Root> roots;

    private ClassPathBytes(List<Root> roots) {
        this.roots = roots;
    }

    static ClassPathBytes of(String[] classpath) {
        var projectRoots = Arrays.stream(classpath == null ? new String[0] : classpath)
                .map(Path::of)
                .flatMap(path -> root(path).stream());
        return new ClassPathBytes(Stream.concat(JRT.stream(), projectRoots).toList());
    }

    Optional<ClassModel> parse(String binaryName) {
        var entry = binaryName.replace('.', '/') + ".class";
        return roots.stream()
                .flatMap(root -> root.read(entry).stream())
                .findFirst()
                .map(ClassFile.of()::parse);
    }

    private static Optional<Root> root(Path path) {
        if (Files.isDirectory(path)) {
            return Optional.of(new Directory(path));
        }
        return OPENED_JARS.computeIfAbsent(path, ClassPathBytes::openJar).map(Jar::new);
    }

    private static Optional<ZipFile> openJar(Path path) {
        try {
            return Optional.of(new ZipFile(path.toFile()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> readFile(Path path) {
        try {
            return Files.isRegularFile(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private sealed interface Root permits Directory, Jar, Jrt {
        Optional<byte[]> read(String entry);
    }

    private record Directory(Path root) implements Root {
        @Override
        public Optional<byte[]> read(String entry) {
            return readFile(root.resolve(entry));
        }
    }

    private record Jar(ZipFile zip) implements Root {
        @Override
        public Optional<byte[]> read(String entry) {
            return Optional.ofNullable(zip.getEntry(entry)).flatMap(found -> {
                try (var input = zip.getInputStream(found)) {
                    return Optional.of(input.readAllBytes());
                } catch (IOException e) {
                    return Optional.empty();
                }
            });
        }
    }

    private record Jrt(Map<String, List<Path>> modulesByPackage) implements Root {

        static Optional<Jrt> open() {
            try {
                var jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
                var version = ClassFile.of()
                    .parse(jrt.getPath("/modules/java.base/module-info.class"))
                    .findAttribute(Attributes.module())
                    .flatMap(ModuleAttribute::moduleVersion)
                    .map(Utf8Entry::stringValue)
                    .orElse("?");
                Launcher.LOGGER.info("jrt java.base version = {}", version);

                try (var packages = Files.list(jrt.getPath("/packages"))) {
                    return Optional.of(new Jrt(packages.collect(Collectors.toUnmodifiableMap(
                        pkg -> pkg.getFileName().toString(),
                        Jrt::modulesOf))));
                }
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static List<Path> modulesOf(Path pkg) {
            try (var modules = Files.list(pkg)) {
                return modules
                    .map(module -> pkg.getFileSystem().getPath("/modules", module.getFileName().toString()))
                    .toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<byte[]> read(String entry) {
            var slash = entry.lastIndexOf('/');
            return slash < 0
                ? Optional.empty()
                : modulesByPackage.getOrDefault(entry.substring(0, slash).replace('/', '.'), List.of()).stream()
                .flatMap(module -> readFile(module.resolve(entry)).stream())
                .findFirst();
        }
    }
}
