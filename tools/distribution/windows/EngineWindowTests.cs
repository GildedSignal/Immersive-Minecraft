using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

static class EngineWindowTests
{
    delegate nint WindowProcedure(nint window,uint message,nint w,nint l);
    static readonly WindowProcedure procedure=DefWindowProc;
    [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)]struct WindowClass{public uint Style;public nint Procedure;public int ClassBytes,WindowBytes;public nint Instance,Icon,Cursor,Background;public string? Menu,Name;}
    [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern ushort RegisterClass(ref WindowClass cls);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern nint CreateWindowEx(uint style,string cls,string title,uint windowStyle,int x,int y,int width,int height,nint parent,nint menu,nint instance,nint data);
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode)]static extern nint GetModuleHandle(string? name);
    [DllImport("user32.dll")]static extern nint DefWindowProc(nint window,uint message,nint w,nint l);
    [DllImport("user32.dll")]static extern bool ShowWindow(nint window,int command);
    [DllImport("user32.dll")]static extern bool DestroyWindow(nint window);
    [DllImport("user32.dll")]static extern bool IsWindowVisible(nint window);
    public static void Fixture(string output) {
        var cls=new WindowClass{Procedure=Marshal.GetFunctionPointerForDelegate(procedure),Instance=GetModuleHandle(null),Name="QtFriendPackTest"};if(RegisterClass(ref cls)==0)throw new Exception("Could not register fixture window");
        // Simulate Qt's progress, console and return-to-launcher windows.
        var windows=new[]{"Preparing game", "Prism Launcher - console", "Prism Launcher"}.Select(title=>CreateWindowEx(0,cls.Name!,title,0x00cf0000,-32000,-32000,300,200,0,0,cls.Instance,0)).ToArray();
        var timer=new System.Windows.Forms.Timer{Interval=150};int ticks=0,visibleLate=0;
        timer.Tick+=(_,_)=>{if(++ticks%2==1)foreach(var window in windows)ShowWindow(window,4);else visibleLate+=windows.Count(IsWindowVisible);
            if(ticks>=20){timer.Stop();foreach(var window in windows)DestroyWindow(window);File.WriteAllText(output,"late-visible="+visibleLate);Application.ExitThread();}};
        timer.Start();Application.Run();timer.Dispose();GC.KeepAlive(procedure);
    }
    public static async Task<string> Run(string output) {
        var fixture=output+".fixture";using var session=EngineSession.StartSuspended(Environment.ProcessPath!,["--engine-window-fixture",fixture]);
        using var frontend=new LauncherForm(true){StartPosition=FormStartPosition.Manual,Location=new(-32000,-32000)};frontend.WatchFixture(session);frontend.Show();frontend.Close();
        if(frontend.Visible||frontend.ShowInTaskbar||session.Exited)throw new Exception("Closing during startup blocked or terminated the game engine");
        var gameClass=new WindowClass{Procedure=Marshal.GetFunctionPointerForDelegate(procedure),Instance=GetModuleHandle(null),Name="GLFWFriendPackFixture"};if(RegisterClass(ref gameClass)==0)throw new Exception("Game fixture class registration failed");
        var game=CreateWindowEx(0,gameClass.Name!,"Fixture",0x00cf0000,-32000,-32000,300,200,0,0,gameClass.Instance,0);
        try{if(GameGate.HasGameWindow(Environment.ProcessId))throw new Exception("Hidden game fixture treated as ready");ShowWindow(game,4);if(!GameGate.HasGameWindow(Environment.ProcessId))throw new Exception("Visible GLFW fixture not detected");if(GameGate.HasGameWindow(session.Id))throw new Exception("Engine Qt window treated as game");frontend.GameReady(Environment.ProcessId);if(frontend.Visible||frontend.ShowInTaskbar)throw new Exception("Frontend still visible after game ready");}finally{DestroyWindow(game);}
        var clock=Stopwatch.StartNew();while(!session.Exited&&clock.Elapsed<TimeSpan.FromSeconds(15))await Task.Delay(30);
        if(!session.Exited)throw new Exception("Window fixture did not exit");
        if(!File.Exists(fixture)||File.ReadAllText(fixture)!="late-visible=0"||session.HiddenWindows<3)throw new Exception("Background window suppression failed");
        await frontend.PollFixture(Path.GetDirectoryName(output)!);if(!frontend.IsDisposed)throw new Exception("Exited engine did not release hidden frontend: "+frontend.FixtureStatus);
        return "PASS: startup close hides frontend without terminating engine; visible GLFW readiness hides frontend; hidden/Qt windows rejected; guard stays active after dismissal; repeated engine windows suppressed; engine exit disposes frontend even with a reused/live cached PID. Fixture only, no Minecraft started.";
    }
}
