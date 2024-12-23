/*
 * Copyright (c) 2023 Daniel Maier.
 * Licensed under the MIT License.
 */

package net.scoreworks.treetools;

import net.scoreworks.treetools.annotations.AbstractClass;
import net.scoreworks.treetools.exceptions.IllegalDataModelException;

import java.lang.reflect.*;
import java.util.*;


/**
 * Class to automatically read and write (formatted) Json-strings for data models. Uses {@link ClassMetadata} to get specific class information.
 * Static and transient fields are ignored by the parser.
 * To make json work for the diverse requirements posed by a complex data model (collections, maps, cross-references, polymorphisms),
 * a few extra fields were introduced to the JSON output. These include:
 *
 * <ul>
 *  <li>'class':    save classname to reconstruct class even in polymorphic cases</li>
 *  <li>'uid':      a unique ID for each {@link MutableObject}. Can be inserted instead of the object to avoid cross-referencing
 *  problems. When used in fields, IDs are wrapped with "|" to tell the parser that this is indeed a
 *  placeholder for an object and not a field value</li>
 *  <li>'key':      signify to the parser, that this field is used to save the object with by the owner and is therefore
 *  needed as a construction parameter for the object</li>
 *  </ul>
 */

public final class JsonParser {
    private static final String INDENT = "  ";

    public static String toJson(RootEntity rootEntity, boolean prettyPrinting) {
        return new Serialization(rootEntity, prettyPrinting).strb.toString();
    }

    private static class Serialization {

        /**
         * Cache created {@link MutableObject} with their ID to be able to use ID placeholders for objects
         */
        private final Map<MutableObject, Integer> createdIDs = new HashMap<>();

        /**
         * Adds line breaks and indentations if set to true
         */
        private final boolean prettyPrinting;

        private final StringBuilder strb = new StringBuilder();


        Serialization(RootEntity rootEntity, boolean prettyPrinting) {
            this.prettyPrinting = prettyPrinting;
            printObject(rootEntity, 0, false);
        }

        @SuppressWarnings("unchecked")
        private void printObject(Object object, int indentation, boolean newLine) {
            if (newLine && prettyPrinting)
                newIndentedLine(strb, indentation);
            strb.append("{");
            indentation++;
            MutableObject mo = null;
            if(prettyPrinting) newIndentedLine(strb, indentation);
            strb.append("'class':").append(object.getClass().getSimpleName()).append(",");

            //check if object is a DataModelEntity and equip with uid and other functional fields if so
            if (object instanceof MutableObject) {
                mo = (MutableObject) object;
                if (!createdIDs.containsKey(mo))
                    createdIDs.put(mo, createdIDs.size());

                //print DataModelEntityFields
                if(prettyPrinting) newIndentedLine(strb, indentation);
                strb.append("'uid':").append(createdIDs.get(mo)).append(",");
                if (mo instanceof MappedChild<?,?>) {
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    printKey(((MappedChild<?,?>) mo).getKey(), "key", indentation);
                }
            }

            //get content fields
            Field[] contentFields;
            if (mo != null)
                contentFields = ClassMetadata.getFields((MutableObject) object);
            else
                contentFields = ClassMetadata.getAllFieldsIncludingInheritedOnes(object.getClass());

            //and loop over them
            for (Field field : contentFields) {
                //static or transient field are not considered
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))
                    continue;
                Object fieldValue = null;
                try {
                    field.setAccessible(true);
                    fieldValue = field.get(object);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                //field is null
                if (fieldValue == null) {
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    strb.append("\"").append(field.getName()).append("\":null,");
                    continue;
                }

                //field is a MutableObject
                if (fieldValue instanceof MutableObject) {
                    MutableObject cr = (MutableObject) fieldValue;
                    if (!createdIDs.containsKey(cr))
                        createdIDs.put(cr, createdIDs.size());
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    strb.append("\"").append(field.getName()).append("\":").append("|").append(createdIDs.get(cr)).append("|,");
                }

                //field is primitive / primitive wrapper / String / Enum / Void / non MutableObject (because MutableObject are children)
                else {
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    if (ClassMetadata.isComplexObject(field.getType())) {
                        strb.append("\"").append(field.getName()).append("\":");
                        printObject(fieldValue, indentation, false);
                    }
                    //String: parse with quotation marks
                    else if (fieldValue instanceof String)
                        strb.append("\"").append(field.getName()).append("\":\"").append(fieldValue).append("\",");
                    //Enum: parse using Enum.name() method
                    else if (fieldValue instanceof Enum<?>)
                        strb.append("\"").append(field.getName()).append("\":").append(((Enum<?>) fieldValue).name()).append(",");
                    //primitive / primitive wrapper / Void: parse by using Object.toString()
                    else
                        strb.append("\"").append(field.getName()).append("\":").append(fieldValue).append(",");
                }
            }

