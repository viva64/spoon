package org.eclipse.jdt.internal.compiler.ast;

import java.util.function.BooleanSupplier;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.NullAnnotationMatching.CheckMode;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.codegen.*;
import org.eclipse.jdt.internal.compiler.flow.*;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.lookup.*;
import java.util.Arrays;

// Этот файл с пакетом, в котором он расположен, были добавлены для фикса зависания в таком же файле из такого же пакета из либы 
// org.eclipse.jdt.core-3.32.0.jar, которая используется Spoon-ом
// 
// Из-за того, что этот файл расположен в исходниках Spoon в том, же пакете, что и в либе org.eclipse.jdt.core-3.32.0.jar, то он 
// загружается из classpath раньше, чем из org.eclipse.jdt.core-3.32.0.jar. Поэтому при рантайме используется версия этого файла, 
// а не из либы org.eclipse.jdt.core-3.32.0.jar
public abstract class Statement extends ASTNode {
  public static final int NOT_COMPLAINED = 0;
  
  public static final int COMPLAINED_FAKE_REACHABLE = 1;
  
  public static final int COMPLAINED_UNREACHABLE = 2;
  
  LocalVariableBinding[] patternVarsWhenTrue;
  
  LocalVariableBinding[] patternVarsWhenFalse;
  
