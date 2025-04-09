package spoon.support.visitor.java;

import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.factory.Factory;

import java.io.IOException;
import java.lang.reflect.Field;

public class AsmTreeBuilder extends JavaReflectionTreeBuilder {
    private static final Logger log = LoggerFactory.getLogger(AsmTreeBuilder.class);

    public AsmTreeBuilder(Factory factory) {
        super(factory);
    }

    /**
     * Uses the ASM library for the field value retrieval, <b>does not cause class initialization</b>
     * @param field of which to evaluate the constant value
     * @return Pair, the left value determines whether the field value was successfully retrieved
     *               the right value is the result, might be null (as a valid value)
     */
    @Override
    public Pair<Boolean, Object> getConstantValue(Field field) {
        var fieldType = field.getType();
        String declaringClassName = field.getDeclaringClass().getTypeName().replaceAll("\\.", "/") + ".class";
        String fieldName = field.getName();
        String fieldDescriptor = Type.getDescriptor(fieldType);
        var inputClassLoader = factory.getEnvironment().getInputClassLoader();
        var classFile = inputClassLoader.getResourceAsStream(declaringClassName);

        try {
            if (classFile == null) {
                throw new IOException();
            }

            ClassReader classReader = new ClassReader(classFile);
            ConstantValueVisitor visitor = new ConstantValueVisitor(fieldName, fieldDescriptor);
            classReader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            var constantValue = visitor.getConstantValue();
            classFile.close();
            return Pair.of(true, typeMap(fieldType, constantValue));
        } catch (IOException e) {
            log.warn("Could not read the class file: {}", declaringClassName ,e);
            return Pair.of(false, null);
        }

    }

    /**
     * @param fieldType the primitive type to cast the {@code obj}
     * @param obj the value to cast to the {@code fieldType}
     * @return the object with the {@code fieldType} type <br>
     *         {@code null} if {@code obj} is null or the field type is not a primitive
     */
    private Object typeMap(Class<?> fieldType, Object obj) {
        if (obj == null) {
            return null;
        }
        // ASM returns int, long, float, double with the correct type
        if (fieldType == int.class) {
            return (obj);
        }
        if (fieldType == long.class) {
            return obj;
        }
        if (fieldType == float.class) {
            return obj;
        }
        if (fieldType == double.class) {
            return obj;
        }
        // other primitive types have to be unboxed to return the necessary boxed type to get the correct model
        if (fieldType == boolean.class) {
            return (((Integer) obj) != 0);
        }
        if (fieldType == byte.class) {
            return ((Integer) obj).byteValue();
        }
        if (fieldType == short.class) {
            return ((Integer) obj).shortValue();
        }
        if (fieldType == char.class) {
            return (char)((Integer) obj).byteValue();
        }
        if (fieldType == String.class) {
            return obj.toString();
        }

        return null; // null if it's not a primitive
    }

    private static class ConstantValueVisitor extends ClassVisitor {
        private final String fieldName;
        private final String fieldDescriptor;
        private Object constantValue;

        public ConstantValueVisitor(String fieldName, String fieldDescriptor) {
            super(Opcodes.ASM9);
            this.fieldName = fieldName;
            this.fieldDescriptor = fieldDescriptor;
            this.constantValue = null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (name.equals(fieldName) && descriptor.equals(fieldDescriptor) && (access & Opcodes.ACC_FINAL) != 0 && (access & Opcodes.ACC_STATIC) != 0) {
                this.constantValue = value;
            }
            return null;
        }

        public Object getConstantValue() {
            return constantValue;
        }
    }

}

