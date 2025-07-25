/**
 * Copyright (C) 2006-2018 INRIA and contributors
 * Spoon - http://spoon.gforge.inria.fr/
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */
package spoon.support.visitor.java;

import com.mysema.query.support.ProjectableQuery;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import spoon.Launcher;
import spoon.SpoonException;
import spoon.metamodel.Metamodel;
import spoon.metamodel.MetamodelConcept;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtAnnotationMethod;
import spoon.reflect.declaration.CtAnnotationType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModifiable;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtRecord;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.TypeFactory;
import spoon.reflect.path.CtElementPathBuilder;
import spoon.reflect.path.CtPathException;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Root;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.FileSystemFile;
import spoon.support.compiler.jdt.JDTSnippetCompiler;
import spoon.support.reflect.CtExtendedModifier;
import spoon.support.reflect.code.CtAssignmentImpl;
import spoon.support.reflect.code.CtConditionalImpl;
import spoon.support.reflect.declaration.CtEnumValueImpl;
import spoon.support.reflect.declaration.CtFieldImpl;
import spoon.support.util.compilation.JavacFacade;
import spoon.support.visitor.equals.EqualsChecker;
import spoon.support.visitor.equals.EqualsVisitor;
import spoon.test.generics.testclasses3.ComparableComparatorBug;
import spoon.test.innerclasses.InnerClasses;
import spoon.test.pkg.PackageTest;
import spoon.test.pkg.cyclic.Outside;
import spoon.test.pkg.cyclic.direct.Cyclic;
import spoon.test.pkg.cyclic.indirect.Indirect;
import spoon.testing.utils.GitHubIssue;

import java.io.File;
import java.io.ObjectInputStream;
import java.lang.annotation.Retention;
import java.net.CookieManager;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spoon.testing.utils.ModelUtils.createFactory;

public class JavaReflectionTreeBuilderTest {

	@Test
	public void testScannerClass() {
		final CtClass<Class> aClass = new JavaReflectionTreeBuilder(createFactory()).scan(Class.class);
		assertNotNull(aClass);
		assertEquals("java.lang.Class", aClass.getQualifiedName());
		//The Class extends Object, but CtElementImpl (made from sources) getSuperclass() returns null. See CtTypeInformation#getSuperclass() comment.
		assertNull(aClass.getSuperclass());
		assertFalse(aClass.getSuperInterfaces().isEmpty());
		assertFalse(aClass.getFields().isEmpty());
		assertFalse(aClass.getMethods().isEmpty());
		assertFalse(aClass.getNestedTypes().isEmpty());
		assertTrue(aClass.isShadow());
	}

	@Test
	public void testScannerEnum() {
		final CtEnum<TextStyle> anEnum = new JavaReflectionTreeBuilder(createFactory()).scan(TextStyle.class);
		assertNotNull(anEnum);
		assertEquals("java.time.format.TextStyle", anEnum.getQualifiedName());
		assertNotNull(anEnum.getSuperclass());
		assertFalse(anEnum.getFields().isEmpty());
		assertFalse(anEnum.getEnumValues().isEmpty());
		assertFalse(anEnum.getMethods().isEmpty());
		assertTrue(anEnum.isShadow());
	}

	@Test
	public void testScannerInterface() {
		final CtInterface<CtLambda> anInterface = new JavaReflectionTreeBuilder(createFactory()).scan(CtLambda.class);
		assertNotNull(anInterface);
		assertEquals("spoon.reflect.code.CtLambda", anInterface.getQualifiedName());
		assertNull(anInterface.getSuperclass());
		assertFalse(anInterface.getSuperInterfaces().isEmpty());
		assertFalse(anInterface.getMethods().isEmpty());
		assertTrue(anInterface.isShadow());
	}

	@Test
	public void testScannerAnnotation() {
		final CtAnnotationType<SuppressWarnings> suppressWarning = new JavaReflectionTreeBuilder(createFactory()).scan(SuppressWarnings.class);
		assertNotNull(suppressWarning);
		assertEquals("java.lang.SuppressWarnings", suppressWarning.getQualifiedName());
		assertFalse(suppressWarning.getAnnotations().isEmpty());
		assertFalse(suppressWarning.getTypeMembers().isEmpty());
		assertTrue(suppressWarning.getTypeMembers().get(0) instanceof CtAnnotationMethod);
		assertTrue(suppressWarning.isShadow());
		assertNotNull(suppressWarning.getAnnotation(Retention.class));
		assertEquals("SOURCE", suppressWarning.getAnnotation(Retention.class).value().toString());
	}

	@Test
	public void testScannerGenericsInClass() {
		final CtType<ComparableComparatorBug> aType = new JavaReflectionTreeBuilder(createFactory()).scan(ComparableComparatorBug.class);
		assertNotNull(aType);

		// New type parameter declaration.
		assertEquals(1, aType.getFormalCtTypeParameters().size());
		CtTypeParameter ctTypeParameter = aType.getFormalCtTypeParameters().get(0);
		assertEquals("E extends java.lang.Comparable<? super E>", ctTypeParameter.toString());
		assertEquals(1, ctTypeParameter.getSuperclass().getActualTypeArguments().size());
		assertTrue(ctTypeParameter.getSuperclass().getActualTypeArguments().get(0) instanceof CtTypeParameterReference);
		assertEquals("? super E", ctTypeParameter.getSuperclass().getActualTypeArguments().get(0).toString());
	}

