package com.immersive.core;

import com.immersive.annotations.DataModelEntity;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Logical unit containing the information of an object as field-object pair. Entity owner and children are
 * omitted, since they can be received via the links provided in LogicalObjectTrees. This way big cascading effects
 * are avoided.
 * These Keys are used in the LogicalObjectTrees, as well as in modificationRecords respectively.
 */
class LogicalObjectKey extends HashMap<Field, Object> {
  private static int globalID;
  private final int uniqueID;
  Class<? extends DataModelEntity> clazz;

  //this is necessary to differentiate keys. We can't use the fields since they can be the same for different objects!
  LogicalObjectKey(Class<? extends DataModelEntity> clazz) {
    this.clazz = clazz;
    this.uniqueID = globalID;
    globalID++;
  }

  boolean logicallySameWith(LogicalObjectKey lok) {
    for(Field f:keySet()) {
      if(!lok.containsKey(f)) {
        return false;
      }
      //TODO test this regarding null/nonNull
      if(!(this.get(f) == lok.get(f))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LogicalObjectKey)) {
      return false;
    }
    LogicalObjectKey right = (LogicalObjectKey) o;
    return this.uniqueID == right.uniqueID;
  }

  //necessary otherwise Hashmap will fail "using" the specified equals
  @Override
  public int hashCode() {
    return uniqueID;
  }

  @Override
  public String toString() {
    StringBuilder strb = new StringBuilder();
    strb.append("{ ");
    for (Entry<Field, Object> entry : entrySet()) {
      strb.append(entry.getKey().getName()).append("=").append(entry.getValue()).append(" ");
    }
    strb.append("}");
    return strb.toString();
  }
}
