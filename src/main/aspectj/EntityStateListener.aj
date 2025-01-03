import net.scoreworks.treetools.*;

//for aspectJ syntax see https://www.eclipse.org/aspectj/doc/next/quick5.pdf

privileged aspect EntityStateListener {
private static TransactionManager tm = TransactionManager.getInstance();

    //create a pointcut to detect changes made to all non static, non final, non transient fields of any return type
    //for all classes extending MutableObject in any method
    pointcut contentFieldSetter(MutableObject mo, Object newValue) : set(!static !final !transient * MutableObject+.*)
        && target(mo)
        && args(newValue);

    //fields set with reflections in a pull do not trigger this aspect
    before(MutableObject mo, Object newValue) : contentFieldSetter(mo, newValue) {
        Repository repository = tm.repositories.get(mo.getRootEntity());
        if (repository != null) {
            repository.logLocalChange(mo);
            mo.notifyRegisteredWrappersAboutChange();
        }
    }
}