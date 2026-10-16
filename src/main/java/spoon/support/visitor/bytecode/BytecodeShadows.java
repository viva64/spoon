/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.support.visitor.bytecode;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.FactoryImpl;
import spoon.reflect.visitor.CtScanner;
import spoon.support.DefaultCoreFactory;

/**
 * Creates and remembers shadow types: models of classes which are not part of the source model, built from the
 * bytes of class files of the class path. No class is loaded, and nothing is read by reflection.
 */
public final class BytecodeShadows {

	private final Factory factory;
	private final Factory shadowFactory;
	private final ClassPathBytes bytes;
	private final ClassModelTreeBuilder builder;
	private final Map<String, CtType<?>> synthetic = new HashMap<>();
	private final Set<String> missing = new HashSet<>();
	// lock free view of the results, so the lookups of the types, which are already known, do not block each other
	private final Map<String, CtType<?>> resolved = new java.util.concurrent.ConcurrentHashMap<>();
	private final Set<String> unresolved = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * @param factory the factory of the model the shadow types are used in
	 */
	public BytecodeShadows(Factory factory) {
		this.factory = factory;
		this.shadowFactory = new FactoryImpl(new DefaultCoreFactory(), factory.getEnvironment());
		String[] classpath = factory.getEnvironment().getSourceClasspath();
		if (classpath == null) {
			// no source class path configured: use the class path of the running JVM
			String runtimeClasspath = System.getProperty("java.class.path", "");
			classpath = runtimeClasspath.isEmpty() ? new String[0] : runtimeClasspath.split(java.io.File.pathSeparator);
		}
		this.bytes = ClassPathBytes.of(classpath);
		this.builder = new ClassModelTreeBuilder(shadowFactory, bytes);
	}

	/**
	 * @param qualifiedName qualified name of a type, nested types are separated by a dollar sign, arrays end with []
	 * @return the shadow type, or null if there are no bytes for such a type
	 */
	public CtType<?> find(String qualifiedName) {
		CtType<?> known = resolved.get(qualifiedName);
		if (known != null) {
			return known;
		}
		if (unresolved.contains(qualifiedName)) {
			return null;
		}
		synchronized (this) {
			CtType<?> found = findLocked(qualifiedName);
			if (found != null) {
				resolved.put(qualifiedName, found);
			} else {
				unresolved.add(qualifiedName);
			}
			return found;
		}
	}

	private CtType<?> findLocked(String qualifiedName) {
		return Optional.<CtType<?>>ofNullable(synthetic.get(qualifiedName))
			.or(() -> built(qualifiedName))
			.or(() -> missing.contains(qualifiedName) ? Optional.empty() : build(qualifiedName))
			.orElse(null);
	}

	/**
	 * @return the shadow type or an empty stub, if there are no bytes for the type
	 */
	public synchronized CtType<?> require(String qualifiedName) {
		CtType<?> found = find(qualifiedName);
		if (found != null) {
			return found;
		}
		unresolved.remove(qualifiedName);
		ShadowName name = ShadowName.parse(qualifiedName);
		Launcher.LOGGER.warn("stub for {}", qualifiedName);
		CtType<?> stub = builder.stub(name.packageName(), name.simpleName());
		rebind(stub);
		missing.remove(qualifiedName);
		return stub;
	}

	private Optional<CtType<?>> build(String qualifiedName) {
		Optional<CtType<?>> result = switch (ShadowName.parse(qualifiedName)) {
			case ShadowName.Primitive primitive -> Optional.of(remember(primitive, builder.primitive(primitive.name())));
			case ShadowName.Array array -> Optional.ofNullable(find(array.component().qualifiedName()))
				.map(component -> remember(array, builder.array(array.packageName(), array.simpleName())));
			case ShadowName.Declared declared -> fromBytecode(declared.qualifiedName());
		};
		if (result.isEmpty()) {
			missing.add(qualifiedName);
		}
		return result;
	}

	private Optional<CtType<?>> fromBytecode(String qualifiedName) {
		Optional<ClassModel> parsed = bytes.parse(qualifiedName);
		if (parsed.isEmpty()) {
			return Optional.empty();
		}
		ClassModel top = topLevelOf(parsed.get());
		if (built(qualifiedNameOf(top)).isEmpty()) {
			try {
				rebind(builder.build(top));
			} catch (RuntimeException | LinkageError e) {
				Launcher.LOGGER.warn("cannot build {} from bytecode", qualifiedNameOf(top), e);
				throw e;
			}
		}
		return built(qualifiedName);
	}

	private CtType<?> remember(ShadowName name, CtType<?> type) {
		rebind(type);
		synthetic.put(name.qualifiedName(), type);
		return type;
	}

	private Optional<CtType<?>> built(String qualifiedName) {
		return Optional.<CtType<?>>ofNullable(shadowFactory.Type().get(qualifiedName));
	}

	private ClassModel topLevelOf(ClassModel model) {
		return outerOf(model)
			.flatMap(bytes::parse)
			.map(this::topLevelOf)
			.orElse(model);
	}

	private static Optional<String> outerOf(ClassModel model) {
		String self = model.thisClass().asInternalName();
		return model.findAttribute(Attributes.innerClasses()).stream()
			.flatMap(attribute -> attribute.classes().stream())
			.filter(info -> info.innerClass().asInternalName().equals(self))
			.flatMap(info -> info.outerClass().stream())
			.map(outer -> outer.asInternalName().replace('/', '.'))
			.findFirst();
	}

	private static String qualifiedNameOf(ClassModel model) {
		return model.thisClass().asInternalName().replace('/', '.');
	}

	private void rebind(CtType<?> type) {
		type.setFactory(factory);
		type.accept(new CtScanner() {
			@Override
			public void scan(CtElement element) {
				if (element != null) {
					element.setFactory(factory);
					super.scan(element);
				}
			}
		});
	}
}