	@Test
	public void testScannerArrayReference() {
		final CtType<URLClassLoader> aType = new JavaReflectionTreeBuilder(createFactory()).scan(URLClassLoader.class);
		assertNotNull(aType);
		final CtMethod<Object> aMethod = aType.getMethod("getURLs");
		assertTrue(aMethod.getType() instanceof CtArrayTypeReference);
		final CtArrayTypeReference<Object> arrayRef = (CtArrayTypeReference<Object>) aMethod.getType();
		assertNull(arrayRef.getPackage());
		assertNull(arrayRef.getDeclaringType());
		assertNotNull(arrayRef.getComponentType());
	}

	@Test
	public void testDeclaredMethods() {
		final CtType<StringBuilder> type = new JavaReflectionTreeBuilder(createFactory()).scan(StringBuilder.class);
		assertNotNull(type);
		// All methods overridden from AbstractStringBuilder and with a type changed have been removed.
		assertEquals(0, type.getMethods().stream().filter(ctMethod -> "java.lang.AbstractStringBuilder".equals(ctMethod.getType().getQualifiedName())).collect(Collectors.toList()).size());
		// reverse is declared in AbstractStringBuilder and overridden in StringBuilder but the type is the same.
		assertNotNull(type.getMethod("reverse"));
		// readObject is declared in StringBuilder.
		assertNotNull(type.getMethod("readObject", type.getFactory().Type().createReference(ObjectInputStream.class)));
	}

	@Test
	public void testDeclaredField() {
		final CtType<CookieManager> aType = new JavaReflectionTreeBuilder(createFactory()).scan(CookieManager.class);
		assertNotNull(aType);
		// CookieManager has only 2 fields. Java reflection doesn't give us field of its superclass.
		assertEquals(2, aType.getFields().size());
	}

	@Test
	public void testDeclaredConstructor() {
		final CtType<JDTSnippetCompiler> aType = new JavaReflectionTreeBuilder(createFactory()).scan(JDTSnippetCompiler.class);
		assertNotNull(aType);
		// JDTSnippetCompiler has only 1 constructor with 2 arguments but its super class has 1 constructor with 1 argument.
		assertEquals(1, ((CtClass<JDTSnippetCompiler>) aType).getConstructors().size());
	}

	@Disabled("Does not work after Java downgrade to 11")
	@Test
	public void testShadowModelEqualsNormalModel() {
		//contract: CtType made from sources is equal to CtType made by reflection
		//with exception of CtExecutable#body, CtParameter#simpleName
		//with exception of Annotations with retention policy SOURCE
		Metamodel metaModel = new Metamodel(new File("src/main/java"));
		List<String> allProblems = new ArrayList<>();
		for (MetamodelConcept concept : metaModel.getConcepts()) {
			allProblems.addAll(checkShadowTypeIsEqual(concept.getImplementationClass()));
			allProblems.addAll(checkShadowTypeIsEqual(concept.getMetamodelInterface()));
		}
		assertTrue(allProblems.isEmpty(), "Found " + allProblems.size() + " problems:\n" + String.join("\n", allProblems));
	}

	private List<String> checkShadowTypeIsEqual(CtType<?> type) {
		if (type == null) {
			return Collections.emptyList();
		}
		Factory shadowFactory = createFactory();
		CtTypeReference<?> shadowTypeRef = shadowFactory.Type().createReference(type.getActualClass());
		CtType<?> shadowType = shadowTypeRef.getTypeDeclaration();

		assertFalse(type.isShadow());
		assertTrue(shadowType.isShadow());

		// Some elements, such as superinterfaces and thrown types, are ordered by their source position if they have
		// one. As a shadow model has no source positions, but a model built from source does, we must unset the source
		// positions of the normal model's elements to ensure that there are no ordering discrepancies.
		type.descendantIterator().forEachRemaining(e -> e.setPosition(SourcePosition.NOPOSITION));

		ShadowEqualsVisitor sev = new ShadowEqualsVisitor(new HashSet<>(Arrays.asList(
				//shadow classes has no body
				CtRole.STATEMENT,

				// shadow classes have no default expression
				CtRole.DEFAULT_EXPRESSION,

				// shadow classes have no comments
				CtRole.COMMENT)));

		return sev.checkDiffs(type, shadowType);
	}

	private static class Diff {
		CtElement element;
		CtElement other;
		Set<CtRole> roles = new HashSet<>();
		Diff(CtElement element, CtElement other) {
			this.element = element;
			this.other = other;
		}
	}

	private static class ShadowEqualsChecker extends EqualsChecker {
		Diff currentDiff;
		List<Diff> differences = new ArrayList<>();

