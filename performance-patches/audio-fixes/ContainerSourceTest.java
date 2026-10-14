import mcperf.audio.ContainerSource;
public final class ContainerSourceTest {
    public static void main(String[] args){int checks=0;
        for(boolean block:new boolean[]{false,true})for(boolean facing:new boolean[]{false,true})for(boolean forward:new boolean[]{false,true})for(boolean backward:new boolean[]{false,true})for(String a:new String[]{"SINGLE","LEFT","RIGHT"})for(String b:new String[]{"SINGLE","LEFT","RIGHT"}){
            boolean expected=block&&facing&&forward&&backward&&!a.equals(b)&&!a.equals("SINGLE")&&!b.equals("SINGLE");
            if(ContainerSource.pairPolicy(block,a,b,facing,forward,backward)!=expected)throw new AssertionError("Unrelated chest/wall excluded");checks++;
        }
        var same=new Object();if(!ContainerSource.isSource(same,same,new Object())||ContainerSource.isSource(new Object(),new Object(),new Object()))throw new AssertionError("Original equality/failure fallback changed");checks+=2;
        System.out.println("PASS "+checks+" pair-policy/equality checks; unrelated walls and unconnected/single containers remain occluding.");
    }
}
