package mcperf.camera;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
public final class FlightMotionConfig {
    public boolean enabled=true,arrowSteering=true,playerBanking=true;
    public double yawDegreesPerSecond=90,pitchDegreesPerSecond=55,inputHalfLife=0.10;
    public double followHalfLife=0.075,bankHalfLife=0.14,maxBankDegrees=16;
    public double extraDistanceBlocks=1.6,fovBoostDegrees=8;
    public static FlightMotionConfig load(){
        var gson=new GsonBuilder().setPrettyPrinting().create();var file=FabricLoader.getInstance().getConfigDir().resolve("immersive-flight-camera.json");
        try{
            if(Files.exists(file)){var c=gson.fromJson(Files.readString(file),FlightMotionConfig.class);c.validate();return c;}
            var c=new FlightMotionConfig();Files.writeString(file,gson.toJson(c)+"\n");return c;
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger("immersive-flight-camera").error("Cannot load flight motion settings; existing file retained",e);var c=new FlightMotionConfig();c.enabled=false;return c;}
    }
    public void validate(){
        yawDegreesPerSecond=bound(yawDegreesPerSecond,10,180);pitchDegreesPerSecond=bound(pitchDegreesPerSecond,10,120);
        inputHalfLife=bound(inputHalfLife,0.02,0.5);followHalfLife=bound(followHalfLife,0.01,0.4);bankHalfLife=bound(bankHalfLife,0.02,0.5);
        maxBankDegrees=bound(maxBankDegrees,0,35);extraDistanceBlocks=bound(extraDistanceBlocks,0,4);fovBoostDegrees=bound(fovBoostDegrees,0,15);
    }
    private static double bound(double v,double low,double high){if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite flight setting");return Math.max(low,Math.min(high,v));}
}
