import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
public final class HotspotStacks {
    public static void main(String[] args)throws Exception{
        var counts=new HashMap<String,Integer>();int render=0,reflection=0;
        try(var file=new RecordingFile(Path.of(args[0]))){while(file.hasMoreEvents()){
            var e=file.readEvent();var type=e.getEventType().getName();if(!type.equals("jdk.ExecutionSample")&&!type.equals("jdk.NativeMethodSample"))continue;
            var t=e.getThread("sampledThread");if(t==null||!t.getJavaName().equals("Render thread")||e.getStackTrace()==null)continue;render++;
            var stack=e.getStackTrace().getFrames().stream().map(f->f.getMethod().getType().getName()+"."+f.getMethod().getName()).toList();
            if(stack.stream().anyMatch(s->s.equals("java.lang.Class.getMethods")||s.equals("java.lang.reflect.Executable.getAnnotation")||s.contains("java.lang.Class.copyMethods"))){reflection++;
                var application=stack.stream().filter(s->!s.startsWith("java.")&&!s.startsWith("jdk.")&&!s.startsWith("sun.")).limit(7).toList();counts.merge(String.join(" <- ",application),1,Integer::sum);
            }
        }}
        System.out.println("Render samples="+render+" reflection-enumeration/annotation samples="+reflection);counts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(15).forEach(x->System.out.println(x.getValue()+" "+x.getKey()));
    }
}
