/*
 * SPDX-License-Identifier: (MIT OR CECILL-C)
 *
 * Copyright (C) 2006-2023 INRIA and contributors
 *
 * Spoon is available either under the terms of the MIT License (see LICENSE-MIT.txt) or the Cecill-C License (see LICENSE-CECILL-C.txt). You as the user are entitled to choose the terms under which to adopt Spoon.
 */

package spoon.support.visitor.bytecode;

import static java.util.Map.entry;

import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.AnnotationDefaultAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtAnnotationMethod;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtRecord;
import spoon.reflect.declaration.CtRecordComponent;
import spoon.reflect.declaration.CtSealable;
import spoon.reflect.declaration.CtShadowable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.CtScanner;

@SuppressWarnings({"unchecked", "rawtypes"})
final class ClassModelTreeBuilder {

    private static final Map<Integer, ModifierKind> MODIFIERS = Map.ofEntries(
        entry(Modifier.PUBLIC, ModifierKind.PUBLIC),
        entry(Modifier.PRIVATE, ModifierKind.PRIVATE),
        entry(Modifier.PROTECTED, ModifierKind.PROTECTED),
        entry(Modifier.STATIC, ModifierKind.STATIC),
        entry(Modifier.FINAL, ModifierKind.FINAL),
        entry(Modifier.ABSTRACT, ModifierKind.ABSTRACT),
        entry(Modifier.SYNCHRONIZED, ModifierKind.SYNCHRONIZED),
        entry(Modifier.VOLATILE, ModifierKind.VOLATILE),
        entry(Modifier.TRANSIENT, ModifierKind.TRANSIENT),
        entry(Modifier.NATIVE, ModifierKind.NATIVE),
        entry(Modifier.STRICT, ModifierKind.STRICTFP));

    private static final Set<ModifierKind> SYNTHETIC_MODIFIERS =
        Set.of(ModifierKind.PUBLIC, ModifierKind.ABSTRACT, ModifierKind.FINAL);

    private static final String OBJECT = "java.lang.Object";
    private static final String ENUM = "java/lang/Enum";
    private static final List<ClassDesc> IMPLICIT_ENUM_CONSTRUCTOR = List.of(ConstantDescs.CD_String, ConstantDescs.CD_int);

    private final Factory factory;
    private final ClassPathBytes bytes;
    private final Map<ClassDesc, Optional<ClassModel>> annotationTypes = new HashMap<>();

    ClassModelTreeBuilder(Factory factory, ClassPathBytes bytes) {
        this.factory = factory;
        this.bytes = bytes;
    }

    CtType<?> build(ClassModel topLevel) {
        var desc = topLevel.thisClass().asSymbol();
        var type = declare(topLevel, topLevel.flags().flagsMask(), desc.displayName(), Optional.empty());
        packageOf(desc.packageName()).addType(type);
        markShadow(type);
        return type;
    }

    CtType<?> primitive(String name) {
        return synthetic(name);
    }

    CtType<?> array(String packageName, String simpleName) {
        var type = synthetic(simpleName);
        type.addSuperInterface(factory.Type().createReference("java.lang.Cloneable"));
        type.addSuperInterface(factory.Type().createReference("java.io.Serializable"));
        packageNamed(packageName).addType(type);
        return type;
    }

    CtType<?> stub(String packageName, String simpleName) {
        CtClass<?> type = factory.Core().createClass();
        type.setSimpleName(simpleName);
        type.setShadow(true);
        packageNamed(packageName).addType(type);
        return type;
    }

    private CtPackage packageNamed(String name) {
        return name.isEmpty() ? factory.Package().getRootPackage() : factory.Package().getOrCreate(name);
    }

    private CtPackage packageOf(String name) {
        if (name.isEmpty()) {
            return factory.Package().getRootPackage();
        }
        var known = factory.Package().get(name);
        if (known != null) {
            return known;
        }
        var created = factory.Package().getOrCreate(name);
        created.setShadow(true);
        bytes.parse(name + ".package-info").ifPresent(info -> annotate(created, info));
        return created;
    }

