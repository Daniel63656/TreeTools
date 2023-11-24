package net.scoreworks.treetools;

/**
 * Child entity that is owned by a final index (e.g. in an array)
 * @param <O> class-type of the owner class
 */
public abstract class IndexedChild<O extends MutableObject> extends Child<O> {

    /**
     * reference to the key the object is saved with
     */
    private Integer index;

    public IndexedChild(O owner, Integer index) {
        super(owner);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
    public void changeIndex(int index) {
        removeFromOwner();
        this.index = index;
        addToOwner();
    }

    @Override
    public Class<?>[] constructorParameterTypes() {
        return new Class<?>[]{getOwner().getClass(), int.class};
    }
    @Override
    public Object[] constructorParameterStates(Remote remote) {
        return new Object[]{remote.getLogicalObjectKeyOfOwner(this), index};
    }
    @Override
    public Object[] constructorParameterObjects() {
        return new Object[]{getOwner(), index};
    }
}