/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */
package spoon.support.compiler.jdt;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import spoon.reflect.declaration.CtElement;
import spoon.support.Internal;

import java.util.Objects;

@Internal
public class ASTPair {

    private final CtElement element;
    private final ASTNode node;
    public ASTPair(CtElement element, ASTNode node) {
        this.element = element;
        this.node = node;
    }

    @Override
	public String toString() {
		return element.getClass().getSimpleName() + "-" + node.getClass().getSimpleName();
	}

    public CtElement element() {
        return element;
    }

    public ASTNode node() {
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ASTPair)) {
            return false;
        }
        final ASTPair astPair = (ASTPair) o;
        return Objects.equals(element, astPair.element) && Objects.equals(node, astPair.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element, node);
    }
}
