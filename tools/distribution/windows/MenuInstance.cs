// A repeat launch is a request to reveal the existing menu, including when
// only its engine guard is visible to Windows. No second game is started.
sealed class MenuInstance:IDisposable
{
    readonly Mutex single;readonly EventWaitHandle open;
    readonly System.Windows.Forms.Timer poll=new(){Interval=100};
    public bool OwnsMenu{get;}
    bool disposed;
    public MenuInstance(string name="Local\\ImmersiveMinecraftMenuV2"){
        single=new(false,name);try{OwnsMenu=single.WaitOne(0);}catch(AbandonedMutexException){OwnsMenu=true;}
        open=new(false,EventResetMode.AutoReset,name+"-open");
    }
    public void RequestOpen()=>open.Set();
    public void Watch(Action reopen){if(!OwnsMenu)throw new InvalidOperationException("Only the menu owner can watch reopen requests.");poll.Tick+=(_,_)=>{if(open.WaitOne(0))reopen();};poll.Start();}
    public void Dispose(){if(disposed)return;disposed=true;poll.Stop();poll.Dispose();open.Dispose();if(OwnsMenu)single.ReleaseMutex();single.Dispose();}
}
