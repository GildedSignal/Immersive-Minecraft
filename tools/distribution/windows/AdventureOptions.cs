using System.Diagnostics;
using System.Text.Json;
using FriendPack;

sealed class AdventureOptions:Form
{
    readonly PixelLabel message=new();readonly ExperienceProgress progress=new();readonly List<MenuButton> actions=[];
    bool working;public bool Uninstalling{get;private set;}
    public AdventureOptions()
    {
        Text="Your adventure";ClientSize=new Size(580,Math.Min(750,Screen.PrimaryScreen!.WorkingArea.Height-80));AutoScroll=true;AutoScrollMinSize=new Size(580,750);StartPosition=FormStartPosition.CenterParent;
        FormBorderStyle=FormBorderStyle.FixedDialog;MaximizeBox=false;MinimizeBox=false;DoubleBuffered=true;BackgroundImage=MenuScenery.Create(580,660);
        var title=new PixelLabel{Text="Your adventure",PixelScale=3,ForeColor=Color.White,Bounds=new Rectangle(40,23,500,36)};Controls.Add(title);
        var mode=Button("Vanilla protection: "+(WorldMode.VanillaProtected?"ON":"OFF"),72);
        mode.Click+=(_,_)=>{if(working)return;try{WorldMode.Toggle();mode.Text="Vanilla protection: "+(WorldMode.VanillaProtected?"ON":"OFF");DescribeMode();}catch(Exception e){Status(e.Message);}};
        Button("Worlds and screenshots",124).Click+=(_,_)=>{var folder=WorldMode.ActiveGame;Directory.CreateDirectory(folder);Process.Start(new ProcessStartInfo(folder){UseShellExecute=true});};
        Button("Import a world copy",176).Click+=async (_,_)=>{
            if(working)return;
            using var picker=new FolderBrowserDialog{Description="Choose a Java world folder containing level.dat. A separate copy is imported; the original stays untouched.",UseDescriptionForTitle=true,InitialDirectory=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),".minecraft","saves")};
            if(picker.ShowDialog(this)!=DialogResult.OK)return;
            await Run(async()=>{WorldMode.AssertClosed();WorldMode.CheckWorldSource(picker.SelectedPath);
                var copied=await Task.Run(()=>PersonalFiles.ImportWorld(picker.SelectedPath,WorldMode.ActiveGame,WorldMode.TargetWorldVersion,progress:Status));
                Status("World copy ready in "+WorldMode.Label+". Original kept.");});
        };
        Controls.Add(new PixelLabel{Text="Copies only. Your original worlds stay untouched.",PixelScale=1,ForeColor=Color.FromArgb(224,223,196),Bounds=new Rectangle(40,222,500,20)});
        Button("Export worlds and servers",228).Click+=async (_,_)=>{
            if(working)return;Directory.CreateDirectory(UninstallPack.ExportFolder);
            using var picker=new SaveFileDialog{Title="Export worlds and servers",Filter="Minecraft backup (*.zip)|*.zip",DefaultExt="zip",InitialDirectory=UninstallPack.ExportFolder,FileName=Path.GetFileName(UninstallPack.BackupName)};
            if(picker.ShowDialog(this)!=DialogResult.OK)return;
            await Run(async()=>{WorldMode.AssertClosed();var result=await Task.Run(()=>PersonalFiles.ExportProfiles(WorldMode.ExistingGames(),picker.FileName,progress:Status));
                Status($"Saved {result.Worlds} worlds and your server lists. No originals changed.");Process.Start(new ProcessStartInfo(Path.GetDirectoryName(picker.FileName)!){UseShellExecute=true});});
        };
        Button("Import personal settings",280).Click+=async (_,_)=>{
            if(working)return;using var picker=new FolderBrowserDialog{Description="Choose your previous Minecraft game folder (contains options.txt).",UseDescriptionForTitle=true,InitialDirectory=Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData)};
            if(picker.ShowDialog(this)!=DialogResult.OK)return;
            await Run(async()=>{WorldMode.AssertClosed();var package=ReleasePackage.Prepare();var recipes=JsonSerializer.Deserialize<ConfigRecipeList>(File.ReadAllText(Path.Combine(package,"config-patches.json")),PackEngine.Json)!;
                await ModSettings.Import(picker.SelectedPath,WorldMode.ActiveGame,recipes.Recipes,replace:true,gameIsRunning:()=>GameGate.IsPlaying(WorldMode.ActiveGame),progress:Status);
                await ControlsMigration.Import(picker.SelectedPath,WorldMode.ActiveGame,recipes.Recipes.Select(r=>r.Path));Status("Your mod preferences and controls are imported. Pack tuning stays; local backups kept.");});
        };
        Button("Sound workshop",332).Click+=(_,_)=>{using var dialog=new ElevenLabsForm();dialog.ShowDialog(this);};
        Button("Export logs",384).Click+=async (_,_)=>{
            if(working)return;var folder=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),"Immersive Minecraft","Support");Directory.CreateDirectory(folder);
            using var picker=new SaveFileDialog{Title="Export support logs",Filter="Support logs (*.zip)|*.zip",DefaultExt="zip",InitialDirectory=folder,FileName="Minecraft-support-"+DateTime.Now.ToString("yyyyMMdd-HHmmss")+".zip"};
            if(picker.ShowDialog(this)!=DialogResult.OK)return;
            await Run(async()=>{var count=await Task.Run(()=>SupportLogs.Export(WorldMode.ExistingGames(),PrismBridge.Root,picker.FileName));Status($"Saved {count} support logs. Send the ZIP to your helper.");Process.Start(new ProcessStartInfo(folder){UseShellExecute=true});});
        };
        Button("Uninstall",436).Click+=async (_,_)=>{
            if(working)return;using var dialog=new RemovalConfirmation();if(dialog.ShowDialog(this)!=DialogResult.OK)return;
            await Run(()=>UninstallPack.Begin(Status,()=>Uninstalling=true));
        };
        // Make room for the always-visible copy reassurance under Import.
        foreach(var item in actions.Where(a=>a.Top>=228))item.Top+=22;
        message.ForeColor=Color.FromArgb(224,223,196);message.PixelScale=2;message.Bounds=new Rectangle(40,517,500,100);Controls.Add(message);DescribeMode();
        progress.Bounds=new Rectangle(40,629,500,12);progress.Style=ProgressBarStyle.Continuous;Controls.Add(progress);
        var done=new MenuButton{Text="Done",Bounds=new Rectangle(170,664,240,44)};done.Click+=(_,_)=>{if(!working)Close();};Controls.Add(done);AcceptButton=done;
        FormClosing+=(_,e)=>{if(working&&!Uninstalling){e.Cancel=true;Status("Please let the copy finish. Originals are kept.");}};
    }
    MenuButton Button(string text,int y){var button=new MenuButton{Text=text,Bounds=new Rectangle(40,y,500,44)};actions.Add(button);Controls.Add(button);return button;}
    void DescribeMode()=>Status(WorldMode.VanillaProtected?"Vanilla terrain and content. Graphics, sound and performance enhancements stay. Separate save folder; original worlds are kept.":"Full terrain and content. Terralith advises new worlds. Saves stay separate from Vanilla enhancements. Importing always creates a copy.");
    void Status(string text){if(IsDisposed)return;if(InvokeRequired){BeginInvoke(()=>Status(text));return;}message.Text=text;}
    async Task Run(Func<Task> action)
    {
        if(working)return;working=true;foreach(var item in actions)item.Enabled=false;progress.Style=ProgressBarStyle.Marquee;
        try{await action();}catch(Exception e){Status(e.Message);}
        finally{working=false;if(!IsDisposed){foreach(var item in actions)item.Enabled=true;progress.Style=ProgressBarStyle.Continuous;progress.Value=100;}}
    }
}
sealed class RemovalConfirmation:Form
{
    public RemovalConfirmation()
    {
        Text="Uninstall Immersive Minecraft";ClientSize=new Size(600,330);StartPosition=FormStartPosition.CenterParent;FormBorderStyle=FormBorderStyle.FixedDialog;MaximizeBox=false;MinimizeBox=false;BackgroundImage=MenuScenery.Create(600,330);
        var message=new PixelLabel{Text="Remove the launcher and both local pack profiles? Worlds and servers are backed up in Documents first. Local sign-in and the ElevenLabs key are removed. Your sharing files and other Minecraft installations stay.",PixelScale=2,ForeColor=Color.White,Bounds=new Rectangle(35,32,530,164)};
        var remove=new MenuButton{Text="Uninstall",DialogResult=DialogResult.OK,Bounds=new Rectangle(40,240,250,46)};
        var keep=new MenuButton{Text="Keep playing",DialogResult=DialogResult.Cancel,Bounds=new Rectangle(310,240,250,46)};
        Controls.AddRange([message,remove,keep]);CancelButton=keep;AcceptButton=keep;
    }
}
