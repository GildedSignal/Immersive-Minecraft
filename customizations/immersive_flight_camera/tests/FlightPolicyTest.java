import mcperf.camera.FlightPolicy;
public final class FlightPolicyTest {
    static int checks;
    static void eq(String want, String actual) { checks++; if(!java.util.Objects.equals(want,actual))throw new AssertionError("expected "+want+" got "+actual); }
    static String first=FlightPolicy.FIRST,third=FlightPolicy.THIRD;
    static String tick(FlightPolicy p,long ms,boolean fly,boolean ride,boolean space,boolean enabled,String view){return p.tick(ms*1_000_000L,fly,ride,space,enabled,view,third);}
    public static void main(String[]args){
        var p=new FlightPolicy();
        // Climbing/levitation/equipment alone are never supplied as actual gliding.
        for(int i=0;i<100;i++)eq(null,tick(p,i*50,false,false,false,true,first));
        eq(null,tick(p,5000,true,false,true,true,first)); // initiating jump
        eq(null,tick(p,7999,true,false,true,true,first));
        eq(third,tick(p,8000,true,false,true,true,first));
        eq(null,tick(p,8050,true,false,true,true,third)); // held Space doesn't repeat
        eq(null,tick(p,8100,true,false,false,true,third));
        eq(first,tick(p,8150,true,false,true,true,third));
        eq(null,tick(p,9000,true,false,true,true,first)); // auto cannot fight override
        eq(null,tick(p,9050,true,false,false,true,first));
        eq(third,tick(p,9100,true,false,true,true,first));
        eq(first,tick(p,9150,false,false,false,true,third)); // Space is flight-only
        p=new FlightPolicy();eq(null,tick(p,0,true,false,false,true,first));
        eq(third,tick(p,100,true,false,true,true,first)); // Space before auto delay
        eq(first,tick(p,200,false,false,false,true,third));
        p=new FlightPolicy();eq(null,tick(p,0,true,false,false,true,third));
        eq(first,tick(p,100,true,false,true,true,third));
        eq(third,tick(p,200,false,false,false,true,first)); // restore pre-flight third
        p=new FlightPolicy();eq(null,tick(p,0,true,false,false,true,first));
        eq(third,tick(p,100,true,false,true,true,first));
        eq(null,tick(p,150,true,false,false,true,third));
        eq(null,tick(p,200,true,false,false,true,"perspective_api.third_person_front"));
        eq(null,tick(p,250,false,false,false,true,"perspective_api.third_person_front")); // POV key still wins
        p=new FlightPolicy();eq(null,tick(p,0,true,false,false,true,first));
        eq(third,tick(p,3000,true,false,false,true,first));
        eq(first,tick(p,3100,true,false,true,true,third));
        eq(first,tick(p,3200,false,false,false,true,first)); // no shoulder view after first toggle
        p=new FlightPolicy();eq(null,tick(p,0,true,false,false,true,first));
        eq(null,tick(p,2999,false,false,false,true,first)); // short trip
        eq(null,tick(p,4000,true,false,false,true,first));
        eq(null,tick(p,6999,true,false,false,true,first)); // new delay
        eq(third,tick(p,7000,true,false,false,true,first));
        eq(first,tick(p,7100,false,false,false,true,third)); // owned auto view restored
        p=new FlightPolicy();eq(null,tick(p,0,true,false,false,true,first));
        eq(null,tick(p,500,true,false,false,true,third)); // manual F5 before timer
        eq(null,tick(p,4000,true,false,false,true,third));
        eq(null,tick(p,4100,false,false,false,true,third));
        p=new FlightPolicy();eq(third,tick(p,0,false,true,false,true,first));
        eq(first,tick(p,50,false,false,false,true,third)); // existing mount behavior
        p=new FlightPolicy();eq(null,tick(p,0,true,false,false,true,first));
        eq(null,tick(p,3000,true,false,true,false,first)); // UI blocks command
        eq(third,tick(p,3050,true,false,true,true,first));
        eq(first,p.reset(third)); // world/player replacement clears ownership
        eq(null,p.reset(first));
        System.out.println(checks+" flight policy checks passed");
    }
}
