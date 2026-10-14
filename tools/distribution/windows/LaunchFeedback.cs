using System.Text.RegularExpressions;

static class LaunchFeedback
{
    public static string Phase(string game,DateTime started) {
        var log=Path.Combine(game,"logs","latest.log");
        try {if(!File.Exists(log)||File.GetLastWriteTimeUtc(log)<started)return "Starting Minecraft…";
            using var file=new FileStream(log,FileMode.Open,FileAccess.Read,FileShare.ReadWrite|FileShare.Delete);
            file.Seek(Math.Max(0,file.Length-64*1024),SeekOrigin.Begin);using var input=new StreamReader(file);var text=input.ReadToEnd();
            if(text.Contains("OpenAL initialized",StringComparison.Ordinal)||text.Contains("Reloading ResourceManager",StringComparison.Ordinal))return "Preparing textures and sounds…";
            if(text.Contains("LWJGL",StringComparison.Ordinal)||text.Contains("Sodium",StringComparison.Ordinal))return "Preparing graphics…";
            if(Regex.IsMatch(text,@"Loading \d+ mods"))return "Loading your mods…";
        }catch(IOException){}return "Starting Minecraft…";
    }
}