    private CtClass<?> synthetic(String simpleName) {
        CtClass<?> type = factory.Core().createClass();
        type.setSimpleName(simpleName);
        type.setShadow(true);
        type.setModifiers(SYNTHETIC_MODIFIERS);
        return type;
    }

    private enum Kind {
        ANNOTATION, INTERFACE, ENUM, RECORD, CLASS;

        static Kind of(ClassModel model, int mask) {
            if ((mask & ClassFile.ACC_ANNOTATION) != 0) {
                return ANNOTATION;
            }
            if ((mask & ClassFile.ACC_INTERFACE) != 0) {
                return INTERFACE;
            }
            if ((mask & ClassFile.ACC_ENUM) != 0
                && model.superclass().map(parent -> parent.asInternalName().equals(ClassModelTreeBuilder.ENUM)).orElse(false)) {
                return ENUM;
            }
            if (model.findAttribute(Attributes.record()).isPresent()) {
                return RECORD;
            }
            return CLASS;
        }

        CtType<?> create(Factory factory) {
            return switch (this) {
                case ANNOTATION -> factory.Core().createAnnotationType();
                case INTERFACE -> factory.Core().createInterface();
                case ENUM -> factory.Core().createEnum();
                case RECORD -> factory.Core().createRecord();
                case CLASS -> factory.Core().createClass();
            };
        }

        boolean hasTypeParameters() {
            return this == INTERFACE || this == RECORD || this == CLASS;
        }

        boolean hasSuperclass() {
            return this == RECORD || this == CLASS;
        }

        boolean hasInterfaces() {
            return this != ANNOTATION;
        }

        boolean hasConstructors() {
            return this == ENUM || this == RECORD || this == CLASS;
        }

        boolean canBeSealed() {
            return this == INTERFACE || this == ENUM || this == CLASS;
        }
    }

    private record Owner(Kind kind, int mask, Optional<ClassDesc> outer) {
    }

    private CtType<?> declare(ClassModel model, int mask, String simpleName, Optional<ClassDesc> outer) {
        var owner = new Owner(Kind.of(model, mask), mask, outer);
        CtType type = owner.kind().create(factory);
        type.setSimpleName(simpleName);
        type.setShadow(true);
        type.setModifiers(modifiers(mask, Modifier.classModifiers()));

        var signature = classSignature(model);
        if (owner.kind().hasTypeParameters()) {
            signature.typeParameters().forEach(parameter -> type.addFormalCtTypeParameter(typeParameter(parameter)));
        }
        if (owner.kind().hasSuperclass() && !qualifiedName(signature.superclassSignature().classDesc()).equals(OBJECT)) {
            var superclass = reference(signature.superclassSignature());
            markNonSealed(type, superclass);
            type.setSuperclass(superclass);
        }
        if (owner.kind().hasInterfaces()) {
            signature.superinterfaceSignatures().stream()
                .map(this::reference)
                .forEach(parent -> {
                    markNonSealed(type, parent);
                    type.addSuperInterface(parent);
                });
        }
        annotate(type, model);
        if (owner.kind().hasConstructors()) {
            model.methods().stream()
                .filter(ClassModelTreeBuilder::isConstructor)
                .filter(method -> !isSynthetic(method.flags().flagsMask()))
                .filter(method -> owner.kind() != Kind.ENUM || !isImplicitEnumConstructor(method))
                .forEach(method -> ((CtClass) type).addConstructor(constructor(method, owner)));
        }
        model.methods().stream()
            .filter(method -> !isConstructor(method) && !method.methodName().equalsString("<clinit>"))
            .filter(method -> !isSynthetic(method.flags().flagsMask()))
            .filter(method -> owner.kind() != Kind.ENUM || !isEnumHelper(method))
            .forEach(method -> type.addMethod(owner.kind() == Kind.ANNOTATION ? annotationMethod(method) : method(method, owner)));
        model.fields().stream()
            .filter(field -> !isSynthetic(field.flags().flagsMask()))
            .forEach(field -> addField(type, field, owner));
        memberClasses(model).forEach(info -> addNested(type, info, model.thisClass().asSymbol()));
        if (owner.kind() == Kind.RECORD) {
            recordComponents(model).forEach(((CtRecord) type)::addRecordComponent);
        }
        if (owner.kind().canBeSealed()) {
            permittedSubclasses(model).forEach(permitted -> {
                type.addModifier(ModifierKind.SEALED);
                ((CtSealable) type).addPermittedType(permitted);
            });
        }
        return type;
    }

