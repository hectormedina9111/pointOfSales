package com.medinasystems.pos.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that records store documents, such as cash sessions. Only a store node accepts these calls;
 * the central node answers 403 with code {@code STORE_ONLY_OPERATION}. The mirror of {@link CentralOnly}.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StoreOnly {
}
