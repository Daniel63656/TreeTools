package com.immersive.transactions;

import com.immersive.annotations.CrossReference;
import com.immersive.transactions.exceptions.NoTransactionsEnabledException;
import com.immersive.transactions.exceptions.TransactionException;
import com.immersive.wrap.Wrapper;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TransactionManager {
  private static final TransactionManager transactionManager = new TransactionManager();
  private int commitID = 1;
  private TransactionManager() {}
  public static TransactionManager getInstance() {
    return transactionManager;
  }

  static Map<Class<? extends DataModelEntity>, DataModelInfo> dataModelInfo = new HashMap<>();
  final TreeMap<CommitId, Commit> commits = new TreeMap<>();
  History history;
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
      throw new RuntimeException("Transactions already enabled! Use getWorkcopyOf() to receive another copy to work on.");
    }
    else {
      LogicalObjectTree LOT = new LogicalObjectTree();
      Workcopy workcopy = new Workcopy(rootEntity, LOT, new CommitId(0));
      buildLogicalObjectTree(LOT, rootEntity);
      workcopies.put(rootEntity, workcopy);
    }
  }

  public void enableUndoRedos(int capactity) {
    history = new History(capactity);
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
    //copy content of root entity
    for (Field field : getContentFields(newRootEntity)) {
      if (field.getAnnotation(CrossReference.class) != null)
        throw new RuntimeException("Cross references not allowed in Root Entity!");
      field.setAccessible(true);
      try {
        field.set(newRootEntity, field.get(rootEntity));
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }
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
    LOT.createLogicalObjectKey(dme, null, false);
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

  static class CrossReferenceToDo {
    DataModelEntity dme;
    LogicalObjectKey before;  //logged to fix cross-references for non changing objects. null for creations
    LogicalObjectKey objectKey, crossReferenceKey;
    Field ObjectField;
    public CrossReferenceToDo(DataModelEntity dme, LogicalObjectKey before, LogicalObjectKey objectKey, LogicalObjectKey crossReferenceKey, Field ObjectField) {
      this.dme = dme;
      this.before = before;
      this.objectKey = objectKey;
      this.crossReferenceKey = crossReferenceKey;
      this.ObjectField = ObjectField;
    }
  }

  //=========these methods are synchronized per RootEntity and therefore package-private============

  Commit commit(RootEntity rootEntity) {
    Workcopy workcopy = workcopies.get(rootEntity);
    if (workcopy == null)
      throw new NoTransactionsEnabledException();
    if (workcopy.locallyChangedOrCreated.isEmpty() && workcopy.locallyDeleted.isEmpty())
      return null;

    CommitId commitId = new CommitId(commitID);
    commitID++;
    Commit commit = new Commit(commitId);
    LogicalObjectTree remote = workcopy.LOT; //This corresponds to the rollback version or "remote"
    List<ChildEntity<?>> removeFromLOT = new ArrayList<>();
    Set<LogicalObjectKey> keysCreatedSoFar = new HashSet<>();

    //create ModificationRecords for CREATED or CHANGED objects
    while (!workcopy.locallyChangedOrCreated.isEmpty()) {
      DataModelEntity dme = workcopy.locallyChangedOrCreated.iterator().next();
      //deletion OVERRIDES creation or change!
      if (dme instanceof ChildEntity<?>) {
        if (workcopy.locallyDeleted.contains(dme)) {
          workcopy.locallyChangedOrCreated.remove(dme);
          //remove deletion instruction if chore was a creation
          if (!remote.containsValue(dme) || (remote.containsValue(dme) && keysCreatedSoFar.contains(remote.getKey(dme))))
            workcopy.locallyDeleted.remove(dme);
          continue;
        }
      }
      commitCreationOrChange(workcopy.locallyChangedOrCreated, commit, remote, dme, keysCreatedSoFar);
    }

    //create ModificationRecords for DELETED objects
    Iterator<ChildEntity<?>> iterator = workcopy.locallyDeleted.iterator();
    while (iterator.hasNext()) {
      ChildEntity<?> te = iterator.next();
      commit.deletionRecords.put(remote.getKey(te), te.getConstructorParamsAsKeys(remote));
      //don't remove from remote yet, because this destroys owner information for possible deletion entries below!
      //save this action in a list to do it at the end of the deletion part of the commit instead
      removeFromLOT.add(te);
      iterator.remove();
    }
    //remove all entries from remote
    for (ChildEntity<?> te : removeFromLOT) {
      remote.removeValue(te);
    }
    workcopy.locallyDeleted.clear();
    workcopy.locallyChangedOrCreated.clear();
    workcopy.currentCommitId = commitId;
    synchronized (commits) {
      commits.put(commitId, commit);
      if (history != null)
        history.addToOngoingCommit(commit);
    }
    return commit;
  }

  private void commitCreationOrChange(Set<DataModelEntity> chores, Commit commit, LogicalObjectTree remote, DataModelEntity dme, Set<LogicalObjectKey> keysCreatedSoFar) {
    //CREATION - not in remote before or only because a cross-reference created it!
    if (!remote.containsValue(dme) || (remote.containsValue(dme) && keysCreatedSoFar.contains(remote.getKey(dme)))) {
      if (dme instanceof RootEntity)
        throw new RuntimeException("Root Entity can only be CHANGED by commits!");
      for (DataModelEntity obj : dme.getConstructorParamsAsObjects()) {
        if (chores.contains(obj)) {
          commitCreationOrChange(chores, commit, remote, obj, keysCreatedSoFar);
        }
      }
      //now its save to get the LOK from owner via remote!
      LogicalObjectKey newKey = remote.createLogicalObjectKey(dme, keysCreatedSoFar, false);  //because this is creation and dme is not currently present in remote, this generates a NEW key and puts it in remote!
      keysCreatedSoFar.add(newKey);
      commit.creationRecords.put(newKey, dme.getConstructorParamsAsKeys(remote));
    }
    else {
      LogicalObjectKey before = remote.getKey(dme);
      remote.removeValue(dme); //remove entry first or otherwise the createLogicalObjectKey method won't actually create a NEW key and puts it in remote!
      LogicalObjectKey after = remote.createLogicalObjectKey(dme, keysCreatedSoFar, false);
      keysCreatedSoFar.add(after);
      //linking cross references together
      for (Map.Entry<LogicalObjectKey, Field> subscribed : before.subscribedLOKs.entrySet()) {
        if (!keysCreatedSoFar.contains(subscribed.getKey()))  //object subscribed is not itself part of a change so add a chore!
          chores.add(remote.get(subscribed.getKey()));
        else {                                                //make all LOKs previously subscribed to before subscribe to after and change their field!
          after.subscribedLOKs.put(subscribed.getKey(), subscribed.getValue());
          subscribed.getKey().put(subscribed.getValue(), after);
        }
      }
      commit.changeRecords.put(before, after);
    }
    chores.remove(dme);
  }

  void undo(RootEntity rootEntity) {
    if (history == null)
      throw new RuntimeException("Undos/Redos were not enabled!");
    if (history.undosAvailable()) {
      Commit commit = history.head.self;
      commit.commitId = new CommitId(commitID);
      commitID++;
      new Pull(rootEntity, commit, true);
      //revert the commit for commit-list!
      Map<LogicalObjectKey, Object[]> temp = commit.creationRecords;
      commit.creationRecords = commit.deletionRecords;
      commit.deletionRecords = temp;
      DualHashBidiMap<LogicalObjectKey, LogicalObjectKey> changes = new DualHashBidiMap<>();
      for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : commit.changeRecords.entrySet())
        changes.put(entry.getValue(), entry.getKey());
      commit.changeRecords = changes;
      synchronized (commits) {
        commits.put(commit.commitId, commit);
      }
    }
  }

  void redo(RootEntity rootEntity) {
    if (history == null)
      throw new RuntimeException("Undos/Redos were not enabled!");
    if (history.redosAvailable()) {
      Commit commit = history.head.next.self;
      commit.commitId = new CommitId(commitID);
      commitID++;
      new Pull(rootEntity, commit, false);
      synchronized (commits) {
        commits.put(commit.commitId, commit);
      }
    }
  }

  public void createUndoState() {
    if (history == null)
      throw new RuntimeException("Undos/Redos were not enabled!");
    history.archive();
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
    Map<LogicalObjectKey, Object[]> creationChores, deletionChores;
    Map<LogicalObjectKey, LogicalObjectKey> changeChores;
    List<CrossReferenceToDo> crossReferences = new ArrayList<>();

    //this constructor is used to do an initializationPull
    private Pull(Workcopy workcopy, Commit initializationCommit) {
      this.workcopy = workcopy;
      workcopy.ongoingPull = true;
      pullOneCommit(initializationCommit, false);
      workcopy.ongoingPull = false;
      workcopy.currentCommitId = new CommitId(0);
    }
    //used for redos/undos
    private Pull(RootEntity rootEntity, Commit commit, boolean revert) {
      workcopy = workcopies.get(rootEntity);
      if (workcopy == null)
        throw new NoTransactionsEnabledException();
      workcopy.ongoingPull = true;
      pullOneCommit(commit, revert);
      workcopy.ongoingPull = false;
      workcopy.currentCommitId = commit.commitId;
      cleanUpUnnecessaryCommits();
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
        pullOneCommit(commit, false);
      }
      if (!commitsToPull.isEmpty())
        pulledSomething = true;
      workcopy.ongoingPull = false;
      cleanUpUnnecessaryCommits();
    }

    private void pullOneCommit(Commit commit, boolean revert) {
      if (!revert) {
        //copy modificationRecords to safely cross things off without changing commit itself!
        creationChores = new HashMap<>(commit.creationRecords);
        deletionChores = commit.deletionRecords;  //no removing
        changeChores = new HashMap<>();           //map changeChores with AFTER as key!
        for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : commit.changeRecords.entrySet())
          changeChores.put(entry.getValue(), entry.getKey());
      }
      else {
        creationChores = new HashMap<>(commit.deletionRecords);
        deletionChores = commit.creationRecords;            //no removing
        changeChores = new HashMap<>(commit.changeRecords); //map changeChores with AFTER as key!
      }
      LOT = workcopy.LOT;

      //DELETION - Assumes Deletion Records are created for all subsequent children!!!
      for (Map.Entry<LogicalObjectKey, Object[]> entry : deletionChores.entrySet()) {
        ChildEntity<?> objectToDelete = (ChildEntity<?>) LOT.get(entry.getKey());
        for (Wrapper<?> wrapper : objectToDelete.getRegisteredWrappers().values()) {
          wrapper.onWrappedCleared();
        }
        for (Wrapper<?> wrapper : objectToDelete.getOwner().getRegisteredWrappers().values()) {
          wrapper.onDataChange();
        }
        destruct(objectToDelete);
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
          pullChangeRecord(changeRecord.getValue(), changeRecord.getKey());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      //at last link the open cross-reference dependencies
      for (CrossReferenceToDo cr : crossReferences) {
        cr.ObjectField.setAccessible(true);
        try {
          if (LOT.get(cr.crossReferenceKey) == null)
            throw new TransactionException("error linking cross references for "+LOT.getKey(cr.dme).hashCode(), cr.crossReferenceKey.hashCode());
          cr.ObjectField.set(cr.dme, LOT.get(cr.crossReferenceKey));
          //remove old value from subscribers if existing
          //cr.crossReferenceKey.subscribedLOKs.remove(cr.before);
          //cr.crossReferenceKey.subscribedLOKs.put(cr.objectKey, cr.ObjectField);  //put new value in
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      crossReferences.clear();
      workcopy.currentCommitId = commit.commitId;
    }

    private void cleanUpUnnecessaryCommits() {
      synchronized(commits) {
        CommitId earliestCommitInUse = workcopy.currentCommitId;
        for (Workcopy workcopy : workcopies.values()) {
          if (workcopy.currentCommitId.compareTo(earliestCommitInUse) < 0)
            earliestCommitInUse = workcopy.currentCommitId;
        }
        commits.headMap(earliestCommitInUse, true).clear();
      }
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
          //check if AFTER exists in changeRecords!
          if (changeChores.containsKey(LOK)) {
            pullChangeRecord(changeChores.get(LOK), LOK);
          }
          //now object can be safely assigned using LOT
          params[i] = LOT.get(key);
        }
        else {
          params[i] = key;
        }
      }
      DataModelEntity objectToCreate = construct(objKey.clazz, params);
      imprintLogicalContentOntoObject(null, objKey, objectToCreate);
      if (objectToCreate instanceof ChildEntity) {
        for (Wrapper<?> wrapper : ((ChildEntity<?>) objectToCreate).getOwner().getRegisteredWrappers().values())
          wrapper.onDataChange();
      }
      //System.out.println("PULL: created key "+objKey.hashCode());
      LOT.put(objKey, objectToCreate);
      creationChores.remove(objKey);
    }

    private void pullChangeRecord(LogicalObjectKey before, LogicalObjectKey after) throws IllegalAccessException {
      DataModelEntity objectToChange = LOT.get(before);
      if (objectToChange == null)
        throw new TransactionException("object to change is null!", before.hashCode());
      imprintLogicalContentOntoObject(before, after, objectToChange);
      for (Wrapper<?> wrapper : objectToChange.getRegisteredWrappers().values())
        wrapper.onDataChange();
      //System.out.println("PULL: changed from "+before.hashCode()+" to "+after.hashCode());
      LOT.put(after, objectToChange);
      changeChores.remove(after);
    }

    private void imprintLogicalContentOntoObject(LogicalObjectKey before, LogicalObjectKey after, DataModelEntity dme) throws IllegalAccessException {
      for (Field field : getContentFields(dme)) {
        if (after.containsKey(field)) {
          //save cross references to do at the very end to avoid infinite recursion when cross-references point at each other!
          if (field.getAnnotation(CrossReference.class) != null) {
            if (after.get(field) != null)
              crossReferences.add(new CrossReferenceToDo(dme, before, after, (LogicalObjectKey) after.get(field), field));
            else {
              field.setAccessible(true);
              field.set(dme, null);
            }
            continue;
          }
          //set the field
          field.setAccessible(true);
          field.set(dme, after.get(field));
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