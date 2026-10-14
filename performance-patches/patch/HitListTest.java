import mcperf.AppendHitList;
import java.util.*;
import java.util.concurrent.*;
public class HitListTest {
  public static void main(String[] args)throws Exception {
    List<Integer> list=new AppendHitList<>();ExecutorService pool=Executors.newFixedThreadPool(15);
    for(int cycle=0;cycle<25;cycle++){
      list.clear();List<Future<?>> jobs=new ArrayList<>();
      for(int ray=0;ray<320;ray++){final int r=ray;jobs.add(pool.submit(()->{for(int bounce=0;bounce<13;bounce++)list.add(r*13+bounce);}));}
      for(Future<?> job:jobs)job.get();
      if(list.size()!=4160||new HashSet<>(list).size()!=4160)throw new AssertionError("Lost or duplicate hits");
      Iterator<Integer> snapshot=list.iterator();list.add(-1);int count=0;while(snapshot.hasNext()){snapshot.next();count++;}
      if(count!=4160)throw new AssertionError("Iterator is not a snapshot");
      if(list.toArray().length!=4161)throw new AssertionError("Array length");
    }
    pool.shutdown();System.out.println("PASS: 25 cycles, 320 rays x 13 hits, 15 concurrent writers; snapshot iteration and arrays");
    for(boolean cow:new boolean[]{true,false}){
      long begin=System.nanoTime();long checksum=0;
      for(int sound=0;sound<100;sound++){
        List<Integer> a=cow?new CopyOnWriteArrayList<>():new AppendHitList<>();
        for(int hit=0;hit<4160;hit++)a.add(hit);
        for(int value:a)checksum+=value;
      }
      System.out.println((cow?"CopyOnWriteArrayList":"AppendHitList")+" 100 sounds, 4160 appends+read: "+(System.nanoTime()-begin)/1e6+" ms; checksum="+checksum);
    }
  }
}
