package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
public @interface IntDef { int[] value() default {}; boolean flag() default false; boolean open() default false; }
