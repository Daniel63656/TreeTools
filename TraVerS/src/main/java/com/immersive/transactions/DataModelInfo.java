package com.immersive.transactions;

import com.immersive.annotations.ChildField;
import com.immersive.annotations.CrossReference;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    Constructor<?> constructor;
    Method destructor;              //no destructor for RootEntity

    DataModelInfo(Class<? extends DataModelEntity> clazz, Class<?>...classes) {
        this.clazz = clazz;
        traceClassFields();
        try {
            constructor = clazz.getDeclaredConstructor(classes);
            if (!RootEntity.class.isAssignableFrom(clazz))
                destructor = clazz.getDeclaredMethod("destruct");
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
            if (Modifier.isStatic(field.getModifiers()))
                continue;
            //field is a collection or map
            if (Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType())) {
                if (field.getAnnotation(ChildField.class) == null) {
                    throw new RuntimeException("Collections on non child objects are not allowed in data model class "+clazz.getName() + ", field: "+field.getName());
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
                    throw new RuntimeException("Found reference to class of the data model in " + clazz.getSimpleName() + " neither annotated as cross-reference nor child, field: "+field.getName());
                }
                //TODO don't allow non DME object fields!
                //ClassUtils.isPrimitiveOrWrapper(field.getType());
            }
            //field is of primitive data type or child without collection
            else {
                if (field.getAnnotation(ChildField.class) != null) {
                    childFieldList.add(field);
                }
                else
                    contentFieldList.add(field);
            }
        }
        contentFields = contentFieldList.toArray(new Field[0]);
        childFields = childFieldList.toArray(new Field[0]);
    }


    //---------Getting fields for specific OBJECTS----------//

    @SuppressWarnings("unchecked")
    ArrayList<ChildEntity<?>> getChildren(DataModelEntity dme) {
        ArrayList<ChildEntity<?>> children = new ArrayList<>();
        for (Field field : childFields) {
            field.setAccessible(true);
            try {
                //field is a collection
                if (Collection.class.isAssignableFrom(field.getType())) {
                    children.addAll(new ArrayList<>((Collection<ChildEntity<?>>)field.get(dme)));
                }
                //field is a map
                else if (Map.class.isAssignableFrom(field.getType())) {
                    children.addAll(new ArrayList<>(((Map<?,ChildEntity<?>>)field.get(dme)).values()));
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

    DataModelEntity construct(Object...objects) {
        constructor.setAccessible(true);
        try {
            return (DataModelEntity) constructor.newInstance(objects);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Error invoking class constructor for "+clazz.getSimpleName()+"!");
    }

    void destruct(ChildEntity<?> te) {
        try {
            destructor.setAccessible(true);
            destructor.invoke(te);
            return;
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Error invoking class destructor "+clazz.getSimpleName()+"!");
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