    private static ClassSignature classSignature(ClassModel model) {
        return model.findAttribute(Attributes.signature())
            .map(attribute -> attribute.asClassSignature())
            .orElseGet(() -> erasedSignature(model));
    }

    private static ClassSignature erasedSignature(ClassModel model) {
        var superclass = model.superclass()
            .map(entry -> entry.asSymbol())
            .orElse(ConstantDescs.CD_Object);
        var interfaces = model.interfaces().stream()
            .map(entry -> Signature.ClassTypeSig.of(entry.asSymbol()))
            .toArray(Signature.ClassTypeSig[]::new);
        return ClassSignature.of(Signature.ClassTypeSig.of(superclass), interfaces);
    }

    private void markNonSealed(CtType<?> type, CtTypeReference<?> parent) {
        var declaration = factory.Type().get(parent.getQualifiedName());
        if (declaration != null
            && declaration.isShadow()
            && declaration.hasModifier(ModifierKind.SEALED)
            && !type.isFinal()
            && !type.hasModifier(ModifierKind.SEALED)) {
            type.addModifier(ModifierKind.NON_SEALED);
        }
    }

    private Stream<CtTypeReference<?>> permittedSubclasses(ClassModel model) {
        return model.findAttribute(Attributes.permittedSubclasses()).stream()
            .flatMap(attribute -> attribute.permittedSubclasses().stream())
            .map(entry -> factory.Type().createReference(qualifiedName(entry.asSymbol())));
    }

    private Stream<CtRecordComponent> recordComponents(ClassModel model) {
        return model.findAttribute(Attributes.record()).stream()
            .flatMap(attribute -> attribute.components().stream())
            .map(this::recordComponent);
    }

    private CtRecordComponent recordComponent(RecordComponentInfo info) {
        var component = factory.Core().createRecordComponent();
        component.setSimpleName(info.name().stringValue());
        component.setType(reference(info.findAttribute(Attributes.signature())
            .map(attribute -> attribute.asTypeSignature())
            .orElseGet(() -> Signature.of(info.descriptorSymbol()))));
        annotate(component, info);
        return component;
    }

    private static boolean isConstructor(MethodModel method) {
        return method.methodName().equalsString("<init>");
    }

    private static boolean isSynthetic(int mask) {
        return (mask & ClassFile.ACC_SYNTHETIC) != 0;
    }

    private static boolean isImplicitEnumConstructor(MethodModel method) {
        return Modifier.isPrivate(method.flags().flagsMask())
               && method.methodTypeSymbol().parameterList().equals(IMPLICIT_ENUM_CONSTRUCTOR);
    }

    private static boolean isEnumHelper(MethodModel method) {
        var name = method.methodName();
        return name.equalsString("values")
               || name.equalsString("valueOf") && method.methodTypeSymbol().parameterList().equals(List.of(ConstantDescs.CD_String));
    }

