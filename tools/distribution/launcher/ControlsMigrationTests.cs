using System.Text.Json.Nodes;
namespace FriendPack;
static class ControlsMigrationTests
{
    public static async Task Run(string root)
    {
        int checks=0;void Check(bool value,string note){if(!value)throw new Exception("FAIL controls: "+note);checks++;}
        var source=Path.Combine(root,"original-modrinth-profile");var target=Path.Combine(root,"fresh-friend-game");
        Directory.CreateDirectory(Path.Combine(source,"config/fzzy_config"));Directory.CreateDirectory(Path.Combine(target,"config"));
        await File.WriteAllTextAsync(Path.Combine(source,"options.txt"),"key_key.forward:key.keyboard.z\nkey_key.inventory:key.keyboard.i\nlang:fr_fr\nmaxFps:90\nresourcePacks:author packs\n");
        await File.WriteAllTextAsync(Path.Combine(target,"options.txt"),"key_key.forward:key.keyboard.w\nmaxFps:180\nresourcePacks:friend packs\n");
        await File.WriteAllTextAsync(Path.Combine(source,"config/litematica.json"),"{\"Hotkeys\":{\"openGui\":{\"keys\":\"M\",\"settings\":{\"allowExtraKeys\":false}}},\"Generic\":{\"quality\":99,\"toolItem\":\"secret personal tool\"}}");
        await File.WriteAllTextAsync(Path.Combine(target,"config/litematica.json"),"{\"Hotkeys\":{\"openGui\":{\"keys\":\"L\"}},\"Generic\":{\"quality\":2}}");
        await File.WriteAllTextAsync(Path.Combine(source,"config/malilib.json"),"{\"Generic\":{\"openConfigGui\":{\"hotkey\":{\"keys\":\"M,C\"},\"enabled\":true},\"keybindDebugging\":true}}");
        await File.WriteAllTextAsync(Path.Combine(source,"config/fzzy_config/keybinds.toml"),"[action]\nkey = 74\n");
        var imported=await ControlsMigration.Import(source,target,[]);
        var options=PackEngine.ReadOptions(await File.ReadAllTextAsync(Path.Combine(target,"options.txt")));
        Check(options["key_key.forward"]=="key.keyboard.z"&&options["key_key.inventory"]=="key.keyboard.i","AZERTY and modded-option controls imported");
        Check(options["maxFps"]=="180"&&options["resourcePacks"]=="friend packs","author graphics/order not imported");
        Check(options["lang"]=="fr_fr","personal language imported");
        var config=JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(target,"config/litematica.json")))!;
        Check(config["Hotkeys"]!["openGui"]!["keys"]!.GetValue<string>()=="M","Litematica control map imported");
        Check(config["Generic"]!["quality"]!.GetValue<int>()==2&&config["Generic"]!["toolItem"]==null,"non-control original config excluded");
        var library=JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(target,"config/malilib.json")))!;
        Check(library["Generic"]!["openConfigGui"]!["hotkey"]!["keys"]!.GetValue<string>()=="M,C"&&library["Generic"]!["keybindDebugging"]==null,"nested hotkeys imported without debug preferences");
        Check(File.Exists(Path.Combine(target,"config/fzzy_config/keybinds.toml")),"dedicated TOML controls imported");
        Check(!ControlsMigration.NeedsImport(target),"receipt prevents repeatedly replacing subsequent choices");
        var preserved=ConfigRecipes.Merge(new("config/litematica.json","json",JsonNode.Parse("{\"Hotkeys\":{},\"Generic\":{\"quality\":4}}")!.AsObject()),config.ToJsonString());
        Check(JsonNode.Parse(preserved)!["Hotkeys"]!["openGui"]!["keys"]!.GetValue<string>()=="M","managed updates keep imported hotkeys");
        Check(Directory.GetFiles(Path.Combine(target,".friendpack-control-backups"),"options.txt",SearchOption.AllDirectories).Length==1,"previous options backed up");
        Check(ControlsMigration.Extract(JsonNode.Parse("{\"stateKey\":\"model_state\",\"openKey\":74}")!.AsObject()).Count==1,"model state is not a keyboard binding");
        var unrelated=Path.Combine(root,"other-profile");Directory.CreateDirectory(unrelated);await File.WriteAllTextAsync(Path.Combine(unrelated,"options.txt"),"key_key.forward:key.keyboard.w");
        Directory.CreateDirectory(Path.Combine(source,"mods"));await File.WriteAllTextAsync(Path.Combine(source,"mods/a.jar"),"");await File.WriteAllTextAsync(Path.Combine(source,"mods/b.jar"),"");
        Check(ControlsMigration.ChooseSource(target,["mods/a.jar","mods/b.jar"],[target,unrelated,source])==source,"selects matching local pack rather than unrelated defaults or destination");
        var snapshot=await File.ReadAllTextAsync(Path.Combine(target,"options.txt"));
        await File.WriteAllTextAsync(Path.Combine(source,"config/litematica.json"),"invalid json");
        bool rejected=false;try{await ControlsMigration.Import(source,target,[]);}catch{rejected=true;}
        Check(rejected&&snapshot==await File.ReadAllTextAsync(Path.Combine(target,"options.txt")),"invalid source rejected before partial writes");
        Console.WriteLine($"PASS {checks} first-install control migration checks.");
    }
}
