/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.support.compiler.jdt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationProcessingTest {
	private static final String RUNS_PROPERTY = "spoon.test.apt.runs";

	private static final String ANNOTATION = """
			package p;

			public @interface Gen {
			}
			""";

	// generates p.<Name>Generated for every type annotated with @p.Gen and records each initialization
	private static final String PROCESSOR = """
			package p;

			import java.io.IOException;
			import java.io.UncheckedIOException;
			import java.io.Writer;
			import java.nio.file.Files;
			import java.nio.file.Path;
			import java.nio.file.StandardOpenOption;
			import java.util.Set;
			import javax.annotation.processing.AbstractProcessor;
			import javax.annotation.processing.ProcessingEnvironment;
			import javax.annotation.processing.RoundEnvironment;
			import javax.annotation.processing.SupportedAnnotationTypes;
			import javax.lang.model.SourceVersion;
			import javax.lang.model.element.Element;
			import javax.lang.model.element.TypeElement;

			@SupportedAnnotationTypes("p.Gen")
			public class GenProcessor extends AbstractProcessor {
				@Override
				public SourceVersion getSupportedSourceVersion() {
					return SourceVersion.latestSupported();
				}

				@Override
				public synchronized void init(ProcessingEnvironment environment) {
					super.init(environment);
					try {
						Files.writeString(Path.of(System.getProperty("%s")), "init\\n",
								StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}

				@Override
				public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
					for (Element element : round.getElementsAnnotatedWith(Gen.class)) {
						String name = element.getSimpleName() + "Generated";
						try (Writer writer = processingEnv.getFiler().createSourceFile("p." + name, element).openWriter()) {
							writer.write("package p; public class " + name + " {}");
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					}
					return true;
				}
			}
			""".formatted(RUNS_PROPERTY);

	private static final String SOURCE = """
			package p;

			@Gen
			public class Marked {
			}
			""";

	@TempDir
	Path tmp;

	private Path runs;

	@BeforeEach
	void setUp() {
		runs = tmp.resolve("runs.txt");
		System.setProperty(RUNS_PROPERTY, runs.toString());
	}

	@AfterEach
	void tearDown() {
		System.clearProperty(RUNS_PROPERTY);
	}

	@Test
	void typesGeneratedByProcessorsArePartOfTheModel() throws IOException {
		// contract: annotation processors from the source classpath run when the model is built,
		// their sources are written to the source output directory and their types are in the model
		Launcher launcher = launcher();

		launcher.buildModel();

		assertNotNull(launcher.getFactory().Type().get("p.MarkedGenerated"));
		assertTrue(Files.isRegularFile(tmp.resolve("generated/p/MarkedGenerated.java")));
	}

	@Test
	void compilingTheModelDoesNotRunProcessorsAgain() throws IOException {
		// contract: the processors already ran when the model was built, compile() passes -proc:none
		Launcher launcher = launcher();
		launcher.setBinaryOutputDirectory(tmp.resolve("classes").toString());

		launcher.buildModel();
		launcher.getModelBuilder().compile();

		assertEquals(List.of("init"), Files.readAllLines(runs));
	}

	private Launcher launcher() throws IOException {
		Path processor = compileProcessor();
		Path sources = write(tmp.resolve("src"), "p/Marked.java", SOURCE);

		Launcher launcher = new Launcher();
		launcher.getEnvironment().setComplianceLevel(17);
		launcher.getEnvironment().setSourceClasspath(new String[] {processor.toString()});
		launcher.setSourceOutputDirectory(tmp.resolve("generated").toString());
		launcher.addInputResource(sources.toString());
		return launcher;
	}

	private Path compileProcessor() throws IOException {
		Path sources = tmp.resolve("processor-src");
		Path classes = tmp.resolve("processor");
		write(sources, "p/Gen.java", ANNOTATION);
		write(sources, "p/GenProcessor.java", PROCESSOR);
		write(classes, "META-INF/services/javax.annotation.processing.Processor", "p.GenProcessor\n");

		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		int exitCode = javac.run(null, null, null, "-proc:none", "-d", classes.toString(),
				sources.resolve("p/Gen.java").toString(), sources.resolve("p/GenProcessor.java").toString());
		assertEquals(0, exitCode);
		return classes;
	}

	private static Path write(Path root, String relative, String content) throws IOException {
		Path file = root.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		return root;
	}
}
