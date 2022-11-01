package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.annotations.PolymorphOwner;
import com.immersive.transactions.exceptions.IllegalDataModelException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
            PolymorphOwner mo = clazz.getAnnotation(PolymorphOwner.class);
            if (mo != null)
                classes[0] = mo.commonInterface();
            constructor = clazz.getDeclaredConstructor(classes);
        } catch (NoSuchMethodException e) {
            throw new IllegalDataModelException(clazz, "has no suitable constructor!");
        }
        try {
            if (!RootEntity.class.isAssignableFrom(clazz))
                destructor = clazz.getDeclaredMethod("destruct");
        } catch (NoSuchMethodException e) {
            throw new IllegalDataModelException(clazz, "has no suitable destructor!");
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
            Class<?> type = field.getType();
            //field is an array
            if (type.isArray()) {
                Class<?> storedType = type.getComponentType();
                if (!DataModelEntity.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in Array \""+field.getName()+"\" which is illegal!");
                childFieldList.add(field);
            }
            //field is collection
            else if (Collection.class.isAssignableFrom(type)) {
                Class<?> storedType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                if (!DataModelEntity.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in Collection \""+field.getName()+"\" which is illegal!");
                childFieldList.add(field);
            }
            //field is a map
            else if (Map.class.isAssignableFrom(type)) {
                Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
                //map or (dis-)continuousRangeMap. Assumes storedType is last type!
                Class<?> storedType = (Class<?>) types[types.length-1];
                if (!DataModelEntity.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in Map \""+field.getName()+"\" which is illegal!");
                childFieldList.add(field);
            }
            //field is other TransactionalEntity
            else if (DataModelEntity.class.isAssignableFrom(type)) {
                if (field.getAnnotation(CrossReference.class) != null)
                    contentFieldList.add(field);
                else
                    childFieldList.add(field);
            }
            //field is primitive / primitive wrapper / String / Enum / Void / non DME-object
            else {
                contentFieldList.add(field);
                if (isComplexObject(type)) {
                    //object must be immutable!
                    if (!Modifier.isFinal(type.getModifiers()))
                        throw new IllegalDataModelException(type, "is not a DataModelClass, so it must be final!");
                    for (Field f : type.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()))
                            continue;
                        if (!Modifier.isFinal(f.getModifiers()))
                            throw new IllegalDataModelException(type, "must be immutable but contains non final field \""+f.getName()+"\"!");
                    }
                }
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
                //field is an array and also initialized
                if (field.getType().isArray() && field.get(dme) != null)
                    children.addAll((Collection<? extends ChildEntity<?>>) field.get(dme));
                //field is a collection
                else if (Collection.class.isAssignableFrom(field.getType()))
                    children.addAll(new ArrayList<>((Collection<ChildEntity<?>>)field.get(dme)));
                //field is a map
                else if (Map.class.isAssignableFrom(field.getType()))
                    children.addAll(new ArrayList<>(((Map<?,ChildEntity<?>>)field.get(dme)).values()));
                else
                    children.add((ChildEntity<?>) field.get(dme));
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

    //field is NOT: primitive / primitive wrapper / String / Enum / Void
    //so field IS:  Array / Collection / Map / Complex object
    static boolean isComplexObject(Class<?> type) {
        return (!ClassUtils.isPrimitiveOrWrapper(type)
                && !String.class.isAssignableFrom(type)
                && !Void. class.isAssignableFrom(type)
                && !type.isEnum());
    }
}
