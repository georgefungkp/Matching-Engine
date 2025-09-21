// GenericReference.java
package equity.disruptor.events;

public class GenericReference<T> {
    private T ref;
    
    public void setRef(T ref) {
        this.ref = ref;
    }
    
    public T getRef() {
        return ref;
    }
    
    public void clear() {
        this.ref = null;
    }
}
