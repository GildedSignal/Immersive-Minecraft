using System.ComponentModel;
using System.Net;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

// The key never enters options, manifests, environment variables, command lines or logs.
static class ElevenLabsCredentials
{
    public const string Target="ImmersiveMinecraft/ElevenLabs";
    const int Generic=1, LocalMachine=2, NotFound=1168;
    [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)]
    struct Credential {
        public uint Flags,Type; public string TargetName,Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;public IntPtr CredentialBlob;public uint Persist,AttributeCount;
        public IntPtr Attributes;public string? TargetAlias;public string UserName;
    }
    [DllImport("advapi32.dll",EntryPoint="CredWriteW",CharSet=CharSet.Unicode,SetLastError=true)]
    [return:MarshalAs(UnmanagedType.Bool)]static extern bool Write(ref Credential credential,uint flags);
    [DllImport("advapi32.dll",EntryPoint="CredReadW",CharSet=CharSet.Unicode,SetLastError=true)]
    [return:MarshalAs(UnmanagedType.Bool)]static extern bool Read(string target,uint type,uint flags,out IntPtr credential);
    [DllImport("advapi32.dll",EntryPoint="CredDeleteW",CharSet=CharSet.Unicode,SetLastError=true)]
    [return:MarshalAs(UnmanagedType.Bool)]static extern bool Delete(string target,uint type,uint flags);
    [DllImport("advapi32.dll")]static extern void CredFree(IntPtr credential);
    public static bool Exists(string target=Target) {
        if(Read(target,Generic,0,out var pointer)){CredFree(pointer);return true;}
        int error=Marshal.GetLastWin32Error();if(error==NotFound)return false;throw new Win32Exception(error);
    }
    public static bool LooksLikeKey(string key)=>key.Length is >=12 and <=2048 && key.All(c=>c is >=(char)33 and <=(char)126);
    public static void Save(string key,string target=Target) {
        if(!LooksLikeKey(key))throw new ArgumentException("Paste the full API key, with no spaces or line breaks.");
        byte[] blob=Encoding.UTF8.GetBytes(key);IntPtr memory=Marshal.AllocHGlobal(blob.Length);
        try {
            Marshal.Copy(blob,0,memory,blob.Length);
            var credential=new Credential {Type=Generic,TargetName=target,Comment="Immersive Minecraft sound workshop",CredentialBlobSize=(uint)blob.Length,CredentialBlob=memory,Persist=LocalMachine,UserName="ElevenLabs"};
            if(!Write(ref credential,0))throw new Win32Exception(Marshal.GetLastWin32Error());
        } finally {Marshal.Copy(new byte[blob.Length],0,memory,blob.Length);Marshal.FreeHGlobal(memory);CryptographicOperations.ZeroMemory(blob);}
    }
    // Only local sound-tool callers need this value. Never return it from a CLI/status command.
    public static string ReadForRequest(string target=Target) {
        if(!Read(target,Generic,0,out var pointer))throw new Win32Exception(Marshal.GetLastWin32Error());
        byte[]? blob=null;
        try {
            var credential=Marshal.PtrToStructure<Credential>(pointer);
            if(credential.CredentialBlobSize>2560)throw new InvalidDataException("Saved credential is too large.");
            blob=new byte[credential.CredentialBlobSize];Marshal.Copy(credential.CredentialBlob,blob,0,blob.Length);
            return Encoding.UTF8.GetString(blob);
        } finally {if(blob!=null)CryptographicOperations.ZeroMemory(blob);CredFree(pointer);}
    }
    public static void Remove(string target=Target) {
        if(!Delete(target,Generic,0)&&Marshal.GetLastWin32Error()!=NotFound)throw new Win32Exception(Marshal.GetLastWin32Error());
    }
}

