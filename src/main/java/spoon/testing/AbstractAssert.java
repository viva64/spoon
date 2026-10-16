/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.testing;

import spoon.SpoonException;
import spoon.processing.Processor;

import java.util.LinkedList;

/**
 * Base contract for all assertion objects: the minimum functionality that
 * any assertion object should provide.
 *
 * @param <T>
 * 		the self type of this assertion class.
 * @param <A>
 * 		the type of the actual value.
 */
public abstract class AbstractAssert<T extends AbstractAssert<T, A>, A> {
	protected final LinkedList<Processor<?>> processors = new LinkedList<>();
	protected final A actual;
	protected final T myself;

	protected AbstractAssert(A actual, Class<?> selfType) {
		this.myself = (T) selfType.cast(this);
		this.actual = actual;
	}

	/**
	 * Applies the processor on the actual value.
	 *
	 * @param processor
	 * 		the given processor.
	 * @return {@code this} assertion object.
	 */
	public T withProcessor(Processor<?> processor) {
		processors.add(processor);
		return myself;
	}

	public int hashCode() {
		return 1;
	}
}
