package com.immersive.transactions;

import com.immersive.annotations.CrossReference;
import com.immersive.transactions.exceptions.NoTransactionsEnabledException;
import com.immersive.wrap.Wrapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.crypto.Data;

public class TransactionManager {
  private static final TransactionManager transactionManager = new TransactionManager();
  private TransactionManager() {}
  public static TransactionManager getInstance() {
    return transactionManager;
  }

  static Map<Class<? extends DataModelEntity>, DataModelInfo> dataModelInfo = new HashMap<>();
  final TreeMap<CommitId,Commit> commits = new TreeMap<>();
  Map<RootEntity, Workcopy> workcopies = new HashMap<>();
  private boolean logAspects;   //used to log aspects kicking in

  public void logAspects(boolean log) {
    logAspects = log;
  }

  public boolean transactionsEnabled(RootEntity rootEntity) {
    return workcopies.containsKey(rootEntity);
  }

  public void enableTransactionsForRootEntity(RootEntity rootEntity) {
    //transactions already enabled
    if (workcopies.containsKey(rootEntity))
      return;
    if (!workcopies.isEmpty()) {
      throw new RuntimeException("Transactions already enabled for different RootEntity! Use getWorkcopyOf() to receive another copy to work on.");
    }
    else {
      LogicalObjectTree LOT = new LogicalObjectTree();
      Workcopy workcopy = new Workcopy(rootEntity, LOT, new CommitId(0));
      buildLogicalObjectTree(LOT, rootEntity);
      workcopies.put(rootEntity, workcopy);
    }
  }

  public RootEntity getWorkcopyOf(RootEntity rootEntity) {
    if (!workcopies.containsKey(rootEntity))
      throw new NoTransactionsEnabledException();
    Commit initializationCommit = new Commit(null);  //since this is only a temporary commit the commitId doesn't really matter!
    //this is necessary to get a DataModel SPECIFIC class!
    RootEntity newRootEntity = (RootEntity) construct(rootEntity.getClass());
    LogicalObjectTree LOT = workcopies.get(rootEntity).LOT;
    LogicalObjectTree newLOT = new LogicalObjectTree();
    buildInitializationCommit(LOT, initializationCommit, rootEntity);
    //TODO copy state of rootEntity itself
    newLOT.put(LOT.getKey(rootEntity), newRootEntity);
    Workcopy workcopy = new Workcopy(newRootEntity, newLOT, new CommitId(0));
    new Pull(workcopy, initializationCommit);
    workcopies.put(newRootEntity, workcopy);
    return newRootEntity;
  }

  public CommitId getCurrentCommitId(RootEntity rootEntity) {
    if (!workcopies.containsKey(rootEntity))
      throw new NoTransactionsEnabledException();
    return workcopies.get(rootEntity).currentCommitId;
  }

  public void shutdown() {
    commits.clear();
    workcopies.clear();
  }

  private void buildLogicalObjectTree(LogicalObjectTree LOT, DataModelEntity dme) {
    LOT.createLogicalObjectKey(dme);
    ArrayList<ChildEntity<?>> children = getChildren(dme);
    if (children != null) {
      for (ChildEntity<?> child : children) {
        buildLogicalObjectTree(LOT, child);
      }
    }
  }

  private void buildInitializationCommit(LogicalObjectTree LOT, Commit commit, DataModelEntity dme) {
    if (!(dme instanceof RootEntity))
      commit.creationRecords.put(LOT.getKey(dme), dme.getConstructorParamsAsKeys(LOT));
    ArrayList<ChildEntity<?>> children = getChildren(dme);
    if (children != null) {
      for (ChildEntity<?> child : children) {
        buildInitializationCommit(LOT, commit, child);
      }
    }
  }

  static class LokField {
    LogicalObjectKey LOK;
    Field field;
    public LokField(LogicalObjectKey LOK, Field field) {
      this.LOK = LOK;
      this.field = field;
    }
  }