sealed record ElevenLabsStatus(bool KeyStored,bool Verified,string State,string Message,DateTimeOffset CheckedAt,int? HttpStatus=null);
static class ElevenLabsConnection
{
    public static readonly string StatusFile=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"ImmersiveMinecraft","sound","elevenlabs-status.json");
    public static void Record(ElevenLabsStatus status) {
        Directory.CreateDirectory(Path.GetDirectoryName(StatusFile)!);
        File.WriteAllText(StatusFile,JsonSerializer.Serialize(status,new JsonSerializerOptions {WriteIndented=true})+"\n");
    }
    public static string SafeStatusJson() {
        bool stored=ElevenLabsCredentials.Exists();
        // A previous verification is informational; existence always reflects the credential store.
        ElevenLabsStatus? status=null;
        try{if(File.Exists(StatusFile))status=JsonSerializer.Deserialize<ElevenLabsStatus>(File.ReadAllText(StatusFile));}catch(JsonException){}
        return JsonSerializer.Serialize(status is null ? new ElevenLabsStatus(stored,false,stored?"saved":"empty",stored?"A key is saved on this PC.":"No key saved.",DateTimeOffset.UtcNow) : status with {KeyStored=stored,Verified=stored&&status.Verified},new JsonSerializerOptions {WriteIndented=true});
    }
    public static async Task<ElevenLabsStatus> Check(string key,CancellationToken cancel=default,HttpMessageHandler? testHandler=null) {
        using var handler=testHandler??new HttpClientHandler {AllowAutoRedirect=false};
        using var http=new HttpClient(handler){Timeout=TimeSpan.FromSeconds(12)};
        using var request=new HttpRequestMessage(HttpMethod.Get,"https://api.elevenlabs.io/v1/user");
        request.Headers.Add("xi-api-key",key);
        request.Headers.UserAgent.ParseAdd("ImmersiveMinecraft-SoundWorkshop/1.0");
        try {
            using var response=await http.SendAsync(request,HttpCompletionOption.ResponseHeadersRead,cancel);
            int code=(int)response.StatusCode;
            if(response.IsSuccessStatusCode)return new(true,true,"connected","Connected. Ready for your sound workshop.",DateTimeOffset.UtcNow,code);
            string error="";
            await using var body=await response.Content.ReadAsStreamAsync(cancel);
            var buffer=new byte[4096];int length=0;
            while(length<buffer.Length){int n=await body.ReadAsync(buffer.AsMemory(length),cancel);if(n==0)break;length+=n;}
            try {
                using var json=JsonDocument.Parse(buffer.AsMemory(0,length));
                if(json.RootElement.TryGetProperty("detail",out var detail)&&detail.ValueKind==JsonValueKind.Object&&detail.TryGetProperty("status",out var status))error=status.GetString()??"";
            } catch(JsonException){}
            if(error=="missing_permissions"||response.StatusCode==HttpStatusCode.Forbidden)
                return new(true,false,"permission_required","Key saved. Enable Read user to verify it; sound permissions are checked when used.",DateTimeOffset.UtcNow,code);
            if(error is "invalid_api_key" or "api_key_not_found" || response.StatusCode==HttpStatusCode.Unauthorized)
                return new(true,false,"rejected","Key saved, but ElevenLabs rejected it. Paste a new key.",DateTimeOffset.UtcNow,code);
            return new(true,false,"check_unavailable","Key saved. ElevenLabs is unavailable; you can check it again later.",DateTimeOffset.UtcNow,code);
        } catch(OperationCanceledException) {return new(true,false,"check_unavailable","Key saved. Connection check timed out; try again later.",DateTimeOffset.UtcNow);}
        catch(HttpRequestException) {return new(true,false,"check_unavailable","Key saved. Could not reach ElevenLabs; try again later.",DateTimeOffset.UtcNow);}
    }
}

