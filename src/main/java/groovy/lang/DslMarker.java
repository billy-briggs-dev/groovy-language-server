// Copyright 2026 Prominic.NET, Inc.
// Licensed under the Apache License, Version 2.0
package groovy.lang;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for DSL scoping in Groovy language server analysis.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER })
public @interface DslMarker {
    Class<?> value() default Object.class;
}
