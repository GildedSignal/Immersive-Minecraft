using System.Diagnostics;
using System.Text.Json;
using System.Runtime.InteropServices;
using FriendPack;

static class UninstallPack
{
    record Ticket(int Parent,DateTime Created,string Backup,string Root);
    public static string ExportFolder=>Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),"Immersive Minecraft","Backups");
    public static string BackupName=>Path.Combine(ExportFolder,"Worlds and servers "+DateTime.Now.ToString("yyyy-MM-dd HH-mm-ss")+"-"+Guid.NewGuid().ToString("N")[..6]+".zip");
    public static async Task Begin(Action<string> status,Action beforeExit)
    {
        WorldMode.AssertClosed();await PrismBridge.CloseIdleEngine();
        var backup=BackupName;
        await Task.Run(()=>PersonalFiles.ExportProfiles(WorldMode.ExistingGames(),backup,progress:status));
        // Backup has committed outside the installation before any removal starts.
        var id=Guid.NewGuid().ToString("N");var folder=Path.Combine(PrismBridge.Root,"uninstall");Directory.CreateDirectory(folder);
        var ticket=new Ticket(Environment.ProcessId,DateTime.UtcNow,backup,PrismBridge.Root);
        await File.WriteAllTextAsync(Path.Combine(folder,id+".json"),JsonSerializer.Serialize(ticket));
        var worker=Path.Combine(Path.GetTempPath(),"ImmersiveMinecraft-uninstall-"+id+".exe");File.Copy(Environment.ProcessPath!,worker);
        var start=new ProcessStartInfo(worker){UseShellExecute=false,CreateNoWindow=true,WindowStyle=ProcessWindowStyle.Hidden};start.ArgumentList.Add("--uninstall-worker");start.ArgumentList.Add(id);
        _=Process.Start(start)??throw new IOException("Could not start removal. Your backup is already saved.");
        beforeExit();Process.Start(new ProcessStartInfo(ExportFolder){UseShellExecute=true});Application.Exit();
    }
    public static void Worker(string id)
    {
        if(!Guid.TryParseExact(id,"N",out _))throw new InvalidDataException("Invalid uninstall request.");
        var file=Path.Combine(PrismBridge.Root,"uninstall",id+".json");var ticket=JsonSerializer.Deserialize<Ticket>(File.ReadAllText(file))!;
        if(ticket.Root!=PrismBridge.Root||DateTime.UtcNow-ticket.Created>TimeSpan.FromMinutes(10)||ticket.Created>DateTime.UtcNow.AddMinutes(1)||!File.Exists(ticket.Backup))throw new IOException("Uninstall request is not valid or its backup is missing.");
        using(var backup=System.IO.Compression.ZipFile.OpenRead(ticket.Backup))if(backup.GetEntry("BACKUP-INFO.json")==null)throw new IOException("The world backup could not be verified; removal stopped.");
        try {using var parent=Process.GetProcessById(ticket.Parent);if(!parent.WaitForExit(30000))throw new IOException("Close the launcher to finish removal.");}catch(ArgumentException){}
        using var single=new Mutex(false,"Local\\ImmersiveMinecraftLauncher");bool acquired;
        try{acquired=single.WaitOne(TimeSpan.FromSeconds(5));}catch(AbandonedMutexException){acquired=true;}
        if(!acquired)throw new IOException("Another launcher is open; removal postponed.");
        try {
            WorldMode.AssertClosed();
            for(int attempt=0;;attempt++)try{PersonalFiles.RemoveTree(PrismBridge.Root,Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData));break;}catch(IOException) when(attempt<2){Thread.Sleep(1000);}
            ElevenLabsCredentials.Remove();RemoveShortcut();
            File.WriteAllText(ticket.Backup+".uninstalled.txt","Immersive Minecraft removed. Worlds and servers are in the adjacent ZIP. Sharing/download files and other Minecraft installations were retained.");
        }finally{single.ReleaseMutex();}
    }
    static void RemoveShortcut()
    {
        var path=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),"Immersive Minecraft.lnk");if(!File.Exists(path))return;
        object? shell=null,shortcut=null;
        try {shell=Activator.CreateInstance(Type.GetTypeFromProgID("WScript.Shell")!)!;shortcut=((dynamic)shell).CreateShortcut(path);string target=((dynamic)shortcut).TargetPath;
            if(Path.GetFullPath(target).StartsWith(Path.GetFullPath(PrismBridge.Root)+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))File.Delete(path);
        }finally{if(shortcut!=null)Marshal.FinalReleaseComObject(shortcut);if(shell!=null)Marshal.FinalReleaseComObject(shell);}
    }
    public static void CleanupWorker(string id)
    {
        if(!Guid.TryParseExact(id,"N",out _)||!string.Equals(Environment.ProcessPath,Path.Combine(Path.GetTempPath(),"ImmersiveMinecraft-uninstall-"+id+".exe"),StringComparison.OrdinalIgnoreCase))return;
        var script=Path.Combine(Path.GetTempPath(),"ImmersiveMinecraft-uninstall-"+id+".ps1");
        File.WriteAllText(script,"""
param([string]$WorkerFile,[int]$WorkerPid)
$cleanupTemp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$cleanupFile=[IO.Path]::GetFullPath($WorkerFile)
if(-not [string]::Equals([IO.Path]::GetDirectoryName($cleanupFile),$cleanupTemp,[StringComparison]::OrdinalIgnoreCase)){exit 1}
if([IO.Path]::GetFileName($cleanupFile) -notmatch '^ImmersiveMinecraft-uninstall-[a-f0-9]{32}\.exe$'){exit 1}
if(-not [string]::Equals([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($PSCommandPath)),$cleanupTemp,[StringComparison]::OrdinalIgnoreCase)){exit 1}
Wait-Process -Id $WorkerPid -Timeout 30 -ErrorAction SilentlyContinue
if(Get-Process -Id $WorkerPid -ErrorAction SilentlyContinue){exit 1}
Remove-Item -LiteralPath $cleanupFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $PSCommandPath -Force -ErrorAction SilentlyContinue
""");
        var start=new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),"WindowsPowerShell","v1.0","powershell.exe")){UseShellExecute=false,CreateNoWindow=true,WindowStyle=ProcessWindowStyle.Hidden};
        foreach(var argument in new[]{"-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",script,"-WorkerFile",Environment.ProcessPath!,"-WorkerPid",Environment.ProcessId.ToString()})start.ArgumentList.Add(argument);
        Process.Start(start);
    }
}
