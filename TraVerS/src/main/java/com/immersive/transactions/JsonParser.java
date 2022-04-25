package com.immersive.transactions;

import static com.immersive.transactions.TransactionManager.getChildFields;

import com.immersive.annotations.CrossReference;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
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
        private void printOneObject(int indentation, DataModelEntity dme) {
            if (indentation > 0) {
                if(prettyPrinting) newIndentedLine(strb, indentation);
            }
            if (!createdIDs.containsKey(dme))
                createdIDs.put(dme, createdIDs.size());
            strb.append("{");
            indentation++;
            if(prettyPrinting) newIndentedLine(strb, indentation);
            strb.append("'class':").append(dme.getClass().getSimpleName()).append(",");
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
            //loop over content fields
            for (Field field : TransactionManager.getContentFields(dme)) {
                Object fieldValue = null;
                try {
                    field.setAccessible(true);
                    fieldValue = field.get(dme);
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
                //field is of primitive data type
                else {
                    if(prettyPrinting) newIndentedLine(strb, indentation);
                    if (fieldValue instanceof String)
                        strb.append("\"").append(field.getName()).append("\":\"").append(fieldValue).append("\",");
                    else
                        strb.append("\"").append(field.getName()).append("\":").append(fieldValue).append(",");
                }
            }

            for (Field field : getChildFields(dme)) {
                field.setAccessible(true);
                try {
                    //field is a collection
                    if (Collection.class.isAssignableFrom(field.getType())) {
                        Object[] objects = ((Collection<DataModelEntity>)field.get(dme)).toArray();
                        if (objects.length > 0) {
                            if(prettyPrinting) newIndentedLine(strb, indentation);
                            strb.append("\"").append(field.getName()).append("\":[");
                            for (Object obj : objects) {
                                printOneObject(indentation+1, (DataModelEntity) obj);
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
                        printOneObject(indentation, (DataModelEntity) field.get(dme));
                        strb.append(",");
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            strb.setLength(strb.length() - 1);
            indentation--;
            if(prettyPrinting) newIndentedLine(strb, indentation);
            strb.append("}");
        }
    }


    @SuppressWarnings("unchecked")
    public static <R extends RootEntity> R fromJson(String json, Class<R> rootClass) {
        return (R) new Deserialization(json, rootClass.getPackage().getName()).rootEntity.dme;
    }

    private static class Deserialization {
        Map<Integer, ObjectInfo> objects = new HashMap<>();
        LinkedList<ObjectInfo> stack = new LinkedList<>();
        Map<Integer, ObjectInfo> creationChores;    //copy of objects after first parsing for crossing things off
        List<CrossReferenceToDo> crossReferences = new ArrayList<>();
        ObjectInfo currentObj = null;
        ObjectInfo rootEntity;

        @SuppressWarnings("unchecked")
        Deserialization(String json, String packageName) {
            //remove artifacts of pretty printing
            json = json.replace("\n", "");
            json = json.replace(INDENT, "");
            //split into object-tokens
            StringTokenizer objectSeparator = new StringTokenizer(json, "{}", true);
            while (objectSeparator.hasMoreElements()) {
                String token = objectSeparator.nextToken();
                if (token.equals("}")) {
                    stack.removeLast();
                    if (!stack.isEmpty())
                        currentObj = stack.getLast();
                }
                StringTokenizer fieldSeparator = new StringTokenizer(token, ",");
                while (fieldSeparator.hasMoreElements()) {
                    String[] pair = fieldSeparator.nextToken().split(":");
                    if (pair.length < 2)
                        continue;
                    String key = pair[0];
                    String value = pair[1];
                    switch (key) {
                        case "'class'":
                            try {
                                currentObj = new ObjectInfo((Class<? extends DataModelEntity>) Class.forName(packageName+"."+pair[1]));
                                if (stack.isEmpty())
                                    rootEntity = currentObj;
                                else {
                                    currentObj.ownerID = stack.getLast().uniqueID;
                                    currentObj.constructionParams.add(new KeyValuePair<>(stack.getLast().clazz, currentObj.ownerID));
                                }
                                stack.add(currentObj);
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                            break;
                        case "'uid'":
                            int uniqueID = parseToID(value);
                            currentObj.uniqueID = uniqueID;
                            objects.put(uniqueID, currentObj);
                            break;
                        case "'key1'":
                        case "'key2'":
                            Class<?> currentKeyClass = stack.get(stack.size()-2).currentKeyClass;
                            if (currentKeyClass == null)
                                throw new RuntimeException("error getting key class for object "+currentObj.clazz.getSimpleName());
                            if (stringEnclosedBy(value, '|'))
                                currentObj.constructionParams.add(new KeyValuePair<>(currentKeyClass, parseToID(value)));
                            else
                                currentObj.constructionParams.add(new KeyValuePair<>(currentKeyClass, parseToPrimitiveWrapper(currentKeyClass, value)));
                            break;
                    }
                    //content fields
                    if (stringEnclosedBy(key, '"')) {
                        //child field, log classes
                        if ((value.length() == 0 || value.charAt(0) == '[')) {
                            Field field = null;
                            try {
                                field = currentObj.clazz.getDeclaredField(key.substring(1, key.length() - 1));
                            } catch (NoSuchFieldException e) {
                                e.printStackTrace();
                            }
                            assert field != null;
                            field.setAccessible(true);
                            Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
                            if (types.length >= 2) {    //map or (dis-)continuousRangeMap. Assumes key is second last type!
                                currentObj.currentKeyClass = (Class<?>) types[types.length-2];
                            }
                        }
                        //primitive or cross reference - don't parse things because we get fieldTypes later anyway
                        else {
                            currentObj.fields.put(key.substring(1, key.length() - 1), value);
                        }
                    }
                }
            }

            //create creationChores from parsed information
            creationChores = new HashMap<>(objects);  //cross off
            Map.Entry<Integer, ObjectInfo> creationChore;
            while (!creationChores.isEmpty()) {
                creationChore = creationChores.entrySet().iterator().next();
                try {
                    createObject(creationChore.getValue());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            //handle cross-references at the end
            for (CrossReferenceToDo cr : crossReferences) {
                cr.ObjectField.setAccessible(true);
                try {
                    cr.ObjectField.set(cr.dme, objects.get(cr.crossReferenceID).dme);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        private void createObject(ObjectInfo info) throws IllegalAccessException {
            Object[] params = new Object[info.constructionParams.size()];
            for (int i=0; i<info.constructionParams.size(); i++) {
                KeyValuePair<Class<?>, Object> kvp = info.constructionParams.get(i);
                if (DataModelEntity.class.isAssignableFrom(kvp.key)) {
                    int uniqueID = (int) kvp.value;
                    if (creationChores.containsKey(uniqueID)) {
                        createObject(creationChores.get(uniqueID));
                    }
                    //now object can be safely assigned from objects list
                    params[i] = objects.get(uniqueID).dme;
                }
                else {
                    params[i] = kvp.value;
                }
            }
            info.dme = TransactionManager.construct(info.clazz, params);
            //unwrap content fields
            for (Field field : TransactionManager.getContentFields(info.dme)) {
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

    private static class ObjectInfo {
        Class<? extends DataModelEntity> clazz;
        Class<?> currentKeyClass;   //can change over different child maps
        int uniqueID, ownerID;
        List<KeyValuePair<Class<?>, Object>> constructionParams = new ArrayList<>();
        Map<String, String> fields = new HashMap<>();
        DataModelEntity dme;

        public ObjectInfo(Class<? extends DataModelEntity> clazz) {
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