		@Override
		protected void setNotEqual(CtRole role) {
			if (role == CtRole.MODIFIER) {
				if (currentDiff.element instanceof CtTypeMember) {
					CtTypeMember tm = (CtTypeMember) currentDiff.element;
					CtType<?> type = tm.getDeclaringType();
					if (type != null) {
						Set<ModifierKind> elementModifiers = ((CtModifiable) currentDiff.element).getModifiers();
						Set<ModifierKind> otherModifiers = ((CtModifiable) currentDiff.other).getModifiers();
						if (type.isInterface()) {
							if (removeModifiers(elementModifiers, ModifierKind.PUBLIC, ModifierKind.ABSTRACT, ModifierKind.FINAL)
									.equals(removeModifiers(otherModifiers, ModifierKind.PUBLIC, ModifierKind.ABSTRACT, ModifierKind.FINAL))) {
								//it is OK, that type memebers of interface differs in public abstract modifiers
								return;
							}
						} else if (type.isEnum()) {
							CtType<?> type2 = type.getDeclaringType();
							if (type2 != null) {
								if (type2.isInterface()) {
									if (removeModifiers(elementModifiers, ModifierKind.PUBLIC, ModifierKind.FINAL/*, ModifierKind.STATIC*/)
											.equals(removeModifiers(otherModifiers, ModifierKind.PUBLIC, ModifierKind.FINAL/*, ModifierKind.STATIC*/))) {
										//it is OK, that type memebers of interface differs in public abstract modifiers
										return;
									}
								}
							}
						}
					}
				}
			}
			currentDiff.roles.add(role);
		}

		private Set<ModifierKind> removeModifiers(Set<ModifierKind> elementModifiers, ModifierKind... modifiers) {
			Set<ModifierKind> copy = new HashSet<>(elementModifiers);
			for (ModifierKind modifierKind : modifiers) {
				copy.remove(modifierKind);
			}
			return copy;
		}

		@Override
		public void scan(CtElement element) {
			currentDiff = new Diff(element, other);
			super.scan(element);
			if (!currentDiff.roles.isEmpty()) {
				differences.add(currentDiff);
			}
		}
	}

	private static class ShadowEqualsVisitor extends EqualsVisitor {
		CtElement rootOfOther;
		CtElementPathBuilder pathBuilder = new CtElementPathBuilder();
		List<String> differences;
		Set<CtRole> ignoredRoles;

		ShadowEqualsVisitor(Set<CtRole> ignoredRoles) {
			super(new ShadowEqualsChecker());
			this.ignoredRoles = ignoredRoles;
		}
		List<Diff> getDiffs() {
			return ((ShadowEqualsChecker) checker).differences;
		}
		@Override
		protected boolean fail(CtRole role, Object element, Object other) {
			if (role == null) {
				this.isNotEqual = false;
				return false;
			}
			if (ignoredRoles.contains(role)) {
				this.isNotEqual = false;
				return false;
			}
			if (element instanceof CtEnumValue && role == CtRole.VALUE) {
				//CtStatementImpl.InsertType.BEFORE contains a value with nested type. Java reflection doesn't support that
				this.isNotEqual = false;
				return false;
			}

			CtElement parentOfOther = stack.peek();
			try {
				differences.add("Difference on path: " + pathBuilder.fromElement(parentOfOther, rootOfOther).toString() + "#" + role.getCamelCaseName()
				+ "\nShadow: " + String.valueOf(other)
				+ "\nNormal: " + String.valueOf(element) + "\n");
			} catch (CtPathException e) {
				throw new SpoonException(e);
			}
			return false;
		}
		@Override
		public void biScan(CtRole role, CtElement element, CtElement other) {
			if (element instanceof CtParameter) {
				CtParameter param = (CtParameter) element;
				CtParameter otherParam = (CtParameter) other;
				if (otherParam.getSimpleName().startsWith("arg")) {
					otherParam.setSimpleName(param.getSimpleName());
				}
				if (param.isFinal()) {
					//modifier final of parameters isn't accessible in runtime
					otherParam.addModifier(ModifierKind.FINAL);
				}
			}
			if (element instanceof CtAnnotation) {
				CtAnnotation myAnnotation = (CtAnnotation) element;
				if (myAnnotation.getAnnotationType().getQualifiedName().equals(Override.class.getName())) {
					return;
				}
				if (myAnnotation.getAnnotationType().getQualifiedName().equals(Root.class.getName())) {
					return;
				}
				//if (myAnnotation.getAnnotationType().getQualifiedName().equals(Serial.class.getName())) {
					//return;
				//}
				if (myAnnotation.getAnnotationType().getQualifiedName().equals(Nullable.class.getName())) {
					return;
				}
			}
			if (role == CtRole.SUPER_TYPE && other == null && element != null && ((CtTypeReference<?>) element).getQualifiedName().equals(Object.class.getName())) {
				//class X<T extends Object> cannot be distinguished in runtime from X<T>
				return;
			}
			super.biScan(role, element, other);
		}
		@Override
		protected void biScan(CtRole role, Collection<? extends CtElement> elements, Collection<? extends CtElement> others) {
			if (role == CtRole.TYPE_MEMBER) {
				//sort type members so they match together
				Map<String, CtTypeMember> elementsByName = groupTypeMembersBySignature((Collection) elements);
				Map<String, CtTypeMember> othersByName = groupTypeMembersBySignature((Collection) others);
				for (Map.Entry<String, CtTypeMember> e : elementsByName.entrySet()) {
					String name = e.getKey();
					CtTypeMember other = othersByName.remove(name);
					if (other == null) {
						if (e.getValue().isImplicit()) {
							//it is OK, that implicit elements are not available in runtime
							continue;
						}
						differences.add("Missing shadow typeMember: " + name);
					}
					biScan(role, e.getValue(), other);
				}
				for (Map.Entry<String, CtTypeMember> e : othersByName.entrySet()) {
					differences.add("Unexpected shadow typeMember: " + e.getKey());
				}
				return;
			}
			if (role == CtRole.ANNOTATION) {
				//remove all RetentionPolicy#SOURCE level annotations from elements
				List<CtAnnotation<?>> fileteredElements = ((List<CtAnnotation<?>>) elements).stream().filter(a -> {
					CtTypeReference<?> at = a.getAnnotationType();
					Class ac = at.getActualClass();
					return ac != Override.class && ac != SuppressWarnings.class && ac != Root.class
						   /*&& ac != Serial.class*/ && ac != Nullable.class;
				}).collect(Collectors.toList());
				super.biScan(role, fileteredElements, others);
				return;
			}
			super.biScan(role, elements, others);
		}
		public List<String> checkDiffs(CtType<?> type, CtType<?> shadowType) {
			differences = new ArrayList<>();
			rootOfOther = shadowType;
			biScan(null, type, shadowType);
			for (Diff diff : getDiffs()) {
				try {
					CtElement parentOf;
					CtElement rootOf;
					if (diff.other != null) {
						parentOf = diff.other.getParent();
						rootOf = rootOfOther;
					} else {
						parentOf = diff.element.getParent();
						rootOf = type;
					}
					differences.add("Diff on path: " + pathBuilder.fromElement(rootOf, parentOf).toString() + "#"
					+ diff.roles.stream().map(CtRole::getCamelCaseName).collect(Collectors.joining(", ", "[", "]"))
					+ "\nShadow: " + String.valueOf(diff.other)
					+ "\nNormal: " + String.valueOf(diff.element) + "\n");
				} catch (CtPathException e) {
					throw new SpoonException(e);
				}

			}
			return differences;
		}
	}

