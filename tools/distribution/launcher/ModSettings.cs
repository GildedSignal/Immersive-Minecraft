using System.IO.Compression;
using System.Text.Json;
using System.Text.Json.Nodes;
namespace FriendPack;

public record SettingsImportResult(int Schema,string Source,int Files,long Bytes,int Kept);
public static class ModSettings
{
    const string Receipt=".friendpack-settings-import.json";
    static readonly HashSet<string> Excluded=new(StringComparer.OrdinalIgnoreCase){"logs","log","cache","caches","tmp","temp","backups","backup","saves","mods","assets","libraries","versions","resourcepacks","shaderpacks","screenshots","crash-reports"};
    static void NoLink(string path){if((File.GetAttributes(path)&FileAttributes.ReparsePoint)!=0)throw new IOException("Linked settings/data folders cannot be imported.");}
    static string Name(string value)=>new(value.Where(char.IsLetterOrDigit).Select(char.ToLowerInvariant).ToArray());
    public static HashSet<string> ModIds(string game) {
        var ids=new HashSet<string>(StringComparer.Ordinal);var mods=Path.Combine(game,"mods");if(!Directory.Exists(mods))return ids;NoLink(mods);
        foreach(var file in Directory.EnumerateFiles(mods,"*.jar")){NoLink(file);try{using var zip=ZipFile.OpenRead(file);var entry=zip.GetEntry("fabric.mod.json");if(entry==null||entry.Length>1024*1024)continue;
            using var data=entry.Open();using var json=JsonDocument.Parse(data);if(json.RootElement.TryGetProperty("id",out var id)&&id.ValueKind==JsonValueKind.String)ids.Add(id.GetString()!);
        }catch(InvalidDataException){}catch(JsonException){}}
        return ids;
    }
    public static bool NeedsImport(string game)=>!File.Exists(PackEngine.Resolve(game,Receipt));
    public static async Task<SettingsImportResult> Import(string source,string destination,ConfigRecipe[] recipes,bool replace=false,Func<bool>? gameIsRunning=null,CancellationToken cancel=default,Action<string>? progress=null) {
        source=Path.GetFullPath(source).TrimEnd(Path.DirectorySeparatorChar);destination=Path.GetFullPath(destination).TrimEnd(Path.DirectorySeparatorChar);
        if(source.Equals(destination,StringComparison.OrdinalIgnoreCase)||source.StartsWith(destination+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)||destination.StartsWith(source+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Choose a separate previous Minecraft installation.");
        NoLink(source);Directory.CreateDirectory(destination);NoLink(destination);
        var sourceOptions=Path.Combine(source,"options.txt");if(File.Exists(sourceOptions)){NoLink(sourceOptions);var options=PackEngine.ReadOptions(await File.ReadAllTextAsync(sourceOptions,cancel));if(options.TryGetValue("version",out var version)&&int.TryParse(version,out var dataVersion)&&dataVersion>4903)throw new IOException("This installation uses a newer Minecraft settings format. Choose a matching version to import.");}
        if(gameIsRunning?.Invoke()==true)throw new IOException("Close Minecraft before importing settings.");
        if(!replace&&!NeedsImport(destination))return JsonSerializer.Deserialize<SettingsImportResult>(await File.ReadAllTextAsync(PackEngine.Resolve(destination,Receipt)),PackEngine.Json)!;
        var known=recipes.ToDictionary(r=>r.Path,StringComparer.OrdinalIgnoreCase);var owners=ModIds(source).Select(Name).ToHashSet(StringComparer.Ordinal);
        var candidates=new List<string>();var config=Path.Combine(source,"config");if(Directory.Exists(config))Walk(config);
        var shaders=Path.Combine(source,"shaderpacks");if(Directory.Exists(shaders)){NoLink(shaders);foreach(var file in Directory.EnumerateFiles(shaders,"*.zip.txt")){NoLink(file);candidates.Add(Path.GetRelativePath(source,file).Replace('\\','/'));}}
        // Folder ownership comes from installed mod IDs or a conventional
        // config/configs child. No per-mod preference/data allowlist.
        foreach(var folder in Directory.EnumerateDirectories(source)){
            var name=Path.GetFileName(folder);if(name.StartsWith('.')||Excluded.Contains(name)||name.Equals("config",StringComparison.OrdinalIgnoreCase))continue;
            if(owners.Contains(Name(name))||Directory.Exists(Path.Combine(folder,"config"))||Directory.Exists(Path.Combine(folder,"configs")))Walk(folder);
        }
        void Walk(string folder){NoLink(folder);foreach(var entry in Directory.EnumerateFileSystemEntries(folder)){
            NoLink(entry);var name=Path.GetFileName(entry);if(name.StartsWith('.')||Excluded.Contains(name))continue;
            if(Directory.Exists(entry)){Walk(entry);continue;}
            if(Path.GetExtension(name).ToLowerInvariant() is ".log" or ".gz" or ".tmp" or ".lock" or ".jar" or ".exe" or ".dll"||name.EndsWith(".log.old",StringComparison.OrdinalIgnoreCase))continue;
            candidates.Add(Path.GetRelativePath(source,entry).Replace('\\','/'));
        }}
        var stage=PackEngine.Resolve(destination,".friendpack-settings-stage/"+Guid.NewGuid().ToString("N"));var backup=PackEngine.Resolve(destination,".friendpack-settings-backups/"+DateTime.UtcNow.ToString("yyyyMMdd-HHmmss")+"-"+Guid.NewGuid().ToString("N"));
        var pending=new List<string>();var written=new List<(string Path,bool Existed)>();long bytes=0;int kept=0;
        try {
            foreach(var relative in candidates){cancel.ThrowIfCancellationRequested();var input=PackEngine.Resolve(source,relative);var target=PackEngine.Resolve(destination,relative);var length=new FileInfo(input).Length;
                if(length>512L*1024*1024||(bytes+=length)>4L*1024*1024*1024)throw new IOException("Mod data exceeds the automatic import limit. Import from a smaller copy.");
                var temp=PackEngine.Resolve(stage,relative);Directory.CreateDirectory(Path.GetDirectoryName(temp)!);
                if(File.Exists(target)&&!replace){
                    if(known.TryGetValue(relative,out var recipe)){
                        await File.WriteAllTextAsync(temp,ConfigRecipes.ImportPreferences(recipe,await File.ReadAllTextAsync(input,cancel),await File.ReadAllTextAsync(target,cancel)),cancel);
                    }else if(relative.StartsWith("config/",StringComparison.OrdinalIgnoreCase)||relative.Split('/').Skip(1).Any(p=>p.Equals("config",StringComparison.OrdinalIgnoreCase)||p.Equals("configs",StringComparison.OrdinalIgnoreCase))){
                        // Unknown first-import settings use the old installation.
                        // All overwritten target bytes have a local backup.
                        File.Copy(input,temp);
                    }else{kept++;continue;}
                }else File.Copy(input,temp);
                if(known.TryGetValue(relative,out var reviewed))await File.WriteAllTextAsync(temp,ConfigRecipes.Merge(reviewed,await File.ReadAllTextAsync(temp,cancel)),cancel);
                pending.Add(relative);progress?.Invoke($"Bringing your mod settings {pending.Count}");
            }
            var result=new SettingsImportResult(1,source,pending.Count,bytes,kept);Directory.CreateDirectory(stage);await File.WriteAllTextAsync(PackEngine.Resolve(stage,Receipt),JsonSerializer.Serialize(result,PackEngine.Json),cancel);pending.Add(Receipt);
            if(gameIsRunning?.Invoke()==true)throw new IOException("Close Minecraft before importing settings.");
            foreach(var relative in pending){cancel.ThrowIfCancellationRequested();var target=PackEngine.Resolve(destination,relative);var existed=File.Exists(target);if(existed){var saved=PackEngine.Resolve(backup,relative);Directory.CreateDirectory(Path.GetDirectoryName(saved)!);File.Copy(target,saved);}
                written.Add((relative,existed));Directory.CreateDirectory(Path.GetDirectoryName(target)!);File.Move(PackEngine.Resolve(stage,relative),target,true);
            }return result;
        }catch{foreach(var item in written.AsEnumerable().Reverse()){var target=PackEngine.Resolve(destination,item.Path);if(item.Existed)File.Copy(PackEngine.Resolve(backup,item.Path),target,true);else if(File.Exists(target))File.Delete(target);}throw;}
        finally{if(Directory.Exists(stage))Directory.Delete(stage,true);}
    }
}
