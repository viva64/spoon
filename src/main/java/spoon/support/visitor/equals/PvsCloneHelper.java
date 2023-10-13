package spoon.support.visitor.equals;

import spoon.reflect.declaration.CtElement;
import spoon.support.visitor.clone.CloneVisitor;

public class PvsCloneHelper extends CloneHelper {

    public static final PvsCloneHelper INSTANCE = new PvsCloneHelper();

    private PvsCloneHelper() {}

    @Override
    public <T extends CtElement> T clone(T element) {
        final CloneVisitor cloneVisitor = new CloneVisitor(this) {
            public <U> void visitCtMethod(final spoon.reflect.declaration.CtMethod<U> m) {
                spoon.reflect.declaration.CtMethod<T> aCtMethod = m.getFactory().Core().createMethod();
                this.builder.copy(m, aCtMethod);
                aCtMethod.setAnnotations(INSTANCE.clone(m.getAnnotations()));
                aCtMethod.setFormalCtTypeParameters(INSTANCE.clone(m.getFormalCtTypeParameters()));
                aCtMethod.setType(INSTANCE.clone(m.getType()));
                aCtMethod.setParameters(INSTANCE.clone(m.getParameters()));
                aCtMethod.setThrownTypes(INSTANCE.clone(m.getThrownTypes()));

                // ignore clone method body

                aCtMethod.setComments(INSTANCE.clone(m.getComments()));
                INSTANCE.tailor(m, aCtMethod);
                this.other = aCtMethod;
            }
        };
        cloneVisitor.scan(element);
        return cloneVisitor.getClone();
    }
}

