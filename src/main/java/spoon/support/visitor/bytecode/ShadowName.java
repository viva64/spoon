/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */

// 2006-2008 (c) Viva64.com Team
// 2008-2020 (c) OOO "Program Verification Systems"
// 2020-2026 (c) PVS-Studio LLC

package spoon.support.visitor.bytecode;

import java.util.Set;

sealed interface ShadowName permits ShadowName.Primitive, ShadowName.Array, ShadowName.Declared {

    Set<String> PRIMITIVES = Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

    static ShadowName parse(String qualifiedName) {
        if (qualifiedName.endsWith("[]")) {
            return new Array(parse(qualifiedName.substring(0, qualifiedName.length() - 2)));
        }
        if (PRIMITIVES.contains(qualifiedName)) {
            return new Primitive(qualifiedName);
        }
        return new Declared(qualifiedName);
    }

    String qualifiedName();

    String packageName();

    String simpleName();

    record Primitive(String name) implements ShadowName {
        @Override
        public String qualifiedName() {
            return name;
        }

        @Override
        public String packageName() {
            return "";
        }

        @Override
        public String simpleName() {
            return name;
        }
    }

    record Array(ShadowName component) implements ShadowName {
        @Override
        public String qualifiedName() {
            return component.qualifiedName() + "[]";
        }

        @Override
        public String packageName() {
            return component.packageName();
        }

        @Override
        public String simpleName() {
            return component.simpleName() + "[]";
        }
    }

    record Declared(String qualifiedName) implements ShadowName {
        @Override
        public String packageName() {
            var dot = qualifiedName.lastIndexOf('.');
            return dot < 0 ? "" : qualifiedName.substring(0, dot);
        }

        @Override
        public String simpleName() {
            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        }
    }
}