using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace FriendPack;

public record PackFile(string Path, string Sha256, long Size, string? Url, string? Payload, PatchSource? Patch=null);
public record PackManifest(int Schema, string Id, string Name, string Version, string Minecraft,
    string Fabric, bool ReleaseReady, PackFile[] Files, Dictionary<string,string> Options,string[]? ModIds=null);
public record InstalledState(string Version, Dictionary<string,string> Files);

public static class PackEngine
{
    public static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive=true, WriteIndented=true };
    public static string Resolve(string root, string relative)
    {
        if (string.IsNullOrWhiteSpace(relative) || relative.Contains('\\') || relative.Contains(':') ||
            relative.Split('/').Any(p => p is "" or "." or "..") || System.IO.Path.IsPathRooted(relative))
            throw new InvalidDataException("Invalid pack path: " + relative);
        var fullRoot=System.IO.Path.GetFullPath(root).TrimEnd(System.IO.Path.DirectorySeparatorChar,System.IO.Path.AltDirectorySeparatorChar)+System.IO.Path.DirectorySeparatorChar;
        var full=System.IO.Path.GetFullPath(System.IO.Path.Combine(root,relative));
        if (!full.StartsWith(fullRoot, OperatingSystem.IsWindows()?StringComparison.OrdinalIgnoreCase:StringComparison.Ordinal))
            throw new InvalidDataException("Pack path escapes its folder.");
        // No writes through an existing symlink/junction inside the destination.
        for (var p=System.IO.Path.GetDirectoryName(full); p!=null && p.StartsWith(fullRoot,StringComparison.OrdinalIgnoreCase); p=System.IO.Path.GetDirectoryName(p))
            if (Directory.Exists(p) && (File.GetAttributes(p)&FileAttributes.ReparsePoint)!=0)
                throw new IOException("Linked destination folder is not supported: "+relative);
        if (File.Exists(full) && (File.GetAttributes(full)&FileAttributes.ReparsePoint)!=0)
            throw new IOException("Linked destination file is not supported: "+relative);
        return full;
    }
    public static async Task<string> Hash(string path) {
        await using var input=File.OpenRead(path);
        return Convert.ToHexString(await SHA256.HashDataAsync(input)).ToLowerInvariant();
    }
    public static bool PersonalOption(string key) => key.StartsWith("key_",StringComparison.Ordinal) || key.StartsWith("soundCategory_",StringComparison.Ordinal) || key is
        "lang" or "mouseSensitivity" or "invertYMouse" or "rawMouseInput" or "discrete_mouse_scroll" or
        "mouseWheelSensitivity" or "toggleCrouch" or "toggleSprint" or "soundDevice" or "fullscreenResolution" or
        "narrator" or "highContrast" or "textBackgroundOpacity" or "chatOpacity" or "chatScale" or
        "chatWidth" or "chatHeightFocused" or "chatHeightUnfocused";
    public static Dictionary<string,string> ReadOptions(string content)
    {
        var result=new Dictionary<string,string>(StringComparer.Ordinal);
        foreach(var line in content.Split('\n')) {
            var p=line.IndexOf(':'); if(p>0) result[line[..p]]=line[(p+1)..].TrimEnd('\r');
        }
        return result;
    }
    public static string MergeOptions(string existing,Dictionary<string,string> managed)
    {
        var values=ReadOptions(existing);
        foreach(var item in managed) if(!PersonalOption(item.Key)) values[item.Key]=item.Value;
        return string.Join('\n',values.Select(x=>x.Key+":"+x.Value))+"\n";
    }
    // Preserve mod-defined binds recursively, including maps such as keybinds/action => key.
    public static bool Binding(string key) => key.Contains("keybind",StringComparison.OrdinalIgnoreCase) ||
        key.Contains("keybinding",StringComparison.OrdinalIgnoreCase) || key.Equals("bindings",StringComparison.OrdinalIgnoreCase) ||
        key.Equals("bind",StringComparison.OrdinalIgnoreCase) || key.EndsWith("Key",StringComparison.Ordinal) ||
        key.Equals("keyCode",StringComparison.OrdinalIgnoreCase) || key.Contains("hotkey",StringComparison.OrdinalIgnoreCase) || key.Equals("keys",StringComparison.OrdinalIgnoreCase);
    public static JsonNode? MergeConfig(JsonNode? desired,JsonNode? existing,bool personal=false)
    {
        if(personal && existing!=null) return existing.DeepClone();
        if(desired is JsonObject a && existing is JsonObject b) {
            var merged=(JsonObject)a.DeepClone();
            foreach(var kv in b) {
                if(!a.ContainsKey(kv.Key)) merged[kv.Key]=kv.Value?.DeepClone();
                else merged[kv.Key]=MergeConfig(a[kv.Key],kv.Value,Binding(kv.Key));
            }
            return merged;
        }
        return desired?.DeepClone();
    }
    static void Validate(PackManifest manifest)
    {
        if(manifest.Schema!=1 || manifest.Id is not ("immersive-minecraft" or "immersive-minecraft-vanilla")) throw new InvalidDataException("Unsupported pack manifest.");
        if(!manifest.ReleaseReady) throw new InvalidDataException("This pack is still being prepared; it is not ready for friends yet.");
        var paths=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach(var item in manifest.Files) {
            if(!paths.Add(item.Path)) throw new InvalidDataException("Duplicate pack file: "+item.Path);
            if(item.Path.Split('/')[0] is not ("mods" or "resourcepacks" or "shaderpacks" or "config" or "defaultconfigs"))
                throw new InvalidDataException("Unapproved pack folder: "+item.Path);
            if(item.Size<=0 || item.Sha256.Length!=64 || !item.Sha256.All(Uri.IsHexDigit)) throw new InvalidDataException("Missing integrity metadata.");
            if((item.Url==null)==(item.Payload==null)) throw new InvalidDataException("File needs exactly one source.");
            if(item.Url!=null && (!Uri.TryCreate(item.Url,UriKind.Absolute,out var url)||url.Scheme!="https"))
                throw new InvalidDataException("Downloads must use HTTPS.");
            if(item.Patch is {} patch && (item.Url==null||patch.Size<=0||patch.Sha256.Length!=64||patch.RecipeSha256.Length!=64||!patch.Sha256.All(Uri.IsHexDigit)||!patch.RecipeSha256.All(Uri.IsHexDigit)))
                throw new InvalidDataException("Incomplete customization integrity metadata.");
        }
    }
    public static async Task Apply(PackManifest manifest,string packageRoot,string minecraftRoot,Action<string> progress,
        CancellationToken cancel=default, ConfigRecipe[]? configurations=null, Func<bool>? gameIsRunning=null)
    {
        Validate(manifest);
        Directory.CreateDirectory(minecraftRoot);
        // The launcher uses its own instance. Never modify the author's running Modrinth profile.
        if(System.IO.Path.GetFullPath(minecraftRoot).Contains("ModrinthApp",StringComparison.OrdinalIgnoreCase))
            throw new IOException("Use the dedicated friend instance, not the running Modrinth profile.");
        if(File.Exists(Resolve(minecraftRoot,".friendpack-playing"))||gameIsRunning?.Invoke()==true) throw new IOException("Close Minecraft before updating the pack.");
        using var updateLock=new FileStream(Resolve(minecraftRoot,".friendpack-update-lock"),FileMode.OpenOrCreate,FileAccess.ReadWrite,FileShare.None);
        var statePath=Resolve(minecraftRoot,".friendpack-state.json");
        var previous=File.Exists(statePath)?JsonSerializer.Deserialize<InstalledState>(await File.ReadAllTextAsync(statePath,cancel),Json):null;
        var transaction=System.IO.Path.Combine(minecraftRoot,".friendpack-transactions",Guid.NewGuid().ToString("N"));
        var staged=System.IO.Path.Combine(transaction,"staged"); var backup=System.IO.Path.Combine(transaction,"backup");
        Directory.CreateDirectory(staged); Directory.CreateDirectory(backup);
        var replacements=new Dictionary<string,string>();
        using var http=new HttpClient { Timeout=TimeSpan.FromMinutes(10) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("ImmersiveMinecraft-FriendPack/1.0");
        int preparedCount=0;
        foreach(var item in manifest.Files) {
            cancel.ThrowIfCancellationRequested(); var target=Resolve(minecraftRoot,item.Path);
            progress($"Preparing your world {++preparedCount}/{manifest.Files.Length}");
            if(File.Exists(target) && await Hash(target)==item.Sha256.ToLowerInvariant()) continue;
            var temp=Resolve(staged,item.Path); Directory.CreateDirectory(System.IO.Path.GetDirectoryName(temp)!);
            if(item.Payload!=null) File.Copy(Resolve(packageRoot,item.Payload),temp);
            else {
                var sourceHash=item.Patch?.Sha256??item.Sha256;
                var sourceSize=item.Patch?.Size??item.Size;
                var cached=Resolve(minecraftRoot,".friendpack-downloads/"+sourceHash.ToLowerInvariant());
                Directory.CreateDirectory(System.IO.Path.GetDirectoryName(cached)!);
                if(!File.Exists(cached)||new FileInfo(cached).Length!=sourceSize||await Hash(cached)!=sourceHash.ToLowerInvariant()) {
                    using var response=await http.GetAsync(item.Url,HttpCompletionOption.ResponseHeadersRead,cancel);
                    response.EnsureSuccessStatusCode();
                    await using var input=await response.Content.ReadAsStreamAsync(cancel);
                    await using(var output=File.Create(temp)) await input.CopyToAsync(output,cancel);
                    if(new FileInfo(temp).Length!=sourceSize||await Hash(temp)!=sourceHash.ToLowerInvariant())throw new InvalidDataException("Download verification failed: "+item.Path);
                    File.Copy(temp,cached,true);
                } else File.Copy(cached,temp);
            }
            if(item.Patch is {} patch) {
                if(new FileInfo(temp).Length!=patch.Size||await Hash(temp)!=patch.Sha256.ToLowerInvariant())throw new InvalidDataException("Original download verification failed: "+item.Path);
                var recipe=Resolve(packageRoot,patch.Recipe);
                if(await Hash(recipe)!=patch.RecipeSha256.ToLowerInvariant())throw new InvalidDataException("Customization recipe verification failed.");
                var original=temp+".original";File.Move(temp,original);
                await Task.Run(()=>ArchivePatch.Apply(original,recipe,temp),cancel);
                File.Delete(original);
            }
            if(new FileInfo(temp).Length!=item.Size || await Hash(temp)!=item.Sha256.ToLowerInvariant())
                throw new InvalidDataException("Download verification failed: "+item.Path);
            if(item.Path.StartsWith("config/",StringComparison.Ordinal) && File.Exists(target)) {
                if(item.Path.EndsWith(".json",StringComparison.OrdinalIgnoreCase)) {
                    var merged=MergeConfig(JsonNode.Parse(await File.ReadAllTextAsync(temp,cancel)),JsonNode.Parse(await File.ReadAllTextAsync(target,cancel)));
                    await File.WriteAllTextAsync(temp,merged?.ToJsonString(Json)??"null",cancel);
                } else {
                    // Non-JSON config files need an explicit reviewed merge policy if binds are present.
                    var old=await File.ReadAllTextAsync(target,cancel); var next=await File.ReadAllTextAsync(temp,cancel);
                    if(old.Split('\n').Concat(next.Split('\n')).Any(l=>l.Contains("keybind",StringComparison.OrdinalIgnoreCase)||l.Contains("binding",StringComparison.OrdinalIgnoreCase)))
                        throw new InvalidDataException("Keybind merge policy required for "+item.Path);
                }
            }
            replacements[item.Path]=temp;
        }
        foreach(var recipe in configurations??[]) {
            if(!recipe.Path.StartsWith("config/",StringComparison.Ordinal)) throw new InvalidDataException("Config recipe outside config folder.");
            if(replacements.ContainsKey(recipe.Path)||manifest.Files.Any(f=>f.Path==recipe.Path))throw new InvalidDataException("Config recipe conflicts with managed file.");
            var target=Resolve(minecraftRoot,recipe.Path); var temp=Resolve(staged,recipe.Path);
            var existing=File.Exists(target)?await File.ReadAllTextAsync(target,cancel):null;
            var merged=ConfigRecipes.Merge(recipe,existing??"");
            if(existing==merged)continue;
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(temp)!); await File.WriteAllTextAsync(temp,merged,cancel);
            replacements[recipe.Path]=temp;
        }
        var optionsPath=Resolve(minecraftRoot,"options.txt");
        var optionsTemp=Resolve(staged,"options.txt");
        var existingOptions=File.Exists(optionsPath)?await File.ReadAllTextAsync(optionsPath,cancel):null;
        var mergedOptions=MergeOptions(existingOptions??"",manifest.Options);
        if(existingOptions!=mergedOptions){
            await File.WriteAllTextAsync(optionsTemp,mergedOptions,cancel);
            replacements["options.txt"]=optionsTemp;
        }
        var desired=manifest.Files.ToDictionary(x=>x.Path,x=>x.Sha256,StringComparer.OrdinalIgnoreCase);
        var removed=new List<string>();
        if(previous!=null) foreach(var item in previous.Files) {
            if(desired.ContainsKey(item.Key)) continue;
            var path=Resolve(minecraftRoot,item.Key);
            // Remove only a formerly managed file whose bytes remain exactly unchanged.
            if(File.Exists(path) && await Hash(path)==item.Value.ToLowerInvariant()) removed.Add(item.Key);
        }
        var changed=new List<(string Path,bool Existed)>();
        // Downloading may take minutes. Recheck the actual game just before the
        // first managed-file mutation, not only when preparation began.
        if(File.Exists(Resolve(minecraftRoot,".friendpack-playing"))||gameIsRunning?.Invoke()==true) throw new IOException("Close Minecraft before updating the pack.");
        try {
            foreach(var path in replacements.Keys.Concat(removed)) {
                progress("Installing "+System.IO.Path.GetFileName(path));
                cancel.ThrowIfCancellationRequested();var target=Resolve(minecraftRoot,path); var existed=File.Exists(target);
                if(existed){var save=Resolve(backup,path);Directory.CreateDirectory(System.IO.Path.GetDirectoryName(save)!);File.Copy(target,save);}
                changed.Add((path,existed));
                if(replacements.TryGetValue(path,out var prepared)){
                    Directory.CreateDirectory(System.IO.Path.GetDirectoryName(target)!);File.Move(prepared,target,true);
                } else File.Delete(target);
            }
            var actual=new Dictionary<string,string>();
            foreach(var item in manifest.Files) actual[item.Path]=await Hash(Resolve(minecraftRoot,item.Path));
            var nextState=statePath+".new";
            await File.WriteAllTextAsync(nextState,JsonSerializer.Serialize(new InstalledState(manifest.Version,actual),Json),cancel);
            File.Move(nextState,statePath,true);
            progress("Pack ready. Your keys are preserved.");
        }catch{
            foreach(var item in changed.AsEnumerable().Reverse()) {
                var target=Resolve(minecraftRoot,item.Path);
                if(item.Existed) File.Copy(Resolve(backup,item.Path),target,true); else if(File.Exists(target)) File.Delete(target);
            }
            throw;
        }
        // Backups stay local to this installation; no personal data is uploaded.
    }
}
