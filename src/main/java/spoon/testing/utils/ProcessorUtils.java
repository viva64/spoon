/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.testing.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import spoon.Launcher;
import spoon.SpoonException;
import spoon.processing.Processor;
import spoon.processing.ProcessorProperties;
import spoon.processing.Property;
import spoon.reflect.factory.Factory;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

import java.util.Collection;

public final class ProcessorUtils {
	private static final ObjectMapper converter = new ObjectMapper();

	private ProcessorUtils() {
		throw new AssertionError();
	}

	public static void process(Factory factory, Collection<Processor<?>> processors) {
		final JDTBasedSpoonCompiler compiler = (JDTBasedSpoonCompiler) new Launcher().createCompiler(factory);
		compiler.process(processors);
	}

}
