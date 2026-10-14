using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using Newtonsoft.Json.Linq;
using Tomlyn;
using Tomlyn.Model;
namespace FriendPack;
public record ConfigRecipe(string Path,string Format,JsonObject Values,bool DefaultsOnly=false,JsonObject? Enforced=null);
public record ConfigRecipeList(int Schema,ConfigRecipe[] Recipes);

public static class ConfigRecipes
{
    public static string Merge(ConfigRecipe recipe,string existing)
    {
        if(recipe.Values==null)throw new InvalidDataException("Settings values are missing: "+recipe.Path);
        if(recipe.DefaultsOnly) {
            // Ownership is declared by the pack, never guessed from setting names.
            var filled=Defaults(recipe,existing);
            return recipe.Enforced is {Count:>0} ? Merge(recipe with {Values=recipe.Enforced,DefaultsOnly=false,Enforced=null},filled) : filled;
        }
        if(recipe.Format is "json" or "json5") {
            var old=string.IsNullOrWhiteSpace(existing)?new JsonObject():JsonNode.Parse(JToken.Parse(existing).ToString(Newtonsoft.Json.Formatting.None));
            return PackEngine.MergeConfig(recipe.Values,old)?.ToJsonString(PackEngine.Json)??"{}";
        }
        if(recipe.Format=="toml") {
            var old=string.IsNullOrWhiteSpace(existing)?new TomlTable():TomlSerializer.Deserialize<TomlTable>(existing)!;
            PatchToml(old,recipe.Values);
            return TomlSerializer.Serialize(old);
        }
        if(recipe.Format=="properties") {
            var values=recipe.Values.ToDictionary(kv=>kv.Key,kv=>kv.Value is JsonValue v&&v.TryGetValue<string>(out var str)?str:kv.Value?.ToJsonString()??"",StringComparer.Ordinal);
            var found=new HashSet<string>();var lines=existing.Replace("\r\n","\n").Split('\n').ToList();
            for(var i=0;i<lines.Count;i++) {
                var match=Regex.Match(lines[i],@"^\s*([^#!=:\s]+)\s*[:=]\s*(.*)$");
                if(match.Success&&values.TryGetValue(match.Groups[1].Value,out var desired)&&!PackEngine.Binding(match.Groups[1].Value)) {
                    found.Add(match.Groups[1].Value);lines[i]=match.Groups[1].Value+"="+Escape(desired);
                }
            }
            foreach(var kv in values) if(!found.Contains(kv.Key)&&!PackEngine.Binding(kv.Key)) lines.Add(kv.Key+"="+Escape(kv.Value));
            return string.Join('\n',lines).TrimEnd('\n')+"\n";
        }
        throw new InvalidDataException("Unsupported config format: "+recipe.Format);
    }
    public static JsonNode? FillMissing(JsonNode? defaults,JsonNode? current) {
        if(defaults is JsonObject wanted&&current is JsonObject present){var result=(JsonObject)present.DeepClone();foreach(var item in wanted){
            if(!present.ContainsKey(item.Key))result[item.Key]=item.Value?.DeepClone();
            else if(item.Value is JsonObject&&present[item.Key] is JsonObject)result[item.Key]=FillMissing(item.Value,present[item.Key]);
        }return result;}return current?.DeepClone();
    }
    static string Defaults(ConfigRecipe recipe,string existing) {
        if(string.IsNullOrWhiteSpace(existing))return Merge(recipe with {DefaultsOnly=false,Enforced=null},existing);
        if(recipe.Format is "json" or "json5") {
            var current=JsonNode.Parse(JToken.Parse(existing).ToString(Newtonsoft.Json.Formatting.None));
            if(current is not JsonObject)throw new InvalidDataException("Settings must be an object: "+recipe.Path);
            return FillMissing(recipe.Values,current)!.ToJsonString(PackEngine.Json);
        }
        if(recipe.Format=="toml") {var current=TomlSerializer.Deserialize<TomlTable>(existing)!;MissingToml(current,recipe.Values);return TomlSerializer.Serialize(current);}
        if(recipe.Format=="properties") {
            var keys=existing.Replace("\r\n","\n").Split('\n').Select(line=>Regex.Match(line,@"^\s*([^#!=:\s]+)\s*[:=]"))
                .Where(m=>m.Success).Select(m=>m.Groups[1].Value).ToHashSet(StringComparer.Ordinal);
            var missing=new JsonObject();foreach(var item in recipe.Values)if(!keys.Contains(item.Key))missing[item.Key]=item.Value?.DeepClone();
            return Merge(recipe with {Values=missing,DefaultsOnly=false,Enforced=null},existing);
        }
        throw new InvalidDataException("Unsupported config format: "+recipe.Format);
    }
    static void MissingToml(TomlTable table,JsonObject defaults) {foreach(var item in defaults){
        if(!table.TryGetValue(item.Key,out var current))table[item.Key]=TomlValue(item.Value);
        else if(current is TomlTable child&&item.Value is JsonObject obj&&!obj.ContainsKey("$friendpackFloat"))MissingToml(child,obj);
    }}
    public static string ImportPreferences(ConfigRecipe recipe,string incoming,string current) {
        if(recipe.Format is "json" or "json5")return UpgradeJson(ControlsMigration.ParseObject(incoming),ControlsMigration.ParseObject(current),recipe.Values).ToJsonString(PackEngine.Json);
        if(recipe.Format=="toml"){
            var source=TomlSerializer.Deserialize<TomlTable>(incoming)!;var target=TomlSerializer.Deserialize<TomlTable>(current)!;
            var baseline=new TomlTable();PatchToml(baseline,recipe.Values);UpgradeToml(source,target,baseline);return TomlSerializer.Serialize(target);
        }
        if(recipe.Format=="properties"){
            Dictionary<string,string> Read(string text)=>text.Replace("\r\n","\n").Split('\n').Select(l=>Regex.Match(l,@"^\s*([^#!=:\s]+)\s*[:=]\s*(.*)$")).Where(m=>m.Success).GroupBy(m=>m.Groups[1].Value).ToDictionary(g=>g.Key,g=>g.Last().Groups[2].Value);
            var source=Read(incoming);var target=Read(current);var result=new JsonObject();
            foreach(var item in source)if(!target.ContainsKey(item.Key)||recipe.Values[item.Key] is JsonValue v&&v.TryGetValue<string>(out var value)&&target[item.Key]==Escape(value))result[item.Key]=item.Value;
            // Source values are already property-escaped; retain raw spelling.
            var lines=current.Replace("\r\n","\n").Split('\n').ToList();var seen=new HashSet<string>();
            for(int i=0;i<lines.Count;i++){var match=Regex.Match(lines[i],@"^\s*([^#!=:\s]+)\s*[:=]");if(match.Success&&result.ContainsKey(match.Groups[1].Value)){var key=match.Groups[1].Value;lines[i]=key+"="+result[key]!.GetValue<string>();seen.Add(key);}}
            foreach(var item in result)if(!seen.Contains(item.Key))lines.Add(item.Key+"="+item.Value!.GetValue<string>());return string.Join('\n',lines).TrimEnd('\n')+"\n";
        }
        throw new InvalidDataException("Unsupported config format: "+recipe.Format);
    }
    static JsonObject UpgradeJson(JsonObject source,JsonObject current,JsonObject defaults){
        var result=(JsonObject)current.DeepClone();foreach(var item in source){
            if(!current.ContainsKey(item.Key)||defaults.ContainsKey(item.Key)&&JsonNode.DeepEquals(current[item.Key],defaults[item.Key]))result[item.Key]=item.Value?.DeepClone();
            else if(item.Value is JsonObject child&&current[item.Key] is JsonObject present)result[item.Key]=UpgradeJson(child,present,defaults[item.Key] as JsonObject??new());
        }return result;
    }
    static void UpgradeToml(TomlTable source,TomlTable current,TomlTable defaults){foreach(var item in source){
        if(!current.TryGetValue(item.Key,out var present)||defaults.TryGetValue(item.Key,out var baseline)&&TomlEqual(present,baseline))current[item.Key]=item.Value;
        else if(item.Value is TomlTable child&&present is TomlTable target)UpgradeToml(child,target,defaults.TryGetValue(item.Key,out var table)&&table is TomlTable prior?prior:new());
    }}
    static bool TomlEqual(object? a,object? b){
        if(a is TomlTable x&&b is TomlTable y)return x.Count==y.Count&&x.All(item=>y.TryGetValue(item.Key,out var other)&&TomlEqual(item.Value,other));
        if(a is System.Collections.IEnumerable list&&b is System.Collections.IEnumerable otherList&&a is not string&&b is not string){var xList=list.Cast<object?>().ToArray();var yList=otherList.Cast<object?>().ToArray();return xList.Length==yList.Length&&xList.Zip(yList).All(pair=>TomlEqual(pair.First,pair.Second));}
        return Equals(a,b);
    }
    static string Escape(string text)=>text.Replace("\\","\\\\").Replace("\n","\\n").Replace("\r","\\r").Replace("\t","\\t");
    static void PatchToml(TomlTable table,JsonObject desired) {
        foreach(var item in desired) {
            if(PackEngine.Binding(item.Key)&&table.ContainsKey(item.Key)) continue;
            if(item.Value is JsonObject obj&&!obj.ContainsKey("$friendpackFloat")) {
                var child=table.TryGetValue(item.Key,out var prior)&&prior is TomlTable previous?previous:new TomlTable();
                PatchToml(child,obj);table[item.Key]=child;
            } else table[item.Key]=TomlValue(item.Value);
        }
    }
    static object TomlValue(JsonNode? value) {
        if(value==null)throw new InvalidDataException("Null is not a TOML value.");
        if(value is JsonObject special&&special.ContainsKey("$friendpackFloat"))return special["$friendpackFloat"]?.GetValue<string>() switch {
            "NaN"=>double.NaN,"Infinity"=>double.PositiveInfinity,"-Infinity"=>double.NegativeInfinity,
            _=>throw new InvalidDataException("Unknown special TOML float.")
        };
        if(value is JsonObject obj){var table=new TomlTable();PatchToml(table,obj);return table;}
        if(value is JsonArray arr){
            if(arr.Count>0&&arr.All(v=>v is JsonObject)){var tables=new TomlTableArray();foreach(var node in arr)tables.Add((TomlTable)TomlValue(node)!);return tables;}
            var array=new TomlArray();foreach(var node in arr)array.Add(TomlValue(node));return array;
        }
        if(value is JsonValue v){
            if(v.TryGetValue<string>(out var s))return s;
            if(v.TryGetValue<bool>(out var b))return b;
            if(v.TryGetValue<long>(out var n))return n;
            if(v.TryGetValue<double>(out var d)&&double.IsFinite(d))return d;
        }
        throw new InvalidDataException("Unsupported TOML value.");
    }
}
