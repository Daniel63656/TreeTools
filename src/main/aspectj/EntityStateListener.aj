import com.immersive.transactions.*;

//for aspectJ syntax see https://www.eclipse.org/aspectj/doc/next/quick5.pdf

privileged aspect EntityStateListener {
private static TransactionManager tm = TransactionManager.getInstance();

    //create a pointcut to detect changes made to all non static, non final, non transient fields of any return type
    //for all classes extending MutableObject in any method
    pointcut contentFieldSetter(MutableObject dme, Object newValue) : set(!static !final !transient * MutableObject+.*)
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