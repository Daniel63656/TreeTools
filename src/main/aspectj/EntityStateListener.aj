import com.immersive.transactions.*;

//for aspectJ syntax see https://www.eclipse.org/aspectj/doc/next/quick5.pdf

privileged aspect EntityStateListener {
private static TransactionManager tm = TransactionManager.getInstance();

    //create a pointcut to detect changes made to all non static, non final, non transient fields of any return type
    //for all classes extending MutableObject in any method
    pointcut contentFieldSetter(MutableObject dme, Object newValue) : set(!static !final !transient * MutableObject+.*)
        && target(dme)
        && args(newValue);

    //fields set with reflections in a pull do not trigger this aspect
    before(MutableObject dme, Object newValue) : contentFieldSetter(dme, newValue) {
        Repository repository = tm.repositories.get(dme.getRootEntity());
        if (repository != null) {
            repository.logLocalChange(dme);
            dme.notifyRegisteredWrappersAboutChange();
        }
    }
}