    private void addField(CtType type, FieldModel model, Owner owner) {
        var mask = model.flags().flagsMask();
        if (owner.kind() == Kind.ENUM && (mask & ClassFile.ACC_ENUM) != 0) {
            CtEnumValue value = factory.Core().createEnumValue();
            value.setSimpleName(model.fieldName().stringValue());
            value.setModifiers(modifiers(mask, Modifier.fieldModifiers()));
            annotate(value, model);
            value.setType(factory.Type().createReference(qualifiedName(model.fieldTypeSymbol())));
            ((CtEnum) type).addEnumValue(value);
            return;
        }
        CtField field = factory.Core().createField();
        field.setSimpleName(model.fieldName().stringValue());
        field.setModifiers(modifiers(mask, Modifier.fieldModifiers()));
        constant(model).ifPresent(field::setDefaultExpression);
        annotate(field, model);
        field.setType(reference(model.findAttribute(Attributes.signature())
            .map(attribute -> attribute.asTypeSignature())
            .orElseGet(() -> Signature.of(model.fieldTypeSymbol()))));
        type.addField(field);
    }

    private Optional<CtExpression<?>> constant(FieldModel field) {
        var mask = field.flags().flagsMask();
        var type = field.fieldTypeSymbol();
        if (!Modifier.isStatic(mask) || !Modifier.isPublic(mask) || !(type.isPrimitive() || type.equals(ConstantDescs.CD_String))) {
            return Optional.empty();
        }
        return field.findAttribute(Attributes.constantValue())
            .map(attribute -> expression(typed(attribute.constant().constantValue(), type)));
    }

    private static Object typed(ConstantDesc value, ClassDesc type) {
        return switch (type.descriptorString()) {
            case "Z" -> (Integer) value != 0;
            case "C" -> (char) (int) (Integer) value;
            case "B" -> (byte) (int) (Integer) value;
            case "S" -> (short) (int) (Integer) value;
            default -> value;
        };
    }

    private CtExpression<?> expression(Object value) {
        return switch (value) {
            case Double number when Double.isNaN(number) -> division(0.0d, 0.0d);
            case Double number when number == Double.POSITIVE_INFINITY -> division(1.0d, 0.0d);
            case Double number when number == Double.NEGATIVE_INFINITY -> division(-1.0d, 0.0d);
            case Float number when Float.isNaN(number) -> division(0.0f, 0.0f);
            case Float number when number == Float.POSITIVE_INFINITY -> division(1.0f, 0.0f);
            case Float number when number == Float.NEGATIVE_INFINITY -> division(-1.0f, 0.0f);
            default -> factory.Code().createLiteral(value);
        };
    }

    private CtExpression<?> division(Object dividend, Object divisor) {
        return factory.Code().createBinaryOperator(
            factory.Code().createLiteral(dividend),
            factory.Code().createLiteral(divisor),
            BinaryOperatorKind.DIV);
    }

    private CtConstructor<?> constructor(MethodModel model, Owner owner) {
        CtConstructor constructor = factory.Core().createConstructor();
        constructor.setBody(factory.Core().createBlock());
        constructor.setModifiers(modifiers(model.flags().flagsMask(), Modifier.constructorModifiers()));
        annotate(constructor, model);
        parameters(model, owner, true).forEach(constructor::addParameter);
        executableSignature(model).typeParameters()
            .forEach(parameter -> constructor.addFormalCtTypeParameter(typeParameter(parameter)));
        thrownTypes(model).forEach(constructor::addThrownType);
        return constructor;
    }

    private CtMethod<?> annotationMethod(MethodModel model) {
        CtAnnotationMethod method = factory.Core().createAnnotationMethod();
        method.setSimpleName(model.methodName().stringValue());
        method.setModifiers(modifiers(model.flags().flagsMask(), Modifier.methodModifiers()));
        method.setType(reference(executableSignature(model).result()));
        method.setShadow(true);
        model.findAttribute(Attributes.annotationDefault())
            .map(AnnotationDefaultAttribute::defaultValue)
            .flatMap(value -> annotationExpression(value, model.methodTypeSymbol().returnType()))
            .ifPresent(method::setDefaultExpression);
        return method;
    }