	private static Map<String, CtTypeMember> groupTypeMembersBySignature(Collection<CtTypeMember> typeMembers) {
		Map<String, CtTypeMember> typeMembersByName = new HashMap<>();
		for (CtTypeMember tm : typeMembers) {
			String name;
			if (tm instanceof CtExecutable) {
				CtExecutable<?> exec = ((CtExecutable) tm);
				name = exec.getSignature();
			} else {
				name = tm.getSimpleName();
			}
			CtTypeMember conflictTM = typeMembersByName.put(name, tm);
			if (conflictTM != null) {
				throw new SpoonException("There are two type members with name: " + name + " in " + tm.getParent(CtType.class).getQualifiedName());
			}
		}
		return typeMembersByName;
	}

	@Test
	public void testSuperInterfaceActualTypeArgumentsByJavaReflectionTreeBuilder() {
		final CtType<CtConditionalImpl> aType = new JavaReflectionTreeBuilder(createFactory()).scan(CtConditionalImpl.class);
		CtTypeReference<?> ifaceRef = aType.getSuperInterfaces().iterator().next();
		assertEquals(CtConditional.class.getName(), ifaceRef.getQualifiedName());
		assertEquals(1, ifaceRef.getActualTypeArguments().size());
		CtTypeReference<?> typeArg = ifaceRef.getActualTypeArguments().get(0);
		assertEquals("T", typeArg.getSimpleName());
		assertTrue(typeArg instanceof CtTypeParameterReference);
	}

	@Test
	public void testSuperInterfaceActualTypeArgumentsByCtTypeReferenceImpl() {
		TypeFactory typeFactory = createFactory().Type();
		CtTypeReference<?> aTypeRef = typeFactory.createReference(CtConditionalImpl.class);
		CtTypeReference<?> ifaceRef = aTypeRef.getSuperInterfaces().iterator().next();
		assertEquals(CtConditional.class.getName(), ifaceRef.getQualifiedName());
		assertEquals(1, ifaceRef.getActualTypeArguments().size());
		CtTypeReference<?> typeArg = ifaceRef.getActualTypeArguments().get(0);
		assertEquals("T", typeArg.getSimpleName());
		assertTrue(typeArg instanceof CtTypeParameterReference);
	}

	@Test
	public void testSuperInterfaceCorrectActualTypeArgumentsByCtTypeReferenceImpl() {
		TypeFactory typeFactory = createFactory().Type();
		CtTypeReference<?> aTypeRef = typeFactory.createReference(CtField.class);
		CtType aType = aTypeRef.getTypeDeclaration();
		for (CtTypeReference<?> ifaceRef : aType.getSuperInterfaces()) {
			for (CtTypeReference<?> actTypeRef : ifaceRef.getActualTypeArguments()) {
				if (actTypeRef instanceof CtTypeParameterReference) {
					//contract: the type parameters of super interfaces are using correct parameters from owner type
					CtTypeParameterReference actTypeParamRef = (CtTypeParameterReference) actTypeRef;
					CtTypeParameter typeParam = actTypeParamRef.getDeclaration();
					assertNotNull(typeParam);
					assertSame(aType, typeParam.getTypeParameterDeclarer());
				}
			}
		}
	}

