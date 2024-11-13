/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.compiler.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.compiler.SpoonFile;

public class SourceOptions<T extends SourceOptions<T>> extends Options<T> {
	private static final Logger log = LoggerFactory.getLogger(SourceOptions.class);

	public SourceOptions() {
		super(SourceOptions.class);
	}

	/** adds the given paths as concatenated string with File.pathSeparator as sources */
	public T sources(String sources) {
		if (sources == null || sources.isEmpty()) {
			return myself;
		}
		return sources(sources.split(File.pathSeparator));
	}

	/** adds the given paths as sources */
	public T sources(String... sources) {
		if (sources == null || sources.length == 0) {
			args.add(".");
			return myself;
		}
		args.addAll(Arrays.asList(sources));
		return myself;
	}

	/** adds the given {@link spoon.compiler.SpoonFile} as sources */
	public T sources(List<SpoonFile> sources) {
		if (sources == null || sources.isEmpty()) {
			args.add(".");
			return myself;
		}
		try {
			Path path = Files.createTempDirectory("spoon-container");

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					Files.walk(path).sorted(Comparator.reverseOrder()).forEach(
							path1 -> {
								try {
									Files.deleteIfExists(path1);
								} catch (IOException e) {
									log.error("An error occurred: ", e);
								}
							});
				} catch (IOException e) {
					log.error("An error occurred: ", e);
				}
			}));

			for (SpoonFile source : sources) {
				if (source.isActualFile()) {
					args.add(source.toString());
				} else {
					try {
						String name = source.getName();
						if (name.startsWith(File.separator)) {
							name = name.substring(1);
						}
						File file = path.resolve(name).toFile();
						file.getParentFile().mkdirs();
						try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
							IOUtils.copy(source.getContent(), fileOutputStream);
						}
						args.add(file.toString());
					} catch (IOException e) {
						throw new RuntimeException(e.getMessage(), e);
					}
				}
			}
		} catch (IOException e) {
			log.error("An error occurred: ", e);
		}
		return myself;
	}
}