  //=========these methods are synchronized per RootEntity and therefore package-private============

  Commit commit(RootEntity rootEntity) {
    Workcopy workcopy = workcopies.get(rootEntity);
    if (workcopy == null)
      throw new NoTransactionsEnabledException();
    if (workcopy.locallyChangedOrCreated.isEmpty() && workcopy.locallyDeleted.isEmpty())
      return null;

    CommitId commitId = new CommitId(commits.size()+1);
    Commit commit = new Commit(commitId);
    LogicalObjectTree LOT = workcopy.LOT; //This corresponds to the rollback version or "remote"
    List<ChildEntity<?>> removeFromLOT = new ArrayList<>();
    Set<DataModelEntity> chores = new HashSet<>(workcopy.locallyChangedOrCreated);

    //create ModificationRecords for CREATED or CHANGED objects
    while (!chores.isEmpty()) {
      DataModelEntity dme = chores.iterator().next();
      //deletion OVERRIDES creation or change!
      if (dme instanceof ChildEntity<?>) {
        if (workcopy.locallyDeleted.contains(dme)) {
          chores.remove(dme);
          //remove deletion instruction if chore was a creation
          if (!LOT.containsValue(dme))
            workcopy.locallyDeleted.remove(dme);
          continue;
        }
      }
      commitCreationOrChange(chores, commit, LOT, dme);
    }

    //create ModificationRecords for DELETED objects
    Iterator<ChildEntity<?>> iterator = workcopy.locallyDeleted.iterator();
    while (iterator.hasNext()) {
      ChildEntity<?> te = iterator.next();
      commit.deletionRecords.put(LOT.getKey(te), te.getConstructorParamsAsKeys(LOT));
      //don't remove from LOT yet, because this destroys owner information for possible deletion entries below!
      //save this action in a list to do it at the end of the deletion part of the commit instead
      removeFromLOT.add(te);
      iterator.remove();
    }
    //remove all entries from LOT
    for (ChildEntity<?> te : removeFromLOT) {
      LOT.removeValue(te);
    }
    workcopy.locallyDeleted.clear();
    workcopy.locallyChangedOrCreated.clear();
    workcopy.currentCommitId = commitId;
    synchronized (commits) {
      commits.put(commitId, commit);
    }
    return commit;
  }

  private void commitCreationOrChange(Set<DataModelEntity> chores, Commit commit, LogicalObjectTree LOT, DataModelEntity dme) {
    //object contained in LOT, so it can be a change or exist because cross-referenced earlier
    if (LOT.containsValue(dme)) {
      LogicalObjectKey before = LOT.getKey(dme);
      LOT.removeValue(dme); //remove entry first or otherwise the createLogicalObjectKey method won't actually create a NEW key and puts it in LOT!
      LogicalObjectKey after = LOT.createLogicalObjectKey(dme);
      //make all LOKs previously subscribed to before subscribe to after and change their field!
      for (Map.Entry<LogicalObjectKey, Field> subscribed : before.subscribedLOKs.entrySet()) {
        subscribed.getKey().put(subscribed.getValue(), after);
        after.subscribedLOKs.put(subscribed.getKey(), subscribed.getValue());
      }
      commit.changeRecords.put(before, after);
    }
    //object not contained in LOT, so it must have been CREATED
    else {
      if (dme instanceof RootEntity)
        throw new RuntimeException("Root Entity can only be CHANGED by commits!");
      for (DataModelEntity obj : dme.getConstructorParamsAsObjects()) {
        if (chores.contains(obj)) {
          commitCreationOrChange(chores, commit, LOT, obj);
        }
      }
      //now its save to get the LOK from owner via LOT!
      LogicalObjectKey newKey = LOT.createLogicalObjectKey(dme);  //because this is creation and dme is not currently present in LOT, this generates a NEW key and puts it in LOT!
      commit.creationRecords.put(newKey, dme.getConstructorParamsAsKeys(LOT));
    }
    chores.remove(dme);
  }

