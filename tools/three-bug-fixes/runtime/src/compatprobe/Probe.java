package compatprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.client.gui.screens.FieldGuideJournalScreen;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;

public final class Probe implements ClientModInitializer {
    private int phase;
    private boolean waiting;
    private long entered;
    private final boolean fixed=Boolean.getBoolean("compat.probe.fixed");
    private final Path report=Path.of(System.getProperty("compat.probe.report"));
    private record UiCase(String name,String label,String glyph,String texture) {}
    private final UiCase[] cases=loadCases();
    private final int uiPhases=cases.length*2;
    private UiCase[] loadCases() {
        String path=System.getProperty("compat.probe.cases");
        if(path==null) return new UiCase[]{new UiCase("ender","container.enderchest",null,"generic_54"),new UiCase("anvil","container.repair",null,"anvil"),new UiCase("stonecutter","container.stonecutter",null,"stonecutter")};
        try {return new com.google.gson.Gson().fromJson(Files.readString(Path.of(path)),UiCase[].class);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if(phase==0 && mc.gui.screen() instanceof TitleScreen) {
                    mc.gui.setScreen(new UiProbe(0,false));phase=1;entered=System.nanoTime();
                    log("Loaded native renderer, Iris, ImmediatelyFast, Scholar, Field Guide and Interactive Stuff.");
                }
                long delay=cases.length>3 && phase>1 && phase<=uiPhases ? 500_000_000L : 3_000_000_000L;
                if(phase==0 || waiting || System.nanoTime()-entered<delay) return;
                if(phase<=uiPhases) {
                    int current=phase;
                    capture(mc,cases[(phase-1)/2].name()+(phase%2==0?"-tooltip":"")+".png",()->{
                        phase=current+1;
                        if(phase<=uiPhases)mc.gui.setScreen(new UiProbe((phase-1)/2,phase%2==0));
                        else {FieldGuideClient.openDefaultScreen();log("Default Field Guide screen: "+mc.gui.screen().getClass().getSimpleName());}
                    });
                } else if(phase==uiPhases+1) {
                    if(fixed)typeJournal(mc);
                    capture(mc,"field-guide.png",()->phase=uiPhases+2);
                } else if(phase==uiPhases+2) {
                    if(fixed) {
                        var manager=ClientFieldGuideManager.getInstance();
                        String text=manager.getJournalPages().getFirst().content;
                        if(!text.contains("My camp notes"))throw new AssertionError("Native typing not recorded: "+text);
                        ProgressManager.getInstance().onWorldUnload();ProgressManager.getInstance().onWorldLoad();
                        FieldGuideClient.openDefaultScreen();
                        if(!manager.getJournalPages().getFirst().content.equals(text))throw new AssertionError("Journal did not survive reload");
                        log("Native click, Ctrl+A, typing, local save, world unload/load and reopen passed.");
                        phase=uiPhases+3;entered=System.nanoTime();return;
                    }
                    log("PASS: native render completed");phase=uiPhases+4;mc.stop();
                } else if(phase==uiPhases+3) {
                    capture(mc,"field-guide-reopened.png",()->{log("PASS: "+cases.length+" UI marker cases, with and without tooltip/text batching; native render completed");phase=uiPhases+4;mc.stop();});
                }
            } catch(Throwable e) {log("FAIL: "+e);e.printStackTrace();phase=9;mc.stop();}
        });
    }
    private void typeJournal(Minecraft mc) {
        if(!(mc.gui.screen() instanceof FieldGuideJournalScreen))throw new AssertionError("Fallback journal absent");
        Screen screen=mc.gui.screen();
        var areas=screen.children().stream().filter(w -> w instanceof AbstractWidget widget && widget.getHeight()>40 && (w.getClass().getSimpleName().contains("TextArea") || w.getClass().getSimpleName().contains("TextBox"))).map(w -> (AbstractWidget)w).toList();
        if(areas.isEmpty())throw new AssertionError("Editable journal widget absent");
        var area=areas.getFirst();
        screen.mouseClicked(new MouseButtonEvent(area.getX()+5,area.getY()+5,new MouseButtonInfo(0,0)),false);
        // Hidden windows have no real pointer hover. Use Screen's normal focus API.
        screen.setFocused(area);area.setFocused(true);
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL));
        for(int ch:"My camp notes".codePoints().toArray())screen.charTyped(new CharacterEvent(ch));
    }
    private void capture(Minecraft mc,String name,Runnable next) {
        waiting=true;
        Screenshot.grab(mc.gameDirectory,name,mc.gameRenderer.mainRenderTarget(),1,msg -> mc.execute(()->{
            log("Captured "+name);next.run();entered=System.nanoTime();waiting=false;
        }));
    }
    private void log(String value) {try {Files.writeString(report,value+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    final class UiProbe extends Screen {
        final int index; final boolean tooltip;
        UiProbe(int index,boolean tooltip) {super(Component.literal("UI render probe"));this.index=index;this.tooltip=tooltip;}
        public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
            g.fill(0,0,width,height,0xff353e39);
            int x=(width-176)/2,y=(height-166)/2;
            g.blit(RenderPipelines.GUI_TEXTURED,Identifier.parse("minecraft:textures/gui/container/"+cases[index].texture()+".png"),x,y,0,0,176,166,256,256);
            // Change previous text batch length and add a tooltip to vary vertex indices/scissors.
            g.text(font,tooltip ? "Batch with preceding tooltip text 1234567" : "A",10,10,0xffeeeeee,false);
            var title=cases[index].glyph()==null ? Component.translatable(cases[index].label()) : Component.literal(cases[index].glyph());
            g.text(font,title,x+8,y+6,0xff404040,false);
            g.text(font,Component.translatable("container.inventory"),x+8,y+72,0xff404040,false);
            if(tooltip) g.setTooltipForNextFrame(font,Component.literal("Test tooltip"),x+85,y+65);
        }
    }
}
