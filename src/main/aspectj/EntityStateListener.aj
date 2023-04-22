import com.immersive.transactions.*;

privileged aspect EntityStateListener {
private static TransactionManager tm = TransactionManager.getInstance();

    pointcut contentFieldSetter(DataModelEntity dme, Object newValue) : set(!static !final (* && !DataModelEntity) DataModelEntity+.*)
        && target(dme)
        && args(newValue);

    //this will not be called when pulling changes cause reflections do not trigger set but redos!
    before(DataModelEntity dme, Object newValue) : contentFieldSetter(dme, newValue) {
        Workcopy workcopy = tm.workcopies.get(dme.getRootEntity());
        if (workcopy != null && !workcopy.ongoingPull) {
            workcopy.logLocalChange(dme);
        }
    }
}