	@Test
	public void testSuperInterfaceQName() {
		//contract: the qualified names of super interfaces are correct
		TypeFactory typeFactory = createFactory().Type();
		CtTypeReference<?> aTypeRef = typeFactory.createReference(CtExpression.class);
		CtType aType = aTypeRef.getTypeDeclaration();
		for (CtTypeReference<?> ifaceRef : aType.getSuperInterfaces()) {
			assertNotNull(ifaceRef.getActualClass(), ifaceRef.getQualifiedName() + " doesn't exist?");
			assertSame(aType, ifaceRef.getParent());
		}
		for (CtTypeReference<?> ifaceRef : aTypeRef.getSuperInterfaces()) {
			assertNotNull(ifaceRef.getActualClass(), ifaceRef.getQualifiedName() + " doesn't exist?");
			assertSame(aType, ifaceRef.getParent());
		}
	}

	@Test
	public void testSuperClass() {
		//contract: the super class has actual type arguments
		TypeFactory typeFactory = createFactory().Type();
		CtTypeReference<?> aTypeRef = typeFactory.createReference(CtEnumValueImpl.class);
		CtType aType = aTypeRef.getTypeDeclaration();
		CtTypeReference<?> superClass = aType.getSuperclass();
		assertEquals(CtFieldImpl.class.getName(), superClass.getQualifiedName());
		assertSame(aType, superClass.getParent());
		assertEquals(1, superClass.getActualTypeArguments().size());
		CtTypeParameterReference paramRef = (CtTypeParameterReference) superClass.getActualTypeArguments().get(0);
		assertSame(aType.getFormalCtTypeParameters().get(0), paramRef.getDeclaration());
	}

	@Test
	public void testSuperOfActualTypeArgumentsOfReturnTypeOfMethod() {

		Consumer<CtType<?>> checker = type -> {
			{
				CtMethod method = type.getMethodsByName("setAssignment").get(0);
				CtTypeReference<?> paramType = ((CtParameter<?>) method.getParameters().get(0)).getType();
				assertEquals(CtExpression.class.getName(), paramType.getQualifiedName());
				assertEquals(1, paramType.getActualTypeArguments().size());
				CtTypeParameterReference actTypeArgOfReturnType = (CtTypeParameterReference) paramType.getActualTypeArguments().get(0);
				assertEquals("A", actTypeArgOfReturnType.getSimpleName());
				CtTypeReference<?> boundType = actTypeArgOfReturnType.getBoundingType();
				//is it really correct to have bounding type T?
				//There should be NO bounding type - may be a special AST node?
				//Even the Object as bounding type here is probably not correct.
				assertEquals("T", boundType.getSimpleName());
				assertTrue(boundType instanceof CtTypeParameterReference);
			}
			{
				CtMethod method = type.getMethodsByName("getAssignment").get(0);
				CtTypeReference<?> returnType = method.getType();
				assertEquals(CtExpression.class.getName(), returnType.getQualifiedName());
				assertEquals(1, returnType.getActualTypeArguments().size());
				CtTypeParameterReference actTypeArgOfReturnType = (CtTypeParameterReference) returnType.getActualTypeArguments().get(0);
				assertEquals("A", actTypeArgOfReturnType.getSimpleName());
				CtTypeReference<?> boundType = actTypeArgOfReturnType.getBoundingType();
				//is it really correct to have bounding type T?
				//There should be NO bounding type - may be a special AST node?
				//Even the Object as bounding type here is probably not correct.
				assertEquals("T", boundType.getSimpleName());
				assertTrue(boundType instanceof CtTypeParameterReference);
			}
		};
		//try the check using CtType build from sources
		final Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.addInputResource(new FileSystemFile(new File("./src/main/java/spoon/support/reflect/code/CtAssignmentImpl.java")));
		launcher.buildModel();
		CtClass<?> classFromSources = launcher.getFactory().Class().get(CtAssignmentImpl.class.getName());
		assertFalse(classFromSources.isShadow());
		checker.accept(classFromSources);

		//try the same check using CtType build using reflection
		CtType<?> classFromReflection = createFactory().Class().get(CtAssignmentImpl.class);
		assertTrue(classFromReflection.isShadow());
		checker.accept(classFromReflection);
	}

	@Test
	public void testTypeParameterCtConditionnal() {
		// contract: when using MyClass<T> T should not have Object as superclass in shadow class

		Factory factory = createFactory();
		CtTypeReference typeReference = factory.Type().createReference(CtConditionalImpl.class);
		CtType shadowType = typeReference.getTypeDeclaration();

		assertEquals(1, shadowType.getFormalCtTypeParameters().size());
		CtTypeParameter typeParameter = shadowType.getFormalCtTypeParameters().get(0);

		assertEquals("T", typeParameter.getSimpleName());
		assertNull(typeParameter.getSuperclass());
	}

	@Test
	public void testPartialShadow() {
		// contract: the shadow class can be partially created
		Factory factory = createFactory();
		CtType<Object> type = factory.Type().get(ProjectableQuery.class);
		assertEquals("ProjectableQuery", type.getSimpleName());
		// because one of the parameter is not in the classpath therefore the reflection did not succeed to list the methods
		assertEquals(0, type.getMethods().size());
	}

