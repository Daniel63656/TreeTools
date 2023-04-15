package com.immersive.transactions.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation used to annotate fields that hold reference to a {@link com.immersive.transactions.DataModelEntity},
 * but are not its owner.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossReference {}
