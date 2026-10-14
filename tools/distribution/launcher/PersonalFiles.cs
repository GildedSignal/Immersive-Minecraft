using System.IO.Compression;
using System.Text.Json;
namespace FriendPack;

public record PersonalExportResult(int Worlds,int Files,long Bytes);
public static class PersonalFiles
{
    static void NoLink(string path){if((File.GetAttributes(path)&FileAttributes.ReparsePoint)!=0)throw new IOException("Linked files/folders cannot be copied as world backups.");}
    static List<(string Path,string Relative)> Files(string folder)
    {
        NoLink(folder);var list=new List<(string,string)>();
        void Walk(string dir){foreach(var entry in Directory.EnumerateFileSystemEntries(dir)){
            NoLink(entry);if(Directory.Exists(entry))Walk(entry);
            else if(Path.GetFileName(entry)!="session.lock")list.Add((entry,Path.GetRelativePath(folder,entry).Replace('\\','/')));
        }}Walk(folder);return list;
    }
    static FileStream? LockWorld(string folder)
    {
        var file=Path.Combine(folder,"session.lock");if(!File.Exists(file))return null;
        NoLink(file);try{return new FileStream(file,FileMode.Open,FileAccess.Read,FileShare.None);}
        catch(IOException){throw new IOException("This world is open. Save and close Minecraft before copying it.");}
    }
    public static async Task<PersonalExportResult> Export(string game,string archive,CancellationToken cancel=default,Action<string>? progress=null)
        =>await ExportProfiles(new Dictionary<string,string>{{"",game}},archive,cancel,progress);
    public static async Task<PersonalExportResult> ExportProfiles(IReadOnlyDictionary<string,string> games,string archive,CancellationToken cancel=default,Action<string>? progress=null)
    {
        archive=Path.GetFullPath(archive);
        foreach(var game in games.Values)if(archive.StartsWith(Path.GetFullPath(game).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Save the backup outside your Minecraft game folder.");
        if(File.Exists(archive))throw new IOException("Choose a new backup filename; existing backups are kept.");
        var locks=new List<FileStream>();var entries=new List<(string Path,string Name)>();long total=0;int worlds=0;
        var temp=archive+".partial-"+Guid.NewGuid().ToString("N");
        try {
            foreach(var profile in games) {
                if(profile.Key.Contains('/')||profile.Key.Contains('\\')||profile.Key is "." or "..")throw new InvalidDataException("Invalid backup profile label.");
                var game=profile.Value;if(Directory.Exists(game))NoLink(game);var saves=Path.Combine(game,"saves");var prefix=profile.Key.Length>0?profile.Key+"/":"";
                if(Directory.Exists(saves)){NoLink(saves);foreach(var world in Directory.EnumerateDirectories(saves).Where(w=>File.Exists(Path.Combine(w,"level.dat")))){
                    worlds++;NoLink(world);var held=LockWorld(world);if(held!=null)locks.Add(held);foreach(var file in Files(world))entries.Add((file.Path,prefix+"saves/"+Path.GetFileName(world)+"/"+file.Relative));
                }}
                var servers=Path.Combine(game,"servers.dat");if(File.Exists(servers)){NoLink(servers);entries.Add((servers,prefix+"servers.dat"));}
            }
            Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
            await using(var output=new FileStream(temp,FileMode.CreateNew,FileAccess.ReadWrite,FileShare.None,81920,true)) {
                using var zip=new ZipArchive(output,ZipArchiveMode.Create,true);int done=0;
                foreach(var file in entries){cancel.ThrowIfCancellationRequested();progress?.Invoke($"Saving your adventures {++done}/{entries.Count}");
                    var entry=zip.CreateEntry(file.Name,CompressionLevel.Fastest);await using var input=File.OpenRead(file.Path);total+=input.Length;await using var destination=entry.Open();await input.CopyToAsync(destination,cancel);
                }
                var note=zip.CreateEntry("BACKUP-INFO.json");await using var data=note.Open();await JsonSerializer.SerializeAsync(data,new {Schema=1,Worlds=worlds,Files=entries.Count,Bytes=total,CreatedUtc=DateTime.UtcNow,Description="Personal Java worlds and saved servers; original files unchanged. No launcher accounts or ElevenLabs credential store included."},PackEngine.Json,cancel);
            }
            File.Move(temp,archive);return new(worlds,entries.Count,total);
        }finally {foreach(var held in locks)held.Dispose();if(File.Exists(temp))File.Delete(temp);}
    }
    public static async Task<string> ImportWorld(string source,string game,int targetDataVersion=4903,CancellationToken cancel=default,Action<string>? progress=null)
    {
        source=Path.GetFullPath(source).TrimEnd(Path.DirectorySeparatorChar);game=Path.GetFullPath(game);
        NoLink(source);var metadata=Path.Combine(source,"level.dat");if(!File.Exists(metadata))throw new IOException("Choose the world folder containing level.dat.");NoLink(metadata);
        using var held=LockWorld(source);
        if(new FileInfo(metadata).Length>32*1024*1024)throw new InvalidDataException("World metadata is too large.");
        var version=SavedServers.WorldDataVersion(await File.ReadAllBytesAsync(metadata,cancel));
        if(version>targetDataVersion)throw new IOException("This save was created by a newer Minecraft version. Use a matching/newer game to open it.");
        var saves=Path.Combine(game,"saves");Directory.CreateDirectory(saves);
        var name=Path.GetFileName(source);var destination=PackEngine.Resolve(saves,name);int suffix=1;
        while(Directory.Exists(destination)||File.Exists(destination))destination=PackEngine.Resolve(saves,name+" (copy "+(suffix++)+")");
        NoLink(game);NoLink(saves);var stagingRoot=PackEngine.Resolve(game,".friendpack-world-imports");Directory.CreateDirectory(stagingRoot);NoLink(stagingRoot);
        var staged=PackEngine.Resolve(stagingRoot,"import-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(staged);
        try {
            var files=Files(source);int done=0;
            foreach(var file in files){cancel.ThrowIfCancellationRequested();progress?.Invoke($"Copying your world {++done}/{files.Count}");
                var target=PackEngine.Resolve(staged,file.Relative);Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                await using var input=File.OpenRead(file.Path);await using var output=new FileStream(target,FileMode.CreateNew,FileAccess.Write,FileShare.None,81920,true);await input.CopyToAsync(output,cancel);
            }
            Directory.Move(staged,destination);return destination;
        }finally{if(Directory.Exists(staged))RemoveTree(staged,stagingRoot);}
    }
    public static void RemoveTree(string root,string allowedParent)
    {
        root=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);var parent=Path.GetFullPath(allowedParent).TrimEnd(Path.DirectorySeparatorChar);
        if(!string.Equals(Path.GetDirectoryName(root),parent,StringComparison.OrdinalIgnoreCase))throw new IOException("Removal target is outside its expected parent folder.");
        if(!Directory.Exists(root))return;NoLink(root);
        void Delete(string dir){foreach(var entry in Directory.EnumerateFileSystemEntries(dir)){
            var attrs=File.GetAttributes(entry);if((attrs&FileAttributes.Directory)!=0){if((attrs&FileAttributes.ReparsePoint)!=0)Directory.Delete(entry,false);else Delete(entry);}
            else {if((attrs&FileAttributes.ReadOnly)!=0)File.SetAttributes(entry,attrs&~FileAttributes.ReadOnly);File.Delete(entry);}
        }Directory.Delete(dir,false);}Delete(root);
    }
}
