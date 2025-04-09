package spoon.support.visitor.java;

import org.junit.jupiter.api.Test;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtType;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static spoon.testing.utils.ModelUtils.createFactory;

public class AsmTreeBuilderTest {
    private final Map<String, Object> fieldMapping = Map.ofEntries(
            Map.entry("i", 1),
            Map.entry("l", 1L),
            Map.entry("s", (short) 1),
            Map.entry("by", (byte) 1),
            Map.entry("b", true),
            Map.entry("d", 1.0),
            Map.entry("f", 1.0f),
            Map.entry("ch", 'g'),
            Map.entry("str", "10")
    );

    @Test
    void test() {
        CtType<?> ctType = new AsmTreeBuilder(createFactory()).scan(spoon.support.visitor.java.testclasses.AsmTree.class);

        while (true) {
            testFields(ctType);
            var nestedTypes = ctType.getNestedTypes();
            if (nestedTypes.isEmpty()) {
                break;
            }
            ctType = nestedTypes.stream().findFirst().get();
        }
    }

    private void testFields(@NotNull CtType<?> type) {
        var fields = type.getFields();
        assertEquals(fieldMapping.size(), fields.size(), "Wrong number of the parsed fields of the class " + type.getQualifiedName());

        for (var field : fields) {
            var fieldName = field.getSimpleName();
            var expectedValue = fieldMapping.get(fieldName);
            var actualValue = Optional.of(field.getDefaultExpression()).map(CtLiteral.class::cast).get().getValue();
            assertEquals(expectedValue, actualValue, String.format("Class %s. Got the wrong value of the field %s", type.getQualifiedName(), fieldName));
        }
    }
}
