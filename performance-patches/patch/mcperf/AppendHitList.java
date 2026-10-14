package mcperf;
import java.util.*;

/** O(1) amortized appends with the snapshot iterator behavior of CopyOnWriteArrayList. */
public final class AppendHitList<E> extends AbstractList<E> implements RandomAccess {
  private final ArrayList<E> values=new ArrayList<>();
  @Override public synchronized boolean add(E value){return values.add(value);}
  @Override public synchronized void add(int index,E value){values.add(index,value);}
  @Override public synchronized boolean addAll(Collection<? extends E> c){return values.addAll(c);}
  @Override public synchronized E get(int index){return values.get(index);}
  @Override public synchronized int size(){return values.size();}
  @Override public synchronized E set(int index,E value){return values.set(index,value);}
  @Override public synchronized E remove(int index){return values.remove(index);}
  @Override public synchronized void clear(){values.clear();}
  @Override public synchronized Object[] toArray(){return values.toArray();}
  @Override public synchronized <T>T[] toArray(T[] a){return values.toArray(a);}
  private synchronized List<E> snapshot(){return Collections.unmodifiableList(new ArrayList<>(values));}
  @Override public Iterator<E> iterator(){return snapshot().iterator();}
  @Override public ListIterator<E> listIterator(int index){return snapshot().listIterator(index);}
  @Override public Spliterator<E> spliterator(){return snapshot().spliterator();}
}