            //loop over child fields if object is a DataModelEntity (static and transient fields are not in that list)
            if (mo != null) {
                for (Field field : ClassMetadata.getCollections(mo)) {
                    field.setAccessible(true);
                    try {
                        //field is null - This is not the same as an empty collection!
                        if (field.get(mo) == null) {
                            if(prettyPrinting) newIndentedLine(strb, indentation);
                            strb.append("\"").append(field.getName()).append("\":null,");
                        }
                        //field is an array
                        else if (field.getType().isArray()) {
                            printArray(field, (MutableObject[]) field.get(mo), indentation);
                        }
                        //field is a collection
                        else if (Collection.class.isAssignableFrom(field.getType())) {
                            printArray(field, ((Collection<MutableObject>)field.get(mo)).toArray(), indentation);
                        }
                        //field is a map
                        else if (Map.class.isAssignableFrom(field.getType())) {
                            Collection<Child<?>> collection = ((Map<?, Child<?>>)field.get(mo)).values();
                            if (collection.size() > 0) {
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("\"").append(field.getName()).append("\":[");
                                for (MutableObject dm : collection) {
                                    printObject(dm, indentation+1, true);
                                    strb.append(",");
                                }
                                strb.setLength(strb.length() - 1);
                                if(prettyPrinting) newIndentedLine(strb, indentation);
                                strb.append("],");
                            }
                        }
                        else throw new RuntimeException("didn't recognize collection of "+mo.getClass().getSimpleName()+","+field.getName());
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            //erase last comma, if last character is a comma (leave brackets alone)
            if (strb.charAt(strb.length() - 1) == ',')
                strb.setLength(strb.length() - 1);
            indentation--;
            if(prettyPrinting) newIndentedLine(strb, indentation);
            strb.append("}");
        }

        private void printKey(Object key, String fieldName, int indentation) {
            strb.append("'").append(fieldName).append("':");
            if (key instanceof MutableObject) {
                if (!createdIDs.containsKey(key))
                    createdIDs.put((MutableObject) key, createdIDs.size());
                strb.append("|").append(createdIDs.get(key)).append("|");
            }
            else if (!ClassMetadata.isComplexObject(key.getClass())) {
                if (key instanceof String)
                    strb.append("\"").append(key).append("\"");
                else    //primitive / primitive wrapper / Enum / Void
                    strb.append(key);
            }
            else printObject(key, indentation, false);
            strb.append(",");
        }

        private void printArray(Field field, Object[] MOs, int indentation) {
            if (MOs.length > 0) {
                if(prettyPrinting) newIndentedLine(strb, indentation);
                strb.append("\"").append(field.getName()).append("\":[");
                for (Object obj : MOs) {
                    printObject(obj, indentation+1, true);
                    strb.append(",");
                }
                strb.setLength(strb.length() - 1);
                if(prettyPrinting) newIndentedLine(strb, indentation);
                strb.append("],");
            }
        }
    }


    @SuppressWarnings("unchecked")
    public static <R extends RootEntity> R fromJson(String json, Class<R> rootClass) {
        return (R) new Deserialization(json, rootClass).rootEntity.mo;
    }

    private static class Deserialization {
        Map<Integer, ObjectInfo> MOs = new HashMap<>();
        List<CrossReferenceToDo> crossReferences = new ArrayList<>();
        ObjectInfo rootEntity;

        Deserialization(String json, Class<?> rootClass) {
            //remove artifacts of pretty printing
            json = json.replace("\n", "");
            json = json.replace(" ", "");
            //split into object-tokens
            StringTokenizer objectSeparator = new StringTokenizer(json, "{}]", true);
            ObjectInfo owner = null;
            while (objectSeparator.hasMoreElements()) {
                owner = readObject(objectSeparator, owner, rootClass, null);
            }

            //create creationChores from parsed information
            Map<Integer, ObjectInfo> creationChores = new HashMap<>(MOs);  //cross off
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
                    cr.ObjectField.set(cr.mo, MOs.get(cr.crossReferenceID).mo);
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
                            assert  currentObj != null;
                            if (owner == null)
                                rootEntity = currentObj;
                            else {
                                currentObj.ownerID = owner.uniqueID;
                                currentObj.constructionParams.add(new KeyValuePair<>(owner.clazz, currentObj.ownerID));
                            }
                            int uniqueID = Integer.parseInt(pair[1]);
                            currentObj.uniqueID = uniqueID;
                            MOs.put(uniqueID, currentObj);
                            break;
                        case "'key'":
                            assert owner != null;
                            assert  currentObj != null;
                            if (currentKeyClass == null)
                                throw new RuntimeException("error getting key class for object " + currentObj.clazz.getSimpleName());

                            //key is another object
                            if (pair.length == 1) {
                                ObjectInfo info = readObject(objectSeparator, currentObj, currentKeyClass, null);
                                currentObj.constructionParams.add(new KeyValuePair<>(currentKeyClass, createNonMutableObject(info)));

                            }
                            //field is another MutableObject
                            else if (stringEnclosedBy(pair[1], '|'))
                                currentObj.constructionParams.add(new KeyValuePair<>(currentKeyClass, parseToID(pair[1])));
                            //primitive, primitive wrapper
                            else {
                                currentObj.constructionParams.add(new KeyValuePair<>(currentKeyClass, parseToPrimitiveWrapper(currentKeyClass, pair[1])));
                            }
                            break;
                    }
                    //content fields
                    if (stringEnclosedBy(key, '"')) {
                        Field field = null;
                        assert  currentObj != null;
                        for (Class<?> iterator=currentObj.clazz; iterator!=null; iterator=iterator.getSuperclass()) {
                            try {
                                field = iterator.getDeclaredField(key.substring(1, key.length() - 1));
                                break;  //end loop if field is found!
                            } catch (NoSuchFieldException ignored) {}
                        }
                        assert field != null;
                        //single child or immutable object
                        if (pair.length == 1) {
                            ObjectInfo info = readObject(objectSeparator, currentObj, field.getType(), null);
                            if (info.uniqueID == null)  //not a MutableObject
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
                        //primitive or cross-reference - don't parse Strings because we get necessary fieldTypes later anyway
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
            if (RootEntity.class.isAssignableFrom(info.clazz)) {
                info.mo = ClassMetadata.constructRootEntity((Class<? extends RootEntity>) info.clazz);
            }
            else {
                Object[] params = new Object[info.constructionParams.size()];
                for (int i = 0; i < info.constructionParams.size(); i++) {
                    KeyValuePair<Class<?>, Object> kvp = info.constructionParams.get(i);
                    if (MutableObject.class.isAssignableFrom(kvp.key)) {
                        int uniqueID = (int) kvp.value;
                        if (creationChores.containsKey(uniqueID)) {
                            createDataModelEntity(creationChores, creationChores.get(uniqueID));
                        }
                        //now object can be safely assigned from objects list
                        params[i] = MOs.get(uniqueID).mo;
                    } else {
                        params[i] = kvp.value;
                    }
                }
                info.mo = ClassMetadata.construct((Class<? extends MutableObject>) info.clazz, params);
            }

            //unwrap content fields
            for (Field field : ClassMetadata.getFields(info.mo)) {
                //field is immutable object
                ObjectInfo pojo = info.complexObjFields.get(field.getName());
                if (pojo != null) {
                    field.setAccessible(true);
                    field.set(info.mo, createNonMutableObject(pojo));
                    continue;
                }
                String value = info.fields.get(field.getName());
                if (value.equals("null")) {
                    field.setAccessible(true);
                    field.set(info.mo, null);
                    continue;
                }
                //field is another MutableObject
                if (stringEnclosedBy(value, '|')) {
                    crossReferences.add(new CrossReferenceToDo(info.mo, parseToID(value), field));
                    continue;
                }
                //set the field
                field.setAccessible(true);
                field.set(info.mo, parseToPrimitiveWrapper(field.getType(), value));
            }
            creationChores.remove(info.uniqueID);
        }
    }

    private static Object createNonMutableObject(ObjectInfo info) {
        Object object = null;
        try {
            Constructor<?> constructor = info.clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            object = constructor.newInstance();
            for (Field field : ClassMetadata.getAllFieldsIncludingInheritedOnes(info.clazz)) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))
                    continue;
                field.setAccessible(true);
                ObjectInfo pojo = info.complexObjFields.get(field.getName());
                String value = info.fields.get(field.getName());
                if (pojo != null)
                    field.set(object, createNonMutableObject(pojo));
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
        Integer uniqueID;   //null if no MutableObject
        int ownerID;
        List<KeyValuePair<Class<?>, Object>> constructionParams = new ArrayList<>();
        Map<String, String> fields = new HashMap<>();
        MutableObject mo;
        //for immutable classes
        Map<String, ObjectInfo> complexObjFields = new HashMap<>();


        public ObjectInfo(Class<?> clazz) {
            this.clazz = clazz;
        }
    }

    private static class CrossReferenceToDo {
        MutableObject mo;
        int crossReferenceID;
        Field ObjectField;
        CrossReferenceToDo(MutableObject mo, int crossReferenceID, Field ObjectField) {
            this.mo = mo;
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
        if (string.equals("null"))
            return null;
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
