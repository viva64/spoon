/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.reflect.visitor.filter;


import spoon.SpoonException;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.Filter;

/**
 * Defines an abstract filter based on matching on the element types.
 *
 * Not necessary in simple cases thanks to the use of runtime reflection.
 */
public abstract class AbstractFilter<T extends CtElement> implements Filter<T> {

	private Class<T> type;

	/**
	 * Creates a filter with the type of the potentially matching elements.
	 */
	@SuppressWarnings("unchecked")
	public AbstractFilter(Class<? super T> type) {
		this.type = (Class<T>) type;
	}

	public Class<T> getType() {
		return type;
	}

	@Override
	public boolean matches(T element) {
		return type.isAssignableFrom(element.getClass());
	}
}
