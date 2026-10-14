using System.Diagnostics;
using System.Text.Json;
using FriendPack;

static class AutomaticUpdates
{
    public static async Task<string?> Prepare(Action<string> status,CancellationToken cancel,HttpClient? testHttp=null,UpdateTrust? testTrust=null,string? testRoot=null) {
        UpdateTrust trust;try{trust=testTrust??Publisher.Trust();}catch(IOException){return null;}var root=testRoot??PrismBridge.Root;
        using var http=testHttp??new HttpClient(new HttpClientHandler{AllowAutoRedirect=false}){Timeout=TimeSpan.FromMinutes(10)};
        http.DefaultRequestHeaders.UserAgent.ParseAdd("ImmersiveMinecraft-Updater/1.0");
        status("Checking for improvements…");
        using var checking=CancellationTokenSource.CreateLinkedTokenSource(cancel);checking.CancelAfter(TimeSpan.FromSeconds(5));
        SignedRelease signed;
        try {using var response=await http.GetAsync(new Uri(new Uri(trust.Origin),"/api/channel"),HttpCompletionOption.ResponseHeadersRead,checking.Token);
            if(!response.IsSuccessStatusCode)return null;await using var stream=await response.Content.ReadAsStreamAsync(checking.Token);var bytes=await ReadBounded(stream,64*1024,checking.Token);signed=JsonSerializer.Deserialize<SignedRelease>(bytes,PackEngine.Json)!;
        }catch(HttpRequestException){status("Offline. Your installed version is ready.");return null;}catch(Exception e)when(e is InvalidDataException or JsonException){status("Update could not be verified. Keeping your installed version.");return null;}catch(TaskCanceledException)when(!cancel.IsCancellationRequested){status("Update service is slow. Using your installed version.");return null;}
        ReleaseDescriptor release;try{release=UpdateProtocol.Verify(signed,trust.PublicKey);}catch(Exception e)when(e is System.Security.Cryptography.CryptographicException or InvalidDataException or FormatException or JsonException){status("Update could not be verified. Keeping your installed version.");return null;}
        var acceptedFile=PackEngine.Resolve(root,"client/update-state.json");long accepted=0;try{if(File.Exists(acceptedFile)){using var saved=JsonDocument.Parse(await File.ReadAllTextAsync(acceptedFile,cancel));accepted=saved.RootElement.GetProperty("Sequence").GetInt64();}}catch(Exception e)when(e is JsonException or KeyNotFoundException or InvalidOperationException){}
        if(!UpdateProtocol.Newer(release,trust.BuiltSequence,accepted))return null;
        var relative="client/"+release.Sha256[..24]+"/Immersive Minecraft.exe";var target=PackEngine.Resolve(root,relative);Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        if(!File.Exists(target)||new FileInfo(target).Length!=release.Size||await PackEngine.Hash(target)!=release.Sha256.ToLowerInvariant()) {
            var temp=target+".partial-"+Guid.NewGuid().ToString("N");
            try {status("Downloading improvements…");using var response=await http.GetAsync(new Uri(new Uri(trust.Origin),"/download/"+release.Sha256),HttpCompletionOption.ResponseHeadersRead,cancel);response.EnsureSuccessStatusCode();await using var input=await response.Content.ReadAsStreamAsync(cancel);
                await using(var output=File.Create(temp)){var buffer=new byte[81920];long received=0;int previous=-1;while(true){var read=await input.ReadAsync(buffer,cancel);if(read==0)break;received+=read;if(received>release.Size)throw new InvalidDataException("Update download size is wrong.");await output.WriteAsync(buffer.AsMemory(0,read),cancel);var percent=(int)(received*100/release.Size);if(percent!=previous){status($"Downloading improvements {percent}%");previous=percent;}}}
                if(new FileInfo(temp).Length!=release.Size||await PackEngine.Hash(temp)!=release.Sha256.ToLowerInvariant())throw new InvalidDataException("Update download could not be verified.");File.Move(temp,target,true);
            }catch(Exception e)when(e is HttpRequestException or IOException or InvalidDataException){status("Update download unavailable. Keeping your installed version.");return null;}finally{if(File.Exists(temp))File.Delete(temp);}
        }
        var state=acceptedFile+".new";await File.WriteAllTextAsync(state,JsonSerializer.Serialize(new{release.Sequence,release.Version,release.Sha256},PackEngine.Json),cancel);File.Move(state,acceptedFile,true);return target;
    }
    static async Task<byte[]> ReadBounded(Stream input,int limit,CancellationToken cancel){using var output=new MemoryStream();var buffer=new byte[8192];while(true){var read=await input.ReadAsync(buffer,cancel);if(read==0)break;if(output.Length+read>limit)throw new InvalidDataException("Update announcement too large.");output.Write(buffer,0,read);}return output.ToArray();}
    public static void Restart(string executable,bool play=false) {
        var info=new ProcessStartInfo(executable){UseShellExecute=false,WorkingDirectory=Path.GetDirectoryName(executable)!};info.ArgumentList.Add("--after-update");info.ArgumentList.Add(Environment.ProcessId.ToString());if(play)info.ArgumentList.Add("--play");_=Process.Start(info)??throw new IOException("Could not start the verified update.");
    }
}
