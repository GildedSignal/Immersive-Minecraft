using System.Management;
using System.Runtime.InteropServices;
using System.Text;

static class GameGate
{
    internal sealed record Entry(int Id,int Parent,string Name,string? Executable,string? Command,DateTime Created);
    public static bool IsPlaying(string gameDirectory)=>PlayingId(gameDirectory)!=null;
    public static int? PlayingId(string gameDirectory,int? engineId=null)
    {
        // Prism sends Minecraft's launch parameters through stdin. Its Java
        // command line need not contain --gameDir. Compare the exact private
        // engine, data directory and selected instance instead. Tokens are
        // examined only in memory, never logged or included in diagnostics.
        using var query=new ManagementObjectSearcher("SELECT ProcessId,ParentProcessId,Name,ExecutablePath,CommandLine,CreationDate FROM Win32_Process WHERE Name='javaw.exe' OR Name='java.exe' OR Name='prismlauncher.exe'");
        using var results=query.Get();var entries=new List<Entry>();
        foreach(ManagementObject row in results)using(row){entries.Add(new(Convert.ToInt32(row["ProcessId"]),Convert.ToInt32(row["ParentProcessId"]),(string)row["Name"],row["ExecutablePath"] as string,row["CommandLine"] as string,row["CreationDate"] is string date?ManagementDateTimeConverter.ToDateTime(date):DateTime.MaxValue));}
        return Match(entries,gameDirectory,PrismBridge.Engine,PrismBridge.Data,engineId);
    }
    internal static int? Match(IEnumerable<Entry> processes,string game,string engine,string data,int? engineId=null)
    {
        var entries=processes.ToArray();
        foreach(var java in entries.Where(e=>e.Name.Equals("javaw.exe",StringComparison.OrdinalIgnoreCase)||e.Name.Equals("java.exe",StringComparison.OrdinalIgnoreCase))){
            var args=Arguments(java.Command);if(SamePath(Argument(args,"--gameDir"),game))return java.Id;
            var parent=entries.FirstOrDefault(e=>e.Id==java.Parent);
            if(parent==null||parent.Created>java.Created||engineId.HasValue&&parent.Id!=engineId)continue;
            if(!parent.Name.Equals("prismlauncher.exe",StringComparison.OrdinalIgnoreCase)||!SamePath(parent.Executable,engine))continue;
            var parentArgs=Arguments(parent.Command);var instance=Argument(parentArgs,"-l")??Argument(parentArgs,"--launch");
            // No inferred association for arbitrary game folders.
            if(instance is not ("immersive-minecraft" or "immersive-minecraft-vanilla")||!SamePath(Argument(parentArgs,"-d")??Argument(parentArgs,"--dir"),data))continue;
            if(SamePath(Path.Combine(data,"instances",instance,".minecraft"),game))return java.Id;
        }
        return null;
    }
    static bool SamePath(string? a,string b){try{return a!=null&&Path.IsPathRooted(a)&&string.Equals(Path.GetFullPath(a).TrimEnd('\\','/'),Path.GetFullPath(b).TrimEnd('\\','/'),StringComparison.OrdinalIgnoreCase);}catch(Exception e)when(e is ArgumentException or NotSupportedException or PathTooLongException){return false;}}
    static string? Argument(string[] args,string name){for(int i=1;i<args.Length;i++){if(args[i]==name&&i+1<args.Length)return args[i+1];if(args[i].StartsWith(name+"=",StringComparison.Ordinal))return args[i][(name.Length+1)..];}return null;}
    [DllImport("shell32.dll",CharSet=CharSet.Unicode)]static extern nint CommandLineToArgvW(string command,out int count);
    [DllImport("kernel32.dll")]static extern nint LocalFree(nint memory);
    static string[] Arguments(string? command){if(string.IsNullOrWhiteSpace(command))return [];var pointer=CommandLineToArgvW(command,out int count);if(pointer==0)return [];try{var args=new string[count];for(int i=0;i<count;i++)args[i]=Marshal.PtrToStringUni(Marshal.ReadIntPtr(pointer,i*IntPtr.Size))!;return args;}finally{LocalFree(pointer);}}
    delegate bool WindowCallback(nint window,nint data);
    [DllImport("user32.dll")]static extern bool EnumWindows(WindowCallback callback,nint data);
    [DllImport("user32.dll")]static extern uint GetWindowThreadProcessId(nint window,out uint pid);
    [DllImport("user32.dll")]static extern bool IsWindowVisible(nint window);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetClassName(nint window,StringBuilder text,int count);
    public static bool HasGameWindow(int pid){bool found=false;EnumWindows((window,_)=>{GetWindowThreadProcessId(window,out var owner);if(owner!=(uint)pid||!IsWindowVisible(window))return true;var cls=new StringBuilder(256);GetClassName(window,cls,cls.Capacity);if(cls.ToString().StartsWith("GLFW",StringComparison.Ordinal)){found=true;return false;}return true;},0);return found;}
}
