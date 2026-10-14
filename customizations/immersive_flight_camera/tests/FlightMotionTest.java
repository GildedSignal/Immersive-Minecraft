import mcperf.camera.FlightMotionMath;
import mcperf.camera.FlightMotionConfig;
public class FlightMotionTest {
    private static int checks;
    private static void check(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args){
        var config=new FlightMotionConfig();config.validate();
        double[] rates={30,60,144,240};double expected=Double.NaN;
        for(double fps:rates){var m=new FlightMotionMath();for(int i=0;i<(int)fps;i++)m.visual(true,60,2,1/fps,config);check(m.bank<0&&Math.abs(m.bank)<=config.maxBankDegrees);check(m.distance<=config.extraDistanceBlocks);check(m.fov<=config.fovBoostDegrees);if(Double.isNaN(expected))expected=m.bank;else check(Math.abs(expected-m.bank)<1e-9);}
        var left=new FlightMotionMath();left.steering(-1,-1,.05,config);check(left.yawRate<0&&left.pitchRate<0);left.visual(true,-100,2,.2,config);check(left.bank>0&&left.bank<=16);double bank=left.bank;left.visual(false,0,0,.5,config);check(Math.abs(left.bank)<bank);left.reset();check(left.bank==0&&left.yawRate==0&&left.pitchRate==0&&left.fov==0&&left.distance==0);
        for(double value:new double[]{-1080,-720,-361,-180,0,180,361,720,1080}){double delta=FlightMotionMath.wrappedDelta(value);check(delta>=-180&&delta<180);}
        check(FlightMotionMath.wrappedDelta(359)==-1);check(FlightMotionMath.wrappedDelta(-359)==1);check(FlightMotionMath.blend(4,8,0,.1)==4);
        var invalid=new FlightMotionConfig();invalid.maxBankDegrees=Double.NaN;try{invalid.validate();throw new AssertionError();}catch(IllegalArgumentException expectedFailure){checks++;}
        System.out.println(checks+" flight motion smoothing, steering, bounds and wrap assertions passed");
    }
}
