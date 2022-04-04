package com.immersive.aspects;

import com.immersive.core.TransactionManager;
import com.immersive.abstractions.*;
import com.immersive.core.Workcopy;
import com.immersive.annotations.*;
import com.immersive.test_model.*;

privileged aspect EntityStateListener {
private static TransactionManager tm = TransactionManager.getInstance();

    pointcut contentFieldSetter(DataModelEntity dme) : set(!static !final * DataModelEntity+.*)
        && target(dme)
        && !@annotation(ChildField);


    pointcut creation(ChildEntity te, DataModelEntity owner) : execution(ChildEntity+.new(..))
    && !execution(ChildEntity.new(..))
    && !execution(KeyedChildEntity.new(..))
                && target(te)
                && args(owner,..);


    pointcut deletion(ChildEntity te) : execution(* ChildEntity+.clear())
        && target(te);

    //this will not be called when pulling changes cause reflections do not trigger set!
    before(DataModelEntity dme) : contentFieldSetter(dme) {
        Workcopy workcopy = getWorkcopy(dme);
        if (workcopy != null) {
            if (!workcopy.ongoingCreation) {
                System.out.println(dme.getClass().getSimpleName()+ " change detected");
                workcopy.locallyChangedOrCreated.add(dme);
            }
        }
    }

    //creations triggered by pull must be stopped here!
    Object around(ChildEntity te, DataModelEntity owner) : creation(te, owner) {
        Workcopy workcopy = getWorkcopy(owner);
        if (workcopy != null) {
            if (!workcopy.ongoingPull) {
                 System.out.println(te.getClass().getSimpleName()+" got created");
                 workcopy.ongoingCreation = true;
                 Object object = proceed(te, owner);
                 workcopy.ongoingCreation = false;
                 workcopy.locallyChangedOrCreated.add(te);
                 return object;
            }
            else {
                 return proceed(te, owner);
            }
        }
        else {
            return proceed(te, owner);
        }
    }

    //not triggered by pulling because pulling uses its separate destructor methods
    before(ChildEntity te) : deletion(te) {
        Workcopy workcopy = getWorkcopy(te);
        if (workcopy != null) {
            workcopy.locallyDeleted.add(te);
            System.out.println(te.getClass().getSimpleName() + " got deleted");
        }
    }

    private Workcopy getWorkcopy(DataModelEntity dme) {
        return tm.workcopies.get(dme.getRootEntity());
      }
}