using FriendPack;
using System.Text.Json;

if(args.Length==1 && args[0]=="--self-test") { await SelfTest.Run(); return; }
if(args.Length==4 && args[0]=="--patch-archive") {ArchivePatch.Apply(args[1],args[2],args[3]); return;}
if(args.Length==2 && args[0]=="--test-configs") {
    var recipes=JsonSerializer.Deserialize<ConfigRecipeList>(await File.ReadAllTextAsync(args[1]),PackEngine.Json)!;
    foreach(var recipe in recipes.Recipes){
        var first=ConfigRecipes.Merge(recipe,""); var second=ConfigRecipes.Merge(recipe,first);
        if(first!=second)throw new InvalidDataException("Non-idempotent config recipe: "+recipe.Path);
    }
    Console.WriteLine($"PASS {recipes.Recipes.Length} configuration recipe parse/write/round-trip checks.");return;
}
if(args.Length!=3 || args[0]!="--prepare") {
    Console.WriteLine("Immersive Minecraft — pack preparation tool");
    Console.WriteLine("--prepare <manifest.json> <dedicated Prism instance folder>");
    Console.WriteLine("--self-test");
    return;
}
try {
    var manifestPath=Path.GetFullPath(args[1]);
    var manifest=JsonSerializer.Deserialize<PackManifest>(await File.ReadAllTextAsync(manifestPath),PackEngine.Json)
        ??throw new InvalidDataException("Manifest is empty.");
    var folder=Path.GetFullPath(args[2]);
    if(Directory.Exists(folder)&&!File.Exists(Path.Combine(folder,".friendpack-owned"))&&Directory.EnumerateFileSystemEntries(folder).Any())
        throw new IOException("Choose an empty folder for the dedicated friend instance.");
    // Readiness is checked before creating an instance or downloading any pack contents.
    if(!manifest.ReleaseReady) throw new InvalidDataException("This build is still being prepared. Missing assets are listed in the inventory.");
    Directory.CreateDirectory(folder); await File.WriteAllTextAsync(Path.Combine(folder,".friendpack-owned"),manifest.Id);
    var package=Path.GetDirectoryName(manifestPath)!;
    var recipePath=Path.Combine(package,"config-patches.json");
    ConfigRecipe[]? recipes=null;
    if(File.Exists(recipePath)) {
        var parsed=JsonSerializer.Deserialize<ConfigRecipeList>(await File.ReadAllTextAsync(recipePath),PackEngine.Json)!;
        if(parsed.Schema!=1)throw new InvalidDataException("Unsupported settings format.");recipes=parsed.Recipes;
    }
    await PackEngine.Apply(manifest,package,Path.Combine(folder,".minecraft"),Console.WriteLine,configurations:recipes);
    await File.WriteAllTextAsync(Path.Combine(folder,"mmc-pack.json"),JsonSerializer.Serialize(new {
        formatVersion=1,components=new[]{
            new { uid="net.minecraft",version=manifest.Minecraft },
            new { uid="net.fabricmc.fabric-loader",version=manifest.Fabric }
        }
    },PackEngine.Json));
    var maxMemory=Math.Clamp(GC.GetGCMemoryInfo().TotalAvailableMemoryBytes/1024/1024/3,2048,8192);
    await File.WriteAllTextAsync(Path.Combine(folder,"instance.cfg"),
        $"[General]\nInstanceType=OneSix\nname={manifest.Name.Replace('\n',' ')}\n"+
        $"OverrideMemory=true\nMinMemAlloc=512\nMaxMemAlloc={maxMemory}\n"+
        "OverrideJavaLocation=false\nOverrideJavaArgs=false\n");
    Console.WriteLine("Dedicated instance ready. Personal controls are preserved.");
}catch(Exception e) { Console.Error.WriteLine(e.Message); Environment.ExitCode=1; }
