package com.immersive.transactions;

import com.immersive.transactions.annotations.CrossReference;
import com.immersive.transactions.annotations.PolymorphOwner;
import com.immersive.transactions.exceptions.IllegalDataModelException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Class to cache for transactions relevant information about a {@link DataModelEntity} class's fields and methods, so they don't have to
 * be obtained for each class each time with reflections
 */
class DataModelInfo {

    /**
     * class whose content is described
     */
    Class<? extends DataModelEntity> clazz;

    /**
     * all fields that contain children of the described class and superclass(es) either in containers or directly
     */
    Field[] childFields;

    /**
     * all other fields of the described class and superclass(es) (owner excluded)
     */
    Field[] contentFields;

    /**
     * the class constructor used for transactions
     */
    Constructor<?> constructor;

    /**
     * create detailed class info
     * @param clazz class to be described
     * @param constructorParams classes used in the transactional constructor
     */
    DataModelInfo(Class<? extends DataModelEntity> clazz, Class<?>...constructorParams) {
        this.clazz = clazz;
        traceClassFields();

        //check if keys are immutable if not a DataModelEntity
        for (int i=1; i<constructorParams.length; i++) {
            Class<?> key = constructorParams[i];
            if (!DataModelEntity.class.isAssignableFrom(key) && isComplexObject(key)) {
                throwIfMutable(key);
            }
        }

        //find and cache the transactional constructor (which has constructionParams as input parameters)
        try {
            //if class can have different owners, get class-type of the owner from annotation
            PolymorphOwner polymorphOwner = clazz.getAnnotation(PolymorphOwner.class);
            if (polymorphOwner != null)
                constructorParams[0] = polymorphOwner.commonInterface();
            constructor = clazz.getDeclaredConstructor(constructorParams);
        } catch (NoSuchMethodException e) {
            throw new IllegalDataModelException(clazz, " has no suitable constructor!");
        }
    }




    /**
     * get a list of all children stored in that object
     * @param dme object to get children from
     */
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

    private void traceClassFields() {
        //first collect all fields of class and all its superclasses
        Field[] relevantFields = new Field[0];
        Class<?> iterator = clazz;
        Package transactionPackage = DataModelEntity.class.getPackage();
        while(iterator.getSuperclass() != null) {
            //stop when reaching transaction package so owners and keys won't be considered
            if (iterator.getPackage() == transactionPackage)
                break;
            relevantFields = ArrayUtils.addAll(relevantFields, iterator.getDeclaredFields());
            iterator = iterator.getSuperclass();
        }

        //then sort fields based on functional relation
        ArrayList<Field> childFieldList = new ArrayList<>();
        ArrayList<Field> contentFieldList = new ArrayList<>();

        for (Field field : relevantFields) {
            //ignore static fields since they don't belong to an object and are therefore not considered and tracked as
            //part of the data model. Also ignore transient fields since they are not considered variables worth tracking
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))
                continue;
            Class<?> type = field.getType();
            //containers of any sort(Arrays, Collections, Maps) can only hold DataModelEntities because otherwise changing
            //of the container elements can't be tracked by transactional system!

            //field is an array
            if (type.isArray()) {
                Class<?> storedType = type.getComponentType();
                if (!DataModelEntity.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in array \""+field.getName()+"\" which is illegal, because changes of this field can't be tracked!");
                childFieldList.add(field);
            }
            //field is a collection
            else if (Collection.class.isAssignableFrom(type)) {
                Class<?> storedType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                if (!DataModelEntity.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in collection \""+field.getName()+"\" which is illegal, because changes of this field can't be tracked!");
                childFieldList.add(field);
            }
            //field is a map
            else if (Map.class.isAssignableFrom(type)) {
                //get the type that is stored in the map. Assumes stored type is the last generic type!
                Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
                Class<?> storedType = (Class<?>) types[types.length-1];
                if (!DataModelEntity.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in map \""+field.getName()+"\" which is illegal, because changes of this field can't be tracked!");
                childFieldList.add(field);
            }
            //field is other TransactionalEntity
            else if (DataModelEntity.class.isAssignableFrom(type)) {
                //cross-referenced DMEs are not considered to be children of the class
                if (field.getAnnotation(CrossReference.class) != null)
                    contentFieldList.add(field);
                else
                    childFieldList.add(field);
            }

            //field is primitive / wrapper class / String / Enum / Void / general non DME-object
            else {
                contentFieldList.add(field);
                //if the field is not a primitive, wrapper class, String, Enum or Void, make sure it is IMMUTABLE, because changes
                //within the non DME-object can't be traced
                if (isComplexObject(type)) {
                    throwIfMutable(type);
                }
            }
        }

        //cache fields
        contentFields = contentFieldList.toArray(new Field[0]);
        childFields = childFieldList.toArray(new Field[0]);
    }

    /**
     * returns true if the specified type is not a primitive, primitive wrapper, String, Enum or Void
     */
    static boolean isComplexObject(Class<?> type) {
        return (!ClassUtils.isPrimitiveOrWrapper(type)
                && !String.class.isAssignableFrom(type)
                && !Void.class.isAssignableFrom(type)
                && !type.isEnum());
    }

    static Field[] getAllFieldsIncludingInheritedOnes(Class<?> iterator) {
        Field[] relevantFields = new Field[0];
        while(iterator.getSuperclass() != null) {
            relevantFields = ArrayUtils.addAll(relevantFields, iterator.getDeclaredFields());
            iterator = iterator.getSuperclass();
        }
        return relevantFields;
    }

    private static void throwIfMutable(Class<?> type) {
        //check if object itself is final
        if (!Modifier.isFinal(type.getModifiers()))
            throw new IllegalDataModelException(type, "is not final and therefore mutable, which is illegal for a non DataModelEntity, because changes can't be tracked!");

        //check if object has a default constructor for the transactional system to use
        try {
            type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalDataModelException(type, "needs to have a default constructor with no input arguments!");
        }

        //check class fields including inherited ones
        for (Field f : getAllFieldsIncludingInheritedOnes(type)) {
            Class<?> fieldType = f.getType();

            //don't allow non DME-objects to be DME-owners!
            if (DataModelEntity.class.isAssignableFrom(fieldType))
                throw new IllegalDataModelException(type, "can't be owner of a DataModelEntity!");

            //static and transient fields are ignored by the transactional system
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()))
                continue;

            //fields must be final
            if (!Modifier.isFinal(f.getModifiers()))
                throw new IllegalDataModelException(type, "must be immutable but contains mutable field \"" + f.getName() + "\". Make it a field whose changes " +
                    "are not tracked by declaring it static or transient or make it final!");
            else {
                //complex objects contained in fields must also be immutable themselves
                if (isComplexObject(fieldType)) {
                    throwIfMutable(fieldType);
                }
            }
        }
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