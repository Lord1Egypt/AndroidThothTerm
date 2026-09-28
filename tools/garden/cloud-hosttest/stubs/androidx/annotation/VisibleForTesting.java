package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
public @interface VisibleForTesting { int otherwise() default 2; }