  //---------------PULL---------------//

  boolean pull(RootEntity rootEntity) {
    return new Pull(rootEntity).pulledSomething;
  }

  //for local context
  private class Pull {
    boolean pulledSomething;
    Workcopy workcopy;
    LogicalObjectTree LOT;
    Map<LogicalObjectKey, Object[]> creationChores;
    Map<LogicalObjectKey, LogicalObjectKey> changeChores;
    HashMap<DataModelEntity, LokField> crossReferences = new HashMap<>();

    //this constructor is used to do an initializationPull
    private Pull(Workcopy workcopy, Commit initializationCommit) {
      this.workcopy = workcopy;
      workcopy.ongoingPull = true;
      pullOneCommit(initializationCommit);
      workcopy.ongoingPull = false;
      workcopy.currentCommitId = new CommitId(0);
    }

    private Pull(RootEntity rootEntity) {
      workcopy = workcopies.get(rootEntity);
      if (workcopy == null)
        throw new NoTransactionsEnabledException();
      List<Commit> commitsToPull;
      //make sure no commits are added to the commit-list while pull copies the list
      synchronized (commits) {
        if (commits.isEmpty())
          return;
        if (commits.lastKey() == workcopy.currentCommitId)
          return;
        workcopy.ongoingPull = true;
        commitsToPull = new ArrayList<>(commits.tailMap(workcopy.currentCommitId, false).values());
      }
      for (Commit commit : commitsToPull) {
        pullOneCommit(commit);
      }
      pulledSomething = true;
      workcopy.ongoingPull = false;
    }

