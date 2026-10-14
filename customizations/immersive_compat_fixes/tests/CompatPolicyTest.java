import mcperf.compat.SoundDispatch;
import mcperf.compat.JournalStore;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class CompatPolicyTest {
    static int checks;
    static void check(boolean value) { if(!value) throw new AssertionError("Check "+checks); checks++; }
    public static void main(String[] args) throws Exception {
        var queue=new ArrayDeque<Runnable>(); var generation=new AtomicLong(); var result=new ArrayList<Integer>();
        for(int i=0;i<4;i++) { final int item=i; SoundDispatch.enqueue(queue::add,generation::get,()->true,()->result.add(item)); }
        check(result.isEmpty()); while(!queue.isEmpty()) queue.remove().run(); check(result.equals(List.of(0,1,2,3)));
        SoundDispatch.enqueue(queue::add,generation::get,()->true,()->result.add(4)); generation.incrementAndGet();queue.remove().run(); check(result.size()==4);
        SoundDispatch.enqueue(queue::add,generation::get,()->false,()->result.add(5));queue.remove().run(); check(result.size()==4);
        Path root=Files.createTempDirectory("local-journal-test");
        Path first=JournalStore.file(root,"playerA\nserverA"), other=JournalStore.file(root,"playerA\nserverB"), player=JournalStore.file(root,"playerB\nserverA");
        check(!first.equals(other)); check(!first.equals(player)); check(first.getParent().equals(root.resolve("fieldguide_local_journals")));
        check(JournalStore.load(first)==null);
        var value=new JournalStore.Journal(1,"Voyage",List.of(new JournalStore.Page("Camp","Coordinates: 123, 65, -50\nCafé — 🗺",123L)));
        JournalStore.save(first,value); check(value.equals(JournalStore.load(first))); check(JournalStore.load(other)==null);
        var next=new JournalStore.Journal(1,"Next",List.of(new JournalStore.Page("New","More notes",456L)));
        JournalStore.save(first,next); check(next.equals(JournalStore.load(first))); check(value.equals(JournalStore.load(first.resolveSibling(first.getFileName()+".bak"))));
        for(var bad:List.of(new JournalStore.Journal(2,"",List.of()),new JournalStore.Journal(1,"x".repeat(65),List.of()),new JournalStore.Journal(1,"",Collections.nCopies(128,new JournalStore.Page("","",0))),new JournalStore.Journal(1,"",List.of(new JournalStore.Page("","x".repeat(1025),0))))) {
            try {JournalStore.save(first,bad);throw new AssertionError("Accepted invalid input");}catch(IllegalArgumentException expected){checks++;}
            check(next.equals(JournalStore.load(first)));
        }
        Files.writeString(other,"{invalid"); try {JournalStore.load(other);throw new AssertionError();}catch(Exception expected){checks++;}check(Files.readString(other).equals("{invalid"));
        System.out.println(checks+" sound dispatch and journal persistence assertions passed");
    }
}