	@Test
	public void testInnerClassWithConstructorParameterAnnotated() {
		Launcher launcher = new Launcher();
		launcher.addInputResource(URLDecoder.decode(JavaReflectionTreeBuilderTest.class
				.getClassLoader()
				.getResource("annotated-parameter-on-nested-class-constructor/Caller.java")
				.getPath(), 
				StandardCharsets.UTF_8));
		launcher.getEnvironment().setSourceClasspath(
				new String[]{
						"src/test/resources"
				});
		launcher.getEnvironment().setAutoImports(true);
		//contract: No error due to runtime annotation of a parameter of a constructor of a shadow nested class
		launcher.buildModel();
		Factory factory = launcher.getFactory();
		CtType caller = factory.Type().get("Caller");
		CtParameter annotatedParameter = ((CtParameter)
				((CtConstructor)
					((CtLocalVariable)
						((CtConstructor)
								caller.getTypeMembers().get(0)
						).getBody().getStatement(2)
					).getType().getTypeDeclaration().getTypeMembers().get(0)
				).getParameters().get(0));

		//contract: the annotation is correctly read
		assertEquals("Bidon", annotatedParameter.getAnnotations().get(0).getAnnotationType().getSimpleName());
	}

	@Test
	public void testInnerClassOfSourceCodeClass() {
		// contract: JavaReflectionTreeBuilder does not rescan the type if source information is available
		Launcher launcher = new Launcher();
		launcher.addInputResource("src/test/java/spoon/support/visitor/java/JavaReflectionTreeBuilderTest.java");
		launcher.buildModel();
		CtType ctType = launcher.getFactory().Type().get(Diff.class);
		assertEquals("Diff", ctType.getSimpleName());
		assertEquals(false, ctType.isAnonymous());
		assertEquals(false, ctType.isShadow());

		Class<?> klass = ctType.getActualClass();
		assertEquals("spoon.support.visitor.java.JavaReflectionTreeBuilderTest$Diff", klass.getName());
		assertEquals(false, klass.isAnonymousClass());

		CtType<?> ctClass = new JavaReflectionTreeBuilder(launcher.getFactory()).scan(klass);
		assertEquals("Diff", ctClass.getSimpleName());
		assertEquals(false, ctClass.isAnonymous());
		assertEquals(false, ctClass.isShadow());
		assertEquals("element", ctClass.getFields().toArray(new CtField[0])[0].getSimpleName());
	}

	@Test
	public void testPurelyReflectiveInnerClass() {
		// contract: JavaReflectionTreeBuilder works for named nested classes
		Launcher launcher = new Launcher();
		// No resources, as we only want to check the reflection tree builder
		launcher.buildModel();
		CtType ctType = launcher.getFactory().Type().get(Diff.class);
		assertEquals("Diff", ctType.getSimpleName());
		assertEquals(false, ctType.isAnonymous());
		assertEquals(true, ctType.isShadow());

		Class<?> klass = ctType.getActualClass();
		assertEquals("spoon.support.visitor.java.JavaReflectionTreeBuilderTest$Diff", klass.getName());
		assertEquals(false, klass.isAnonymousClass());

		CtType<?> ctClass = new JavaReflectionTreeBuilder(launcher.getFactory()).scan(klass);
		assertEquals("Diff", ctClass.getSimpleName());
		assertEquals(false, ctClass.isAnonymous());
		assertEquals(true, ctClass.isShadow());
		Set<String> moduleNames1 = ctClass.getFields().stream()
				.map(CtField::getSimpleName).collect(Collectors.toSet());

		assertEquals(moduleNames1, Set.of("element", "other", "roles"));
	}


	@Test
	public void testAnonymousClass() {
		// contract: JavaReflectionTreeBuilder works on anonymous classes

		// the test object: an anonymous class
		Object o = new Object() {
			void foo() {

			}
		};
		Launcher launcher = new Launcher();
		launcher.addInputResource("src/test/java/spoon/support/visitor/java/JavaReflectionTreeBuilderTest.java");
		launcher.buildModel();
		CtType ctType = launcher.getModel().getElements(new TypeFilter<CtType>(CtType.class) {
			@Override
			public boolean matches(CtType element) {
				return super.matches(element) && element.isAnonymous();
			}
		}).get(0);
		assertEquals("1", ctType.getSimpleName());
		assertEquals(true, ctType.isAnonymous());
		assertEquals(false, ctType.isShadow());

		Class<?> klass = ctType.getActualClass();
		assertEquals("spoon.support.visitor.java.JavaReflectionTreeBuilderTest$1", klass.getName());
		assertEquals(true, klass.isAnonymousClass());

		CtType<?> ctClass = new JavaReflectionTreeBuilder(launcher.getFactory()).scan(klass);
		assertEquals("", ctClass.getSimpleName());
		assertEquals(true, ctClass.isAnonymous());
		assertEquals(true, ctClass.isShadow());
		assertEquals("foo", ctClass.getMethods().toArray(new CtMethod[0])[0].getSimpleName());
	}

