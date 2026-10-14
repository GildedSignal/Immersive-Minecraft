using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Runtime.InteropServices;
using FriendPack;

static class ReleasePackage
{
    public static string Prepare(string? destination=null)
    {
        using var resource=Assembly.GetExecutingAssembly().GetManifestResourceStream("ImmersiveMinecraft.Release")
            ??throw new InvalidDataException("The pack is missing from this launcher. Download the complete release.");
        var digest=Convert.ToHexString(SHA256.HashData(resource)).ToLowerInvariant();resource.Position=0;
        var root=destination==null?Path.Combine(PrismBridge.Root,"packages",digest):Path.GetFullPath(destination);
        using var archive=new ZipArchive(resource,ZipArchiveMode.Read);
        if(Directory.Exists(root)&&Complete(root,archive))return root;
        if(destination!=null&&Directory.Exists(root)&&Directory.EnumerateFileSystemEntries(root).Any())
            throw new IOException("Choose an empty folder for the package test.");
        var stage=root+"-new-"+Guid.NewGuid().ToString("N");Directory.CreateDirectory(stage);
        try {
            var names=new HashSet<string>(StringComparer.OrdinalIgnoreCase);long total=0;
            foreach(var entry in archive.Entries) {
                if(entry.FullName.EndsWith('/'))continue;
                if(!names.Add(entry.FullName)||entry.Length>256L*1024*1024||(total+=entry.Length)>512L*1024*1024)
                    throw new InvalidDataException("Unexpected release package contents.");
                var path=PackEngine.Resolve(stage,entry.FullName);Directory.CreateDirectory(Path.GetDirectoryName(path)!);entry.ExtractToFile(path);
            }
            if(!File.Exists(Path.Combine(stage,"manifest.json")))throw new InvalidDataException("Missing pack manifest.");
            Directory.CreateDirectory(Path.GetDirectoryName(root)!);
            if(Directory.Exists(root))Directory.Delete(root,true);
            Directory.Move(stage,root);return root;
        }finally{if(Directory.Exists(stage))Directory.Delete(stage,true);}
    }
    static bool Complete(string root,ZipArchive archive)
    {
        try {
            foreach(var entry in archive.Entries) {
                if(entry.FullName.EndsWith('/'))continue;
                var file=PackEngine.Resolve(root,entry.FullName);
                if(!File.Exists(file)||new FileInfo(file).Length!=entry.Length)return false;
                using var original=entry.Open();using var existing=File.OpenRead(file);
                if(!SHA256.HashData(original).SequenceEqual(SHA256.HashData(existing)))return false;
            }
            return true;
        }catch(IOException){return false;}
    }
    public static void InstallShortcut()
    {
        var source=Environment.ProcessPath??throw new IOException("Launcher location is unavailable.");
        using var input=File.OpenRead(source);var hash=Convert.ToHexString(SHA256.HashData(input)).ToLowerInvariant();
        var installed=Path.Combine(PrismBridge.Root,"client",hash[..24],"Immersive Minecraft.exe");
        Directory.CreateDirectory(Path.GetDirectoryName(installed)!);
        if(!File.Exists(installed))File.Copy(source,installed);
        var shellType=Type.GetTypeFromProgID("WScript.Shell")??throw new IOException("Windows shortcuts are unavailable.");
        object? shell=null,shortcut=null;
        try {
            shell=Activator.CreateInstance(shellType)!;
            shortcut=((dynamic)shell).CreateShortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),"Immersive Minecraft.lnk"));
            ((dynamic)shortcut).TargetPath=installed;((dynamic)shortcut).WorkingDirectory=Path.GetDirectoryName(installed);
            ((dynamic)shortcut).Description="Your immersive Minecraft adventure";((dynamic)shortcut).Save();
        }finally {
            if(shortcut!=null)Marshal.FinalReleaseComObject(shortcut);
            if(shell!=null)Marshal.FinalReleaseComObject(shell);
        }
    }
}