  protected static boolean isKnowDeadCodePattern(Expression expression) {
    if (expression instanceof UnaryExpression)
      expression = ((UnaryExpression)expression).expression; 
    if (expression instanceof org.eclipse.jdt.internal.compiler.ast.Reference)
      return true; 
    return false;
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
   */
  public abstract FlowInfo analyseCode(BlockScope paramBlockScope, FlowContext paramFlowContext, FlowInfo paramFlowInfo);
  
  public boolean doesNotCompleteNormally() {
    return false;
  }
  
  public boolean completesByContinue() {
    return false;
  }
  
  public boolean canCompleteNormally() {
    return true;
  }
  
  public boolean continueCompletes() {
    return false;
  }
  
  protected void analyseArguments(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo, MethodBinding methodBinding, Expression[] arguments) {
    if (arguments != null) {
      CompilerOptions compilerOptions = currentScope.compilerOptions();
      if (compilerOptions.sourceLevel >= 3342336L && methodBinding.isPolymorphic())
        return; 
      boolean considerTypeAnnotations = currentScope.environment().usesNullTypeAnnotations();
      boolean hasJDK15NullAnnotations = (methodBinding.parameterNonNullness != null);
      int numParamsToCheck = methodBinding.parameters.length;
      int varArgPos = -1;
      TypeBinding varArgsType = null;
      boolean passThrough = false;
      if (considerTypeAnnotations || hasJDK15NullAnnotations)
        if (methodBinding.isVarargs()) {
          varArgPos = numParamsToCheck - 1;
          varArgsType = methodBinding.parameters[varArgPos];
          if (numParamsToCheck == arguments.length) {
            TypeBinding lastType = (arguments[varArgPos]).resolvedType;
            if (lastType == TypeBinding.NULL || (
              varArgsType.dimensions() == lastType.dimensions() && 
              lastType.isCompatibleWith(varArgsType)))
              passThrough = true; 
          } 
          if (!passThrough)
            numParamsToCheck--; 
        }  
      if (considerTypeAnnotations) {
        for (int i = 0; i < numParamsToCheck; i++) {
          TypeBinding expectedType = methodBinding.parameters[i];
          Boolean specialCaseNonNullness = hasJDK15NullAnnotations ? methodBinding.parameterNonNullness[i] : null;
          analyseOneArgument18(currentScope, flowContext, flowInfo, expectedType, arguments[i], 
              specialCaseNonNullness, (methodBinding.original()).parameters[i]);
        } 
        if (!passThrough && varArgsType instanceof ArrayBinding) {
          TypeBinding expectedType = ((ArrayBinding)varArgsType).elementsType();
          Boolean specialCaseNonNullness = hasJDK15NullAnnotations ? methodBinding.parameterNonNullness[varArgPos] : null;
          for (int j = numParamsToCheck; j < arguments.length; j++)
            analyseOneArgument18(currentScope, flowContext, flowInfo, expectedType, arguments[j], 
                specialCaseNonNullness, (methodBinding.original()).parameters[varArgPos]); 
        } 
      } else if (hasJDK15NullAnnotations) {
        for (int i = 0; i < numParamsToCheck; i++) {
          if (methodBinding.parameterNonNullness[i] == Boolean.TRUE) {
            TypeBinding expectedType = methodBinding.parameters[i];
            Expression argument = arguments[i];
            int nullStatus = argument.nullStatus(flowInfo, flowContext);
            if (nullStatus != 4)
              flowContext.recordNullityMismatch(currentScope, argument, argument.resolvedType, expectedType, flowInfo, nullStatus, null); 
          } 
        } 
      } 
    } 
  }
  
  void analyseOneArgument18(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo, TypeBinding expectedType, Expression argument, Boolean expectedNonNullness, TypeBinding originalExpected) {
    if (argument instanceof ConditionalExpression && argument.isPolyExpression()) {
      ConditionalExpression ce = (ConditionalExpression)argument;
      ce.internalAnalyseOneArgument18(currentScope, flowContext, expectedType, ce.valueIfTrue, flowInfo, ce.ifTrueNullStatus, expectedNonNullness, originalExpected);
      ce.internalAnalyseOneArgument18(currentScope, flowContext, expectedType, ce.valueIfFalse, flowInfo, ce.ifFalseNullStatus, expectedNonNullness, originalExpected);
      return;
    } 
    if (argument instanceof SwitchExpression && argument.isPolyExpression()) {
      SwitchExpression se = (SwitchExpression)argument;
      for (int i = 0; i < se.resultExpressions.size(); i++)
        se.internalAnalyseOneArgument18(currentScope, flowContext, expectedType, 
            se.resultExpressions.get(i), flowInfo, (
            (Integer)se.resultExpressionNullStatus.get(i)).intValue(), expectedNonNullness, originalExpected); 
      return;
    } 
    int nullStatus = argument.nullStatus(flowInfo, flowContext);
    internalAnalyseOneArgument18(currentScope, flowContext, expectedType, argument, flowInfo, 
        nullStatus, expectedNonNullness, originalExpected);
  }
  
  void internalAnalyseOneArgument18(BlockScope currentScope, FlowContext flowContext, TypeBinding expectedType, Expression argument, FlowInfo flowInfo, int nullStatus, Boolean expectedNonNullness, TypeBinding originalExpected) {
    int statusFromAnnotatedNull = (expectedNonNullness == Boolean.TRUE) ? nullStatus : 0;
    NullAnnotationMatching annotationStatus = NullAnnotationMatching.analyse(expectedType, argument.resolvedType, nullStatus);
    if (!annotationStatus.isAnyMismatch() && statusFromAnnotatedNull != 0)
      expectedType = originalExpected; 
    if (statusFromAnnotatedNull == 2) {
      currentScope.problemReporter().nullityMismatchingTypeAnnotation(argument, argument.resolvedType, expectedType, annotationStatus);
    } else if (annotationStatus.isAnyMismatch() || (statusFromAnnotatedNull & 0x10) != 0) {
      if (!expectedType.hasNullTypeAnnotations() && expectedNonNullness == Boolean.TRUE) {
        LookupEnvironment env = currentScope.environment();
        expectedType = env.createAnnotatedType(expectedType, new AnnotationBinding[] { env.getNonNullAnnotation() });
      } 
      flowContext.recordNullityMismatch(currentScope, argument, argument.resolvedType, expectedType, flowInfo, nullStatus, annotationStatus);
    } 
  }
  
  void checkAgainstNullAnnotation(BlockScope scope, FlowContext flowContext, FlowInfo flowInfo, Expression expr) {
    long tagBits;
    int nullStatus = expr.nullStatus(flowInfo, flowContext);
    MethodBinding methodBinding = null;
    boolean useTypeAnnotations = scope.environment().usesNullTypeAnnotations();
    try {
      methodBinding = scope.methodScope().referenceMethodBinding();
      tagBits = useTypeAnnotations ? methodBinding.returnType.tagBits : methodBinding.tagBits;
    } catch (NullPointerException nullPointerException) {
      return;
    } 
    if (useTypeAnnotations) {
      checkAgainstNullTypeAnnotation(scope, methodBinding.returnType, expr, flowContext, flowInfo);
    } else if (nullStatus != 4) {
      if ((tagBits & 0x100000000000000L) != 0L)
        flowContext.recordNullityMismatch(scope, expr, expr.resolvedType, methodBinding.returnType, flowInfo, nullStatus, null); 
    } 
  }
  
  protected void checkAgainstNullTypeAnnotation(BlockScope scope, TypeBinding requiredType, Expression expression, FlowContext flowContext, FlowInfo flowInfo) {
    if (expression instanceof ConditionalExpression && expression.isPolyExpression()) {
      ConditionalExpression ce = (ConditionalExpression)expression;
      internalCheckAgainstNullTypeAnnotation(scope, requiredType, ce.valueIfTrue, ce.ifTrueNullStatus, flowContext, flowInfo);
      internalCheckAgainstNullTypeAnnotation(scope, requiredType, ce.valueIfFalse, ce.ifFalseNullStatus, flowContext, flowInfo);
      return;
    } 
    if (expression instanceof SwitchExpression && expression.isPolyExpression()) {
      SwitchExpression se = (SwitchExpression)expression;
      for (int i = 0; i < se.resultExpressions.size(); i++)
        internalCheckAgainstNullTypeAnnotation(scope, requiredType, 
            se.resultExpressions.get(i), (
            (Integer)se.resultExpressionNullStatus.get(i)).intValue(), flowContext, flowInfo); 
      return;
    } 
    int nullStatus = expression.nullStatus(flowInfo, flowContext);
    internalCheckAgainstNullTypeAnnotation(scope, requiredType, expression, nullStatus, flowContext, flowInfo);
  }
  
  private void internalCheckAgainstNullTypeAnnotation(BlockScope scope, TypeBinding requiredType, Expression expression, int nullStatus, FlowContext flowContext, FlowInfo flowInfo) {
    NullAnnotationMatching annotationStatus = NullAnnotationMatching.analyse(requiredType, expression.resolvedType, null, null, nullStatus, expression, NullAnnotationMatching.CheckMode.COMPATIBLE);
    if (annotationStatus.isDefiniteMismatch()) {
      scope.problemReporter().nullityMismatchingTypeAnnotation(expression, expression.resolvedType, requiredType, annotationStatus);
    } else {
      if (annotationStatus.wantToReport())
        annotationStatus.report((Scope)scope); 
      if (annotationStatus.isUnchecked())
        flowContext.recordNullityMismatch(scope, expression, expression.resolvedType, requiredType, flowInfo, nullStatus, annotationStatus); 
    } 
  }
  
  public void branchChainTo(BranchLabel label) {}
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
   */
  public boolean breaksOut(char[] label) {
    return new ASTVisitor() {

		boolean breaksOut;
		@Override
		public boolean visit(TypeDeclaration type, BlockScope skope) { return label != null; }
		@Override
		public boolean visit(TypeDeclaration type, ClassScope skope) { return label != null; }
		@Override
		public boolean visit(LambdaExpression lambda, BlockScope skope) { return label != null;}
		@Override
		public boolean visit(WhileStatement whileStatement, BlockScope skope) { return label != null; }
		@Override
		public boolean visit(DoStatement doStatement, BlockScope skope) { return label != null; }
		@Override
		public boolean visit(ForeachStatement foreachStatement, BlockScope skope) { return label != null; }
		@Override
		public boolean visit(ForStatement forStatement, BlockScope skope) { return label != null; }
		@Override
		public boolean visit(SwitchStatement switchStatement, BlockScope skope) { return label != null; }

		@Override
		public boolean visit(BreakStatement breakStatement, BlockScope skope) {
			if (label == null || CharOperation.equals(label,  breakStatement.label))
				this.breaksOut = true;
	    	return false;
	    }
		@Override
		public boolean visit(YieldStatement yieldStatement, BlockScope skope) {
	    	return false;
	    }
		public boolean breaksOut() {
			Statement.this.traverse(this, null);
			return this.breaksOut;
		}
	}.breaksOut();
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public boolean continuesAtOuterLabel() {
	return new ASTVisitor() {
		boolean continuesToLabel;
		@Override
		public boolean visit(ContinueStatement continueStatement, BlockScope skope) {
			if (continueStatement.label != null)
				this.continuesToLabel = true;
	    	return false;
	    }
		public boolean continuesAtOuterLabel() {
			Statement.this.traverse(this, null);
			return this.continuesToLabel;
		}
	}.continuesAtOuterLabel();
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public int complainIfUnreachable(FlowInfo flowInfo, BlockScope scope, int previousComplaintLevel, boolean endOfBlock) {
    if ((flowInfo.reachMode() & 0x3) != 0) {
      if ((flowInfo.reachMode() & 0x1) != 0)
        this.bits &= Integer.MAX_VALUE; 
      if (flowInfo == FlowInfo.DEAD_END) {
        if (previousComplaintLevel < 2) {
          if (!doNotReportUnreachable())
            scope.problemReporter().unreachableCode(this); 
          if (endOfBlock)
            scope.checkUnclosedCloseables(flowInfo, null, null, null); 
        } 
        return 2;
      } 
      if (previousComplaintLevel < 1) {
        scope.problemReporter().fakeReachable(this);
        if (endOfBlock)
          scope.checkUnclosedCloseables(flowInfo, null, null, null); 
      } 
      return 1;
    } 
    return previousComplaintLevel;
  }
  
  protected boolean doNotReportUnreachable() {
    return false;
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public void generateArguments(MethodBinding binding, Expression[] arguments, BlockScope currentScope, CodeStream codeStream) {
    if (binding.isVarargs()) {
      TypeBinding[] params = binding.parameters;
      int paramLength = params.length;
      int varArgIndex = paramLength - 1;
      for (int i = 0; i < varArgIndex; i++)
        arguments[i].generateCode(currentScope, codeStream, true); 
      ArrayBinding varArgsType = (ArrayBinding)params[varArgIndex];
      ArrayBinding codeGenVarArgsType = (ArrayBinding)binding.parameters[varArgIndex].erasure();
      int elementsTypeID = (varArgsType.elementsType()).id;
      int argLength = (arguments == null) ? 0 : arguments.length;
      if (argLength > paramLength) {
        codeStream.generateInlinedValue(argLength - varArgIndex);
        codeStream.newArray(codeGenVarArgsType);
        for (int j = varArgIndex; j < argLength; j++) {
          codeStream.dup();
          codeStream.generateInlinedValue(j - varArgIndex);
          arguments[j].generateCode(currentScope, codeStream, true);
          codeStream.arrayAtPut(elementsTypeID, false);
        } 
      } else if (argLength == paramLength) {
        TypeBinding lastType = (arguments[varArgIndex]).resolvedType;
        if (lastType == TypeBinding.NULL || (
          varArgsType.dimensions() == lastType.dimensions() && 
          lastType.isCompatibleWith((TypeBinding)codeGenVarArgsType))) {
          arguments[varArgIndex].generateCode(currentScope, codeStream, true);
        } else {
          codeStream.generateInlinedValue(1);
          codeStream.newArray(codeGenVarArgsType);
          codeStream.dup();
          codeStream.generateInlinedValue(0);
          arguments[varArgIndex].generateCode(currentScope, codeStream, true);
          codeStream.arrayAtPut(elementsTypeID, false);
        } 
      } else {
        codeStream.generateInlinedValue(0);
        codeStream.newArray(codeGenVarArgsType);
      } 
    } else if (arguments != null) {
      for (int i = 0, max = arguments.length; i < max; i++)
        arguments[i].generateCode(currentScope, codeStream, true); 
    } 
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public abstract void generateCode(BlockScope paramBlockScope, CodeStream paramCodeStream);
  
  public boolean isBoxingCompatible(TypeBinding expressionType, TypeBinding targetType, Expression expression, Scope scope) {
    if (scope.isBoxingCompatibleWith(expressionType, targetType))
      return true; 
    return (expressionType.isBaseType() && 
      !targetType.isBaseType() && 
      !targetType.isTypeVariable() && 
      (scope.compilerOptions()).sourceLevel >= 3211264L && (
      targetType.id == 26 || targetType.id == 27 || targetType.id == 28) && 
      expression.isConstantValueOfTypeAssignableToType(expressionType, scope.environment().computeBoxingType(targetType)));
  }
  
  public boolean isEmptyBlock() {
    return false;
  }
  
  public boolean isValidJavaStatement() {
    return true;
  }
  
  public StringBuffer print(int indent, StringBuffer output) {
    return printStatement(indent, output);
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public abstract StringBuffer printStatement(int paramInt, StringBuffer paramStringBuffer);
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public abstract void resolve(BlockScope paramBlockScope);
  
  public LocalVariableBinding[] getPatternVariablesWhenTrue() {
    return this.patternVarsWhenTrue;
  }
  
  public LocalVariableBinding[] getPatternVariablesWhenFalse() {
    return this.patternVarsWhenFalse;
  }
  
  public void addPatternVariablesWhenTrue(LocalVariableBinding[] vars) {
    this.patternVarsWhenTrue = addPatternVariables(this.patternVarsWhenTrue, vars);
  }
  
  public void addPatternVariablesWhenFalse(LocalVariableBinding[] vars) {
    this.patternVarsWhenFalse = addPatternVariables(this.patternVarsWhenFalse, vars);
  }
  
  private LocalVariableBinding[] addPatternVariables(LocalVariableBinding[] current, LocalVariableBinding[] add) {
    if (add == null || add.length == 0)
      return current; 
    if (current == null || Arrays.equals(current, add)) { // ФИКС ЗАВИСАНИЯ в org.eclipse.jdt.core-3.32.0.jar
      current = add;
    } else {
      byte b;
      int i;
      LocalVariableBinding[] arrayOfLocalVariableBinding;
      for (i = (arrayOfLocalVariableBinding = add).length, b = 0; b < i; ) {
        LocalVariableBinding local = arrayOfLocalVariableBinding[b];
        current = addPatternVariables(current, local);
        b++;
      } 
    } 
    return current;
  }
  
  private LocalVariableBinding[] addPatternVariables(LocalVariableBinding[] current, LocalVariableBinding add) {
    int oldSize = current.length;
    if (oldSize > 0 && current[oldSize - 1] == add)
      return current; 
    int newLength = current.length + 1;
    System.arraycopy(current, 0, current = new LocalVariableBinding[newLength], 0, oldSize);
    current[oldSize] = add;
    return current;
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public void promotePatternVariablesIfApplicable(LocalVariableBinding[] patternVariablesInScope, BooleanSupplier condition) {
    if (patternVariablesInScope != null && condition.getAsBoolean()) {
      byte b;
      int i;
      LocalVariableBinding[] arrayOfLocalVariableBinding;
      for (i = (arrayOfLocalVariableBinding = patternVariablesInScope).length, b = 0; b < i; ) {
        LocalVariableBinding binding = arrayOfLocalVariableBinding[b];
        binding.modifiers &= 0xEFFFFFFF;
        b++;
      } 
    } 
  }
  
  /** Documentation for tests SpoonArchitectureEnforcerTest.testSrcMainJava SUCCESS COMPLETE
  */
  public void resolveWithPatternVariablesInScope(LocalVariableBinding[] patternVariablesInScope, BlockScope scope) {
    if (patternVariablesInScope != null) {
      byte b;
      int i;
      LocalVariableBinding[] arrayOfLocalVariableBinding;
      for (i = (arrayOfLocalVariableBinding = patternVariablesInScope).length, b = 0; b < i; ) {
        LocalVariableBinding binding = arrayOfLocalVariableBinding[b];
        binding.modifiers &= 0xEFFFFFFF;
        b++;
      } 
      resolve(scope);
      for (i = (arrayOfLocalVariableBinding = patternVariablesInScope).length, b = 0; b < i; ) {
        LocalVariableBinding binding = arrayOfLocalVariableBinding[b];
        binding.modifiers |= 0x10000000;
        b++;
      } 
    } else {
      resolve(scope);
    } 
  }
  
  public TypeBinding resolveExpressionType(BlockScope scope) {
    return null;
  }
  
  public boolean containsPatternVariable() {
    return false;
  }
  
  public TypeBinding invocationTargetType() {
    return null;
  }
  
  public TypeBinding expectedType() {
    return invocationTargetType();
  }
  
  public ExpressionContext getExpressionContext() {
    return ExpressionContext.VANILLA_CONTEXT;
  }
  
  protected MethodBinding findConstructorBinding(BlockScope scope, Invocation site, ReferenceBinding receiverType, TypeBinding[] argumentTypes) {
    MethodBinding ctorBinding = scope.getConstructor(receiverType, argumentTypes, (InvocationSite)site);
    return resolvePolyExpressionArguments(site, ctorBinding, argumentTypes, scope);
  }
}

