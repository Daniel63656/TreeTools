package com.immersive.core;

import com.immersive.annotations.ChildField;
import com.immersive.annotations.CrossReference;
import com.immersive.annotations.DataModelEntity;
import com.immersive.annotations.OwnerField;
import com.immersive.annotations.RootEntity;
import com.immersive.annotations.TransactionalEntity;

import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

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

    DataModelInfo(Class<? extends DataModelEntity> clazz, Class<? extends DataModelEntity> ownerClass) {
        this.clazz = clazz;
        traceClassFields();
        try {
            if (!(RootEntity.class.isAssignableFrom(clazz))) {
                constructor = clazz.getDeclaredConstructor(ownerClass);
                destructor = clazz.getDeclaredMethod("destruct");
            } else {
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
        while (DataModelEntity.class.isAssignableFrom(iterator)) {
            relevantFields = ArrayUtils.addAll(relevantFields, iterator.getDeclaredFields());
            if (iterator.getSuperclass() == null)
                break;
            iterator = iterator.getSuperclass();
        }

        for (Field field : relevantFields) {
            //field is a collection
            if (Collection.class.isAssignableFrom(field.getType())) {
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
                else if (field.getAnnotation(OwnerField.class) == null) {
                    throw new RuntimeException("Found reference to class of the data model neither annotated as cross-reference nor owner nor child!");
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

    ArrayList<TransactionalEntity<?>> getChildren(DataModelEntity dme) {
        ArrayList<TransactionalEntity<?>> children = new ArrayList<>();
        for (Field field : childFields) {
            field.setAccessible(true);
            try {
                //field is a collection
                if (Collection.class.isAssignableFrom(field.getType())) {
                    children.addAll(new ArrayList<TransactionalEntity<?>>((Collection)field.get(dme)));
                }
                else {
                    children.add((TransactionalEntity<?>) field.get(dme));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return children;
    }

    DataModelEntity construct(DataModelEntity owner) {
        try {
            //RootEntity
            if (owner == null) {
                constructor.setAccessible(true);
                return (DataModelEntity) constructor.newInstance();
            }
            //TransactionalEntity
            else {
                constructor.setAccessible(true);
                return (DataModelEntity) constructor.newInstance(owner);
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Error invoking class constructor!");
    }

    void destruct(TransactionalEntity<?> te) {
        try {
            destructor.setAccessible(true);
            destructor.invoke(te);
            return;
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Error invoking class destructor!");
    }

}