	@Test
	public void testCannotGetDefaultExpressionBecauseOfException() {
		/*
		 * contract:
		 *    JavaReflectionTreeBuilder can't set defaultExpression for the field (public static primitive),
		 *    as Reflection API throws the exception ExceptionInInitializerError when attempting to get it.
		 *    {@link JavaReflectionTreeBuilder#visitField(Filed)} ignores this exception.
		 */
		CtType<?> ctType = new JavaReflectionTreeBuilder(createFactory()).scan(spoon.support.visitor.java.testclasses.NPEInStaticInit.class);

		CtField<?> value = ctType.getField("VALUE");
		// should have gotten '1'
		assertNull(value.getDefaultExpression());

		/*
		 * contract:
		 *    JavaReflectionTreeBuilder can't set defaultExpression for the field (public static primitive),
		 *    as Reflection API throws the exception UnsatisfiedLinkError when attempting to get it.
		 *    {@link JavaReflectionTreeBuilder#visitField(Filed)} ignores this exception.
		 */
		ctType = new JavaReflectionTreeBuilder(createFactory()).scan(spoon.support.visitor.java.testclasses.UnsatisfiedLinkErrorInStaticInit.class);

		value = ctType.getField("VALUE");
		// should have gotten '1'
		assertNull(value.getDefaultExpression());
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_16)
	public void testShadowRecords() throws ClassNotFoundException {
		// contract: records are shadowable.
		Factory factory = 	createFactory();
		// we need to do this because this a jdk16+ class
		Class<?> unixDomainPrincipal = Class.forName("jdk.net.UnixDomainPrincipal");
		CtType<?> type = factory.Type().get(unixDomainPrincipal);
		assertNotNull(type);
		CtRecord unixRecord = (CtRecord) type;
		assertTrue(unixRecord.isShadow());
		// UserPrincipal user and GroupPrincipal group
		assertEquals(2, unixRecord.getRecordComponents().size());
	}


	@Test
	void testShadowPackage() {
		// contract: elements of a package with a corresponding CtElement implementation
		// are visited and built into the model
		Factory factory = createFactory();
		CtType<?> type = new JavaReflectionTreeBuilder(factory).scan(PackageTest.class);
		CtPackage ctPackage = type.getPackage();
		assertEquals(1, ctPackage.getAnnotations().size());
		assertEquals(ctPackage.getAnnotations().get(0).getAnnotationType().getQualifiedName(), "java.lang.Deprecated");
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_17)
	void testShadowSealedTypes() throws ClassNotFoundException {
		// contract: sealed/non-sealed types are in the shadow model
		Factory factory = createFactory();
		// load a few ConstantDesc types
		Class<?> constantDesc = Class.forName("java.lang.constant.ConstantDesc"); // since Java 12, sealed since Java 17
		Class<?> dynamicConstantDesc = Class.forName("java.lang.constant.DynamicConstantDesc"); // since Java 12
		Class<?> enumDesc = Class.forName("java.lang.Enum$EnumDesc"); // since Java 12
		CtInterface<?> ctConstantDesc = (CtInterface<?>) factory.Type().get(constantDesc);
		CtType<?> ctDynamicConstantDesc = factory.Type().get(dynamicConstantDesc);
		CtType<?> ctEnumDesc = factory.Type().get(enumDesc);
		CtType<?> ctString = factory.Type().get(String.class);

		// make sure they are loaded correctly
		assertNotNull(ctConstantDesc);
		assertNotNull(ctDynamicConstantDesc);
		assertNotNull(ctEnumDesc);
		assertNotNull(ctString);

		// ConstDesc is sealed
		assertThat(ctConstantDesc.getExtendedModifiers(), hasItem(CtExtendedModifier.explicit(ModifierKind.SEALED)));
		// DynamicConstDesc and String are permitted types
		assertThat(ctConstantDesc.getPermittedTypes(), hasItems(ctDynamicConstantDesc.getReference(), ctString.getReference()));
		// EnumDesc extends DynamicConstantDesc, so it should not be added to the permitted types of ConstantDesc
		assertThat(ctConstantDesc.getPermittedTypes(), not(hasItem(ctEnumDesc.getReference())));
		// DynamicConstDesc is non-sealed
		assertThat(ctDynamicConstantDesc.getExtendedModifiers(), hasItem(CtExtendedModifier.explicit(ModifierKind.NON_SEALED)));
		// EnumDesc extends DynamicConstDesc which is non-sealed, so it is not non-sealed itself
		assertThat(ctEnumDesc.getModifiers(), not(hasItem(ModifierKind.NON_SEALED)));
		// String is final and not sealed, so neither sealed nor non-sealed should be applied
		assertThat(ctString.getModifiers(), not(hasItems(ModifierKind.SEALED, ModifierKind.NON_SEALED)));
	}

	@Test
	void testCyclicAnnotationScanning() {
		// contract: scanning annotations does not cause StackOverflowError
		// due to recursive package -> annotation -> package -> annotation scanning
		Factory factory = createFactory();
		// a simple cycle: package a -> annotation a.A -> package a
		assertDoesNotThrow(() -> new JavaReflectionTreeBuilder(factory).scan(Cyclic.class));
		// an indirect cycle: package a -> annotation b.B -> package b -> annotation a.A -> package a
		assertDoesNotThrow(() -> new JavaReflectionTreeBuilder(factory).scan(Indirect.class));
		// an independent starting point, causing Cyclic and Indirect to be visited too
		assertDoesNotThrow(() -> new JavaReflectionTreeBuilder(factory).scan(Outside.class));
	}

