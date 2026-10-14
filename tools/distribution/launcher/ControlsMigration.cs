using System.Text.Json;
using System.Text.Json.Nodes;
using Newtonsoft.Json.Linq;
namespace FriendPack;

public record ControlImportResult(int Options,int ConfigFiles,string Source);
public static class ControlsMigration
{
    static bool ControlName(string key)=>!key.Equals("stateKey",StringComparison.OrdinalIgnoreCase)&&(PackEngine.Binding(key)||key.Equals("keys",StringComparison.OrdinalIgnoreCase)||key.Contains("hotkey",StringComparison.OrdinalIgnoreCase));
    public static JsonObject Extract(JsonObject source)
    {
        var selected=new JsonObject();
        foreach(var item in source) {
            if(ControlName(item.Key) && item.Value is not null && !(item.Value is JsonValue value&&value.TryGetValue<bool>(out _)))selected[item.Key]=item.Value.DeepClone();
            else if(item.Value is JsonObject child) {var nested=Extract(child);if(nested.Count>0)selected[item.Key]=nested;}
        }
        return selected;
    }
    static JsonObject Parse(string text)=>JsonNode.Parse(JToken.Parse(text).ToString(Newtonsoft.Json.Formatting.None)) as JsonObject??throw new InvalidDataException("Control configuration is not an object.");
    public static JsonObject ParseObject(string text)=>Parse(text);
    static void Overlay(JsonObject target,JsonObject controls)
    {
        foreach(var item in controls) {
            if(!ControlName(item.Key)&&item.Value is JsonObject nested&&target[item.Key] is JsonObject existing)Overlay(existing,nested);
            else target[item.Key]=item.Value?.DeepClone();
        }
    }
    public static string? FindSource(string destination,IEnumerable<string> managedMods,IEnumerable<string>? modIds=null)
    {
        var roaming=Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        var candidates=new List<string>();
        var modrinth=Path.Combine(roaming,"ModrinthApp","profiles");
        if(Directory.Exists(modrinth))candidates.AddRange(Directory.EnumerateDirectories(modrinth));
        var prism=Path.Combine(roaming,"PrismLauncher","instances");
        if(Directory.Exists(prism))candidates.AddRange(Directory.EnumerateDirectories(prism).Select(p=>Path.Combine(p,".minecraft")));
        var selected=ChooseSource(destination,managedMods,candidates,modIds);
        if(selected!=null)return selected;
        var vanilla=Path.Combine(roaming,".minecraft");return File.Exists(Path.Combine(vanilla,"options.txt"))&&!Same(vanilla,destination)?vanilla:null;
    }
    public static string? ChooseSource(string destination,IEnumerable<string> managedMods,IEnumerable<string> candidates,IEnumerable<string>? modIds=null)
    {
        var names=managedMods.Select(p=>Path.GetFileName(p)!).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var ids=(modIds??[]).ToHashSet(StringComparer.Ordinal);
        var selected=candidates.Where(p=>File.Exists(Path.Combine(p,"options.txt"))&&!Same(p,destination))
            .Select(p=>new {Path=p,Score=ids.Count>0?ModSettings.ModIds(p).Count(ids.Contains):Directory.Exists(Path.Combine(p,"mods"))?Directory.EnumerateFiles(Path.Combine(p,"mods"),"*.jar").Count(f=>names.Contains(Path.GetFileName(f))):0,Time=File.GetLastWriteTimeUtc(Path.Combine(p,"options.txt"))})
            .Where(p=>p.Score>=Math.Min(10,names.Count)).OrderByDescending(p=>p.Score).ThenByDescending(p=>p.Time).FirstOrDefault();
        return selected?.Path;
    }
    static bool Same(string a,string b)=>string.Equals(Path.GetFullPath(a).TrimEnd(Path.DirectorySeparatorChar),Path.GetFullPath(b).TrimEnd(Path.DirectorySeparatorChar),StringComparison.OrdinalIgnoreCase);
    public static bool NeedsImport(string destination)=>!File.Exists(Path.Combine(destination,".friendpack-controls-import.json"));
    public static async Task<ControlImportResult> Import(string source,string destination,IEnumerable<string> recipePaths)
    {
        if(Same(source,destination))throw new InvalidDataException("Choose a different Minecraft installation to import controls from.");
        var options=Path.Combine(source,"options.txt");if(!File.Exists(options))throw new FileNotFoundException("This folder has no Minecraft controls.");
        Directory.CreateDirectory(destination);
        var backup=PackEngine.Resolve(destination,".friendpack-control-backups/"+DateTime.UtcNow.ToString("yyyyMMdd-HHmmss")+"-"+Guid.NewGuid().ToString("N"));
        var pending=new Dictionary<string,string>();
        var targetOptions=PackEngine.Resolve(destination,"options.txt");
        var personal=PackEngine.ReadOptions(await File.ReadAllTextAsync(options)).Where(p=>PackEngine.PersonalOption(p.Key)).ToDictionary(p=>p.Key,p=>p.Value);
        var merged=PackEngine.ReadOptions(File.Exists(targetOptions)?await File.ReadAllTextAsync(targetOptions):"");
        foreach(var pair in personal)merged[pair.Key]=pair.Value;
        pending["options.txt"]=string.Join('\n',merged.Select(p=>p.Key+":"+p.Value))+"\n";
        var paths=recipePaths.Concat(new[]{"config/litematica.json","config/malilib.json","config/tweakeroo.json","config/fzzy_config/keybinds.toml"}).Distinct(StringComparer.OrdinalIgnoreCase);
        int count=0;
        foreach(var relative in paths) {
            if(!relative.StartsWith("config/",StringComparison.Ordinal))continue;
            var original=PackEngine.Resolve(source,relative);if(!File.Exists(original))continue;
            var target=PackEngine.Resolve(destination,relative);
            if(relative=="config/fzzy_config/keybinds.toml") {pending[relative]=await File.ReadAllTextAsync(original);count++;continue;}
            if(!relative.EndsWith(".json",StringComparison.OrdinalIgnoreCase)&&!relative.EndsWith(".json5",StringComparison.OrdinalIgnoreCase))continue;
            var controls=Extract(Parse(await File.ReadAllTextAsync(original)));if(controls.Count==0)continue;
            var existing=File.Exists(target)?Parse(await File.ReadAllTextAsync(target)):new JsonObject();Overlay(existing,controls);
            pending[relative]=existing.ToJsonString(PackEngine.Json);count++;
        }
        // Parse everything before changing anything; keep a local rollback copy.
        var written=new List<(string Path,bool Existed)>();
        try {
            foreach(var item in pending) {
                var target=PackEngine.Resolve(destination,item.Key);bool existed=File.Exists(target);
                if(existed){var saved=PackEngine.Resolve(backup,item.Key);Directory.CreateDirectory(Path.GetDirectoryName(saved)!);File.Copy(target,saved);}
                written.Add((item.Key,existed));Directory.CreateDirectory(Path.GetDirectoryName(target)!);await File.WriteAllTextAsync(target,item.Value);
            }
            var result=new ControlImportResult(personal.Count,count,Path.GetFullPath(source));
            await File.WriteAllTextAsync(Path.Combine(destination,".friendpack-controls-import.json"),JsonSerializer.Serialize(result,PackEngine.Json));return result;
        }catch {
            foreach(var item in written.AsEnumerable().Reverse()) {var target=PackEngine.Resolve(destination,item.Path);if(item.Existed)File.Copy(PackEngine.Resolve(backup,item.Path),target,true);else File.Delete(target);}
            throw;
        }
    }
}
