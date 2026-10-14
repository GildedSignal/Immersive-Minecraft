using System.IO.Compression;
using System.Text.Json;
using System.Text.Json.Nodes;
namespace FriendPack;
static class SettingsAndLogsTests
{
    public static async Task Run(string root) {
        int count=0;void Check(bool ok,string label){if(!ok)throw new Exception("FAIL: "+label);count++;}
        var source=Path.Combine(root,"generic-source");var target=Path.Combine(root,"generic-target");Directory.CreateDirectory(source);Directory.CreateDirectory(target);
        async Task Put(string folder,string path,string text){var file=PackEngine.Resolve(folder,path);Directory.CreateDirectory(Path.GetDirectoryName(file)!);await File.WriteAllTextAsync(file,text);}
        async Task Jar(string folder,string name,string id){var file=PackEngine.Resolve(folder,"mods/"+name);Directory.CreateDirectory(Path.GetDirectoryName(file)!);using var zip=ZipFile.Open(file,ZipArchiveMode.Create);var entry=zip.CreateEntry("fabric.mod.json");using var output=new StreamWriter(entry.Open());await output.WriteAsync("{\"id\":\""+id+"\",\"version\":\"different-version\"}");}
        await Jar(source,"mod-old-name-1.jar","arbitrary_mod");await Put(source,"options.txt","key_jump:key.keyboard.g\n");
        Check(ControlsMigration.ChooseSource(target,["mods/mod-new-name-9.jar"],[source],["arbitrary_mod"])==source,"match mod ID across filenames/versions");
        var defaults=JsonNode.Parse("{\"workerBudget\":4,\"showPanel\":true,\"theme\":\"pack\",\"newSetting\":true,\"nested\":{\"label\":\"default\",\"limit\":6}}")!.AsObject();
        var recipe=new ConfigRecipe("config/arbitrary.json5","json5",defaults,true,JsonNode.Parse("{\"workerBudget\":4,\"nested\":{\"limit\":6}}")!.AsObject());
        var text=ConfigRecipes.Merge(recipe,"{ workerBudget:99, showPanel:false, theme:'personal', unknown:42, nested:{label:'player',limit:90}, }");var merged=ControlsMigration.ParseObject(text);
        Check(merged["workerBudget"]!.GetValue<int>()==4&&merged["nested"]!["limit"]!.GetValue<int>()==6,"declared tuning wins only selected fields");
        Check(!merged["showPanel"]!.GetValue<bool>()&&merged["theme"]!.GetValue<string>()=="personal"&&merged["nested"]!["label"]!.GetValue<string>()=="player","all ordinary fields survive");
        Check(merged["unknown"]!.GetValue<int>()==42&&merged["newSetting"]!.GetValue<bool>(),"unknown preferences retained and missing defaults filled");
        Check(ConfigRecipes.Merge(recipe,text)==text,"repeated update is idempotent");
        var array=new ConfigRecipe("config/array.json","json",JsonNode.Parse("{\"items\":[1,2],\"nullable\":3}")!.AsObject(),true);
        var arrays=ControlsMigration.ParseObject(ConfigRecipes.Merge(array,"{\"items\":[8],\"nullable\":null}"));Check(arrays["items"]![0]!.GetValue<int>()==8&&arrays["nullable"]==null,"arrays/null are personal atomic values");
        var toml=new ConfigRecipe("config/arbitrary.toml","toml",JsonNode.Parse("{\"prefs\":{\"theme\":\"pack\",\"new\":true},\"budget\":4}")!.AsObject(),true,JsonNode.Parse("{\"budget\":4}")!.AsObject());
        var tomlText=ConfigRecipes.Merge(toml,"budget=99\n[prefs]\ntheme='personal'\n");var parsed=Tomlyn.TomlSerializer.Deserialize<Tomlyn.Model.TomlTable>(tomlText)!;
        Check(Convert.ToInt64(parsed["budget"])==4&&(string)((Tomlyn.Model.TomlTable)parsed["prefs"]!)["theme"]! =="personal","TOML ownership and new defaults");
        var upgradedToml=Tomlyn.TomlSerializer.Deserialize<Tomlyn.Model.TomlTable>(ConfigRecipes.ImportPreferences(toml,"budget=99\n[prefs]\ntheme='old'\n","budget=4\n[prefs]\ntheme='edited-here'\n"))!;
        Check(Convert.ToInt64(upgradedToml["budget"])==99&&(string)((Tomlyn.Model.TomlTable)upgradedToml["prefs"]!)["theme"]! =="edited-here","TOML legacy import retains changed target preferences");
        var props=new ConfigRecipe("config/arbitrary.properties","properties",JsonNode.Parse("{\"theme\":\"pack\",\"budget\":\"4\",\"new\":\"yes\"}")!.AsObject(),true,JsonNode.Parse("{\"budget\":\"4\"}")!.AsObject());
        var propText=ConfigRecipes.Merge(props,"# personal\ntheme=personal\nbudget=99\n");Check(propText.Contains("theme=personal")&&propText.Contains("budget=4")&&propText.Contains("new=yes"),"properties ownership");
        var upgradedProps=ConfigRecipes.ImportPreferences(props,"theme=old\nbudget=99\n","theme=edited-here\nbudget=4\n");Check(upgradedProps.Contains("theme=edited-here")&&upgradedProps.Contains("budget=99"),"properties legacy import retains changed target preferences");
        await Put(source,recipe.Path,"{ workerBudget:99,showPanel:false,theme:'from-old',nested:{label:'old-label',limit:90},unknown:42 }");
        await Put(target,recipe.Path,"{\"workerBudget\":4,\"showPanel\":true,\"theme\":\"edited-here\",\"nested\":{\"label\":\"default\",\"limit\":6}}");
        await Put(source,"config/never-seen-mod/custom.ini","arbitrary-setting=player\n");
        await Put(source,"arbitrary_mod/config/ui.cfg","minimap=false");await Put(source,"arbitrary_mod/data/region.bin","map-data");await Put(source,"arbitrary_mod/cache/ignore.bin","cache");
        await Put(source,"another-root/config/preferences.xml","<personal/>");await Put(source,"accounts.json","SECRET");await Put(source,"saves/world/level.dat","WORLD");await Put(source,"unrelated/secrets.txt","SECRET");
        var result=await ModSettings.Import(source,target,[recipe]);Check(result.Files==5,"generic settings, metadata-owned and conventional root discovery");
        var imported=ControlsMigration.ParseObject(await File.ReadAllTextAsync(PackEngine.Resolve(target,recipe.Path)));
        Check(!imported["showPanel"]!.GetValue<bool>()&&imported["theme"]!.GetValue<string>()=="edited-here","legacy recipe defaults restored without overwriting changed target preferences");
        Check(imported["workerBudget"]!.GetValue<int>()==4,"import cannot bypass enforced tuning");
        Check(File.Exists(PackEngine.Resolve(target,"config/never-seen-mod/custom.ini"))&&File.Exists(PackEngine.Resolve(target,"another-root/config/preferences.xml")),"unlisted mods and arbitrary config formats copied");
        Check(File.ReadAllText(PackEngine.Resolve(target,"arbitrary_mod/data/region.bin"))=="map-data"&&!Directory.Exists(Path.Combine(target,"arbitrary_mod/cache")),"owned data copied but cache skipped");
        Check(!File.Exists(Path.Combine(target,"accounts.json"))&&!Directory.Exists(Path.Combine(target,"saves"))&&!Directory.Exists(Path.Combine(target,"unrelated")),"credentials/worlds/unowned folders excluded");
        await Put(target,"config/never-seen-mod/custom.ini","changed-after-import");await ModSettings.Import(source,target,[recipe]);Check(File.ReadAllText(PackEngine.Resolve(target,"config/never-seen-mod/custom.ini"))=="changed-after-import","receipt preserves later preferences");
        Check(Directory.Exists(Path.Combine(target,".friendpack-settings-backups")),"overwritten target has local backup");
        var failed=Path.Combine(root,"generic-failed");await Put(source,recipe.Path,"{invalid}");bool rejected=false;try{await ModSettings.Import(source,failed,[recipe]);}catch{rejected=true;}
        Check(rejected&&!File.Exists(Path.Combine(failed,".friendpack-settings-import.json"))&&!File.Exists(Path.Combine(failed,"config/never-seen-mod/custom.ini")),"invalid reviewed input commits nothing");
        var support=Path.Combine(root,"support");await Put(support,"logs/latest.log","[CHAT] private words\naccessToken=SECRET\nConnecting to private.example.net, 25565\nSetting user: MyName\nC:\\Users\\PERSON\\game\njava.lang.IllegalStateException: useful error\n");await Put(support,"crash-reports/crash.txt","refresh_token=SECRET\njava.lang.NullPointerException: useful stack\n");await Put(support,"accounts.json","SECRET");await Put(support,"config/secret.json","SECRET");
        var archive=Path.Combine(root,"Support.zip");var logs=await SupportLogs.Export(new Dictionary<string,string>{{"Pack",support}},Path.Combine(root,"empty-launcher"),archive);Check(logs==2,"selects actual game and crash logs");
        using var contents=ZipFile.OpenRead(archive);var latest=contents.GetEntry("Pack/latest.log")!;using var reader=new StreamReader(latest.Open());var sanitized=reader.ReadToEnd();
        Check(!sanitized.Contains("SECRET")&&!sanitized.Contains("private.example")&&!sanitized.Contains("private words")&&!sanitized.Contains("PERSON")&&!sanitized.Contains("MyName"),"support removes common private data");
        Check(sanitized.Contains("IllegalStateException")&&contents.GetEntry("SUPPORT-INFO.json")!=null,"preserves useful errors and support metadata");
        Check(!contents.Entries.Any(e=>e.Name=="accounts.json"||e.FullName.Contains("config/")),"no private accounts or configs exported");
        Console.WriteLine($"PASS {count} general settings ownership/migration and support-log checks.");
    }
}
