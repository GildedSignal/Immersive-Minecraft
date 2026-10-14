using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

// Only windows belonging to this private engine PID are managed. Minecraft,
// browsers, another Prism installation and other applications stay untouched.
sealed class EngineSession:IDisposable
{
    readonly Process process;readonly nint nativeProcess;readonly HookCallback hookCallback;readonly nint hook;
    readonly System.Windows.Forms.Timer timer=new(){Interval=50};
    public bool Exited {get{var state=WaitForSingleObject(nativeProcess,0);if(state==uint.MaxValue)throw new IOException("Could not check the background engine handle.");return state==0;}}
    public int Id=>process.Id;
    public int ExitCode=>Exited&&GetExitCodeProcess(nativeProcess,out var code)?unchecked((int)code):throw new IOException("Could not read the background engine exit status.");
    public string? RequiredInteraction{get;private set;}
    public int HiddenWindows{get;private set;}
    bool disposed;
    bool authentication;
    delegate void HookCallback(nint hook,uint eventType,nint window,int objectId,int childId,uint thread,uint time);
    delegate bool WindowCallback(nint window,nint data);
    [DllImport("user32.dll")]static extern nint SetWinEventHook(uint min,uint max,nint module,HookCallback callback,uint process,uint thread,uint flags);
    [DllImport("user32.dll")]static extern bool UnhookWinEvent(nint hook);
    [DllImport("user32.dll")]static extern bool EnumWindows(WindowCallback callback,nint data);
    [DllImport("user32.dll")]static extern uint GetWindowThreadProcessId(nint window,out uint pid);
    [DllImport("user32.dll")]static extern bool IsWindowVisible(nint window);
    [DllImport("user32.dll")]static extern bool ShowWindow(nint window,int command);
    [DllImport("user32.dll")]static extern bool SetWindowPos(nint window,nint after,int x,int y,int cx,int cy,uint flags);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetWindowText(nint window,StringBuilder text,int count);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetClassName(nint window,StringBuilder text,int count);
    [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)]struct StartupInfo{public uint Size;public string? Reserved,Desktop,Title;public uint X,Y,Width,Height,CharsX,CharsY,Fill,Flags;public ushort Show,ReservedSize;public nint ReservedBytes,Input,Output,Error;}
    [StructLayout(LayoutKind.Sequential)]struct ProcessInfo{public nint Process,Thread;public uint Id,ThreadId;}
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]static extern bool CreateProcess(string application,StringBuilder command,nint processAttributes,nint threadAttributes,bool inherit,uint flags,nint environment,string directory,ref StartupInfo startup,out ProcessInfo process);
    [DllImport("kernel32.dll")]static extern uint ResumeThread(nint thread);
    [DllImport("kernel32.dll")]static extern bool CloseHandle(nint handle);
    [DllImport("kernel32.dll")]static extern bool TerminateProcess(nint process,uint exit);
    [DllImport("kernel32.dll")]static extern nint OpenProcess(uint access,bool inherit,uint id);
    [DllImport("kernel32.dll")]static extern uint WaitForSingleObject(nint handle,uint milliseconds);
    [DllImport("kernel32.dll")]static extern bool GetExitCodeProcess(nint handle,out uint exit);
    EngineSession(Process process,bool authentication=false,nint nativeProcess=0) {
        this.process=process;
        this.nativeProcess=nativeProcess!=0?nativeProcess:OpenProcess(0x00101000,false,(uint)process.Id);
        if(this.nativeProcess==0)throw new IOException("Could not retain the background engine handle.");
        this.authentication=authentication;
        hookCallback=(_,type,window,id,child,thread,time)=>{if(id==0&&child==0)Suppress(window,type==0x8000);};
        hook=SetWinEventHook(0x8000,0x8003,0,hookCallback,(uint)process.Id,0,0);
        if(hook==0){if(nativeProcess==0)CloseHandle(this.nativeProcess);throw new IOException("Windows could not hide the background launcher.");}
        timer.Tick+=(_,_)=>Sweep();timer.Start();Sweep();
    }
    void Suppress(nint window,bool creating=false) {
        if(disposed||window==0||Exited)return;GetWindowThreadProcessId(window,out var pid);if(pid!=(uint)process.Id)return;
        var cls=new StringBuilder(256);GetClassName(window,cls,cls.Capacity);if(!cls.ToString().StartsWith("Qt",StringComparison.Ordinal))return;
        var text=new StringBuilder(512);GetWindowText(window,text,text.Capacity);var title=text.ToString();
        // Authentication requires interaction only once (or after expiry).
        // Never turn a necessary login dialog into an invisible deadlock.
        if(new[]{"account","login","sign in","Microsoft","offline","compte","connexion","anmelden","konto"}.Any(term=>title.Contains(term,StringComparison.OrdinalIgnoreCase))){
            authentication=true;RequiredInteraction="Finish your Minecraft sign-in to continue.";return;
        }
        // A new user's localized account manager must remain accessible. Once
        // an account is selected, later launches use fully hidden engine UI.
        if(authentication&&!System.Text.RegularExpressions.Regex.IsMatch(title,@"^Prism Launcher(?: \d+\.\d+(?:\.\d+)?)?$"))return;
        if(creating)SetWindowPos(window,0,-32000,-32000,0,0,0x0015);
        if(IsWindowVisible(window)){ShowWindow(window,0);HiddenWindows++;}
    }
    void Sweep()=>EnumWindows((window,_)=>{Suppress(window);return true;},0);
    public void Quiet()=>timer.Stop();
    public static EngineSession Start(string engine,string data,string id) {
        bool needsAccount=true;var accounts=Path.Combine(data,"accounts.json");
        try {if(File.Exists(accounts)){using var file=System.Text.Json.JsonDocument.Parse(File.ReadAllText(accounts));needsAccount=!file.RootElement.TryGetProperty("accounts",out var list)||list.GetArrayLength()==0;}}catch(Exception e)when(e is IOException or System.Text.Json.JsonException or InvalidOperationException){}
        return StartSuspended(engine,["-d",data,"-l",id],needsAccount);
    }
    public static EngineSession StartSuspended(string executable,string[] arguments,bool authentication=false) {
        static string Quote(string value){if(value.Contains('"')||value.EndsWith('\\'))throw new IOException("Unsupported launcher argument.");return "\""+value+"\"";}
        var command=new StringBuilder(string.Join(' ',new[]{executable}.Concat(arguments).Select(Quote)));
        var startup=new StartupInfo{Size=(uint)Marshal.SizeOf<StartupInfo>(),Flags=1,Show=0};
        if(!CreateProcess(executable,command,0,0,false,0x08000004,0,Path.GetDirectoryName(executable)!,ref startup,out var native))throw new IOException("Could not start Minecraft's background engine.");
        EngineSession? session=null;bool transferred=false;
        try {session=new EngineSession(Process.GetProcessById((int)native.Id),authentication,native.Process);transferred=true;if(ResumeThread(native.Thread)==uint.MaxValue)throw new IOException("Could not resume the background engine.");return session;}
        catch {TerminateProcess(native.Process,1);session?.Dispose();throw;}
        finally{CloseHandle(native.Thread);if(!transferred)CloseHandle(native.Process);}
    }
    // Test helper uses a fixture process, never the user's installed launcher.
    public static EngineSession Watch(Process process)=>new(process);
    public void Dispose(){if(disposed)return;disposed=true;timer.Stop();timer.Dispose();if(hook!=0)UnhookWinEvent(hook);CloseHandle(nativeProcess);process.Dispose();}
}