	@Test
	void testInnerClassesConstructorParameters() {
		// contract: inner classes have exactly one parameter for the immediately enclosing instance
		Factory factory = createFactory();

		CtType<InnerClasses> scan = new JavaReflectionTreeBuilder(factory).scan(InnerClasses.class);
		List<String> inners = List.of("A", "B", "C", "D", "E", "F");
		CtType<?> current = scan;
		for (String inner : inners) {
			current = current.getNestedType(inner);
		}
		assertThat(current, instanceOf(CtClass.class));
		CtClass<?> asClass = (CtClass<?>) current;
		assertThat(asClass.getConstructors().size(), equalTo(1));
		assertThat(asClass.getConstructors().iterator().next().getParameters().size(), equalTo(inners.size()));
	}

	@Test
	@GitHubIssue(issueNumber = 4972, fixed = true)
	void parameterNamesAreParsedWhenCompilingWithParametersFlag() throws ClassNotFoundException {
		ClassLoader loader = JavacFacade.compileFiles(
			Map.of(
				"Test",
				"class Test {\n"
					+ "  public void foo(String bar) {}\n" +
					"}\n"
			),
			List.of("-parameters")
		);
		CtType<?> test = new JavaReflectionTreeBuilder(createFactory()).scan(loader.loadClass("Test"));
		CtMethod<?> method = test.getMethodsByName("foo").get(0);
		CtParameter<?> parameter = method.getParameters().get(0);

		assertThat(parameter.getSimpleName(), is("bar"));
	}

	@Test
	void testStaticInnerClassConstructorWithEnclosingClassArgument() throws ClassNotFoundException {
		// contract: Static inner classes can take explicit arguments of the enclosing type
		ClassLoader loader = JavacFacade.compileFiles(
			Map.of(
				"Outer",
				"class Outer {\n"
					+ "  static class Inner { public Inner(Outer outer) {} } \n" +
					"}\n"
			),
			List.of()
		);
		Class<?> inner = loader.loadClass("Outer$Inner");
		CtClass<?> ctInner = (CtClass<?>) new JavaReflectionTreeBuilder(createFactory()).scan(inner);

		assertEquals(1, inner.getConstructors().length);
		assertEquals(1, inner.getConstructors()[0].getParameterCount());

		assertThat(ctInner.getConstructors(), hasSize(1));
		assertThat(
			ctInner.getConstructors().iterator().next().getParameters(),
			hasSize(1)
		);
	}

	@Test
	void testNonStaticInnerClassConstructorWithEnclosingClassArgument() throws ClassNotFoundException {
		// contract: Non-static inner classes have one implicit argument of the enclosing type
		ClassLoader loader = JavacFacade.compileFiles(
			Map.of(
				"Outer",
				"class Outer {\n"
					+ "  class Inner { public Inner(Outer outer) {} } \n" +
					"}\n"
			),
			List.of()
		);
		Class<?> inner = loader.loadClass("Outer$Inner");
		CtClass<?> ctInner = (CtClass<?>) new JavaReflectionTreeBuilder(createFactory()).scan(inner);

		assertEquals(1, inner.getConstructors().length);
		assertEquals(2, inner.getConstructors()[0].getParameterCount());

		assertThat(ctInner.getConstructors(), hasSize(1));
		assertThat(
			ctInner.getConstructors().iterator().next().getParameters(),
			hasSize(1)
		);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"class Victim {}",
		"enum Victim {;}",
		"interface Victim {}",
		"@interface Victim {}"
	})
	void testInnerClassesAreNotAddedToPackage(String collider) throws ClassNotFoundException {
		// contract: Inner classes are not added to their package
		ClassLoader loader = JavacFacade.compileFiles(
			Map.of(
				"First.java",
				"class First {\n"
					+ collider +
					"}\n",
				"Victim.java",
				"class Victim {\n" +
					"  class Inner {\n" +
					"    int bar;\n" +
					"  }\n" +
					"}\n"
			),
			List.of()
		);
		Factory factory = createFactory();
		// Load the victim
		factory.Type().get(loader.loadClass("Victim"));
		// Let it get replaced by First$Collider
		factory.Type().get(loader.loadClass("First"));

		// This will throw if the replacement was successful
		CtType<?> victim = assertDoesNotThrow(() -> factory.Type().get(loader.loadClass("Victim$Inner")));

		// Make sure we got the right class, but this should be fine now in any case
		assertNotNull(victim.getField("bar"));
		assertNull(victim.getField("foo"));
	}

	@Test
	void test() throws ClassNotFoundException {
		// contract: Infinity, -Infinity, NaN are not literals
		ClassLoader loader = JavacFacade.compileFiles(
			Map.of(
				"SpecialValues.java",
				"public class SpecialValues {\n" +
				"  public static final double d_inf = 1.0d / 0.0d;\n" +
				"  public static final double d_m_inf = -1.0d / 0.0d;\n" +
				"  public static final double d_nan = 0.0d / 0.0d;\n" +
				"  public static final float f_inf = 1.0f / 0.0f;\n" +
				"  public static final float f_m_inf = -1.0f / 0.0f;\n" +
				"  public static final float f_nan = 0.0f / 0.0f;\n" +
				"}\n"
			),
			List.of()
		);

		Factory factory = createFactory();
		// Load the class
		CtType<?> specialValues = factory.Type().get(loader.loadClass("SpecialValues"));
		for (CtField<?> field : specialValues.getFields()) {
			assertNotNull(field.getDefaultExpression());
			assertFalse(field.getDefaultExpression() instanceof CtLiteral<?>, "special value cannot be represented by literal");
		}

	}

}
