package spoon.support.visitor.java.testclasses;

public class AsmTree {
    public static final int i = 1;
    public static final long l = 1;
    public static final short s = 1;
    public static final byte by = 1;
    public static final boolean b = true;
    public static final double d = 1.0;
    public static final float f = 1.0f;
    public static final char ch = 'g';
    public static final String str = "10";

    private static class InnerClass {
        public static final int i = 1;
        public static final long l = 1;
        public static final short s = 1;
        public static final byte by = 1;
        public static final boolean b = true;
        public static final double d = 1.0;
        public static final float f = 1.0f;
        public static final char ch = 'g';
        public static final String str = "10";

        private static class InnerInnerClass {
            public static final int i = 1;
            public static final long l = 1;
            public static final short s = 1;
            public static final byte by = 1;
            public static final boolean b = true;
            public static final double d = 1.0;
            public static final float f = 1.0f;
            public static final char ch = 'g';
            public static final String str = "10";
        }
    }
}
