package mcperf.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;

/** Local discovery information never contains server rewards or permissions. */
public final class GuideStore {
    static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public record Progress(int schema,List<String> unlocked,List<String> seen,Map<String,Long> times,
        Map<String,Long> gameTimes,Map<String,String> names,Map<String,String> descriptions,Map<String,String> variants) {}
    public static void validate(Progress p) {
        if(p==null || p.schema!=1)throw new IllegalArgumentException("Invalid local discovery version");
        for(var values:List.of(p.unlocked,p.seen)) {
            if(values.size()>8192)throw new IllegalArgumentException("Too many local discoveries");
            for(var s:values)if(s==null||s.length()>512)throw new IllegalArgumentException("Invalid discovery identifier");
        }
        for(var map:List.of(p.names,p.descriptions,p.variants)) {
            if(map.size()>8192)throw new IllegalArgumentException("Too many entry notes");
            for(var e:map.entrySet())if(e.getKey()==null||e.getKey().length()>512||e.getValue()==null||e.getValue().length()>8192)throw new IllegalArgumentException("Invalid entry note");
        }
        for(var map:List.of(p.times,p.gameTimes)) {
            if(map.size()>8192)throw new IllegalArgumentException("Too many discovery dates");
            for(var e:map.entrySet())if(e.getKey()==null||e.getKey().length()>512||e.getValue()==null)throw new IllegalArgumentException("Invalid discovery date");
        }
    }
    public static Progress load(Path file)throws java.io.IOException {
        if(!Files.exists(file))return null;
        if(Files.size(file)>8_000_000)throw new java.io.IOException("Local guide is too large");
        var value=GSON.fromJson(Files.readString(file),Progress.class);validate(value);return value;
    }
    public static void save(Path file,Progress value)throws java.io.IOException {
        validate(value);Files.createDirectories(file.getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(value)+"\n");
        if(Files.exists(file))Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
        try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
