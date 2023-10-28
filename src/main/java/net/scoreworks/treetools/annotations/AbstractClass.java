package net.scoreworks.treetools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation used to annotate classes that have several classes extending it. This helps to resolve the type
 * during JSON-parsing.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AbstractClass {
    Class<?>[] subclasses();
}
