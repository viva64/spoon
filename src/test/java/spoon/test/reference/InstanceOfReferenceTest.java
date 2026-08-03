package spoon.test.reference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtTypePattern;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.filter.VariableReferenceFunction;
import spoon.testing.utils.BySimpleName;
import spoon.testing.utils.GitHubIssue;
import spoon.testing.utils.ModelTest;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static spoon.test.SpoonTestHelpers.createModelFromString;
import static spoon.testing.assertions.SpoonAssertions.assertThat;
import java.util.stream.Collectors;

/**
 * Tests that references to pattern variables declared using the <code>instanceof</code> operator can be resolved.
 * Pattern matching for instanceof was introduced in Java 16, cf. <a href=https://openjdk.java.net/jeps/394>JEP 394</a>.
 * Variables declared in pattern matches have <a href="https://openjdk.org/projects/amber/design-notes/patterns/pattern-match-semantics">flow scope semantics</a>.
 */
public class InstanceOfReferenceTest {
	@Test
	public void testVariableDeclaredInIf() {
		String code = ("class X {\n"
		               + "    String typePattern(Object obj) {\n"
		               + "        boolean someCondition = true;\n"
		               + "        if (someCondition && obj instanceof String s) {\n"
		               + "            return s;\n"
		               + "        }\n"
		               + "        return \"\";\n"
		               + "    }\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(1);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testVariableDeclaredInWhileLoop() {
		String code = ("class X {\n"
		               + "	public void processShapes(List<Object> shapes) {\n"
		               + "		var iter = 0;\n"
		               + "		while (iter < shapes.size() && shapes.get(iter) instanceof String shape) {\n"
		               + "			iter++;\n"
		               + "			System.out.println(shape);\n"
		               + "		}\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(3);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testVariableDeclaredInForLoop() {
		String code = ("class X {\n"
		               + "	public void processShapes(List<Object> shapes) {\n"
		               + "		for (var iter = 0; iter < shapes.size() && shapes.get(iter) instanceof String shape; iter++) {\n"
		               + "			System.out.println(shape);\n"
		               + "		}\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(3);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testDeclaredVariableUsedInSameCondition() {
		String code = ("class X {\n"
		               + "	public void processShapes(Object obj) {\n"
		               + "		if (obj instanceof String s && s.length() > 5) {\n"
		               + "			// NOP\n"
		               + "		}\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(0);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testDeclaredVariableUsedInSameCondition2() {
		String code = ("class X {\n"
		               + "	public void hasRightSize(Shape s) throws MyException {\n"
		               + "		return s instanceof Circle c && c.getRadius() > 10;\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(0);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testFlowScope() {
		String code = ("class X {\n"
		               + "	public void onlyForStrings(Object o) throws MyException {\n"
		               + "		if (!(o instanceof String s))\n"
		               + "			throw new MyException();\n"
		               + "		// s is in scope\n"
		               + "		System.out.println(s);\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(0);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testFlowScope2() {
		String code = ("class X {\n"
		               + "	String s = \"abc\";\n"
		               + "\n"
		               + "	public void method2(Object o) {\n"
		               + "		if (!(o instanceof String s)) {\n"
		               + "			System.out.println(\"not a string\");\n"
		               + "		} else {\n"
		               + "			System.out.println(s); // The local variable is in scope here!\n"
		               + "		}\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(0);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testFlowScope3() {
		String code = ("class X {\n"
		               + "	String typePattern(Object obj) {\n"
		               + "		if (obj instanceof String s) {\n"
		               + "			System.out.println(\"It's a string\");\n"
		               + "		} else {\n"
		               + "			throw new RuntimeException(\"It's not a string\");\n"
		               + "		}\n"
		               + "		return s; // We can still access s here!\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariableReference<?> ref = model.getElements(new TypeFilter<>(CtLocalVariableReference.class)).get(0);
		var decl = ref.getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@ModelTest(code = ("class Test {\n"
	                   + "	void typePattern(Object o) {\n"
	                   + "		if (o instanceof String i) {\n"
	                   + "			System.out.println(i);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		if (!(o instanceof String i)) {\n"
	                   + "		} else {\n"
	                   + "			System.out.println(i);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		if (!(o instanceof String i)) {\n"
	                   + "			throw new IllegalArgumentException();\n"
	                   + "		}\n"
	                   + "		System.out.println(i);\n"
	                   + "	}\n"
	                   + "}\n"), complianceLevel = 21)
	public void testFlowScope4(@BySimpleName("Test") CtClass<?> ctClass) {
		// contract: references to pattern variables hiding other variables with the same name are resolved correctly
		List<CtLocalVariable<?>> variables = ctClass.getElements(new TypeFilter<>(CtLocalVariable.class));
		List<CtLocalVariableReference<?>> references = ctClass.getElements(new TypeFilter<>(CtLocalVariableReference.class));

		assertThat(variables).hasSize(3);
		assertThat(variables).extracting(CtLocalVariable::getSimpleName).allMatch("i"::equals);

		assertThat(references).hasSize(3);
		assertThat(references).extracting(CtLocalVariableReference::getSimpleName).allMatch("i"::equals);

		assertThat(references.get(0)).hasExactlyPotentialDeclarations(variables.get(0));
		assertThat(references.get(1)).hasExactlyPotentialDeclarations(variables.get(1));
		assertThat(references.get(2)).hasExactlyPotentialDeclarations(variables.get(2));
	}

	@ModelTest(code = ("class Test {\n"
	                   + "	void typePattern(Object o) {\n"
	                   + "		do {\n"
	                   + "			if (!(o instanceof Integer i)) {\n"
	                   + "				continue;\n"
	                   + "			}\n"
	                   + "\n"
	                   + "			System.out.println(i);\n"
	                   + "		} while (!(o instanceof String i));\n"
	                   + "\n"
	                   + "		System.out.println(i);\n"
	                   + "	}\n"
	                   + "}\n"), complianceLevel = 21)
	public void testDoWhilePattern(@BySimpleName("Test") CtClass<?> ctClass) {
		// contract: references to pattern variables introduced in a do-while are resolved correctly
		List<CtLocalVariable<?>> variables = ctClass.getElements(new TypeFilter<>(CtLocalVariable.class));
		List<CtLocalVariableReference<?>> references = ctClass.getElements(new TypeFilter<>(CtLocalVariableReference.class));

		assertThat(variables).hasSize(2);
		assertThat(variables).extracting(CtLocalVariable::getSimpleName).allMatch("i"::equals);

		CtLocalVariable<?> doWhileVariable = variables.get(0);
		CtLocalVariable<?> ifVariable = variables.get(1);

		assertThat(doWhileVariable).getType().isEqualTo(String.class);
		assertThat(ifVariable).getType().isEqualTo(Integer.class);


		assertThat(references).hasSize(2);
		assertThat(references).extracting(CtLocalVariableReference::getSimpleName).allMatch("i"::equals);

		assertThat(references.get(0)).getType().isEqualTo(Integer.class);
		assertThat(references.get(0)).hasExactlyPotentialDeclarations(ifVariable, doWhileVariable);

		assertThat(references.get(1)).getType().isEqualTo(String.class);
		assertThat(references.get(1)).hasExactlyPotentialDeclarations(doWhileVariable);
	}

	@ModelTest(code = ("class Test {\n"
	                   + "	void typePattern(Object o) {\n"
	                   + "		while (!(o instanceof String i)) {\n"
	                   + "			if (!(o instanceof Integer i)) {\n"
	                   + "				continue;\n"
	                   + "			}\n"
	                   + "\n"
	                   + "			System.out.println(i);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		System.out.println(i);\n"
	                   + "	}\n"
	                   + "}\n"), complianceLevel = 21)
	public void testNegatedWhilePattern(@BySimpleName("Test") CtClass<?> ctClass) {
		// contract: a pattern variable is introduced by while (e) S iff it is introduced by e when false
		List<CtLocalVariable<?>> variables = ctClass.getElements(new TypeFilter<>(CtLocalVariable.class));
		List<CtLocalVariableReference<?>> references = ctClass.getElements(new TypeFilter<>(CtLocalVariableReference.class));

		assertThat(variables).hasSize(2);
		assertThat(variables).extracting(CtLocalVariable::getSimpleName).allMatch("i"::equals);

		CtLocalVariable<?> whileVariable = variables.get(0);
		CtLocalVariable<?> ifVariable = variables.get(1);

		assertThat(whileVariable).getType().isEqualTo(String.class);
		assertThat(ifVariable).getType().isEqualTo(Integer.class);


		assertThat(references).hasSize(2);
		assertThat(references).extracting(CtLocalVariableReference::getSimpleName).allMatch("i"::equals);

		assertThat(references.get(0)).getType().isEqualTo(Integer.class);
		assertThat(references.get(0)).hasExactlyPotentialDeclarations(ifVariable, whileVariable);

		assertThat(references.get(1)).getType().isEqualTo(String.class);
		assertThat(references.get(1)).hasExactlyPotentialDeclarations(whileVariable);
	}


	@ModelTest(code = ("class Test {\n"
	                   + "	String i = \"\";\n"
	                   + "	void typePattern(Object o) {\n"
	                   + "		label: while (!(o instanceof String i)) {\n"
	                   + "			if (!(o instanceof Integer i)) {\n"
	                   + "				break label;\n"
	                   + "			}\n"
	                   + "\n"
	                   + "			System.out.println(i);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		System.out.println(i);\n"
	                   + "	}\n"
	                   + "}\n"), complianceLevel = 21)
	public void testNegatedWhilePatternWithBreakLabel(@BySimpleName("Test") CtClass<?> ctClass) {
		// contract: a pattern variable is introduced by while (e) S iff it is introduced by e when false
		List<CtVariable<?>> variables = ctClass.getElements(new TypeFilter<>(CtVariable.class));
		List<CtVariableReference<?>> references = ctClass.getElements(new TypeFilter<>(CtVariableReference.class));

		assertThat(variables).hasSize(4);

		CtVariable<?> fieldVariable = variables.get(0);
		assertThat(fieldVariable).getType().isEqualTo(String.class);
		assertThat(fieldVariable).getSimpleName().isEqualTo("i");

		CtVariable<?> whileVariable = variables.get(2);
		assertThat(whileVariable).getSimpleName().isEqualTo("i");

		CtVariable<?> ifVariable = variables.get(3);
		assertThat(ifVariable).getSimpleName().isEqualTo("i");
		assertThat(ifVariable).getType().isEqualTo(Integer.class);

		assertThat(references).hasSize(6); // System.out are references to fields

		assertThat(references.get(0)).hasExactlyPotentialDeclarations(variables.get(1));
		assertThat(references.get(1)).hasExactlyPotentialDeclarations(variables.get(1));
		// skip the System.out reference
		assertThat(references.get(3)).hasExactlyPotentialDeclarations(ifVariable, fieldVariable);
		// skip the System.out reference

		// Because of the break target label the while pattern variable is not in scope in the last print statement:
		assertThat(references.get(5)).hasExactlyPotentialDeclarations(fieldVariable);
	}

	@ModelTest(code = ("class Test {\n"
	                   + "	String i = \"\";\n"
	                   + "\n"
	                   + "	void typePattern(Object o) {\n"
	                   + "		while (o instanceof String i) {\n"
	                   + "			System.out.println(i);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		System.out.println(i);\n"
	                   + "	}\n"
	                   + "}\n"), complianceLevel = 21)
	public void testMatchingWhilePattern(@BySimpleName("Test") CtClass<?> ctClass) {
		// contract: a pattern variable introduced by condition c in `while (c) S` when true is definitely matched at S.
		//           In simpler words: The variable `i` introduced by the instanceof pattern `o instanceof String i`, must
		//           be accessible by the associated statement/block of the while.
		List<CtLocalVariable<?>> variables = ctClass.getElements(new TypeFilter<>(CtLocalVariable.class));
		List<CtLocalVariableReference<?>> references = ctClass.getElements(new TypeFilter<>(CtLocalVariableReference.class));

		assertThat(variables).hasSize(1);
		assertThat(variables).extracting(CtLocalVariable::getSimpleName).allMatch("i"::equals);

		CtLocalVariable<?> whileVariable = variables.get(0);
		assertThat(whileVariable).getType().isEqualTo(String.class);


		assertThat(references).hasSize(1);
		assertThat(references).extracting(CtLocalVariableReference::getSimpleName).allMatch("i"::equals);

		assertThat(references.get(0)).hasExactlyPotentialDeclarations(whileVariable, ctClass.getField("i"));
	}

	@ModelTest(code = ("class Test {\n"
	                   + "	String s1 = \"\";\n"
	                   + "	String s2 = \"\";\n"
	                   + "\n"
	                   + "	void typePattern(Object a, Object b) {\n"
	                   + "		if (a instanceof String s1 && b instanceof String s2) {\n"
	                   + "			System.out.println(\"s1\" + s1 + \"s2\" + s2);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		if (!(a instanceof String s1) && b instanceof String s2) {\n"
	                   + "			System.out.println(\"s1\" + s1 + \"s2\" + s2);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		if (a instanceof String s1 && !(b instanceof String s2)) {\n"
	                   + "			System.out.println(\"s1\" + s1 + \"s2\" + s2);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		if (!(a instanceof String s1) && !(b instanceof String s2)) {\n"
	                   + "			System.out.println(\"s1\" + s1 + \"s2\" + s2);\n"
	                   + "		}\n"
	                   + "\n"
	                   + "		System.out.println(s1 + s2);\n"
	                   + "	}\n"
	                   + "}\n"), complianceLevel = 21)
	public void testBinaryOperatorAnd(@BySimpleName("Test") CtClass<?> ctClass) {
		// contract: references to pattern variables introduced by a && b are resolved correctly
		List<CtVariable<?>> variables = ctClass.getElements(new TypeFilter<>(CtVariable.class));
		List<CtVariableReference<?>> references = ctClass.getElements(new TypeFilter<>(CtVariableReference.class));

		assertThat(variables).hasSize(12);

		// The fields:
		assertThat(variables.get(0)).getSimpleName().isEqualTo("s1");
		assertThat(variables.get(1)).getSimpleName().isEqualTo("s2");

		var s1Field = variables.get(0);
		var s2Field = variables.get(1);

		// The parameters:
		assertThat(variables.get(2)).getSimpleName().isEqualTo("a");
		assertThat(variables.get(3)).getSimpleName().isEqualTo("b");

		var aParameter = variables.get(2);
		var bParameter = variables.get(3);

		// For the first if condition, the first reference will be to the parameters, then to the pattern variables:
		assertThat(references.get(0)).hasExactlyPotentialDeclarations(aParameter);
		assertThat(references.get(1)).hasExactlyPotentialDeclarations(bParameter);

		assertThat(references.get(3)).hasExactlyPotentialDeclarations(variables.get(4), s1Field);
		assertThat(references.get(4)).hasExactlyPotentialDeclarations(variables.get(5), s2Field);

		// The second if condition
		assertThat(references.get(5)).hasExactlyPotentialDeclarations(aParameter);
		assertThat(references.get(6)).hasExactlyPotentialDeclarations(bParameter);

		assertThat(references.get(8)).hasExactlyPotentialDeclarations(s1Field);
		assertThat(references.get(9)).hasExactlyPotentialDeclarations(variables.get(7), s2Field);

		// The third if condition
		assertThat(references.get(10)).hasExactlyPotentialDeclarations(aParameter);
		assertThat(references.get(11)).hasExactlyPotentialDeclarations(bParameter);

		assertThat(references.get(13)).hasExactlyPotentialDeclarations(variables.get(8), s1Field);
		assertThat(references.get(14)).hasExactlyPotentialDeclarations(s2Field);

		// The fourth if condition
		assertThat(references.get(15)).hasExactlyPotentialDeclarations(aParameter);
		assertThat(references.get(16)).hasExactlyPotentialDeclarations(bParameter);

		assertThat(references.get(18)).hasExactlyPotentialDeclarations(s1Field);
		assertThat(references.get(19)).hasExactlyPotentialDeclarations(s2Field);

		// The last print statement (the else) references the negated pattern variables:
		assertThat(references.get(21)).hasExactlyPotentialDeclarations(s1Field);
		assertThat(references.get(22)).hasExactlyPotentialDeclarations(s2Field);
	}


	private static Stream<Arguments> provideTestCasesForNegatedScoping() {
		return Stream.of(
			Arguments.of("o instanceof String s", List.of("s"), List.of()),
			Arguments.of("!(o instanceof String s)", List.of(), List.of("s")),
			Arguments.of("!(!(o instanceof String s))", List.of("s"), List.of()),
			Arguments.of("!(!(!(o instanceof String s)))", List.of(), List.of("s")),
			Arguments.of("o instanceof String s && s.length() > 5", List.of("s"), List.of()),
			Arguments.of("o instanceof String s || number > 5", List.of(), List.of()),
			Arguments.of("!(o instanceof String s) || s.length() > 5", List.of(), List.of("s")),
			Arguments.of("!(o instanceof String s1) || !(obj instanceof String s2)", List.of(), List.of("s1", "s2"))
		);
	}

	@ParameterizedTest
	@MethodSource("provideTestCasesForNegatedScoping")
	public void testNegatedInstanceofScoping(String condition, Collection<String> patternVarsInThen, Collection<String> patternVarsInElse) {
		// The code declares three variables that are both referenced in the then and else branch.
		//
		// If a pattern is defined, this will shadow the field where it is true.
		// The test then checks that the references resolve to either the pattern variable or the local variable.
		String code = String.format("class Test {\n"
		               + "	String s = \"abc\";\n"
		               + "	String s1 = \"def\";\n"
		               + "	String s2 = \"ghi\";\n"
		               + "\n"
		               + "	void test(Object o, Object obj, int number) {\n"
		               + "		if (%s) {\n"
		               + "			System.out.printf(\"\", s, s1, s2);\n"
		               + "			throw new IllegalArgumentException();\n"
		               + "		}\n"
		               + "\n"
		               + "		System.out.printf(\"\", s, s1, s2);\n"
		               + "	}\n"
		               + "}\n", condition);

		CtModel model = createModelFromString(code, 21);

		List<? extends CtLocalVariable<?>> patternVariables = model.getElements(new TypeFilter<>(CtTypePattern.class))
			.stream()
			.map(CtTypePattern::getVariable)
			.collect(Collectors.toUnmodifiableList());

		var invocations = model.getElements(new TypeFilter<>(CtInvocation.class)).stream().filter(
			ctInvocation -> ctInvocation.getExecutable().getSimpleName().equals("printf")
		).collect(Collectors.toUnmodifiableList());
		CtInvocation<?> thenPrint = invocations.get(0);
		CtInvocation<?> elsePrint = invocations.get(1);

		for (var fallback : model.getElements(new TypeFilter<>(CtField.class))) {
			CtLocalVariable<?> patternVariable = patternVariables.stream()
				.filter(variable -> variable.getSimpleName().equals(fallback.getSimpleName()))
				.findFirst()
				.orElse(null);

			CtVariableRead<?> thenVariableRead = thenPrint.getArguments()
				.stream()
				.filter(arg -> arg instanceof CtVariableRead<?>)
				.map(arg -> (CtVariableRead<?>) arg)
				.filter(access -> access.getVariable().getSimpleName().equals(fallback.getSimpleName()))
				.findFirst()
				.orElseThrow();

			CtVariableRead<?> elseVariableRead = elsePrint.getArguments()
				.stream()
				.filter(arg -> arg instanceof CtVariableRead<?>)
				.map(arg -> (CtVariableRead<?>) arg)
				.filter(access -> access.getVariable().getSimpleName().equals(fallback.getSimpleName()))
				.findFirst()
				.orElseThrow();

			boolean refersToPatternVarInThen = patternVarsInThen.contains(fallback.getSimpleName());
			boolean refersToPatternVarInElse = patternVarsInElse.contains(fallback.getSimpleName());

			assertThat(thenVariableRead.getVariable().getDeclaration())
				.as("'%s' should reference the %s variable in 'then'", thenVariableRead, refersToPatternVarInThen ? "pattern" : "field")
				.isSameAs(refersToPatternVarInThen ? patternVariable : fallback);

			assertThat(elseVariableRead.getVariable().getDeclaration())
				.as("'%s' should reference the %s variable in 'else'", elseVariableRead, refersToPatternVarInElse ? "pattern" : "field")
				.isSameAs(refersToPatternVarInElse ? patternVariable : fallback);

		}
	}


	@ModelTest(code = ("class Test {\n"
	                   + "	String s = \"\";\n"
	                   + "\n"
	                   + "	void method(Object obj) {\n"
	                   + "		System.out.println(obj instanceof String s ? s : s);\n"
	                   + "		System.out.println(!(obj instanceof String s) ? s : s);\n"
	                   + "	}\n"
	                   + "}\n"), complianceLevel = 21)
	public void testConditionalPatternScope(@BySimpleName("Test") CtClass<?> ctClass) {
		// contract: references to pattern variables introduced in a conditional are resolved correctly
		List<CtVariable<?>> variables = ctClass.getElements(new TypeFilter<>(CtVariable.class));
		List<CtVariableReference<?>> references = ctClass.getElements(new TypeFilter<>(CtVariableReference.class));

		// System.out
		assertThat(references.get(1)).hasExactlyPotentialDeclarations(variables.get(1)); // (obj instanceof ...
		assertThat(references.get(2)).hasExactlyPotentialDeclarations(variables.get(2), variables.get(0)); // instanceof String s ? s
		assertThat(references.get(3)).hasExactlyPotentialDeclarations(variables.get(0)); // : s
		// System.out
		assertThat(references.get(5)).hasExactlyPotentialDeclarations(variables.get(1)); // !(obj instanceof ...
		assertThat(references.get(6)).hasExactlyPotentialDeclarations(variables.get(0)); // instanceof String s ? s
		assertThat(references.get(7)).hasExactlyPotentialDeclarations(variables.get(3), variables.get(0)); // : s
	}


	@Test
	public void testCorrectScoping() {
		String code = ("	class Example2 {\n"
		               + "		Point p;\n"
		               + "\n"
		               + "		void test2(Object o) {\n"
		               + "			if (o instanceof Point p) {\n"
		               + "				// p refers to the pattern variable\n"
		               + "				System.out.println(p);\n"
		               + "			} else {\n"
		               + "				// p refers to the field\n"
		               + "				System.out.println(p);\n"
		               + "			}\n"
		               + "		}\n"
		               + "	}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		var refs = model.getElements(new TypeFilter<>(CtLocalVariableReference.class));
		assertEquals(1, refs.size());
		var decl = refs.get(0).getDeclaration();
		assertNotNull(decl);
		assertEquals(variable, decl);
	}

	@Test
	public void testRecordPatterns() {
		String code = ("	record Point(int x, int y) {}\n"
		               + "	record Circle(Point center, int radius) {}\n"
		               + "\n"
		               + "	public class Y {\n"
		               + "		public void test() {\n"
		               + "			Object obj = new Circle(new Point(10, 20), 5);\n"
		               + "			if (obj instanceof Circle(Point (int x, int y), int r)) {\n"
		               + "					System.out.println(\"Object is a Circle at center (\" + x + \", \" + y + \") with radius \" + r);\n"
		               + "			}\n"
		               + "		}\n"
		               + "	}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> varX = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(0).getVariable();
		CtLocalVariable<?> varY = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(1).getVariable();
		CtLocalVariable<?> varR = model.getElements(new TypeFilter<>(CtTypePattern.class)).get(2).getVariable();
		var refs = model.getElements(new TypeFilter<>(CtLocalVariableReference.class));
		assertEquals(4, refs.size()); // includes reference to 'obj'
		var declX = refs.get(1).getDeclaration();
		var declY = refs.get(2).getDeclaration();
		var declR = refs.get(3).getDeclaration();
		assertEquals(varX, declX);
		assertEquals(varY, declY);
		assertEquals(varR, declR);
	}

	@Test
	@GitHubIssue(issueNumber = 6591, fixed = true)
	void testVarUsageInIf() {
		// contract: Variable reference finding should look into if bodies
		String code = ("class X {\n"
		               + "	public void foo() {\n"
		               + "		String buffer = \"hello\";\n"
		               + "		if (true) {\n"
		               + "		  buffer += \" world\";\n"
		               + "		}\n"
		               + "	}\n"
		               + "}\n");
		CtModel model = createModelFromString(code, 21);
		CtLocalVariable<?> variable = model.getElements(new TypeFilter<>(CtLocalVariable.class)).get(0);

		List<CtVariableReference<?>> references = variable.map(new VariableReferenceFunction()).list();
		assertThat(references).hasSize(1);
	}
}
