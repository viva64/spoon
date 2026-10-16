package spoon.test.util;

import spoon.SpoonException;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Test helpers which load classes of the model by name. Spoon itself does not load classes any more,
 * but some tests check the model against the runtime classes.
 */
public final class RuntimeAccess {

	private RuntimeAccess() {
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> actualClass(CtTypeReference<T> reference) {
		if (reference instanceof CtArrayTypeReference) {
			Class<?> component = actualClass(((CtArrayTypeReference<?>) reference).getComponentType());
			return (Class<T>) Array.newInstance(component, 0).getClass();
		}
		if (reference.getQualifiedName().endsWith("[]")) {
			String name = reference.getQualifiedName();
			Class<?> component = actualClass(reference.getFactory().Type().createReference(name.substring(0, name.length() - 2)));
			return (Class<T>) Array.newInstance(component, 0).getClass();
		}
		switch (reference.getQualifiedName()) {
			case "boolean":
				return (Class<T>) boolean.class;
			case "byte":
				return (Class<T>) byte.class;
			case "char":
				return (Class<T>) char.class;
			case "short":
				return (Class<T>) short.class;
			case "int":
				return (Class<T>) int.class;
			case "long":
				return (Class<T>) long.class;
			case "float":
				return (Class<T>) float.class;
			case "double":
				return (Class<T>) double.class;
			case "void":
				return (Class<T>) void.class;
			default:
				break;
		}
		try {
			return (Class<T>) Class.forName(reference.getQualifiedName(), false, loader(reference));
		} catch (ClassNotFoundException | LinkageError e) {
			throw new spoon.support.SpoonClassNotFoundException("cannot load class: " + reference.getQualifiedName(), e);
		}
	}

	public static <T> Class<T> actualClass(CtType<T> type) {
		return actualClass(type.getReference());
	}

	private static ClassLoader loader(CtElement element) {
		try {
			String[] classpath = element.getFactory().getEnvironment().getSourceClasspath();
			if (classpath == null || classpath.length == 0) {
				return Thread.currentThread().getContextClassLoader();
			}
			java.net.URL[] urls = new java.net.URL[classpath.length];
			for (int i = 0; i < classpath.length; i++) {
				urls[i] = new java.io.File(classpath[i]).toURI().toURL();
			}
			return new java.net.URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
		} catch (java.net.MalformedURLException e) {
			throw new SpoonException(e);
		}
	}

	public static Method actualMethod(CtExecutableReference<?> reference) {
		List<CtTypeReference<?>> parameters = reference.getParameters();
		CtTypeReference<?> declaringType = reference.getDeclaringType();
		if (declaringType == null) {
			return null;
		}
		methods:
		for (Method m : actualClass(declaringType).getDeclaredMethods()) {
			if (!m.getDeclaringClass().isSynthetic() && m.isSynthetic()) {
				continue;
			}
			if (!m.getName().equals(reference.getSimpleName()) || m.getParameterTypes().length != parameters.size()) {
				continue;
			}
			for (int i = 0; i < parameters.size(); i++) {
				if (m.getParameterTypes()[i] != actualClass(parameters.get(i))) {
					continue methods;
				}
			}
			return m;
		}
		return null;
	}

	public static Constructor<?> actualConstructor(CtExecutableReference<?> reference) {
		List<CtTypeReference<?>> parameters = reference.getParameters();
		CtTypeReference<?> declaringType = reference.getDeclaringType();
		if (declaringType == null) {
			return null;
		}
		constructors:
		for (Constructor<?> c : actualClass(declaringType).getDeclaredConstructors()) {
			if (c.getParameterTypes().length != parameters.size()) {
				continue;
			}
			for (int i = 0; i < parameters.size(); i++) {
				if (c.getParameterTypes()[i] != actualClass(parameters.get(i))) {
					continue constructors;
				}
			}
			return c;
		}
		return null;
	}

	public static Member actualField(CtFieldReference<?> reference) {
		CtTypeReference<?> declaringType = reference.getDeclaringType();
		if (declaringType == null) {
			throw new SpoonException("Declaring type of field " + reference.getSimpleName() + " isn't defined");
		}
		Class<?> clazz = actualClass(declaringType);
		try {
			return clazz.getDeclaredField(reference.getSimpleName());
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public static <A extends Annotation> A actualAnnotation(CtAnnotation<A> annotation) {
		Class<A> type = (Class<A>) actualClass(annotation.getAnnotationType());
		InvocationHandler handler = (proxy, method, args) -> {
			String name = method.getName();
			if ("toString".equals(name)) {
				return annotation.toString();
			} else if ("annotationType".equals(name)) {
				return type;
			}
			return annotation.getValueAsObject(name);
		};
		return (A) Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, handler);
	}

	/** the former CtElement#getAnnotation(Class) */
	public static <A extends Annotation> A annotation(CtElement element, Class<A> annotationType) {
		for (CtAnnotation<? extends Annotation> a : element.getAnnotations()) {
			if (a.getAnnotationType().getQualifiedName().equals(annotationType.getName())) {
				return actualAnnotation((CtAnnotation<A>) a);
			}
		}
		return null;
	}
}
