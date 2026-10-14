using System.Text.Json.Nodes;
namespace FriendPack;
public static class SelfTest
{
    static int checks;
    static void Check(bool ok,string message){if(!ok)throw new Exception("FAIL: "+message);checks++;}
    static async Task Throws(Func<Task> f,string label){try{await f();}catch{checks++;return;}throw new Exception("FAIL: accepted "+label);}
    public static async Task Run(){
        var root=Path.Combine(Path.GetTempPath(),"friendpack-test-"+Guid.NewGuid().ToString("N"));
        var package=Path.Combine(root,"package");var game=Path.Combine(root,"game");Directory.CreateDirectory(package);Directory.CreateDirectory(game);
        Check(PackEngine.Resolve(root+Path.DirectorySeparatorChar,"mods/a.jar")==Path.GetFullPath(Path.Combine(root,"mods/a.jar")),"root with trailing separator");
        await ArchivePatchTests.Run(root);
        await ControlsMigrationTests.Run(root);
        await SavedServersTests.Run(root);
        await PersonalFilesTests.Run(root);
        await SettingsAndLogsTests.Run(root);
        async Task<PackFile> Asset(string path,string text){var payload="payload/"+path;var file=PackEngine.Resolve(package,payload);Directory.CreateDirectory(Path.GetDirectoryName(file)!);await File.WriteAllTextAsync(file,text);return new(path,await PackEngine.Hash(file),new FileInfo(file).Length,null,payload);}
        var mod=await Asset("mods/a.jar","first mod");var config=await Asset("config/hud.json","{\"keybinds\":{\"open\":\"key.keyboard.h\"},\"animation\":true,\"nested\":{\"toggleKey\":72,\"speed\":2}}");
        var pack=new PackManifest(1,"immersive-minecraft","Immersive Minecraft","1","26.2","0.19.5",true,[mod,config],new(){{"version","4903"},{"resourcePacks","[\"vanilla\",\"file/a.zip\"]"},{"maxFps","180"},{"key_jump","key.keyboard.space"},{"soundDevice","author headset"}});
        Directory.CreateDirectory(Path.Combine(game,"config"));
        await File.WriteAllTextAsync(Path.Combine(game,"options.txt"),"key_jump:key.keyboard.r\nlang:fr_fr\nmaxFps:60\nsoundDevice:friend speakers\n");
        await File.WriteAllTextAsync(Path.Combine(game,"config/hud.json"),"{\"keybinds\":{\"open\":\"key.keyboard.g\"},\"animation\":false,\"nested\":{\"toggleKey\":90,\"speed\":1},\"localPreference\":true}");
        await PackEngine.Apply(pack,package,game,_=>{});
        var opts=PackEngine.ReadOptions(await File.ReadAllTextAsync(Path.Combine(game,"options.txt")));
        Check(opts["version"]=="4903","options format version accompanies modern migrated keys");
        Check(opts["key_jump"]=="key.keyboard.r","vanilla key preserved");Check(opts["lang"]=="fr_fr","language preserved");Check(opts["soundDevice"]=="friend speakers","sound device preserved");Check(opts["maxFps"]=="180","graphics updated");Check(opts["resourcePacks"]==pack.Options["resourcePacks"],"pack order applied");
        var cfg=JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(game,"config/hud.json")))!;
        Check(cfg["keybinds"]!["open"]!.GetValue<string>()=="key.keyboard.g","mod key map preserved");Check(cfg["nested"]!["toggleKey"]!.GetValue<int>()==90,"nested key preserved");Check(cfg["animation"]!.GetValue<bool>(),"mod setting applied");Check(cfg["nested"]!["speed"]!.GetValue<int>()==2,"nested setting applied");Check(cfg["localPreference"]!.GetValue<bool>(),"local unknown setting preserved");
        var stateBefore=await File.ReadAllTextAsync(Path.Combine(game,".friendpack-state.json"));
        await Throws(()=>PackEngine.Apply(pack,package,game,_=>{},gameIsRunning:()=>true),"actual running-game callback");
        var gateCalls=0;
        await Throws(()=>PackEngine.Apply(pack,package,game,_=>{},gameIsRunning:()=>++gateCalls>=2),"game starting during preparation");
        Check(gateCalls==2,"game callback rechecked at commit boundary");
        Check(await File.ReadAllTextAsync(Path.Combine(game,"mods/a.jar"))=="first mod","game starting during preparation leaves managed mod unchanged");
        Check(await File.ReadAllTextAsync(Path.Combine(game,".friendpack-state.json"))==stateBefore,"late game detection leaves installed state unchanged");
        await Throws(()=>PackEngine.Apply(pack with {ReleaseReady=false},package,game,_=>{}),"unfinished release");
        foreach(var path in new[]{"../outside","mods/../../outside","C:/Windows/x","/absolute","mods\\x","mods/./x"})
            await Throws(()=>Task.FromResult(PackEngine.Resolve(game,path)),"path traversal "+path);
        Check(await File.ReadAllTextAsync(Path.Combine(game,".friendpack-state.json"))==stateBefore,"rejected release leaves state");
        var bad=mod with {Sha256=new string('0',64)};
        await Throws(()=>PackEngine.Apply(pack with {Files=[bad,config]},package,game,_=>{}),"hash mismatch");Check(await File.ReadAllTextAsync(Path.Combine(game,"mods/a.jar"))=="first mod","hash failure leaves installed mod");
        await File.WriteAllTextAsync(Path.Combine(game,".friendpack-playing"),"active");
        await Throws(()=>PackEngine.Apply(pack,package,game,_=>{}),"running game");File.Delete(Path.Combine(game,".friendpack-playing"));
        await File.WriteAllTextAsync(Path.Combine(game,"mods/user.jar"),"personal mod");
        var next=await Asset("mods/b.jar","second mod");await PackEngine.Apply(pack with {Version="2",Files=[next,config]},package,game,_=>{});
        Check(!File.Exists(Path.Combine(game,"mods/a.jar")),"unchanged old managed mod removed");Check(File.Exists(Path.Combine(game,"mods/user.jar")),"user mod kept");
        await File.WriteAllTextAsync(Path.Combine(game,"mods/b.jar"),"friend modified");
        await PackEngine.Apply(pack with {Version="3",Files=[mod,config]},package,game,_=>{});Check(await File.ReadAllTextAsync(Path.Combine(game,"mods/b.jar"))=="friend modified","modified obsolete file kept");
        var before=await File.ReadAllTextAsync(Path.Combine(game,".friendpack-state.json"));var modV2=await Asset("mods/a.jar","changed mod");var configV2=await Asset("config/hud.json","{\"animation\":false}");using var cancel=new CancellationTokenSource();var installed=0;
        await Throws(()=>PackEngine.Apply(pack with {Version="4",Files=[modV2,configV2]},package,game,s=>{if(s.StartsWith("Installing")&&++installed==2)cancel.Cancel();},cancel.Token),"interrupted commit");
        Check(await File.ReadAllTextAsync(Path.Combine(game,"mods/a.jar"))=="first mod","interrupted update restores prior mod");Check(await File.ReadAllTextAsync(Path.Combine(game,".friendpack-state.json"))==before,"interrupted update retains prior state");
        var json5=new ConfigRecipe("config/test.json5","json5",JsonNode.Parse("{\"speed\":3}")!.AsObject());
        var json5Output=JsonNode.Parse(ConfigRecipes.Merge(json5,"{ // comment\n speed:1, keybinds:{open:'G'}, unknown:true,}"))!;
        Check(json5Output["speed"]!.GetValue<int>()==3&&json5Output["keybinds"]!["open"]!.GetValue<string>()=="G","JSON5 syntax and omitted key map");
        var toml=new ConfigRecipe("config/test.toml","toml",JsonNode.Parse("{\"graphics\":{\"speed\":3,\"openKey\":65},\"enabled\":true}")!.AsObject());
        var tomlOutput=ConfigRecipes.Merge(toml,"enabled = false\n[graphics]\nspeed = 1\nopenKey = 90\ncustom = 'friend'\n");
        var parsed=Tomlyn.TomlSerializer.Deserialize<Tomlyn.Model.TomlTable>(tomlOutput)!;var graphics=(Tomlyn.Model.TomlTable)parsed["graphics"]!;
        Check(Convert.ToInt64(graphics["speed"])==3&&Convert.ToInt64(graphics["openKey"])==90&&(string)graphics["custom"]! =="friend","TOML nested binding and unknown preference");
        var properties=new ConfigRecipe("config/test.properties","properties",JsonNode.Parse("{\"quality\":\"high\",\"keybind\":\"author\"}")!.AsObject());
        var propertyOutput=ConfigRecipes.Merge(properties,"# friend settings\nquality=low\nkeybind=friend\nunknown=yes\n");
        Check(propertyOutput.Contains("quality=high")&&propertyOutput.Contains("keybind=friend")&&propertyOutput.Contains("unknown=yes"),"properties binding/comment preservation");
        Console.WriteLine($"PASS {checks} installer checks. Test files: {root}");
    }
}
