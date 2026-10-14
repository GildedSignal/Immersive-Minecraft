using System.IO.Compression;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
namespace FriendPack;

public static class SupportLogs
{
    static readonly RegexOptions Flags=RegexOptions.IgnoreCase|RegexOptions.CultureInvariant;
    public static string Redact(string text) {
        var lines=text.Replace("\r\n","\n").Split('\n');var safe=new List<string>();
        foreach(var line in lines){
            if(Regex.IsMatch(line,@"\[CHAT\]|(?:access|refresh|id)[_-]?token|authorization|bearer\s|api[_ -]?key|client[_ -]?secret|--(?:accessToken|username|uuid)|sessionid|minecraft arguments|java arguments|launch arguments|command line|world seed|seed:",Flags)){safe.Add("[Private log line removed]");continue;}
            var value=Regex.Replace(line,@"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b","[token removed]");
            value=Regex.Replace(value,@"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b","[player id]",Flags);
            value=Regex.Replace(value,@"\b(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?\b","[address]");
            value=Regex.Replace(value,@"(?<=Connecting to )\S+","[server]",Flags);
            value=Regex.Replace(value,@"(?<=C:\\Users\\)[^\\\r\n]+","[user]",Flags);
            value=Regex.Replace(value,@"(?<=/Users/)[^/\r\n]+|(?<=/home/)[^/\r\n]+","[user]",Flags);
            value=Regex.Replace(value,@"\b[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b","[email]");
            value=Regex.Replace(value,@"(?<=Setting user: )\S+","[player]",Flags);
            safe.Add(value);
        }return string.Join('\n',safe);
    }
    public static async Task<int> Export(IReadOnlyDictionary<string,string> games,string launcherRoot,string archive,CancellationToken cancel=default) {
        archive=Path.GetFullPath(archive);if(File.Exists(archive))throw new IOException("Choose a new log-export filename.");
        foreach(var root in games.Values.Append(launcherRoot))if(archive.StartsWith(Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Save support logs outside the installation.");
        var entries=new List<(string File,string Name)>();
        foreach(var profile in games){if(profile.Key.Contains('/')||profile.Key.Contains('\\')||profile.Key is "." or "..")throw new IOException("Invalid support profile label.");
            Add(Path.Combine(profile.Value,"logs","latest.log"),profile.Key+"/latest.log");
            var crashes=Path.Combine(profile.Value,"crash-reports");if(Directory.Exists(crashes)){
                RejectLink(crashes);foreach(var file in Directory.EnumerateFiles(crashes,"*.txt").OrderByDescending(File.GetLastWriteTimeUtc).Take(3))Add(file,profile.Key+"/crash-reports/"+Path.GetFileName(file));
            }
        }
        Add(Path.Combine(launcherRoot,"logs","launcher.log"),"launcher.log");
        // Engine logs can contain complete launch arguments. Redact line by line
        // before they enter the support ZIP. Accounts/configs are never selected.
        Add(Path.Combine(launcherRoot,"data","logs","PrismLauncher-0.log"),"engine.log");
        void Add(string file,string name){if(File.Exists(file)){RejectLink(file);entries.Add((file,name));}}
        var temporary=archive+".partial-"+Guid.NewGuid().ToString("N");
        try {
            Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
            await using(var output=new FileStream(temporary,FileMode.CreateNew,FileAccess.ReadWrite,FileShare.None)){
                using var zip=new ZipArchive(output,ZipArchiveMode.Create,true);
                foreach(var entry in entries){cancel.ThrowIfCancellationRequested();var original=ReadTail(entry.File,16*1024*1024);var clean=Redact(original);await Write(zip,entry.Name,clean,cancel);}
                await Write(zip,"SUPPORT-INFO.json",JsonSerializer.Serialize(new{Schema=1,CreatedUtc=DateTime.UtcNow,LogFiles=entries.Count,Profiles=games.Keys,Runtime=Environment.Version.ToString(),OS=System.Runtime.InteropServices.RuntimeInformation.OSDescription,Mods=games.ToDictionary(g=>g.Key,g=>Directory.Exists(Path.Combine(g.Value,"mods"))?Directory.EnumerateFiles(Path.Combine(g.Value,"mods"),"*.jar").Select(Path.GetFileName).Order().ToArray():[])},PackEngine.Json),cancel);
                await Write(zip,"READ ME.txt","Support logs only. Accounts, keys, worlds, server lists and personal config files are excluded. Common tokens, chat, email, player IDs and addresses are redacted; unusual mod messages may still contain personal details. Files over 16 MiB contain their latest section. Nothing was uploaded. Send this ZIP to the person helping you.",cancel);
            }File.Move(temporary,archive);return entries.Count;
        }finally{if(File.Exists(temporary))File.Delete(temporary);}
    }
    static void RejectLink(string file){for(string? path=file;path!=null&&Directory.Exists(path)||path!=null&&File.Exists(path);path=Path.GetDirectoryName(path))if((File.GetAttributes(path!)&FileAttributes.ReparsePoint)!=0)throw new IOException("Linked support files are not exported.");}
    static string ReadTail(string file,int size){using var data=new FileStream(file,FileMode.Open,FileAccess.Read,FileShare.ReadWrite|FileShare.Delete);var start=Math.Max(0,data.Length-size);data.Seek(start,SeekOrigin.Begin);using var input=new StreamReader(data,Encoding.UTF8,true);if(start>0)input.ReadLine();return input.ReadToEnd();}
    static async Task Write(ZipArchive zip,string name,string text,CancellationToken cancel){var entry=zip.CreateEntry(name,CompressionLevel.Fastest);await using var stream=entry.Open();await using var writer=new StreamWriter(stream,Encoding.UTF8);await writer.WriteAsync(text.AsMemory(),cancel);}
}
