import java.util.*;
import me.abdelaziz.api.annotation.*;
import me.abdelaziz.runtime.*;
import me.abdelaziz.util.NativeBinder;
public final class NativeBinderCacheTest {
    public static class Parent {
        @VynFunc public int inherited(){return 7;}
        public int hidden(){return 88;}
    }
    @VynType(name="Host")
    public static class Host extends Parent {
        int value,destroyed;
        @VynConstructor public Host(){value=1;}
        @VynConstructor public Host(int n){value=n;}
        @VynFunc(name="getValue") public int value(){return value;}
        @VynFunc public void setValue(int n){value=n;}
        @VynFunc public int fail(){throw new IllegalArgumentException("native sentinel");}
        @VynDestructor public void destroy(){destroyed++;}
    }
    static int checks;
    static void check(boolean okay){if(!okay)throw new AssertionError("Native semantics failed at "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        var env=new Environment(null);var host=new Host(4);var other=new Host(9);
        var a=(VynInstance)NativeBinder.toValue(env,host).asJavaObject();var b=(VynInstance)NativeBinder.toValue(env,other).asJavaObject();
        check(a.getMethod("getValue").call(env,List.of()).asInt()==4);check(b.getMethod("getValue").call(env,List.of()).asInt()==9);
        a.getMethod("setValue").call(env,List.of(new Value(13)));check(host.value==13&&other.value==9);
        check(a.getMethod("getValue").call(env,List.of()).asInt()==13);check(a.getMethod("inherited").call(env,List.of()).asInt()==7);
        boolean hidden=false;try{hidden=a.getMethod("hidden")!=null;}catch(RuntimeException ignored){}check(!hidden);
        boolean failed=false;try{a.getMethod("fail").call(env,List.of());}catch(IllegalArgumentException e){failed=e.getMessage().equals("native sentinel");}check(failed);
        a.getMethod("_destroy").call(env,List.of());check(host.destroyed==1&&other.destroyed==0);
        check(a.getMethod("_init_0")!=null&&a.getMethod("_init_1")!=null);
        boolean reinitializationRejected=false;try{a.getMethod("_init_1").call(env,List.of(new Value(21)));}catch(RuntimeException e){reinitializationRejected=e.getMessage().contains("already defined");}check(reinitializationRejected);
        for(int n=0;n<100;n++){var fresh=(VynInstance)NativeBinder.toValue(env,new Host(n)).asJavaObject();check(fresh.getMethod("getValue").call(env,List.of()).asInt()==n);}
        for(int n=0;n<5000;n++)NativeBinder.toValue(env,host);
        long begin=System.nanoTime();for(int n=0;n<20000;n++)NativeBinder.toValue(env,host);
        System.out.println("PASS "+checks+" native binding assertions; 20000 host wraps took "+(System.nanoTime()-begin)/1e6+" ms (fixture only).");
    }
}
