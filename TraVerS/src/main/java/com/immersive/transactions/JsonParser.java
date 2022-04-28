package com.immersive.transactions;

import static com.immersive.transactions.TransactionManager.getChildFields;

import com.immersive.annotations.CrossReference;
import com.immersive.annotations.AbstractClass;
import com.immersive.transactions.exceptions.IllegalDataModelException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * class to read and write (formatted) Json-Strings for RootEntities.
 * To make json work for circular references and collections other than Arrays, a few extra literals were introduced:
 *
 *  |   used to enclose a uniqueID which is an unambiguous number assigned to each object.
 *      objects CROSS-REFERENCING each other or having objects AS KEYS in a map use the uniqueID instead
 *
 *  '   each collection can be handled, because the deserialization calls the transaction-constructors of the objects.
 *      In order to do so, the class, as well as the keys of (Double-)KeyedChildEntity must be known.
 *      These, together with the uniqueID, were added as key-value-pairs, wenclosed with ' to differentiate from class fields
 */

public final class JsonParser {
    private static final String INDENT = "    ";

    public static String toJson(RootEntity rootEntity, boolean prettyPrinting) {
        return new Serialization(rootEntity, prettyPrinting).strb.toString();
    }

    private static class Serialization {
        Map<DataModelEntity, Integer> createdIDs = new HashMap<>();
        StringBuilder strb = new StringBuilder();
        boolean prettyPrinting;

        Serialization(RootEntity rootEntity, boolean prettyPrinting) {
            this.prettyPrinting = prettyPrinting;
            printOneObject(0, rootEntity);
        }