    private CtMethod<?> method(MethodModel model, Owner owner) {
        var mask = model.flags().flagsMask();
        var signature = executableSignature(model);
        CtMethod method = factory.Core().createMethod();
        method.setSimpleName(model.methodName().stringValue());
        if (!Modifier.isAbstract(mask)) {
            method.setBody(factory.Core().createBlock());
        }
        method.setModifiers(modifiers(mask, Modifier.methodModifiers()));
        method.setDefaultMethod(owner.kind() == Kind.INTERFACE
                                && (mask & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC);
        annotate(method, model);
        signature.typeParameters().forEach(parameter -> method.addFormalCtTypeParameter(typeParameter(parameter)));
        parameters(model, owner, false).forEach(method::addParameter);
        method.setType(reference(signature.result()));
        thrownTypes(model).forEach(method::addThrownType);
        return method;
    }

    private static MethodSignature executableSignature(MethodModel model) {
        return model.findAttribute(Attributes.signature())
            .map(attribute -> attribute.asMethodSignature())
            .orElseGet(() -> MethodSignature.of(model.methodTypeSymbol()));
    }

    private List<CtParameter<?>> parameters(MethodModel model, Owner owner, boolean isConstructor) {
        var descriptor = model.methodTypeSymbol().parameterList();
        var generic = model.findAttribute(Attributes.signature())
            .map(attribute -> attribute.asMethodSignature().arguments())
            .orElseGet(() -> descriptor.stream().map(Signature::of).toList());
        var offset = isConstructor ? Math.max(0, descriptor.size() - generic.size()) : 0;
        var details = parameterDetails(model, descriptor.size());
        var annotations = parameterAnnotations(model, descriptor.size());
        var varArgs = (model.flags().flagsMask() & ClassFile.ACC_VARARGS) != 0;
        return IntStream.range(0, generic.size())
            .filter(index -> !isConstructor || !isImplicitParameter(index, descriptor, details, owner))
            .<CtParameter<?>>mapToObj(index -> {
                CtParameter parameter = factory.Core().createParameter();
                parameter.setSimpleName(details.get(index + offset)
                    .flatMap(MethodParameterInfo::name)
                    .map(name -> name.stringValue())
                    .filter(name -> !name.isEmpty())
                    .orElse("arg" + index));
                parameter.setVarArgs(varArgs && index == descriptor.size() - 1);
                annotations.get(index + offset).stream()
                    .flatMap(annotation -> annotation(annotation).stream())
                    .forEach(parameter::addAnnotation);
                parameter.setType(reference(generic.get(index)));
                return parameter;
            })
            .toList();
    }

    private static List<Optional<MethodParameterInfo>> parameterDetails(MethodModel model, int count) {
        return model.findAttribute(Attributes.methodParameters())
            .map(attribute -> attribute.parameters())
            .filter(parameters -> parameters.size() == count)
            .map(parameters -> parameters.stream().map(Optional::of).toList())
            .orElseGet(() -> Collections.nCopies(count, Optional.empty()));
    }

    private static List<List<java.lang.classfile.Annotation>> parameterAnnotations(MethodModel model, int count) {
        var declared = model.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
            .map(attribute -> attribute.parameterAnnotations())
            .orElse(List.of());
        var padded = new ArrayList<List<java.lang.classfile.Annotation>>(Collections.nCopies(Math.max(0, count - declared.size()), List.of()));
        padded.addAll(declared);
        return List.copyOf(padded);
    }

    private static boolean isImplicitParameter(int index, List<ClassDesc> descriptor, List<Optional<MethodParameterInfo>> details, Owner owner) {
        var mandated = details.get(index)
            .map(info -> (info.flagsMask() & ClassFile.ACC_MANDATED) != 0)
            .orElse(false);
        return mandated
               || index == 0
                  && !Modifier.isStatic(owner.mask())
                  && owner.outer().map(outer -> outer.equals(descriptor.getFirst())).orElse(false);
    }

    private Stream<CtTypeReference<? extends Throwable>> thrownTypes(MethodModel model) {
        return model.findAttribute(Attributes.exceptions()).stream()
            .flatMap(attribute -> attribute.exceptions().stream())
            .map(entry -> factory.Type().createReference(qualifiedName(entry.asSymbol())));
    }

    private Stream<InnerClassInfo> memberClasses(ClassModel model) {
        var self = model.thisClass().asInternalName();
        return model.findAttribute(Attributes.innerClasses()).stream()
            .flatMap(attribute -> attribute.classes().stream())
            .filter(info -> info.outerClass().map(outer -> outer.asInternalName().equals(self)).orElse(false))
            .filter(info -> info.innerName().isPresent());
    }

    private void addNested(CtType type, InnerClassInfo info, ClassDesc outer) {
        bytes.parse(info.innerClass().asInternalName().replace('/', '.'))
            .map(nested -> declare(nested, info.flagsMask(), info.innerName().orElseThrow().stringValue(), Optional.of(outer)))
            .ifPresent(type::addNestedType);
    }

    private void annotate(CtElement element, AttributedElement model) {
        model.findAttribute(Attributes.runtimeVisibleAnnotations()).stream()
            .flatMap(attribute -> attribute.annotations().stream())
            .flatMap(annotation -> annotation(annotation).stream())
            .forEach(element::addAnnotation);
    }

    private Optional<CtAnnotation<?>> annotation(java.lang.classfile.Annotation model) {
        return annotationType(model.classSymbol()).map(type -> {
            CtAnnotation annotation = factory.Core().createAnnotation();
            annotation.setShadow(true);
            annotation.setAnnotationType(factory.Type().createReference(qualifiedName(model.classSymbol())));
            type.methods().stream()
                .filter(element -> !isSynthetic(element.flags().flagsMask()))
                .filter(element -> !element.methodName().equalsString("<clinit>"))
                .forEach(element -> annotation.addValue(element.methodName().stringValue(), elementValue(model, element)));
            return annotation;
        });
    }

    private Optional<ClassModel> annotationType(ClassDesc desc) {
        return annotationTypes.computeIfAbsent(desc, key -> bytes.parse(qualifiedName(key))
            .filter(model -> (model.flags().flagsMask() & ClassFile.ACC_ANNOTATION) != 0));
    }

    private CtExpression<?> elementValue(java.lang.classfile.Annotation annotation, MethodModel element) {
        var name = element.methodName();
        return annotation.elements().stream()
            .filter(explicit -> explicit.name().equalsString(name.stringValue()))
            .map(AnnotationElement::value)
            .findFirst()
            .or(() -> element.findAttribute(Attributes.annotationDefault()).map(AnnotationDefaultAttribute::defaultValue))
            .flatMap(value -> annotationExpression(value, element.methodTypeSymbol().returnType()))
            .orElseGet(this::unrepresentableValue);
    }

    private CtExpression<?> unrepresentableValue() {
        return factory.Code().createLiteral("");
    }

    private Optional<CtExpression<?>> annotationExpression(AnnotationValue value, ClassDesc type) {
        if (!type.isArray()) {
            return scalar(value);
        }
        var component = type.componentType();
        if (component.isPrimitive()) {
            return Optional.empty();
        }
        var values = value instanceof AnnotationValue.OfArray array ? array.values() : List.of(value);
        if (values.size() == 1) {
            return scalar(values.getFirst());
        }
        var elements = values.stream().map(this::scalar).toList();
        if (elements.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        CtNewArray array = factory.Core().createNewArray();
        array.setType(factory.Type().createArrayReference(reference(Signature.of(component))));
        elements.forEach(element -> array.addElement(element.orElseThrow()));
        return Optional.of(array);
    }

    private Optional<CtExpression<?>> scalar(AnnotationValue value) {
        return switch (value) {
            case AnnotationValue.OfConstant constant -> Optional.of(factory.Code().createLiteral(constant.resolvedValue()));
            case AnnotationValue.OfEnum constant -> Optional.of(enumRead(constant.classSymbol(), constant.constantName().stringValue()));
            case AnnotationValue.OfClass type -> Optional.of(factory.Code().createClassAccess(reference(Signature.of(type.classSymbol()))));
            case AnnotationValue.OfAnnotation nested -> Optional.empty();
            case AnnotationValue.OfArray array -> Optional.empty();
        };
    }

    private CtExpression<?> enumRead(ClassDesc type, String constant) {
        CtTypeReference declaring = factory.Type().createReference(qualifiedName(type));
        CtFieldRead read = factory.Core().createFieldRead();
        read.setVariable(factory.Field().createReference(declaring, declaring, constant));
        read.setTarget(factory.Code().createTypeAccess(declaring));
        read.setType(declaring);
        return read;
    }

    private CtTypeParameter typeParameter(Signature.TypeParam parameter) {
        var typeParameter = factory.Core().createTypeParameter();
        typeParameter.setSimpleName(parameter.identifier());
        Stream.concat(parameter.classBound().stream(), parameter.interfaceBounds().stream())
            .map(this::reference)
            .filter(bound -> !bound.getQualifiedName().equals(OBJECT))
            .forEach(bound -> typeParameter.setSuperclass(typeParameter.getSuperclass() == null
                ? bound
                : factory.createIntersectionTypeReferenceWithBounds(List.of(typeParameter.getSuperclass(), bound))));
        return typeParameter;
    }

    private CtTypeReference<?> reference(Signature signature) {
        return switch (signature) {
            case Signature.BaseTypeSig base -> factory.Type().createReference(
                ClassDesc.ofDescriptor(String.valueOf(base.baseType())).displayName());
            case Signature.TypeVarSig variable -> factory.Core().createTypeParameterReference()
                .<CtTypeReference<?>>setSimpleName(variable.identifier());
            case Signature.ArrayTypeSig array -> factory.Type().createArrayReference(reference(array.componentSignature()));
            case Signature.ClassTypeSig type -> classReference(type);
        };
    }

    private CtTypeReference<?> classReference(Signature.ClassTypeSig signature) {
        CtTypeReference<?> reference = factory.Type().createReference(qualifiedName(signature.classDesc()));
        signature.typeArgs().forEach(argument -> reference.addActualTypeArgument(typeArgument(argument)));
        return reference;
    }

    private CtTypeReference<?> typeArgument(Signature.TypeArg argument) {
        return switch (argument) {
            case Signature.TypeArg.Unbounded unbounded -> factory.Core().createWildcardReference();
            case Signature.TypeArg.Bounded bounded -> switch (bounded.wildcardIndicator()) {
                case NONE -> reference(bounded.boundType());
                case EXTENDS -> wildcard(true, bounded.boundType());
                case SUPER -> wildcard(false, bounded.boundType());
            };
        };
    }

    private CtWildcardReference wildcard(boolean upper, Signature bound) {
        CtWildcardReference wildcard = factory.Core().createWildcardReference();
        wildcard.setUpper(upper);
        wildcard.setBoundingType(reference(bound));
        return wildcard;
    }

    private static void markShadow(CtElement root) {
        root.accept(new CtScanner() {
            @Override
            public void scan(CtElement element) {
                if (element instanceof CtShadowable shadowable && !(element instanceof CtReference)) {
                    shadowable.setShadow(true);
                }
                super.scan(element);
            }
        });
    }

    private static String qualifiedName(ClassDesc desc) {
        return desc.packageName().isEmpty()
            ? desc.displayName()
            : desc.packageName() + "." + desc.displayName();
    }

    private static Set<ModifierKind> modifiers(int mask, int allowed) {
        var present = mask & allowed;
        return MODIFIERS.entrySet().stream()
            .filter(modifier -> (present & modifier.getKey()) != 0)
            .map(Map.Entry::getValue)
            .collect(Collectors.toUnmodifiableSet());
    }
}