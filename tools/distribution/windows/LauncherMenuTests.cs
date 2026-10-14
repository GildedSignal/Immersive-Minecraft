using System.Diagnostics;

static class LauncherMenuTests
{
    public static async Task<string> Run(){
        var name="Local\\ImmersiveMinecraftMenuFixture-"+Guid.NewGuid().ToString("N");
        using(var owner=new MenuInstance(name)){
            if(!owner.OwnsMenu)throw new Exception("Fixture owner did not acquire menu");
            using var frontend=new LauncherForm(true){StartPosition=FormStartPosition.Manual,Location=new(-32000,-32000)};
            int requests=0;owner.Watch(()=>{requests++;frontend.ReopenMenu();});frontend.Show();
            for(int i=0;i<3;i++){
                if(i!=1){frontend.ShowInTaskbar=false;frontend.Hide();}else frontend.WindowState=FormWindowState.Minimized;
                using var child=Process.Start(new ProcessStartInfo(Environment.ProcessPath!){UseShellExecute=false,CreateNoWindow=true,ArgumentList={"--menu-notify-fixture",name}})!;
                await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));if(child.ExitCode!=0)throw new Exception("Duplicate acquired ownership instead of requesting menu");
                var watch=Stopwatch.StartNew();while(requests<i+1&&watch.Elapsed<TimeSpan.FromSeconds(3))await Task.Delay(25);
                if(requests!=i+1||!frontend.Visible||!frontend.ShowInTaskbar||frontend.WindowState!=FormWindowState.Normal)throw new Exception("Hidden/minimized menu did not reopen");
            }
            frontend.Close();
        }
        using var next=new MenuInstance(name);if(!next.OwnsMenu)throw new Exception("Menu lock was not released after exit");
        return "PASS: actual repeat-launch child processes request the existing menu; hidden and minimized frontend restored three times; no duplicate ownership; lock reacquired after exit. Unique fixture scope, no real game or settings touched.";
    }
}
