package com.immersive.core;

import com.immersive.annotations.ChildField;
import com.immersive.annotations.CrossReference;

import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * class to hold and cache for transactions relevant fields and methods so they don't have to
 * be obtained for each class each time with reflections
 */
class DataModelInfo {
    Class<? extends DataModelEntity> clazz;
    Field[] contentFields;          //all fields of class and superclass(es) excluding owner and children
    Field[] childFields;
    Constructor<?> constructor;     //different for RootEntity
    Method destructor;              //no destructor for RootEntity

    DataModelInfo(Class<? extends DataModelEntity> clazz, Class<? extends DataModelEntity> ownerClass, Class<?> keyClass) {
        this.clazz = clazz;
        traceClassFields();
        try {
            if (ownerClass != null) {
                if (keyClass == null)                                       //ChildEntity
                    constructor = clazz.getDeclaredConstructor(ownerClass);
                else                                                        //KeyedChildEntity
                    constructor = clazz.getDeclaredConstructor(ownerClass, keyClass);
                destructor = clazz.getDeclaredMethod("destruct");
            } else {                                                        //RootEntity
                constructor = clazz.getDeclaredConstructor();
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to find constructor/destructor for class" + clazz.getName());
        }
    }

    private void traceClassFields() {
        ArrayList<Field> childFieldList = new ArrayList<>();
        ArrayList<Field> contentFieldList = new ArrayList<>();
        Field[] relevantFields = new Field[0];
        Class<?> iterator = clazz;
        Package abstractionsPackage = DataModelEntity.class.getPackage();
        while (DataModelEntity.class.isAssignableFrom(iterator)) {
            if (iterator.getPackage() == abstractionsPackage)
                break;
            relevantFields = ArrayUtils.addAll(relevantFields, iterator.getDeclaredFields());
            if (iterator.getSuperclass() == null)
                break;
            iterator = iterator.getSuperclass();
        }

        for (Field field : relevantFields) {
            //field is a collection or
            if (Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType())) {
                if (field.getAnnotation(ChildField.class) == null) {
                    throw new RuntimeException("Collections on non child objects are not allowed in data model!");
                }
                childFieldList.add(field);
            }
            //field contains other TransactionalEntity
            else if (DataModelEntity.class.isAssignableFrom(field.getType())) {
                if (field.getAnnotation(CrossReference.class) != null) {
                    contentFieldList.add(field);
                }
                else if (field.getAnnotation(ChildField.class) != null) {
                    childFieldList.add(field);
                }
                else {
                    throw new RuntimeException("Found reference to class of the data model in " + clazz.getSimpleName() + " neither annotated as cross-reference nor nor child!");
                }
            }
            //field is of primitive data type
            else {
                contentFieldList.add(field);
            }
        }
        contentFields = contentFieldList.toArray(new Field[0]);
        childFields = childFieldList.toArray(new Field[0]);
    }


    //---------Getting fields for specific OBJECTS----------//

    ArrayList<ChildEntity<?>> getChildren(DataModelEntity dme) {
        ArrayList<ChildEntity<?>> children = new ArrayList<>();
        for (Field field : childFields) {
            field.setAccessible(true);
            try {
                //field is a collection
                if (Collection.class.isAssignableFrom(field.getType())) {
                    children.addAll(new ArrayList<>((Collection<ChildEntity<?>>)field.get(dme)));  //TODO what to do here to stop xlint from complaining "unsafe"
                }
                //field is a map
                else if (Map.class.isAssignableFrom(field.getType())) {
                    children.addAll(new ArrayList<>(((Map<?,ChildEntity<?>>)field.get(dme)).values()));  //TODO what to do here to stop xlint from complaining "unsafe"
                }
                else {
                    children.add((ChildEntity<?>) field.get(dme));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return children;
    }

    DataModelEntity construct(DataModelEntity owner, Class<?> key) {
        constructor.setAccessible(true);
        try {
            //RootEntity
            if (owner == null)
                return (DataModelEntity) constructor.newInstance();
            else {
                if (key != null)
                    return (DataModelEntity) constructor.newInstance(owner, key);
                return (DataModelEntity) constructor.newInstance(owner);
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Error invoking class constructor!");
    }

    void destruct(ChildEntity<?> te) {
        try {
            destructor.setAccessible(true);
            destructor.invoke(te);
            return;
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Error invoking class destructor!");
    }

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder();
        strb.append(">Info of ").append(clazz.getSimpleName()).append(":");
        if (contentFields.length > 0) {
            strb.append("\ncontentFields: ");
            for (Field field : contentFields) {
                strb.append(field.getName()).append(" ");
            }
        }
        if (childFields.length > 0) {
            strb.append("\nchildFields: ");
            for (Field field : childFields) {
                strb.append(field.getName()).append(" ");
            }
        }
        return strb.append("\n").toString();
    }

}
