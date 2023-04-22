import com.immersive.transactions.*;

privileged aspect EntityStateListener {
private static TransactionManager tm = TransactionManager.getInstance();

    //create a pointcut to detect changes made to all non static, non final, non transient fields of a MutableObject
    //exclude references to other MutableObjects
    pointcut contentFieldSetter(MutableObject dme, Object newValue) : set(!static !final !transient (* && !MutableObject) MutableObject+.*)
        && target(dme)
        && args(newValue);

    //this will not be called when pulling changes cause reflections do not trigger set but redos!
    before(MutableObject dme, Object newValue) : contentFieldSetter(dme, newValue) {
        Repository repo = tm.repositories.get(dme.getRootEntity());
        if (repo != null && !repo.ongoingPull) {
            repo.logLocalChange(dme);
        }
    }
}