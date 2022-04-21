package com.immersive.transactions;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Objects;

/**
 * Logical unit containing the information of an object as field-object pair. Entity owner and children are
 * omitted, since they can be received via the links provided in LogicalObjectTrees. This way big cascading effects
 * are avoided. Also omitted are keys, needed for construction (because owner maps object). These are assumed to be FINAL.
 * These Keys are used in the LogicalObjectTrees, as well as in modificationRecords respectively.
 */
class LogicalObjectKey extends HashMap<Field, Object> {
  private static int globalID;
  private final int uniqueID;
  Class<? extends DataModelEntity> clazz;
  //LOKs that have this LOK as cross-reference (together with the field)
  HashMap<LogicalObjectKey, Field> subscribedLOKs = new HashMap<>();

  //this is necessary to differentiate keys. We can't use the fields since they can be the same for different objects!
  LogicalObjectKey(Class<? extends DataModelEntity> clazz) {
    this.clazz = clazz;
    this.uniqueID = globalID;
    globalID++;
  }

  void unsubscribeFromCrossReferences() {
    for (Object obj : this.values()) {
      if (obj instanceof LogicalObjectKey) {
        ((LogicalObjectKey) obj).subscribedLOKs.remove(this);
      }
    }
  }

  boolean logicallySameWith(LogicalObjectKey lok) {
    for(Field f:keySet()) {
      if(!lok.containsKey(f)) {
        return false;
      }
      if(!Objects.equals(this.get(f), lok.get(f))) {
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

  String printSubscribers() {
    StringBuilder strb = new StringBuilder();
    if (!subscribedLOKs.isEmpty()) {
      strb.append(" subscribed: ");
      for (LogicalObjectKey LOK : subscribedLOKs.keySet()) {
        strb.append("["). append(LOK.uniqueID).append("]");
      }
    }
    return strb.toString();
  }

  @Override
  public String toString() {
    StringBuilder strb = new StringBuilder();
    strb.append(clazz.getSimpleName()).append("(").append(uniqueID).append(")");
    if (!isEmpty()) {
      strb.append("={");
      for (Entry<Field, Object> entry : entrySet()) {
        if (entry.getValue() instanceof LogicalObjectKey)
          strb.append(entry.getKey().getName()).append("=[").append(entry.getValue().hashCode()).append("]");
        else
          strb.append(entry.getKey().getName()).append("=").append(entry.getValue());
        strb.append(" ");
      }
      strb.setLength(strb.length() - 1);  //remove last space
      strb.append("}");
    }
    return strb.toString();
  }
}
