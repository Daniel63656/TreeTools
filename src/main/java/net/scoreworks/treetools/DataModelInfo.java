package net.scoreworks.treetools;

import net.scoreworks.treetools.annotations.PolymorphOwner;
import net.scoreworks.treetools.exceptions.IllegalDataModelException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.*;
import java.util.*;


/**
 * Class to cache for transactions relevant information about a {@link MutableObject} class's fields and methods, so they don't have to
 * be obtained for each class each time with reflections
 */
public class DataModelInfo {

    /** store {@link DataModelInfo} of analyzed classes for quick access */
    private static final Map<Class<? extends MutableObject>, DataModelInfo> dataModelInfo = new HashMap<>();

    /**
     * class-type whose content is described
     */
    Class<? extends MutableObject> clazz;

    /**
     * all fields that contain collections of the described class and superclass(es) (excluding transactional class fields).
     * This includes arrays, sets, and maps. In these collections, only objects of type {@link MutableObject} can be
     * saved to make tracking of changes possible. Lists are not supported
     */
    Field[] collections;

    /**
     * all other "plain" fields of the described class and superclass(es) (excluding transactional class fields), including
     * references to other {@link MutableObject}s
     */
    Field[] fields;

    /**
     * the class constructor used for transactions
     */
    Constructor<?> constructor;

    /**
     * create detailed class info
     * @param clazz class to be described
     * @param constructorParams classes used in the transactional constructor
     */
    DataModelInfo(Class<? extends MutableObject> clazz, Class<?>...constructorParams) {
        this.clazz = clazz;
        traceClassFields();

        //check if keys are immutable if not a DataModelEntity
        for (int i=1; i<constructorParams.length; i++) {
            Class<?> key = constructorParams[i];
            if (!MutableObject.class.isAssignableFrom(key) && isComplexObject(key)) {
                throwIfMutable(key);
            }
        }

        //find and cache the transactional constructor (which has constructionParams as input parameters)
        try {
            //if class can have different owners, get class-type of the owner from annotation
            //TODO write tests for this (and yes this seems to be necessary)
            PolymorphOwner polymorphOwner = clazz.getAnnotation(PolymorphOwner.class);
            if (polymorphOwner != null)
                constructorParams[0] = polymorphOwner.commonInterface();
            constructor = clazz.getDeclaredConstructor(constructorParams);
        } catch (NoSuchMethodException e) {
            throw new IllegalDataModelException(clazz, " has no suitable constructor!");
        }
    }