    private void pullOneCommit(Commit commit) {
      if (commit.commitId != null)
        System.out.println("----------Pulling commit number " + commit.commitId.id + ":----------");
      //copy modificationRecords to safely cross things off without changing commit itself!
      creationChores = new HashMap<>(commit.creationRecords);
      changeChores   = new HashMap<>(commit.changeRecords);
      LOT = workcopy.LOT;

      //DELETION - Assumes Deletion Records are created for all subsequent children!!!
      for (Map.Entry<LogicalObjectKey, Object[]> entry : commit.deletionRecords.entrySet()) {
        ChildEntity<?> objectToDelete = (ChildEntity<?>) LOT.get(entry.getKey());
        for (Wrapper<?> wrapper : objectToDelete.getRegisteredWrappers().values()) {
          wrapper.onWrappedCleared();
        }
        destruct(objectToDelete);
        System.out.println("removed " + LOT.getKey(objectToDelete).clazz.getSimpleName() + ", key " + LOT.getKey(objectToDelete).hashCode());
        LOT.removeValue(objectToDelete);
      }
      //CREATION - Recursion possible because of dependency on owner and cross-references
      Map.Entry<LogicalObjectKey, Object[]> creationRecord;
      while (!creationChores.isEmpty()) {
        creationRecord = creationChores.entrySet().iterator().next();
        try {
          pullCreationRecord(creationRecord.getKey(), creationRecord.getValue());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      //CHANGE - Recursion possible because of dependency on cross-references
      Map.Entry<LogicalObjectKey, LogicalObjectKey> changeRecord;
      while (!changeChores.isEmpty()) {
        changeRecord = changeChores.entrySet().iterator().next();
        try {
          pullChangeRecord(changeRecord.getKey(), changeRecord.getValue());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      //at last link the open cross-reference dependencies
      for (Map.Entry<DataModelEntity, LokField> entry : crossReferences.entrySet()) {
        Field field = entry.getValue().field;
        field.setAccessible(true);
        try {
          if (LOT.get(entry.getValue().LOK) == null)
            throw new RuntimeException("error linking cross references!!!!");
          field.set(entry.getKey(), LOT.get(entry.getValue().LOK));
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      crossReferences.clear();
      workcopy.currentCommitId = commit.commitId;
    }

    //-----recursive functions-----//

    private void pullCreationRecord(LogicalObjectKey objKey, Object[] constKeys) throws IllegalAccessException {
      Object[] params = new Object[constKeys.length];
      for (int i=0; i<constKeys.length; i++) {
        Object key = constKeys[i];
        if (key instanceof LogicalObjectKey) {
          LogicalObjectKey LOK = (LogicalObjectKey) key;
          if (creationChores.containsKey(LOK)) {
            pullCreationRecord(LOK, creationChores.get(LOK));
          }
          if (changeChores.containsKey(LOK)) {
            pullChangeRecord(LOK, changeChores.get(LOK));
          }
          //now object can be safely assigned using LOT
          params[i] = LOT.get(key);
        }
        else {
          params[i] = key;
        }
      }
      DataModelEntity objectToCreate = construct(objKey.clazz, params);
      imprintLogicalContentOntoObject(objKey, objectToCreate);
      System.out.println("created " + objKey.clazz.getSimpleName() + ", key " + objKey.hashCode());
      LOT.put(objKey, objectToCreate);
      creationChores.remove(objKey);
    }

    private void pullChangeRecord(LogicalObjectKey before, LogicalObjectKey after) throws IllegalAccessException {
      DataModelEntity objectToChange = LOT.get(before);
      if (objectToChange == null)
        throw new RuntimeException("object to change is null!!!!,key "+before.hashCode());
      imprintLogicalContentOntoObject(after, objectToChange);
      for (Wrapper<?> wrapper : objectToChange.getRegisteredWrappers().values())
        wrapper.onDataChange();
      System.out.println("changed " + LOT.getKey(objectToChange).clazz.getSimpleName() + ", key " + LOT.getKey(objectToChange).hashCode() + " to key " + after.hashCode());
      LOT.put(after, objectToChange);
      changeChores.remove(before);
    }

    private void imprintLogicalContentOntoObject(LogicalObjectKey LOK, DataModelEntity dme) throws IllegalAccessException {
      for (Field field : getContentFields(dme)) {
        if (LOK.containsKey(field)) {
          //save cross references to do at the very end to avoid infinite recursion when cross-references point at each other!
          if (field.getAnnotation(CrossReference.class) != null) {
            if (LOK.get(field) != null)
              crossReferences.put(dme, new LokField((LogicalObjectKey) LOK.get(field), field));
            else {
              field.setAccessible(true);
              field.set(dme, null);
            }
            continue;
          }
          //set the field
          field.setAccessible(true);
          field.set(dme, LOK.get(field));
        }
      }
    }
  }


  //=====getting and caching class fields and methods===============================================

  static ArrayList<ChildEntity<?>> getChildren(DataModelEntity dme) {
    if (!dataModelInfo.containsKey(dme.getClass()))
      dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), dme.getClassesOfConstructorParams()));
    return dataModelInfo.get(dme.getClass()).getChildren(dme);
  }

  static Field[] getContentFields(DataModelEntity dme) {
    if (!dataModelInfo.containsKey(dme.getClass()))
      dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), dme.getClassesOfConstructorParams()));
    return dataModelInfo.get(dme.getClass()).contentFields;
  }

  static DataModelEntity construct(Class<? extends DataModelEntity> clazz, Object...objects) {
    if (!dataModelInfo.containsKey(clazz)) {
      Class<?>[] classes = new Class<?>[objects.length];
      for (int i=0; i<objects.length; i++) {
        classes[i] = objects[i].getClass();
      }
      dataModelInfo.put(clazz, new DataModelInfo(clazz, classes));
    }
    return dataModelInfo.get(clazz).construct(objects);
  }

  static void destruct(ChildEntity<?> te) {
    if (!dataModelInfo.containsKey(te.getClass()))
      dataModelInfo.put(te.getClass(), new DataModelInfo(te.getClass(), te.getClassesOfConstructorParams()));
    dataModelInfo.get(te.getClass()).destruct(te);
  }



}