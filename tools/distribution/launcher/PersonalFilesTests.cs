using System.Buffers.Binary;
using System.IO.Compression;
using System.Text;
namespace FriendPack;
static class PersonalFilesTests
{
    static byte[] Level(int version)
    {
        using var raw=new MemoryStream();using(var output=new BinaryWriter(raw,Encoding.UTF8,true)){
            void Name(string name){var bytes=Encoding.UTF8.GetBytes(name);Span<byte> size=stackalloc byte[2];BinaryPrimitives.WriteUInt16BigEndian(size,(ushort)bytes.Length);output.Write(size);output.Write(bytes);}
            output.Write((byte)10);Name("");output.Write((byte)10);Name("Data");output.Write((byte)3);Name("DataVersion");Span<byte> number=stackalloc byte[4];BinaryPrimitives.WriteInt32BigEndian(number,version);output.Write(number);
            output.Write((byte)8);Name("Generator");Name("minecraft:overworld");output.Write((byte)0);output.Write((byte)0);
        }
        using var zipped=new MemoryStream();using(var gzip=new GZipStream(zipped,CompressionLevel.Fastest,true))gzip.Write(raw.ToArray());return zipped.ToArray();
    }
    public static async Task Run(string root)
    {
        int checks=0;void Check(bool okay,string note){if(!okay)throw new Exception("FAIL personal files: "+note);checks++;}
        async Task Reject(Func<Task> action,string note){try{await action();}catch(IOException){checks++;return;}throw new Exception("FAIL accepted: "+note);}
        var basis=Path.Combine(root,"personal-files");var source=Path.Combine(basis,"original","My adventure");var game=Path.Combine(basis,"game");Directory.CreateDirectory(Path.Combine(source,"region"));Directory.CreateDirectory(Path.Combine(source,"datapacks"));Directory.CreateDirectory(game);
        await File.WriteAllBytesAsync(Path.Combine(source,"level.dat"),Level(4903));await File.WriteAllTextAsync(Path.Combine(source,"region","r.0.0.mca"),"original region");await File.WriteAllTextAsync(Path.Combine(source,"datapacks","custom.zip"),"datapack fixture");await File.WriteAllTextAsync(Path.Combine(source,"session.lock"),"lock");
        var hash=await PackEngine.Hash(Path.Combine(source,"level.dat"));Check(SavedServers.WorldDataVersion(Level(4903))==4903,"native compressed world DataVersion");Check(SavedServers.WorldIdentifiers(Level(4903)).Contains("minecraft:overworld"),"world namespace detection");
        var copied=await PersonalFiles.ImportWorld(source,game);Check(await PackEngine.Hash(Path.Combine(source,"level.dat"))==hash&&await File.ReadAllTextAsync(Path.Combine(source,"region","r.0.0.mca"))=="original region","original world unchanged");
        Check(await File.ReadAllTextAsync(Path.Combine(copied,"datapacks","custom.zip"))=="datapack fixture"&&!File.Exists(Path.Combine(copied,"session.lock")),"datapacks retained and transient lock omitted");
        var another=await PersonalFiles.ImportWorld(source,game);Check(another!=copied&&Directory.Exists(copied)&&Path.GetFileName(another)=="My adventure (copy 1)","collision creates a separate copy");
        using(var held=new FileStream(Path.Combine(source,"session.lock"),FileMode.Open,FileAccess.ReadWrite,FileShare.None))await Reject(()=>PersonalFiles.ImportWorld(source,game),"open source world");
        await File.WriteAllBytesAsync(Path.Combine(source,"level.dat"),Level(5000));await Reject(()=>PersonalFiles.ImportWorld(source,game),"world from newer Minecraft");await File.WriteAllBytesAsync(Path.Combine(source,"level.dat"),Level(4903));
        var before=Directory.GetDirectories(Path.Combine(game,"saves")).Length;using(var cancel=new CancellationTokenSource()){
            try{await PersonalFiles.ImportWorld(source,game,cancel:cancel.Token,progress:_=>cancel.Cancel());throw new Exception("Accepted cancellation");}catch(OperationCanceledException){}
        }
        Check(Directory.GetDirectories(Path.Combine(game,"saves")).Length==before&&!Directory.EnumerateDirectories(Path.Combine(game,".friendpack-world-imports")).Any(),"cancelled copy has no visible/partial world");
        await File.WriteAllTextAsync(Path.Combine(game,"servers.dat"),"personal server fixture");await File.WriteAllTextAsync(Path.Combine(game,"accounts.json"),"private account fixture");var other=Path.Combine(basis,"other-game");Directory.CreateDirectory(other);await File.WriteAllTextAsync(Path.Combine(other,"servers.dat"),"other server fixture");
        var backup=Path.Combine(basis,"backup.zip");var result=await PersonalFiles.ExportProfiles(new Dictionary<string,string>{{"Full pack",game},{"Vanilla enhancements",other}},backup);
        using(var zip=ZipFile.OpenRead(backup)){
            Check(result.Worlds==2&&zip.GetEntry("Full pack/saves/My adventure/region/r.0.0.mca")!=null&&zip.GetEntry("Vanilla enhancements/servers.dat")!=null,"both profiles and servers exported");
            Check(zip.Entries.All(e=>!e.FullName.EndsWith("accounts.json")&&!e.FullName.EndsWith("session.lock"))&&zip.GetEntry("BACKUP-INFO.json")!=null,"backup excludes accounts and locks");
        }
        await Reject(()=>PersonalFiles.Export(game,backup),"overwrite existing backup");await Reject(()=>PersonalFiles.Export(game,Path.Combine(game,"inside.zip")),"archive inside game");
        var cancelled=Path.Combine(basis,"cancelled.zip");using(var cancel=new CancellationTokenSource()){
            try{await PersonalFiles.Export(game,cancelled,cancel.Token,_=>cancel.Cancel());throw new Exception("Accepted cancellation");}catch(OperationCanceledException){}
        }
        Check(!File.Exists(cancelled)&&!Directory.GetFiles(basis,"cancelled.zip.partial-*").Any(),"cancelled export leaves no incomplete archive");
        var removal=Path.Combine(basis,"private-install");Directory.CreateDirectory(Path.Combine(removal,"nested"));var readOnly=Path.Combine(removal,"nested","file");await File.WriteAllTextAsync(readOnly,"owned");File.SetAttributes(readOnly,FileAttributes.ReadOnly);var canary=Path.Combine(basis,"other-install");Directory.CreateDirectory(canary);await File.WriteAllTextAsync(Path.Combine(canary,"keep"),"outside");
        await Reject(()=>{PersonalFiles.RemoveTree(basis,removal);return Task.CompletedTask;},"incorrect removal boundary");
        if(OperatingSystem.IsWindows()){
            var script=Path.Combine(basis,"create-fixture-link.ps1");await File.WriteAllTextAsync(script,"param([string]$Link,[string]$Target)\nNew-Item -ItemType Junction -Path $Link -Target $Target -ErrorAction Stop | Out-Null\n");
            async Task Link(string link,string target){
                var start=new System.Diagnostics.ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),"WindowsPowerShell","v1.0","powershell.exe")){UseShellExecute=false,CreateNoWindow=true,WindowStyle=System.Diagnostics.ProcessWindowStyle.Hidden};
                foreach(var value in new[]{"-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",script,"-Link",link,"-Target",target})start.ArgumentList.Add(value);
                using var process=System.Diagnostics.Process.Start(start)!;await process.WaitForExitAsync();if(process.ExitCode!=0)throw new Exception("Could not create bounded test junction");
            }
            var linked=Path.Combine(source,"linked");await Link(linked,canary);await Reject(()=>PersonalFiles.ImportWorld(source,game),"linked file tree in source world");Directory.Delete(linked,false);
            var staging=Path.Combine(game,".friendpack-world-imports");Directory.Delete(staging,false);await Link(staging,canary);await Reject(()=>PersonalFiles.ImportWorld(source,game),"linked import staging directory");Directory.Delete(staging,false);
            await Link(Path.Combine(removal,"linked"),canary);
        }
        PersonalFiles.RemoveTree(removal,basis);Check(!Directory.Exists(removal)&&File.Exists(Path.Combine(canary,"keep")),"only named private tree removed, neighboring/linked installation preserved");
        Console.WriteLine($"PASS {checks} world copy, export and removal checks.");
    }
}
