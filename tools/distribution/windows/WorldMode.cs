using System.Text.Json;
using FriendPack;

static class WorldMode
{
    public const string Full="immersive-minecraft",Vanilla="immersive-minecraft-vanilla";
    static string Settings=>Path.Combine(PrismBridge.Root,"world-mode.json");
    public static bool VanillaProtected
    {
        get =>ReadPreference(Settings,File.Exists(Path.Combine(Game(Full),".friendpack-state.json")));
    }
    public static bool ReadPreference(string settings,bool legacyInstalled){
        try{if(File.Exists(settings)){using var data=JsonDocument.Parse(File.ReadAllText(settings));return data.RootElement.GetProperty("vanilla").GetBoolean();}}catch(Exception e) when(e is IOException or JsonException or InvalidOperationException or KeyNotFoundException){}
        // Keep existing full saves with their original mod set; fresh installs default protected.
        return !legacyInstalled;
    }
    public static string Id=>VanillaProtected?Vanilla:Full;
    public static string Game(string id)=>Path.Combine(PrismBridge.Data,"instances",id,".minecraft");
    public static string ActiveGame=>Game(Id);
    public static string Label=>VanillaProtected?"Vanilla enhancements":"Full terrain and content";
    public static PackManifest Load(string package,bool? vanilla=null)=>JsonSerializer.Deserialize<PackManifest>(File.ReadAllText(Path.Combine(package,(vanilla??VanillaProtected)?"manifest-vanilla.json":"manifest.json")),PackEngine.Json)??throw new InvalidDataException("Missing selected world profile.");
    public static void Toggle(){AssertClosed();Directory.CreateDirectory(PrismBridge.Root);var temporary=Settings+".new-"+Guid.NewGuid().ToString("N");try{File.WriteAllText(temporary,JsonSerializer.Serialize(new{vanilla=!VanillaProtected}));File.Move(temporary,Settings,true);}finally{if(File.Exists(temporary))File.Delete(temporary);}}
    public static void AssertClosed(){if(new[]{Full,Vanilla}.Any(id=>GameGate.IsPlaying(Game(id))))throw new IOException("Save and close Minecraft before changing profiles or managing worlds.");}
    public static Dictionary<string,string> ExistingGames()=>new Dictionary<string,string>{{"Full pack",Game(Full)},{"Vanilla enhancements",Game(Vanilla)}}.Where(p=>Directory.Exists(p.Value)).ToDictionary(p=>p.Key,p=>p.Value);
    public static int TargetWorldVersion=>4903;
    public static void CheckWorldSource(string source)
    {
        if(VanillaProtected&&Path.GetFullPath(source).StartsWith(Path.GetFullPath(Path.Combine(Game(Full),"saves"))+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))
            throw new IOException("Keep full-pack saves in Full terrain mode. Import the original vanilla copy into Vanilla enhancements instead.");
        if(!VanillaProtected)return;
        using var data=JsonDocument.Parse(File.ReadAllText(Path.Combine(ReleasePackage.Prepare(),"manifest-vanilla.json")));
        var excluded=data.RootElement.GetProperty("excludedContentMods").EnumerateArray().Select(v=>v.GetProperty("id").GetString()!).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var metadata=Path.Combine(source,"level.dat");if(!File.Exists(metadata))throw new IOException("Choose the folder containing level.dat.");
        if(new FileInfo(metadata).Length>32*1024*1024)throw new IOException("World metadata is too large.");
        var identifiers=SavedServers.WorldIdentifiers(File.ReadAllBytes(metadata));
        if(excluded.Any(id=>identifiers.Any(v=>v.StartsWith(id+":",StringComparison.OrdinalIgnoreCase)||v.Contains("/"+id,StringComparison.OrdinalIgnoreCase))))
            throw new IOException("This save references terrain/content disabled in Vanilla enhancements. Keep it in Full terrain mode, or use its original vanilla copy.");
        var parent=Directory.GetParent(source);var game=parent?.Name.Equals("saves",StringComparison.OrdinalIgnoreCase)==true?parent.Parent?.FullName:null;
        if(game!=null&&Directory.Exists(Path.Combine(game,"mods")))foreach(var jar in Directory.EnumerateFiles(Path.Combine(game,"mods"),"*.jar")){
            using var archive=System.IO.Compression.ZipFile.OpenRead(jar);var entry=archive.GetEntry("fabric.mod.json");if(entry==null)continue;
            using var text=new StreamReader(entry.Open());var mod=Newtonsoft.Json.Linq.JObject.Parse(text.ReadToEnd());
            if(excluded.Contains(mod.Value<string>("id")??""))throw new IOException("The source installation includes terrain/content mods. Use the original vanilla save for protected mode, or import this copy into Full terrain mode.");
        }
    }
}