sealed class ElevenLabsForm:Form
{
    readonly TextBox key=new(){UseSystemPasswordChar=true,MaxLength=2048,PlaceholderText="Paste or type your API key"};
    readonly PixelLabel message=new(){PixelScale=2,ForeColor=Color.FromArgb(235,235,212)};
    readonly MenuButton connect=new(){Text="Paste and connect"};
    readonly MenuButton remove=new(){Text="Remove key"};
    readonly CancellationTokenSource closing=new();
    bool busy;readonly bool preview;
    protected override bool ShowWithoutActivation=>preview;
    public ElevenLabsForm(bool preview=false) {
        this.preview=preview;Text="Immersive Minecraft - Sound Workshop";ClientSize=new Size(720,470);StartPosition=FormStartPosition.CenterScreen;
        FormBorderStyle=FormBorderStyle.FixedSingle;MaximizeBox=false;DoubleBuffered=true;BackgroundImage=MenuScenery.Create(720,470);
        BackColor=Color.FromArgb(23,29,25);ForeColor=Color.White;
        var title=new PixelLabel {Text="Sound Workshop",PixelScale=5,Bounds=new Rectangle(25,42,670,45),ForeColor=Color.FromArgb(238,230,200)};
        var subtitle=new PixelLabel {Text="Connect ElevenLabs",PixelScale=3,Bounds=new Rectangle(25,107,670,32),ForeColor=Color.FromArgb(255,239,128)};
        var instruction=new PixelLabel {Text="Copy your API key, then click Paste and connect.",Bounds=new Rectangle(45,161,630,45)};
        key.Bounds=new Rectangle(105,220,510,37);key.Font=new Font("Consolas",14);key.BackColor=Color.FromArgb(27,31,27);key.ForeColor=Color.FromArgb(235,235,220);key.BorderStyle=BorderStyle.FixedSingle;
        message.Bounds=new Rectangle(45,276,630,56);
        connect.Bounds=new Rectangle(140,344,440,46);
        var get=new MenuButton {Text="Get a key",Bounds=new Rectangle(140,401,214,43)};remove.Bounds=new Rectangle(366,401,214,43);
        Controls.AddRange([title,subtitle,instruction,key,message,connect,get,remove]);AcceptButton=connect;
        message.Text=preview?"Your key stays on this PC for the sound tools.":ElevenLabsCredentials.Exists()?"A key is already saved on this PC.":"Your key stays on this PC for the sound tools.";
        remove.Enabled=!preview&&ElevenLabsCredentials.Exists();
        connect.Click+=async (_,_)=>await SaveAndCheck();
        get.Click+=(_,_)=>System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("https://elevenlabs.io/app/settings/api-keys"){UseShellExecute=true});
        remove.Click+=(_,_)=>{
            if(busy)return;
            try {ElevenLabsCredentials.Remove();key.Clear();remove.Enabled=false;message.Text="Key removed from this PC.";ElevenLabsConnection.Record(new(false,false,"empty",message.Text,DateTimeOffset.UtcNow));}
            catch(Win32Exception){message.Text="Windows could not remove the saved key. Please try again.";}
        };
        FormClosing+=(_,_)=>{closing.Cancel();key.Clear();};
    }
    async Task SaveAndCheck() {
        if(busy||preview)return;
        string value=key.Text.Trim();
        if(value.Length==0){try{value=Clipboard.GetText(TextDataFormat.Text).Trim();}catch(ExternalException){message.Text="Clipboard is busy. Paste your key in the field.";return;}}
        if(!ElevenLabsCredentials.LooksLikeKey(value)){message.Text="Copy your full API key first, or paste it in the field.";return;}
        busy=true;connect.Enabled=remove.Enabled=false;
        try {
            ElevenLabsCredentials.Save(value);key.Clear();
            ElevenLabsConnection.Record(new(true,false,"saved","Key saved on this PC. Checking connection.",DateTimeOffset.UtcNow));
            message.Text="Key saved. Checking your connection...";
            var result=await ElevenLabsConnection.Check(value,closing.Token);ElevenLabsConnection.Record(result);
            if(!IsDisposed){message.Text=result.Message;message.ForeColor=result.Verified?Color.FromArgb(178,226,126):Color.FromArgb(255,221,143);}
        } catch(Win32Exception){if(!IsDisposed)message.Text="Windows could not save the key. Please try again.";}
        catch(IOException){if(!IsDisposed)message.Text="Could not update the connection status. Please try again.";}
        finally{value="";busy=false;if(!IsDisposed){connect.Enabled=true;remove.Enabled=ElevenLabsCredentials.Exists();}}
    }
    protected override void Dispose(bool disposing){if(disposing&&!IsDisposed){closing.Cancel();closing.Dispose();key.Clear();}base.Dispose(disposing);}
}
