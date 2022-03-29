package com.immersive.core;

import com.immersive.annotations.CrossReference;
import com.immersive.annotations.DataModelEntity;
import com.immersive.annotations.RootEntity;
import com.immersive.annotations.TransactionalEntity;

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
    private TransactionManager() {}
    public static TransactionManager getInstance() {
      return transactionManager;
    }

    static Map<Class<? extends DataModelEntity>, DataModelInfo> dataModelInfo = new HashMap<>();
    TreeMap<CommitId,Commit> commits = new TreeMap<>();
    Map<RootEntity, Workcopy> workcopies = new HashMap<>();


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
      throw new RuntimeException("No Transactions enabled for specified RootEntity to create a workcopy of. Use enableTransactionsForRootEntity() first to enable Transactions for your RootEntity.");
    Workcopy copyFrom = workcopies.get(rootEntity);
    LogicalObjectTree LOT = new LogicalObjectTree();
    Set<CrossReferenceToDo> crtd = new HashSet<>();

    try {
      RootEntity newRootEntity = (RootEntity) buildJOTAndCopyLOT(crtd, copyFrom.LOT, LOT, copyFrom.rootEntity, null);
      //do all the undone cross references
      for (CrossReferenceToDo c : crtd) {
        c.setCrossReference(LOT);
      }
      Workcopy workcopy = new Workcopy(newRootEntity, LOT, new CommitId(0));
      workcopies.put(newRootEntity, workcopy);
      return workcopy.rootEntity;
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    throw new RuntimeException("Failed to create Workcopy!");
  }

  public void shutdown() {
    commits.clear();
    workcopies.clear();
  }

  static ArrayList<TransactionalEntity<?>> getChildren(DataModelEntity dme) {
    if (!dataModelInfo.containsKey(dme.getClass())) {
      if (dme instanceof RootEntity)
        dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), null));
      else
        dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), ((DataModelEntity)((TransactionalEntity<?>) dme).getOwner()).getClass()));
    }
    return dataModelInfo.get(dme.getClass()).getChildren(dme);
  }

  static Field[] getContentFields(DataModelEntity dme) {
    if (!dataModelInfo.containsKey(dme.getClass())) {
      if (dme instanceof RootEntity)
        dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), null));
      else
        dataModelInfo.put(dme.getClass(), new DataModelInfo(dme.getClass(), ((DataModelEntity)((TransactionalEntity<?>) dme).getOwner()).getClass()));
    }
    return dataModelInfo.get(dme.getClass()).contentFields;
  }

  static DataModelEntity construct(Class<? extends DataModelEntity> clazz, DataModelEntity owner) {
    if (!dataModelInfo.containsKey(clazz))
      dataModelInfo.put(clazz, new DataModelInfo(clazz, owner.getClass()));
    return dataModelInfo.get(clazz).construct(owner);
  }

  static void destruct(TransactionalEntity<?> te) {
    if (!dataModelInfo.containsKey(te.getClass()))
      dataModelInfo.put(te.getClass(), new DataModelInfo(te.getClass(), ((DataModelEntity) te.getOwner()).getClass()));
    dataModelInfo.get(te.getClass()).destruct(te);
  }

  private void buildLogicalObjectTree(LogicalObjectTree LOT, DataModelEntity dme) {
    LOT.createLogicalObjectKey(dme);
    ArrayList<TransactionalEntity<?>> children = getChildren(dme);
    if (children != null) {
      for (TransactionalEntity<?> child : children) {
        buildLogicalObjectTree(LOT, child);
      }
    }
  }

  private DataModelEntity buildJOTAndCopyLOT(Set<CrossReferenceToDo> crtd, LogicalObjectTree LOT_from, LogicalObjectTree LOT_to, DataModelEntity dme_from, DataModelEntity owner_to) throws IllegalAccessException {
    DataModelEntity dme_to = construct(dme_from.getClass(), owner_to);
    LogicalObjectKey LOK_from = LOT_from.getKey(dme_from);
    LOT_to.put(LOK_from, dme_to);

    for (Field field : getContentFields(dme_to)) {
      if (LOK_from.containsKey(field)) {
        if (field.getAnnotation(CrossReference.class) != null) {
          LogicalObjectKey LOKOfCrossReference = (LogicalObjectKey) LOK_from.get(field);
          //check if cross-reference got already created. null will always lead to a false here
          if (LOT_to.containsKey(LOKOfCrossReference)) {
            field.setAccessible(true);
            field.set(dme_to, LOT_to.get(LOKOfCrossReference));
          }
          else {
            if (LOKOfCrossReference == null)
              continue;
            //do this field once complete non cross-dependent tree has been build
            crtd.add(new CrossReferenceToDo(dme_to, field, LOKOfCrossReference));
          }
        }
        else {
          //set the primitive field
          field.setAccessible(true);
          field.set(dme_to, LOK_from.get(field));
        }
      }
    }

    ArrayList<TransactionalEntity<?>> children = getChildren(dme_from);
    for (TransactionalEntity<?> child : children) {
      buildJOTAndCopyLOT(crtd, LOT_from, LOT_to, child, dme_to);
    }
    return dme_to;
  }

  //simple class to cache the action of setting a cross-reference
  private static class CrossReferenceToDo {
    DataModelEntity dme;
    Field crossReference;
    LogicalObjectKey crossReferenceLOK;

    CrossReferenceToDo(DataModelEntity dme, Field crossReference, LogicalObjectKey crossReferenceLOK) {
      this.dme = dme;
      this.crossReference = crossReference;
      this.crossReferenceLOK = crossReferenceLOK;
    }

    void setCrossReference(LogicalObjectTree LOT) throws IllegalAccessException {
      crossReference.setAccessible(true);
      crossReference.set(dme, LOT.get(crossReferenceLOK));
    }
  }

  //---------------COMMIT---------------//

    public void commit(RootEntity rootEntity) {
      Workcopy workcopy = workcopies.get(rootEntity);
      if (workcopy == null)
        throw new RuntimeException("No Transactions enabled for specified RootEntity. Use enableTransactionsForRootEntity() first to enable Transactions for your RootEntity.");
      if (workcopy.locallyChangedOrCreated.isEmpty() && workcopy.locallyDeleted.isEmpty())
        return;

      CommitId commitId = new CommitId(commits.size()+1);
      Commit commit = new Commit(commitId);
      LogicalObjectTree LOT = workcopy.LOT; //This corresponds to the rollback version or "remote"
      List<TransactionalEntity<?>> removeFromLOT = new ArrayList<>();
      Set<DataModelEntity> chores = new HashSet<>(workcopy.locallyChangedOrCreated);

      //create ModificationRecords for CREATED or CHANGED objects
      while (!chores.isEmpty()) {
        DataModelEntity dme = chores.iterator().next();
        //mdeletion OVERRIDES creation or change!
        if (dme instanceof TransactionalEntity<?>) {
          if (workcopy.locallyDeleted.contains(dme)) {
            workcopy.locallyDeleted.remove(dme);
            chores.remove(dme);
            continue;
          }
        }
        commitCreationOrChange(chores, commit, LOT, dme);
      }

      //create ModificationRecords for DELETED objects
      Iterator<TransactionalEntity<?>> iterator = workcopy.locallyDeleted.iterator();
      while (iterator.hasNext()) {
        TransactionalEntity<?> te = iterator.next();
        commit.deletionRecords.put(LOT.getKey(te), LOT.getLogicalObjectKeyOfOwner(te));
        //don't remove from LOT yet, because this destroys owner information for possible deletion entries below!
        //save this action in a list to do it at the end of the deletion part of the commit instead
        removeFromLOT.add(te);
        iterator.remove();
      }
      //remove all entries from LOT
      for (TransactionalEntity<?> te : removeFromLOT) {
        LOT.removeValue(te);
      }
      workcopy.locallyDeleted.clear();
      workcopy.locallyChangedOrCreated.clear();
      commits.put(commitId, commit);
    }

    private void commitCreationOrChange(Set<DataModelEntity> chores, Commit commit, LogicalObjectTree LOT, DataModelEntity dme) {
      //object contained in LOT, so it must be a CHANGE
      if (LOT.containsValue(dme)) {
        LogicalObjectKey before = LOT.getKey(dme);
        LOT.removeValue(dme); //remove entry first or otherwise the createLogicalObjectKey method won't actually create a NEW key and puts it in LOT!
        LogicalObjectKey after = LOT.createLogicalObjectKey(dme);
        commit.changeRecords.put(before, after);
      }
      //object not contained in LOT, so it must have been CREATED
      else {
        if (dme instanceof RootEntity) {
          throw new RuntimeException("Root Entity can only be CHANGED by commits!");
        } else {
          TransactionalEntity<?> te = (TransactionalEntity<?>) dme;
          if (chores.contains(te.getOwner())) {
            commitCreationOrChange(chores, commit, LOT, te.getOwner());
          }
          //now its save to get the LOK from owner via LOT!
          LogicalObjectKey newKey = LOT.createLogicalObjectKey(dme);  //because this is creation and dme is not currently present in LOT, this generates a NEW key and puts it in LOT!
          commit.creationRecords.put(newKey, LOT.getLogicalObjectKeyOfOwner(te));
        }
      }
      chores.remove(dme);
    }


  //---------------PULL---------------//
  
  public void pull(RootEntity rootEntity) {
    new Pull(rootEntity);
  }

  //for local context
  private class Pull {
    Map<LogicalObjectKey, LogicalObjectKey> creationChores;
    Map<LogicalObjectKey, LogicalObjectKey> changeChores;
    Workcopy workcopy;
    LogicalObjectTree LOT;

    private Pull(RootEntity rootEntity) {
      workcopy = workcopies.get(rootEntity);
      if (workcopy == null)
        throw new RuntimeException("No Transactions enabled for specified RootEntity.");
      if (commits.isEmpty())
        throw new RuntimeException("No commits found!");
      if (commits.lastKey() == workcopy.currentCommitId)
        return;
      workcopy.ongoingPull = true;
      for (Commit commit : commits.tailMap(workcopy.currentCommitId, false).values()) {
        pullOneCommit(commit);
      }
      workcopy.ongoingPull = false;
      workcopy.currentCommitId = commits.lastKey();
    }

    private void pullOneCommit(Commit commit) {
      //copy modificationRecords to safely cross things off without changing commit itself!
      creationChores = new HashMap<>(commit.creationRecords);
      changeChores   = new HashMap<>(commit.changeRecords);
      LOT = workcopy.LOT;
      Map.Entry<LogicalObjectKey, LogicalObjectKey> modificationRecord; //create Map.Entry that picks a random entry from Map until Map is empty

      //DELETION - Assumes Deletion Records are created for all subsequent children!!!
      for (Map.Entry<LogicalObjectKey, LogicalObjectKey> entry : commit.deletionRecords.entrySet()) {
        TransactionalEntity<?> objectToDelete = (TransactionalEntity<?>) LOT.get(entry.getKey());
        destruct(objectToDelete);
        LOT.removeValue(objectToDelete);
      }
      //CREATION - Recursion possible because of dependency on owner and cross-references
      while (!creationChores.isEmpty()) {
        modificationRecord = creationChores.entrySet().iterator().next();
        try {
          pullCreationRecord(modificationRecord.getKey(), modificationRecord.getValue());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      //CHANGE - Recursion possible because of dependency on cross-references
      while (!changeChores.isEmpty()) {
        modificationRecord = changeChores.entrySet().iterator().next();
        try {
          pullChangeRecord(modificationRecord.getKey(), modificationRecord.getValue());
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }
    
    //-----recursive functions-----//

    private void pullCreationRecord(LogicalObjectKey objKey, LogicalObjectKey ownerKey) throws IllegalAccessException {
      if (creationChores.containsKey(ownerKey)) {
        pullCreationRecord(ownerKey, creationChores.get(ownerKey));
      }
      if (changeChores.containsKey(ownerKey)) {
        pullChangeRecord(ownerKey, creationChores.get(ownerKey));
      }
      DataModelEntity objectToCreate = construct(objKey.clazz, LOT.get(ownerKey));
      imprintLogicalContentOntoObject(objKey, objectToCreate);
      LOT.put(objKey, objectToCreate);
      creationChores.remove(objKey);
    }

    private void pullChangeRecord(LogicalObjectKey before, LogicalObjectKey after) throws IllegalAccessException {
      DataModelEntity objectToChange = LOT.get(before);
      imprintLogicalContentOntoObject(after, objectToChange);
      LOT.put(after, objectToChange);
      changeChores.remove(before);
    }

    private void imprintLogicalContentOntoObject(LogicalObjectKey LOK, DataModelEntity dme) throws IllegalAccessException {
      for (Field field : getContentFields(dme)) {
        if (LOK.containsKey(field)) {
          //handle dependencies on cross-references first!
          if (field.getAnnotation(CrossReference.class) != null) {
            LogicalObjectKey crossReferenceKey = (LogicalObjectKey) LOK.get(field);
            if (creationChores.containsKey(crossReferenceKey)) {
              pullCreationRecord(crossReferenceKey, creationChores.get(crossReferenceKey));
            }
            if (changeChores.containsKey(crossReferenceKey)) {
              pullChangeRecord(crossReferenceKey, creationChores.get(crossReferenceKey));
            }
            field.setAccessible(true);
            field.set(dme, LOT.get(crossReferenceKey));
            continue;
          }
          //set the field
          field.setAccessible(true);
          field.set(dme, LOK.get(field));
        }
      }
    }
  }



}