    /**
     * construct a data model specific {@link RootEntity}
     */
    static RootEntity constructRootEntity(Class<? extends RootEntity> clazz) {
        if (!dataModelInfo.containsKey(clazz)) {
            dataModelInfo.put(clazz, new DataModelInfo(clazz));
        }
        DataModelInfo info = dataModelInfo.get(clazz);
        info.constructor.setAccessible(true);
        try {
            return (RootEntity) info.constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("Error invoking class constructor for "+clazz.getSimpleName()+"!");
    }

    static Child<?> construct(Class<? extends MutableObject> clazz, Object...objects) {
        if (!dataModelInfo.containsKey(clazz)) {
            Class<?>[] classes = new Class<?>[objects.length];
            for (int i=0; i<objects.length; i++) {
                classes[i] = objects[i].getClass();
            }
            dataModelInfo.put(clazz, new DataModelInfo(clazz, classes));
        }

        DataModelInfo info = dataModelInfo.get(clazz);
        info.constructor.setAccessible(true);
        try {
            return (Child<?>) info.constructor.newInstance(objects);
        } catch (InvocationTargetException e) {
            RuntimeException exception = new RuntimeException("Error invoking class constructor for "+clazz.getSimpleName()
                    +", caused by:\n"+e.getTargetException().getMessage());
            exception.setStackTrace(e.getTargetException().getStackTrace());
            throw exception;
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Error invoking class constructor for "+clazz.getSimpleName()+" with parameters: "+ Arrays.toString(objects));
        }
    }

    static Field[] getFields(MutableObject dme) {
        if (!dataModelInfo.containsKey(dme.getClass()))
            dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), dme.constructorParameterTypes()));
        return dataModelInfo.get(dme.getClass()).fields;
    }

    static Field[] getCollections(MutableObject dme) {
        if (!dataModelInfo.containsKey(dme.getClass()))
            dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), dme.constructorParameterTypes()));
        return dataModelInfo.get(dme.getClass()).collections;
    }

    /**
     * get a list of all children stored in all {@link DataModelInfo#collections} of a given {@link MutableObject}
     * @param dme object to get children from
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<Child<?>> getChildren(MutableObject dme) {
        if (!dataModelInfo.containsKey(dme.getClass()))
            dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), dme.constructorParameterTypes()));
        DataModelInfo info = dataModelInfo.get(dme.getClass());

        //collect all children into an ArrayList
        ArrayList<Child<?>> children = new ArrayList<>();
        for (Field field : info.collections) {
            field.setAccessible(true);
            try {
                //field is an array and also initialized
                if (field.getType().isArray() && field.get(dme) != null)
                    children.addAll((Collection<? extends Child<?>>) field.get(dme));
                    //field is a collection
                else if (Collection.class.isAssignableFrom(field.getType()))
                    children.addAll(new ArrayList<>((Collection<Child<?>>)field.get(dme)));
                    //field is a map
                else if (Map.class.isAssignableFrom(field.getType()))
                    children.addAll(new ArrayList<>(((Map<?, Child<?>>)field.get(dme)).values()));
                else
                    throw new IllegalDataModelException(dme.getClass(), "contains an unknown tape of collection in field" + field.getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return children;
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


    //==========PRIVATE METHODS====================================================

    private void traceClassFields() {
        //first collect all fields of class and all its superclasses
        Field[] relevantFields = new Field[0];
        Class<?> iterator = clazz;
        Package transactionPackage = MutableObject.class.getPackage();
        while(iterator.getSuperclass() != null) {
            //stop when reaching transaction package, meaning owners and keys won't be considered
            if (iterator.getPackage() == transactionPackage)
                break;
            relevantFields = ArrayUtils.addAll(relevantFields, iterator.getDeclaredFields());
            iterator = iterator.getSuperclass();
        }

        //then sort fields based on how they are owned
        ArrayList<Field> collectionList = new ArrayList<>();
        ArrayList<Field> fieldList = new ArrayList<>();

        for (Field field : relevantFields) {
            //ignore static fields since they don't belong to an object and are therefore not considered and tracked as
            //part of the data model. Also ignore transient fields since they are not considered variables worth tracking
            //final variables aren't mutable but their value is still important to copy an object
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))
                continue;
            Class<?> type = field.getType();
            //containers of any sort(Arrays, Collections, Maps) can only hold DataModelEntities because otherwise changing
            //of the container elements can't be tracked by transactional system!

            //field is an array
            if (type.isArray()) {
                Class<?> storedType = type.getComponentType();
                if (!MutableObject.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in array \""+field.getName()+"\" which is illegal, because changes of this field can't be tracked!");
                collectionList.add(field);
            }
            //field is a collection
            else if (Collection.class.isAssignableFrom(type)) {
                Class<?> storedType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                if (!MutableObject.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in collection \""+field.getName()+"\" which is illegal, because changes of this field can't be tracked!");
                collectionList.add(field);
            }
            //field is a map
            else if (Map.class.isAssignableFrom(type)) {
                //get the type that is stored in the map. Assumes stored type is the last generic type!
                Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
                Class<?> storedType = (Class<?>) types[types.length-1];
                if (!MutableObject.class.isAssignableFrom(storedType))
                    throw new IllegalDataModelException(clazz, "contains non DataModelEntities in map \""+field.getName()+"\" which is illegal, because changes of this field can't be tracked!");
                collectionList.add(field);
            }
            //field is MutableObject / immutable object / primitive / wrapper class / String / Enum / Void
            else {
                fieldList.add(field);
                //if the field is a custom non MutableObject, make sure it is IMMUTABLE, because changes
                //within a MutableObject can't be traced
                if (!MutableObject.class.isAssignableFrom(type) && isComplexObject(type))
                    throwIfMutable(type);
            }
        }

        //to array
        fields = fieldList.toArray(new Field[0]);
        collections = collectionList.toArray(new Field[0]);
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
            if (MutableObject.class.isAssignableFrom(fieldType))
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
        if (fields.length > 0) {
            strb.append("\ncontentFields: ");
            for (Field field : fields) {
                strb.append(field.getName()).append(" ");
            }
        }
        if (collections.length > 0) {
            strb.append("\nchildFields: ");
            for (Field field : collections) {
                strb.append(field.getName()).append(" ");
            }
        }
        return strb.append("\n").toString();
    }
}