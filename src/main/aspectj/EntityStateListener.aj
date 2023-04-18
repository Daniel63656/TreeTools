import com.immersive.transactions.*;

privileged aspect EntityStateListener {
private static TransactionManager tm = TransactionManager.getInstance();

    pointcut contentFieldSetter(DataModelEntity dme, Object newValue) : set(!static !final (* && !DataModelEntity) DataModelEntity+.*)
        && target(dme)
        && args(newValue);

    pointcut creation(ChildEntity te, DataModelEntity owner) : execution(ChildEntity+.new(..))
        && !execution(ChildEntity.new(..))
        && !execution(KeyedChildEntity.new(..))
        && !execution(DoubleKeyedChildEntity.new(..))
        && target(te)
        && args(owner,..);

    //this will not be called when pulling changes cause reflections do not trigger set but redos!
    before(DataModelEntity dme, Object newValue) : contentFieldSetter(dme, newValue) {
        Workcopy workcopy = getWorkcopy(dme);
        if (workcopy != null && !workcopy.ongoingPull) {
            if (tm.logAspects && !workcopy.locallyChangedOrCreated.contains(dme))
                System.out.println(dme.getClass().getSimpleName()+ " marked as 'changed' by altering " + thisJoinPoint.getSignature().getName());
            workcopy.locallyChangedOrCreated.add(dme);
        }
    }

    //creations triggered by pull must be stopped here!
    before(ChildEntity te, DataModelEntity owner) : creation(te, owner) {
        Workcopy workcopy = getWorkcopy(owner);
        if (workcopy != null && !workcopy.ongoingPull) {
            if (tm.logAspects && !workcopy.locallyChangedOrCreated.contains(te))
                System.out.println(te.getClass().getSimpleName()+" got created");
            workcopy.locallyChangedOrCreated.add(te);
        }
    }

    private Workcopy getWorkcopy(DataModelEntity dme) {
        return tm.workcopies.get(dme.getRootEntity());
    }
}