        @SuppressWarnings("unchecked")
        private void printOneObject(int indentation, Object object) {
            if (indentation > 0) {
                if(prettyPrinting) newIndentedLine(strb, indentation);
            }
            strb.append("{");
            indentation++;
            DataModelEntity dme = null;
            if(prettyPrinting) newIndentedLine(strb, indentation);
            strb.append("'class':").append(object.getClass().getSimpleName()).append(",");
            if (object instanceof DataModelEntity) {
                dme = (DataModelEntity) object;
                if (!createdIDs.containsKey(dme))
                    createdIDs.put(dme, createdIDs.size());
                //print DataModelEntityFields
                if(prettyPrinting) newIndentedLine(strb, indentation);
                strb.append("'uid':|").append(createdIDs.get(dme)).append("|,");
                if (dme instanceof KeyedChildEntity<?,?>) {
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    Object key = ((KeyedChildEntity<?,?>) dme).getKey();
                    if (key instanceof DataModelEntity) {
                        if (!createdIDs.containsKey(key))
                            createdIDs.put((DataModelEntity) key, createdIDs.size());
                        strb.append("'key1':|").append(createdIDs.get(key)).append("|,");
                    }
                    else
                        strb.append("'key1':").append(key).append(",");
                }
                if (dme instanceof DoubleKeyedChildEntity<?,?>) {
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    Object key = ((DoubleKeyedChildEntity<?,?>) dme).getEndKey();
                    if (key instanceof DataModelEntity) {
                        if (!createdIDs.containsKey(key))
                            createdIDs.put((DataModelEntity) key, createdIDs.size());
                        strb.append("'key2':|").append(createdIDs.get(key)).append("|,");
                    }
                    else
                        strb.append("'key2':").append(key).append(",");
                }
            }
            
            //get content fields
            Field[] contentFields;
            if (dme != null)
                contentFields =  TransactionManager.getContentFields((DataModelEntity) object);
            else
                contentFields = object.getClass().getDeclaredFields();
            //and loop over them
            for (Field field : contentFields) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                Object fieldValue = null;
                try {
                    field.setAccessible(true);
                    fieldValue = field.get(object);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                if (fieldValue == null)
                    continue;
                //field is a cross-reference
                if (field.getAnnotation(CrossReference.class) != null) {
                    DataModelEntity cr = (DataModelEntity) fieldValue;
                    if (!createdIDs.containsKey(cr))
                        createdIDs.put(cr, createdIDs.size());
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    strb.append("\"").append(field.getName()).append("\":").append("|").append(createdIDs.get(cr)).append("|,");
                }
                //field is primitive / primitive wrapper / String / Enum / Void / non DME-object
                else {
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    if (DataModelInfo.isComplexObject(field.getType())) {
                        strb.append("\"").append(field.getName()).append("\":");
                        printOneObject(indentation+1, fieldValue);
                    }
                    else if (fieldValue instanceof String)
                        strb.append("\"").append(field.getName()).append("\":\"").append(fieldValue).append("\",");
                    else
                        strb.append("\"").append(field.getName()).append("\":").append(fieldValue).append(",");
                }
            }
            //loop over child fields if object is a DataModelEntity
            if (dme != null) {
                for (Field field : getChildFields(dme)) {
                    field.setAccessible(true);
                    try {
                        //field is an array
                        if (field.getType().isArray() && field.get(dme) != null) {
                            DataModelEntity[] DMEs = (DataModelEntity[]) field.get(dme);
                            if (DMEs.length > 0) {
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("\"").append(field.getName()).append("\":[");
                                for (Object obj : DMEs) {
                                    printOneObject(indentation+1, obj);
                                    strb.append(",");
                                }
                                strb.setLength(strb.length() - 1);
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("],");
                            }
                        }
                        //field is a collection
                        else if (Collection.class.isAssignableFrom(field.getType())) {
                            Object[] DMEs = ((Collection<DataModelEntity>)field.get(dme)).toArray();
                            if (DMEs.length > 0) {
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("\"").append(field.getName()).append("\":[");
                                for (Object obj : DMEs) {
                                    printOneObject(indentation+1, obj);
                                    strb.append(",");
                                }
                                strb.setLength(strb.length() - 1);
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("],");
                            }
                        }
                        //field is a map
                        else if (Map.class.isAssignableFrom(field.getType())) {
                            Collection<ChildEntity<?>> collection = ((Map<?,ChildEntity<?>>)field.get(dme)).values();
                            if (collection.size() > 0) {
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("\"").append(field.getName()).append("\":[");
                                for (DataModelEntity dm : collection) {
                                    printOneObject(indentation+1, dm);
                                    strb.append(",");
                                }
                                strb.setLength(strb.length() - 1);
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("],");
                            }
                        }
                        else if (field.get(dme) != null) {
                            if(prettyPrinting) newIndentedLine(strb, indentation);
                            strb.append("\"").append(field.getName()).append("\":");
                            printOneObject(indentation, field.get(dme));
                            strb.append(",");
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
            //only erase last comma, no brackets!
            if (strb.charAt(strb.length() - 1) == ',')
                strb.setLength(strb.length() - 1);
            indentation--;
            if(prettyPrinting) newIndentedLine(strb, indentation);
            strb.append("}");
        }
    }


    @SuppressWarnings("unchecked")
    public static <R extends RootEntity> R fromJson(String json, Class<R> rootClass) {
        return (R) new Deserialization(json, rootClass).rootEntity.dme;
    }

    private static class Deserialization {
        Map<Integer, ObjectInfo> DMEs = new HashMap<>();
        List<CrossReferenceToDo> crossReferences = new ArrayList<>();
        ObjectInfo rootEntity;

        Deserialization(String json, Class<?> rootClass) {
            //remove artifacts of pretty printing
            json = json.replace("\n", "");
            json = json.replace(INDENT, "");
            //split into object-tokens
            StringTokenizer objectSeparator = new StringTokenizer(json, "{}]", true);
            ObjectInfo owner = null;
            while (objectSeparator.hasMoreElements()) {
                owner = readObject(objectSeparator, owner, rootClass, null);
            }

            //create creationChores from parsed information
            Map<Integer, ObjectInfo> creationChores = new HashMap<>(DMEs);  //cross off
            Map.Entry<Integer, ObjectInfo> creationChore;
            while (!creationChores.isEmpty()) {
                creationChore = creationChores.entrySet().iterator().next();
                try {
                    createDataModelEntity(creationChores, creationChore.getValue());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            //handle cross-references at the end
            for (CrossReferenceToDo cr : crossReferences) {
                cr.ObjectField.setAccessible(true);
                try {
                    cr.ObjectField.set(cr.dme, DMEs.get(cr.crossReferenceID).dme);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        private ObjectInfo readObject(StringTokenizer objectSeparator, ObjectInfo owner, Class<?> currentClass, Class<?> currentKeyClass) {
            String token = objectSeparator.nextToken();
            ObjectInfo currentObj = null;
            while (!token.equals("}")) {
                StringTokenizer fieldSeparator = new StringTokenizer(token, ",");
                while (fieldSeparator.hasMoreElements()) {
                    String keyValue = fieldSeparator.nextToken();
                    if (!keyValue.contains(":"))
                        continue;
                    String[] pair = keyValue.split(":");
                    String key = pair[0];
                    switch (key) {
                        case "'class'":
                            currentObj = new ObjectInfo(currentClass);
                            //do polymorphic deserialization
                            if (!currentClass.getSimpleName().equals(pair[1])) {
                                AbstractClass abst = currentClass.getAnnotation(AbstractClass.class);
                                if (abst == null)
                                    throw new IllegalDataModelException(currentClass, "is an abstract class but does not specify its subclasses in the @AbstractClass interface!");
                                for (Class<?> subClass : abst.subclasses()) {
                                    if (subClass.getSimpleName().equals(pair[1])) {
                                        currentObj.clazz = subClass;
                                        break;
                                    }
                                }
                            }
                            break;
                        case "'uid'":
                            if (owner == null)
                                rootEntity = currentObj;
                            else {
                                currentObj.ownerID = owner.uniqueID;
                                currentObj.constructionParams.add(new KeyValuePair<>(owner.clazz, currentObj.ownerID));
                            }
                            int uniqueID = parseToID(pair[1]);
                            currentObj.uniqueID = uniqueID;
                            DMEs.put(uniqueID, currentObj);
                            break;
                        case "'key1'":
                        case "'key2'":
                            assert owner != null;
                            if (currentKeyClass == null)
                                throw new RuntimeException("error getting key class for object " + currentObj.clazz.getSimpleName());
                            if (stringEnclosedBy(pair[1], '|'))
                                currentObj.constructionParams.add(new KeyValuePair<>(currentKeyClass, parseToID(pair[1])));
                            else
                                currentObj.constructionParams.add(new KeyValuePair<>(currentKeyClass, parseToPrimitiveWrapper(currentKeyClass, pair[1])));
                            break;
                    }
                    //content fields
                    if (stringEnclosedBy(key, '"')) {
                        Field field = null;
                        for (Class<?> iterator=currentObj.clazz; iterator!=null; iterator=iterator.getSuperclass()) {
                            try {
                                field = iterator.getDeclaredField(key.substring(1, key.length() - 1));
                                break;  //end loop if field is found!
                            } catch (NoSuchFieldException ignored) {}
                        }
                        assert field != null;
                        //single child or non DME-object
                        if (pair.length == 1) {
                            ObjectInfo info = readObject(objectSeparator, currentObj, field.getType(), null);
                            if (info.uniqueID == null)  //not a DME
                                currentObj.complexObjFields.put(key.substring(1, key.length() - 1), info);
                        }
                        //child collection
                        else if (pair[1].charAt(0) == '[') {
                            Class<?> keyClass = null;
                            Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
                            Class<?> elementClass = (Class<?>) types[types.length - 1];
                            if (types.length >= 2) {    //map or (dis-)continuousRangeMap. Assumes key is second last type!
                                keyClass = (Class<?>) types[types.length - 2];
                            }
                            token = objectSeparator.nextToken();
                            while (!(token.charAt(0) == ']')) {
                                readObject(objectSeparator, currentObj, elementClass, keyClass);
                                token = objectSeparator.nextToken();
                            }
                        }
                        //primitive or cross reference - don't parse Strings because we get necessary fieldTypes later anyway
                        else {
                            currentObj.fields.put(key.substring(1, key.length() - 1), pair[1]);
                        }
                    }
                }
                token = objectSeparator.nextToken();
            }
            return currentObj;
        }

        @SuppressWarnings("unchecked")
        private void createDataModelEntity(Map<Integer, ObjectInfo> creationChores, ObjectInfo info) throws IllegalAccessException {
            Object[] params = new Object[info.constructionParams.size()];
            for (int i=0; i<info.constructionParams.size(); i++) {
                KeyValuePair<Class<?>, Object> kvp = info.constructionParams.get(i);
                if (DataModelEntity.class.isAssignableFrom(kvp.key)) {
                    int uniqueID = (int) kvp.value;
                    if (creationChores.containsKey(uniqueID)) {
                        createDataModelEntity(creationChores, creationChores.get(uniqueID));
                    }
                    //now object can be safely assigned from objects list
                    params[i] = DMEs.get(uniqueID).dme;
                }
                else {
                    params[i] = kvp.value;
                }
            }
            info.dme = TransactionManager.construct((Class<? extends DataModelEntity>) info.clazz, params);
            //unwrap content fields
            for (Field field : TransactionManager.getContentFields(info.dme)) {
                //field is non DME-object
                ObjectInfo pojo = info.complexObjFields.get(field.getName());
                if (pojo != null) {
                    field.setAccessible(true);
                    field.set(info.dme, createObject(pojo));
                    continue;
                }
                String value = info.fields.get(field.getName());
                if (value == null)  //value not stored because was null
                    continue;
                if (field.getAnnotation(CrossReference.class) != null) {
                    crossReferences.add(new CrossReferenceToDo(info.dme, parseToID(value), field));
                    continue;
                }
                //set the field
                field.setAccessible(true);
                field.set(info.dme, parseToPrimitiveWrapper(field.getType(), value));
            }
            creationChores.remove(info.uniqueID);
        }
    }

    private static Object createObject(ObjectInfo info) {
        Object object = null;
        try {
            object = info.clazz.getDeclaredConstructor().newInstance();
            for (Field field : info.clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                field.setAccessible(true);
                ObjectInfo pojo = info.complexObjFields.get(field.getName());
                String value = info.fields.get(field.getName());
                if (pojo != null)
                    field.set(object, createObject(pojo));
                else if (value != null)
                    field.set(object, parseToPrimitiveWrapper(field.getType(), value));
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return object;
    }

    private static class ObjectInfo {
        Class<?> clazz;
        Integer uniqueID;   //null if no DME
        int ownerID;
        List<KeyValuePair<Class<?>, Object>> constructionParams = new ArrayList<>();
        Map<String, String> fields = new HashMap<>();
        DataModelEntity dme;
        //for complex non-DME objects
        Map<String, ObjectInfo> complexObjFields = new HashMap<>();


        public ObjectInfo(Class<?> clazz) {
            this.clazz = clazz;
        }
    }

    private static class CrossReferenceToDo {
        DataModelEntity dme;
        int crossReferenceID;
        Field ObjectField;
        CrossReferenceToDo(DataModelEntity dme, int crossReferenceID, Field ObjectField) {
            this.dme = dme;
            this.crossReferenceID = crossReferenceID;
            this.ObjectField = ObjectField;
        }
    }

    private static class KeyValuePair<K, V> {
        K key;
        V value;
        public KeyValuePair(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static boolean stringEnclosedBy(String string, Character enclosing) {
        return string.charAt(0) == enclosing && string.charAt(string.length()-1) == enclosing;
    }

    private static int parseToID(String string) {
        return Integer.parseInt(string.substring(1, string.length() - 1));
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseToPrimitiveWrapper(Class<T> clazz, String string) {
        // add some null-handling logic here? and empty values.
        if (Integer.class.isAssignableFrom(clazz) || Integer.TYPE.isAssignableFrom(clazz))
            return (T) Integer.valueOf(string);
        else if (Long.class.isAssignableFrom(clazz) || Long.TYPE.isAssignableFrom(clazz))
            return (T) Long.valueOf(string);
        else if (Short.class.isAssignableFrom(clazz) || Short.TYPE.isAssignableFrom(clazz))
            return (T) Short.valueOf(string);
        else if (Byte.class.isAssignableFrom(clazz) || Byte.TYPE.isAssignableFrom(clazz))
            return (T) Byte.valueOf(string);
        else if (Float.class.isAssignableFrom(clazz) || Float.TYPE.isAssignableFrom(clazz))
            return (T) Float.valueOf(string);
        else if (Double.class.isAssignableFrom(clazz) || Double.TYPE.isAssignableFrom(clazz))
            return (T) Double.valueOf(string);
        else if (Boolean.class.isAssignableFrom(clazz) || Boolean.TYPE.isAssignableFrom(clazz))
            return (T) Boolean.valueOf(string);
        else if (String.class.isAssignableFrom(clazz))
            return (T) string.substring(1, string.length() - 1);
        else if (clazz.isEnum())
            return (T) Enum.valueOf((Class<? extends Enum>) clazz, string);
        throw new RuntimeException("Error parsing string " +string+ " to primitive wrapper class!");
    }

    private static void newIndentedLine(StringBuilder strb, int number) {
        strb.append("\n");
        while (number > 0) {
            strb.append(INDENT);
            number--;
        }
    }
}
