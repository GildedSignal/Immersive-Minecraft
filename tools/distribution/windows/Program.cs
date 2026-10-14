using System.Diagnostics;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;
using FriendPack;

internal static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        bool autoPlay=false;
        ApplicationConfiguration.Initialize();
        if(args.Length==2&&args[0]=="--menu-reopen-test"){
            using var host=new Form{ShowInTaskbar=false,Opacity=0};host.Shown+=async (_,_)=>{try{File.WriteAllText(args[1],await LauncherMenuTests.Run());}catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}host.Close();};Application.Run(host);return;
        }
        if(args.Length==2&&args[0]=="--menu-notify-fixture"){using var fixture=new MenuInstance(args[1]);if(fixture.OwnsMenu)Environment.ExitCode=1;else fixture.RequestOpen();return;}
        if(args.Length==2&&args[0]=="--sign-release"){try{var signed=Publisher.Announcement(Environment.ProcessPath!).GetAwaiter().GetResult();File.WriteAllText(args[1],JsonSerializer.Serialize(signed,PackEngine.Json));}catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}return;}
        if(args.Length==2&&args[0]=="--update-self-test"){try{File.WriteAllText(args[1],UpdateTests.Run().GetAwaiter().GetResult());}catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}return;}
        if(args.Length==2&&args[0]=="--publisher-init"){Publisher.Init(args[1]);return;}
        if(args.Length==2&&args[0]=="--publish-update"){
            try{Publisher.Publish(Environment.ProcessPath!,_=>{}).GetAwaiter().GetResult();File.WriteAllText(args[1],"PASS: signed launcher uploaded, verified and published. Existing player installations untouched.");}catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==1&&args[0]=="--publisher"){Application.Run(new PublisherForm());return;}
        if(args.Length is 2 or 3&&args[0]=="--after-update"&&int.TryParse(args[1],out var oldId)&&oldId!=Environment.ProcessId){try{using var old=Process.GetProcessById(oldId);if(!old.WaitForExit(30000))return;}catch(ArgumentException){}autoPlay=args.Length==3&&args[2]=="--play";args=[];}
        if(args.Length==2&&args[0]=="--engine-window-fixture"){EngineWindowTests.Fixture(args[1]);return;}
        if(args.Length==2&&args[0]=="--launch-detection-test"){try{File.WriteAllText(args[1],LaunchDetectionTests.Run());}catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}return;}
        if(args.Length==2&&args[0]=="--engine-window-test"){
            using var host=new Form{ShowInTaskbar=false,Opacity=0};host.Shown+=async (_,_)=>{try{File.WriteAllText(args[1],await EngineWindowTests.Run(args[1]));}catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}host.Close();};Application.Run(host);return;
        }
        if(args.Length==4&&args[0]=="--import-settings") {
            try {var game=Path.GetFullPath(args[2]);if(GameGate.IsPlaying(game))throw new IOException("Close Minecraft before importing settings.");
                var package=ReleasePackage.Prepare();var recipes=JsonSerializer.Deserialize<ConfigRecipeList>(File.ReadAllText(Path.Combine(package,"config-patches.json")),PackEngine.Json)!;
                var result=ModSettings.Import(args[1],game,recipes.Recipes,gameIsRunning:()=>GameGate.IsPlaying(game)).GetAwaiter().GetResult();
                File.WriteAllText(args[3],$"PASS: imported {result.Files} mod settings/data files, retained {result.Kept} existing data files; originals and local backups kept.");
            }catch(Exception e){File.WriteAllText(args[3],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==3&&args[0]=="--export-logs") {
            try {var count=SupportLogs.Export(WorldMode.ExistingGames(),PrismBridge.Root,args[1]).GetAwaiter().GetResult();File.WriteAllText(args[2],$"PASS: exported {count} redacted support logs. No upload.");}
            catch(Exception e){File.WriteAllText(args[2],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==2&&args[0]=="--world-mode-test"){
            try{var folder=Path.Combine(Path.GetTempPath(),"world-mode-test-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(folder);var settings=Path.Combine(folder,"world-mode.json");
                if(!WorldMode.ReadPreference(settings,false)||WorldMode.ReadPreference(settings,true))throw new Exception("Incorrect fresh/legacy default");
                File.WriteAllText(settings,"{\"vanilla\":true}");if(!WorldMode.ReadPreference(settings,true))throw new Exception("Protected choice lost");
                File.WriteAllText(settings,"{\"vanilla\":false}");if(WorldMode.ReadPreference(settings,false))throw new Exception("Full choice lost");
                File.WriteAllText(settings,"{");if(WorldMode.ReadPreference(settings,true))throw new Exception("Invalid preference exposed full saves");
                if(Path.GetFullPath(WorldMode.Game(WorldMode.Full))==Path.GetFullPath(WorldMode.Game(WorldMode.Vanilla)))throw new Exception("Save roots collide");
                File.WriteAllText(args[1],"PASS: fresh protected default, legacy full default, explicit choices, invalid-preference fallback and separate save roots (6 checks). No installation preference changed.");}
            catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==2&&args[0]=="--uninstall-worker") {
            try {UninstallPack.Worker(args[1]);}catch(Exception e){Directory.CreateDirectory(UninstallPack.ExportFolder);File.WriteAllText(Path.Combine(UninstallPack.ExportFolder,"Uninstall-result.txt"),"Removal did not finish: "+e.Message);Environment.ExitCode=1;}finally{try{UninstallPack.CleanupWorker(args[1]);}catch{}}return;
        }
        if(args.Length==4&&args[0]=="--import-servers") {
            try {var game=Path.GetFullPath(args[2]);if(GameGate.IsPlaying(game))throw new IOException("Close Minecraft before importing servers.");
                var result=SavedServers.Import([args[1]],game,false).GetAwaiter().GetResult();File.WriteAllText(args[3],$"PASS: added {result.Added} saved server entries; {result.Total} total. Original list preserved locally; no addresses exported.");}
            catch(Exception e){File.WriteAllText(args[3],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==4&&args[0]=="--import-controls") {
            try {
                var game=Path.GetFullPath(args[2]);if(GameGate.IsPlaying(game))throw new IOException("Close Minecraft before importing controls.");
                var package=ReleasePackage.Prepare();var recipes=JsonSerializer.Deserialize<ConfigRecipeList>(File.ReadAllText(Path.Combine(package,"config-patches.json")),PackEngine.Json)!;
                var result=ControlsMigration.Import(args[1],game,recipes.Recipes.Select(r=>r.Path)).GetAwaiter().GetResult();
                File.WriteAllText(args[3],$"PASS: imported {result.Options} personal options and {result.ConfigFiles} control configuration files; local backups retained.");
            }catch(Exception e){File.WriteAllText(args[3],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==2&&args[0]=="--configure-engine") {
            try {PrismBridge.CloseIdleEngine().GetAwaiter().GetResult();PrismBridge.Install(_=>{}).GetAwaiter().GetResult();File.WriteAllText(args[1],"PASS: automatic Java, memory and first-run defaults configured.");}
            catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==2&&args[0]=="--shortcut-test") {
            try {ReleasePackage.InstallShortcut();File.WriteAllText(args[1],"PASS: permanent local launcher and desktop shortcut created.");}
            catch(Exception e){File.WriteAllText(args[1],"FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==2&&args[0]=="--extract-package") {
            try {ReleasePackage.Prepare(args[1]);}catch(Exception e){File.WriteAllText(args[1]+"-error.txt",e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==2&&args[0] is "--prepare-only" or "--prepare-vanilla") {
            try {
                var package=ReleasePackage.Prepare();
                var manifest=WorldMode.Load(package,args[0]=="--prepare-vanilla");
                var recipes=JsonSerializer.Deserialize<ConfigRecipeList>(File.ReadAllText(Path.Combine(package,"config-patches.json")),PackEngine.Json)!;
                var instance=Path.GetFullPath(args[1]);var game=Path.Combine(instance,".minecraft");
                if(GameGate.IsPlaying(game))throw new IOException("Close the test game before preparing the instance.");
                PrismBridge.ImportLocalControls(game,manifest,recipes).GetAwaiter().GetResult();
                PackEngine.Apply(manifest,package,game,_=>{},configurations:recipes.Recipes,gameIsRunning:()=>GameGate.IsPlaying(game)).GetAwaiter().GetResult();
                PrismBridge.PrepareInstance(instance,manifest).GetAwaiter().GetResult();
                File.WriteAllText(instance+"-result.txt","PASS: embedded pack installation and settings complete.");
            }catch(Exception e){File.WriteAllText(args[1]+"-result.txt","FAIL: "+e.Message);Environment.ExitCode=1;}return;
        }
        if(args.Length==2&&args[0]=="--generate-fieldbook-sounds") {
            try {FieldbookSounds.Generate(Path.GetFullPath(args[1])).GetAwaiter().GetResult();}
            catch(Exception e) {File.WriteAllText(Path.Combine(Path.GetFullPath(args[1]),"generation-error.txt"),e is InvalidOperationException ? e.Message : e.GetType().Name);Environment.ExitCode=1;}
            return;
        }
        if(args.Length==1&&args[0]=="--elevenlabs") {Application.Run(new ElevenLabsForm());return;}
        if(args.Length==2&&args[0]=="--elevenlabs-status") {File.WriteAllText(Path.GetFullPath(args[1]),ElevenLabsConnection.SafeStatusJson());return;}
        if(args.Length==2&&args[0]=="--elevenlabs-self-test") {File.WriteAllText(Path.GetFullPath(args[1]),ElevenLabsSelfTest.Run().GetAwaiter().GetResult());return;}
        if(args.Length==2&&args[0]=="--elevenlabs-preview") {
            using var preview=new ElevenLabsForm(true);preview.ShowInTaskbar=false;preview.Opacity=0;
            preview.StartPosition=FormStartPosition.Manual;preview.Location=new Point(-10000,-10000);preview.Show();Application.DoEvents();
            using var bitmap=new Bitmap(preview.Width,preview.Height);preview.DrawToBitmap(bitmap,new Rectangle(Point.Empty,preview.Size));
            bitmap.Save(Path.GetFullPath(args[1]));preview.Hide();return;
        }
        if(args.Length==2&&args[0] is "--options-preview" or "--uninstall-preview") {
            using Form preview=args[0]=="--options-preview"?new AdventureOptions():new RemovalConfirmation();
            preview.ShowInTaskbar=false;preview.Opacity=0;preview.StartPosition=FormStartPosition.Manual;preview.Location=new Point(-10000,-10000);preview.Show();Application.DoEvents();
            using var bitmap=new Bitmap(preview.Width,preview.Height);preview.DrawToBitmap(bitmap,new Rectangle(Point.Empty,preview.Size));bitmap.Save(Path.GetFullPath(args[1]));preview.Hide();return;
        }
        if(args.Length==2&&args[0] is "--preview" or "--starting-preview") {
            using var preview=new LauncherForm(true);preview.ShowInTaskbar=false;preview.Opacity=0;
            if(args[0]=="--starting-preview")preview.PreviewStarting();
            preview.StartPosition=FormStartPosition.Manual;preview.Location=new Point(-10000,-10000);preview.Show();Application.DoEvents();
            using var bitmap=new Bitmap(preview.Width,preview.Height);preview.DrawToBitmap(bitmap,new Rectangle(Point.Empty,preview.Size));
            bitmap.Save(Path.GetFullPath(args[1]));preview.Hide();return;
        }
        if(args.Length==1&&args[0]=="--engine-test") {PrismBridge.Install(Console.WriteLine).GetAwaiter().GetResult();return;}
        if(args.Length==3&&args[0]=="--game-gate-test") {File.WriteAllText(Path.GetFullPath(args[2]),GameGate.IsPlaying(args[1])?"running":"closed");return;}
        using var menu=new MenuInstance();
        if(!menu.OwnsMenu){menu.RequestOpen();return;}
        using var frontend=new LauncherForm(false,autoPlay);menu.Watch(frontend.ReopenMenu);Application.Run(frontend);
    }
}

sealed class LauncherForm:Form
{
    readonly PixelLabel status=new();readonly MenuButton play=new();readonly ExperienceProgress progress=new();
    bool busy;readonly bool preview;CancellationTokenSource? preparation;bool closing;
    EngineSession? engineSession;readonly System.Windows.Forms.Timer launchMonitor=new(){Interval=1000};
    string? launchingGame;DateTime launchStarted;bool gameSeen,checkingLaunch;
    int? playingId;
    protected override bool ShowWithoutActivation=>preview;
    public LauncherForm(bool preview,bool autoPlay=false)
    {
        this.preview=preview;Text="Immersive Minecraft";ClientSize=new Size(800,500);
        FormBorderStyle=FormBorderStyle.FixedSingle;MaximizeBox=false;StartPosition=FormStartPosition.CenterScreen;
        DoubleBuffered=true;BackgroundImage=MenuScenery.Create(800,500);BackgroundImageLayout=ImageLayout.Stretch;
        BackColor=Color.FromArgb(21,27,27);ForeColor=Color.White;
        var title=new PixelLabel {Text="Immersive Minecraft",PixelScale=5,ForeColor=Color.FromArgb(227,227,216),Bounds=new Rectangle(40,80,720,52)};
        var subtitle=new PixelLabel {Text="Your world, ready to explore!",ForeColor=Color.FromArgb(255,239,128),Bounds=new Rectangle(40,151,720,27)};
        status.Text=preview?"Ready to play. Your keys stay yours.":"Preparing your adventure…";
        status.Bounds=new Rectangle(100,222,600,56);status.ForeColor=Color.White;
        progress.Bounds=new Rectangle(220,289,360,14);progress.Style=ProgressBarStyle.Marquee;
        play.Text="Play";play.Bounds=new Rectangle(220,326,360,52);play.Enabled=preview;
        var note=new PixelLabel {Text="First visit? Sign in to Minecraft once.",ForeColor=Color.FromArgb(205,207,198),Bounds=new Rectangle(75,428,650,26)};
        var footer=new PixelLabel {Text="World imports are copies. Your originals stay untouched.",ForeColor=Color.FromArgb(153,165,153),PixelScale=1,Bounds=new Rectangle(40,474,720,16)};
        var sounds=new MenuButton {Text="Options",Bounds=new Rectangle(220,384,360,38)};
        sounds.Click+=async (_,_)=>{if(busy)return;using var dialog=new AdventureOptions();dialog.ShowDialog(this);if(!preview&&!IsDisposed&&!dialog.Uninstalling)await PrepareAndPlay(false);};
        Controls.AddRange([title,subtitle,status,progress,play,sounds,note,footer]);
        AcceptButton=play;
        if(preview){progress.Style=ProgressBarStyle.Continuous;progress.Value=100;}
        play.Click+=async (_,_)=>await PrepareAndPlay(true);
        launchMonitor.Tick+=async (_,_)=>await CheckLaunch();
        Shown+=async (_,_)=>{if(!preview)await PrepareAndPlay(autoPlay);};
        FormClosing+=(_,e)=>{if(busy){e.Cancel=true;closing=true;preparation?.Cancel();status.Text="Finishing safely…";return;}if(engineSession!=null&&!engineSession.Exited){e.Cancel=true;DismissForGame();}};
        FormClosed+=(_,_)=>{launchMonitor.Dispose();engineSession?.Dispose();};
    }
    public void PreviewStarting(){status.Text="Preparing textures and sounds… 28s";play.Text="Starting";play.Enabled=false;progress.Style=ProgressBarStyle.Marquee;}
    // Keep only the private engine's window guard alive until it exits. Closing
    // our window never terminates Minecraft, and it never returns after playing.
    void DismissForGame(){ShowInTaskbar=false;Hide();}
    internal async void ReopenMenu(){if(IsDisposed)return;var wasHidden=!Visible;ShowInTaskbar=true;WindowState=FormWindowState.Normal;Show();if(!preview)Activate();if(wasHidden&&!busy&&engineSession==null&&!preview)await PrepareAndPlay(false);}
    internal void WatchFixture(EngineSession session)=>engineSession=session;
    internal Task PollFixture(string directory){launchingGame=directory;return CheckLaunch();}
    internal string FixtureStatus=>status.Text;
    internal void GameReady(int pid){gameSeen=true;playingId=pid;engineSession?.Quiet();SetStatus("Minecraft is running. Enjoy your adventure!");progress.Style=ProgressBarStyle.Continuous;progress.Value=100;play.Text="Playing";DismissForGame();}
    void SetStatus(string message){if(IsDisposed)return;if(InvokeRequired){BeginInvoke(()=>SetStatus(message));return;}if(status.Text==message)return;status.Text=message;
        if(!preview)try{var log=Path.Combine(PrismBridge.Root,"logs","launcher.log");Directory.CreateDirectory(Path.GetDirectoryName(log)!);if(File.Exists(log)&&new FileInfo(log).Length>1024*1024)File.Move(log,log+".old",true);File.AppendAllText(log,DateTime.UtcNow.ToString("O")+" "+SupportLogs.Redact(message)+Environment.NewLine);}catch(IOException){}
        var match=System.Text.RegularExpressions.Regex.Match(message,@"Preparing your world (\d+)/(\d+)");
        if(match.Success){progress.Style=ProgressBarStyle.Continuous;progress.Value=int.Parse(match.Groups[1].Value)*100/int.Parse(match.Groups[2].Value);}
    }
    async Task PrepareAndPlay(bool launch)
    {
        if(busy||engineSession!=null)return;busy=true;play.Enabled=false;progress.Style=ProgressBarStyle.Marquee;preparation=new();
        try {
            if(!preview&&(GameGate.IsPlaying(WorldMode.Game(WorldMode.Full))||GameGate.IsPlaying(WorldMode.Game(WorldMode.Vanilla)))){SetStatus("Minecraft is running. Options and Export logs are available.");progress.Style=ProgressBarStyle.Continuous;progress.Value=100;play.Text="Playing";return;}
            if(!preview){WorldMode.AssertClosed();var updated=await AutomaticUpdates.Prepare(SetStatus,preparation.Token);if(updated!=null){SetStatus("Opening the improved launcher…");AutomaticUpdates.Restart(updated,launch);busy=false;Close();return;}}
            SetStatus("Opening your adventure…");
            var package=await Task.Run(()=>ReleasePackage.Prepare());
            var manifestFile=Path.Combine(package,WorldMode.VanillaProtected?"manifest-vanilla.json":"manifest.json");
            if(!File.Exists(manifestFile))throw new InvalidDataException("The pack manifest is missing.");
            var manifest=JsonSerializer.Deserialize<PackManifest>(await File.ReadAllTextAsync(manifestFile),PackEngine.Json)!;
            if(!manifest.ReleaseReady)throw new InvalidDataException("This pack is still being prepared. Check back when the release is ready.");
            var instance=Path.Combine(PrismBridge.Data,"instances",manifest.Id);var game=Path.Combine(instance,".minecraft");
            WorldMode.AssertClosed();
            await PrismBridge.CloseIdleEngine();
            var recipeFile=Path.Combine(package,"config-patches.json");
            if(!File.Exists(recipeFile))throw new InvalidDataException("The release is missing its settings.");
            var recipes=JsonSerializer.Deserialize<ConfigRecipeList>(await File.ReadAllTextAsync(recipeFile),PackEngine.Json)!;
            if(recipes.Schema!=1)throw new InvalidDataException("Unsupported settings format.");
            SetStatus("Bringing your settings and servers…");await PrismBridge.ImportLocalControls(game,manifest,recipes,SetStatus);
            await PackEngine.Apply(manifest,package,game,SetStatus,cancel:preparation.Token,configurations:recipes.Recipes,gameIsRunning:()=>GameGate.IsPlaying(game));
            await PrismBridge.PrepareInstance(instance,manifest);
            await PrismBridge.Install(SetStatus,preparation.Token);
            preparation.Token.ThrowIfCancellationRequested();
            ReleasePackage.InstallShortcut();
            SetStatus("Ready: "+WorldMode.Label+".");progress.Style=ProgressBarStyle.Continuous;progress.Value=100;play.Enabled=true;
            if(launch){launchStarted=DateTime.UtcNow;launchingGame=game;gameSeen=false;playingId=null;
                engineSession=EngineSession.Start(PrismBridge.Engine,PrismBridge.Data,manifest.Id);
                SetStatus("Starting Minecraft…");progress.Style=ProgressBarStyle.Marquee;play.Text="Starting";play.Enabled=false;launchMonitor.Start();}
        }catch(OperationCanceledException){SetStatus("Preparation paused. Your completed downloads are saved.");}
        catch(Exception e){SetStatus(e.Message);progress.Style=ProgressBarStyle.Continuous;progress.Value=0;play.Text="Try again";play.Enabled=true;}
        finally{busy=false;preparation.Dispose();preparation=null;if(closing)Close();}
    }
    async Task CheckLaunch() {
        if(checkingLaunch||engineSession==null||launchingGame==null)return;checkingLaunch=true;
        try {
            // Check the owned engine handle first. A cached Java PID may have
            // been reused by another app after game exit; it must not keep our
            // invisible frontend and menu lock alive indefinitely.
            if(engineSession.Exited){var failed=engineSession.ExitCode!=0;engineSession.Dispose();engineSession=null;launchMonitor.Stop();progress.Style=ProgressBarStyle.Continuous;progress.Value=failed?0:100;play.Text=failed?"Try again":"Play";play.Enabled=true;
                if(gameSeen||!Visible){Close();return;}SetStatus(failed?"Minecraft stopped with an error. Options / Export logs can help.":"Minecraft did not open. Options / Export logs can help.");return;}
            int? playing=null;
            if(gameSeen&&playingId is int activeId){try{using var active=Process.GetProcessById(activeId);if(!active.HasExited)playing=activeId;}catch(ArgumentException){}}
            else playing=await Task.Run(()=>GameGate.PlayingId(launchingGame,engineSession.Id));
            if(IsDisposed)return;
            if(playing is int pid){
                if(gameSeen||GameGate.HasGameWindow(pid)){if(!gameSeen)GameReady(pid);return;}
            }
            if(gameSeen&&playing==null){SetStatus("Closing Minecraft…");return;}
            var elapsed=(int)(DateTime.UtcNow-launchStarted).TotalSeconds;
            SetStatus((engineSession.RequiredInteraction??LaunchFeedback.Phase(launchingGame,launchStarted))+" "+elapsed+"s");
        }catch(Exception e){SetStatus("Checking Minecraft startup: "+e.Message);}
        finally{checkingLaunch=false;}
    }
}

static class PrismBridge
{
    public static readonly string Root=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"ImmersiveMinecraft");
    public static string Data=>Path.Combine(Root,"data");
    public static async Task ImportLocalControls(string game,PackManifest manifest,ConfigRecipeList recipes,Action<string>? status=null)
    {
        var receipt=Path.Combine(game,".friendpack-controls-import.json");
        var source=ControlsMigration.FindSource(game,manifest.Files.Where(f=>f.Path.StartsWith("mods/")).Select(f=>f.Path),manifest.ModIds);
        var other=WorldMode.Game(manifest.Id==WorldMode.Full?WorldMode.Vanilla:WorldMode.Full);
        if(!string.Equals(Path.GetFullPath(other),Path.GetFullPath(game),StringComparison.OrdinalIgnoreCase)&&File.Exists(Path.Combine(other,"options.txt")))source=other;
        if(ControlsMigration.NeedsImport(game)) {
            if(source!=null)await ControlsMigration.Import(source,game,recipes.Recipes.Select(r=>r.Path));
            else {Directory.CreateDirectory(game);await File.WriteAllTextAsync(receipt,"{\"source\":null}");}
        }
        using var previous=JsonDocument.Parse(await File.ReadAllTextAsync(receipt));
        foreach(var field in previous.RootElement.EnumerateObject())if(field.Name.Equals("source",StringComparison.OrdinalIgnoreCase)&&field.Value.ValueKind==JsonValueKind.String&&Directory.Exists(field.Value.GetString()))source=field.Value.GetString();
        if(source!=null&&ModSettings.NeedsImport(game))await ModSettings.Import(source,game,recipes.Recipes,gameIsRunning:()=>GameGate.IsPlaying(game),progress:status);
        var vanilla=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),".minecraft");
        await SavedServers.Import(source!=null&&File.Exists(Path.Combine(source,"servers.dat"))?[source]:[vanilla],game);
    }
    const string Version="11.1.1";
    const string Digest="05841d0b3bfc0a8212658457cc6035e1c29cd7ffae919464e874e384cbe8d062";
    public static string Engine=>Path.Combine(Root,"launcher",Version,"prismlauncher.exe");
    public static async Task Install(Action<string> status,CancellationToken cancel=default)
    {
        if(System.Runtime.InteropServices.RuntimeInformation.OSArchitecture!=System.Runtime.InteropServices.Architecture.X64)
            throw new PlatformNotSupportedException("This release needs 64-bit Windows on an Intel or AMD processor.");
        if(File.Exists(Engine)){await WriteSettings();return;}
        Directory.CreateDirectory(Path.Combine(Root,"cache"));var zip=Path.Combine(Root,"cache","prism-"+Version+".zip");
        if(!File.Exists(zip)||await PackEngine.Hash(zip)!=Digest){
            status("Preparing the launcher…");using var http=new HttpClient{Timeout=TimeSpan.FromMinutes(10)};
            http.DefaultRequestHeaders.UserAgent.ParseAdd("ImmersiveMinecraft-FriendPack/1.0");
            using var response=await http.GetAsync($"https://github.com/PrismLauncher/PrismLauncher/releases/download/{Version}/PrismLauncher-Windows-MinGW-w64-Portable-{Version}.zip",HttpCompletionOption.ResponseHeadersRead,cancel);
            response.EnsureSuccessStatusCode();await using var input=await response.Content.ReadAsStreamAsync();
            await using(var output=File.Create(zip+".new"))await input.CopyToAsync(output,cancel);
            if(await PackEngine.Hash(zip+".new")!=Digest)throw new InvalidDataException("Launcher download could not be verified.");
            File.Move(zip+".new",zip,true);
        }
        var folder=Path.GetDirectoryName(Engine)!;var staged=folder+"-staging-"+Guid.NewGuid().ToString("N");Directory.CreateDirectory(staged);
        using(var archive=ZipFile.OpenRead(zip))foreach(var entry in archive.Entries) {
            cancel.ThrowIfCancellationRequested();var relative=entry.FullName.Replace('\\','/');if(relative.EndsWith('/')){Directory.CreateDirectory(PackEngine.Resolve(staged,relative.TrimEnd('/')));continue;}
            var target=PackEngine.Resolve(staged,relative);Directory.CreateDirectory(Path.GetDirectoryName(target)!);entry.ExtractToFile(target);
        }
        if(!File.Exists(Path.Combine(staged,"prismlauncher.exe")))throw new InvalidDataException("Launcher package has an unexpected layout.");
        Directory.CreateDirectory(Path.GetDirectoryName(folder)!);Directory.Move(staged,folder);
        await WriteSettings();
        status("Launcher ready.");
    }
    static async Task WriteSettings()
    {
        Directory.CreateDirectory(Data);var settings=Path.Combine(Data,"prismlauncher.cfg");
        var lines=File.Exists(settings)?(await File.ReadAllLinesAsync(settings)).ToList():new List<string>{"[General]"};
        var memory=MemoryLimit();
        var managed=new Dictionary<string,string>{{"AutomaticJavaSwitch","true"},{"AutomaticJavaDownload","true"},{"UserAskedAboutAutomaticJavaDownload","true"},{"IgnoreJavaWizard","true"},{"CloseAfterLaunch","false"},{"QuitAfterGameStop","true"},{"ShowConsole","false"},{"ShowConsoleOnError","false"},{"AutoCloseConsole","true"},{"MinMemAlloc","512"},{"MaxMemAlloc",memory.ToString()}};
        // Valid preferences prevent language/theme setup pages. Keep existing choices.
        var initial=new Dictionary<string,string>{{"Language",System.Globalization.CultureInfo.CurrentUICulture.Name.Replace('-','_')},{"ApplicationTheme","dark"},{"IconTheme","pe_colored"}};
        var automaticJava=Path.Combine(Data,"java","java-runtime-epsilon","bin","javaw.exe");
        if(File.Exists(automaticJava))managed["JavaPath"]=automaticJava.Replace('\\','/');
        int general=lines.FindIndex(s=>s.Trim()=="[General]");if(general<0){lines.Add("[General]");general=lines.Count-1;}
        int end=general+1;while(end<lines.Count&&!lines[end].TrimStart().StartsWith('['))end++;
        foreach(var pair in initial)if(!lines.Skip(general+1).Take(end-general-1).Any(s=>s.Split('=',2)[0].Trim()==pair.Key&&s.Split('=',2).Length==2&&!string.IsNullOrWhiteSpace(s.Split('=',2)[1])))managed[pair.Key]=pair.Value;
        for(int i=end-1;i>general;i--){var key=lines[i].Split('=',2)[0].Trim();if(managed.TryGetValue(key,out var value)){lines[i]=key+"="+value;managed.Remove(key);}}
        lines.InsertRange(end,managed.Select(p=>p.Key+"="+p.Value));
        await File.WriteAllLinesAsync(settings,lines);
    }
    public static async Task CloseIdleEngine()
    {
        foreach(var process in Process.GetProcessesByName("prismlauncher"))using(process){
            // Only this pack's private engine is managed; another Prism installation
            // neither blocks the pack nor gets closed.
            string? executable;try{executable=process.MainModule?.FileName;}catch{continue;}
            if(!string.Equals(executable,Engine,StringComparison.OrdinalIgnoreCase))continue;
            if(!process.CloseMainWindow())throw new IOException("Close the pack's launcher window before updating.");
            try{await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));}
            catch(TimeoutException){throw new IOException("Close the pack's launcher window before updating.");}
        }
    }
    public static async Task PrepareInstance(string folder,PackManifest manifest)
    {
        Directory.CreateDirectory(folder);
        await File.WriteAllTextAsync(Path.Combine(folder,"mmc-pack.json"),JsonSerializer.Serialize(new {formatVersion=1,components=new[]{new{uid="net.minecraft",version=manifest.Minecraft},new{uid="net.fabricmc.fabric-loader",version=manifest.Fabric}}},PackEngine.Json));
        var memory=MemoryLimit();var file=Path.Combine(folder,"instance.cfg");
        var lines=File.Exists(file)?(await File.ReadAllLinesAsync(file)).ToList():new List<string>{"[General]"};
        var values=new Dictionary<string,string>{{"InstanceType","OneSix"},{"name",manifest.Name.Replace('\n',' ')},{"OverrideMemory","true"},{"MinMemAlloc","512"},{"MaxMemAlloc",memory.ToString()},{"OverrideConsole","true"},{"ShowConsole","false"},{"ShowConsoleOnError","false"},{"AutoCloseConsole","true"}};
        int general=lines.FindIndex(s=>s.Trim()=="[General]");if(general<0){lines.Add("[General]");general=lines.Count-1;}
        int end=general+1;while(end<lines.Count&&!lines[end].TrimStart().StartsWith('['))end++;
        for(int i=end-1;i>general;i--){var key=lines[i].Split('=',2)[0].Trim();if(values.Remove(key,out var value))lines[i]=key+"="+value;}
        if(!lines.Skip(general+1).Take(end-general-1).Any(s=>s.StartsWith("OverrideJavaLocation=")))values["OverrideJavaLocation"]="false";
        if(!lines.Skip(general+1).Take(end-general-1).Any(s=>s.StartsWith("OverrideJavaArgs=")))values["OverrideJavaArgs"]="false";
        lines.InsertRange(end,values.Select(p=>p.Key+"="+p.Value));await File.WriteAllLinesAsync(file,lines);
    }
    static long MemoryLimit()=>Math.Clamp(GC.GetGCMemoryInfo().TotalAvailableMemoryBytes/1024/1024/3,2048,8192);
}

