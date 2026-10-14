package mcperf.camera;
/** Time-based smoothing and flight steering; independent of the rendering frame rate. */
public final class FlightMotionMath {
    public double yawRate,pitchRate,bank,distance,fov;
    public static double blend(double from,double to,double seconds,double halfLife){return from+(to-from)*(-Math.expm1(-Math.log(2)*Math.max(0,seconds)/halfLife));}
    public static double wrappedDelta(double value){return value-Math.floor((value+180)/360)*360;}
    public void steering(int yaw,int pitch,double dt,FlightMotionConfig c){
        yawRate=blend(yawRate,yaw*c.yawDegreesPerSecond,dt,c.inputHalfLife);
        pitchRate=blend(pitchRate,pitch*c.pitchDegreesPerSecond,dt,c.inputHalfLife);
    }
    public void visual(boolean flying,double turnRate,double speed,double dt,FlightMotionConfig c){
        double weight=flying?Math.max(0,Math.min(1,(speed-.4)/1.4)):0;
        bank=blend(bank,flying?Math.max(-c.maxBankDegrees,Math.min(c.maxBankDegrees,-turnRate*.18)):0,dt,c.bankHalfLife);
        distance=blend(distance,c.extraDistanceBlocks*weight,dt,.18);fov=blend(fov,c.fovBoostDegrees*weight,dt,.18);
    }
    public void reset(){yawRate=pitchRate=bank=distance=fov=0;}
}
