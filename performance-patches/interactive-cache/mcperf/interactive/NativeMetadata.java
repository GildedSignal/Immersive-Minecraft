package mcperf.interactive;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
/** Cache immutable exposure metadata, never script environments or host values. */
public final class NativeMetadata {
    @SuppressWarnings("unchecked")
    static Class<? extends Annotation> annotation(Class<?> owner,String name){try{return (Class<? extends Annotation>)Class.forName("me.abdelaziz.api.annotation."+name,false,owner.getClassLoader());}catch(ClassNotFoundException e){throw new IllegalStateException(e);}}
    static final ClassValue<Method[]> METHODS=new ClassValue<>(){protected Method[] computeValue(Class<?> owner){
        var function=annotation(owner,"VynFunc");var destructor=annotation(owner,"VynDestructor");
        return Arrays.stream(owner.getMethods()).filter(m->m.isAnnotationPresent(function)||m.isAnnotationPresent(destructor)).toArray(Method[]::new);
    }};
    static final ClassValue<Constructor<?>[]> CONSTRUCTORS=new ClassValue<>(){protected Constructor<?>[] computeValue(Class<?> owner){
        var constructor=annotation(owner,"VynConstructor");return Arrays.stream(owner.getConstructors()).filter(c->c.isAnnotationPresent(constructor)).toArray(Constructor<?>[]::new);
    }};
    public static Method[] methods(Class<?> owner){return METHODS.get(owner).clone();}
    public static Constructor<?>[] constructors(Class<?> owner){return CONSTRUCTORS.get(owner).clone();}
}
