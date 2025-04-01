package spoon.support.visitor.java;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;

import java.lang.reflect.Field;
import java.util.List;

public class ClassGraphTreeBuilder extends JavaReflectionTreeBuilder {
    private final ClassGraph classGraph;
    private ScanResult scanResult;

    public ClassGraphTreeBuilder(Factory factory) {
        super(factory);

        var classGraph = new ClassGraph().enableClassInfo()
                                         .enableFieldInfo()
                                         .enableStaticFinalFieldConstantInitializerValues()
                                         .enableSystemJarsAndModules();

        var sourceClasspath = factory.getEnvironment().getSourceClasspath();
        if (ArrayUtils.isNotEmpty(sourceClasspath)) {
            classGraph = classGraph.overrideClasspath(List.of(sourceClasspath));
        }

        this.classGraph = classGraph;
    }

    @Override
    public <T, R extends CtType<T>> R scan(Class<T> clazz) {
        if (clazz.getCanonicalName() != null) {
            scanResult = classGraph.acceptClasses(clazz.getCanonicalName()).scan();
        }

        R result = super.scan(clazz);

        if (scanResult != null) {
            scanResult.close();
        }
        return result;
    }

    /**
     * Uses the ClassGraph library for the field value retrieval, <b>does not cause class initialization</b>
     * @param field of which to evaluate the constant value
     * @return Pair, the left value determines whether the field value was successfully retrieved
     *               the right value is the result, might be null (valid value)
     */
    @Override
    protected Pair<Boolean, Object> getConstantValue(Field field) {
        var className = field.getDeclaringClass().getCanonicalName();
        var classInfo = scanResult.getAllClassesAsMap().get(className);

        if (scanResult == null) {
            return Pair.of(false, null);
        }

        if (classInfo != null) {
            var fieldInfo = classInfo.getFieldInfo(field.getName());
            if (fieldInfo != null) {
                return Pair.of(true, fieldInfo.getConstantInitializerValue());
            }
        }

        return Pair.of(false, null);
    }
}
