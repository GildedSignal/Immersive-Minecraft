using System.Security.Cryptography;
using System.Reflection;
using System.Text.Json;
using FriendPack;

public record UpdateTrust(string Origin,string PublicKey,long BuiltSequence);
static class Publisher
{
    const string KeyName="ImmersiveMinecraft-Publisher-v1";
    static CngKey Key(){if(!CngKey.Exists(KeyName))return CngKey.Create(CngAlgorithm.Rsa,KeyName,new CngKeyCreationParameters{ExportPolicy=CngExportPolicies.None,KeyUsage=CngKeyUsages.Signing,Parameters={new CngProperty("Length",BitConverter.GetBytes(3072),CngPropertyOptions.None)}});return CngKey.Open(KeyName);}
    public static void Init(string output){using var key=Key();using var rsa=new RSACng(key);File.WriteAllText(output,Convert.ToBase64String(rsa.ExportSubjectPublicKeyInfo()));}
    public static UpdateTrust Trust(){using var input=Assembly.GetExecutingAssembly().GetManifestResourceStream("ImmersiveMinecraft.UpdateTrust");return input!=null?JsonSerializer.Deserialize<UpdateTrust>(input,PackEngine.Json)!:throw new IOException("This build has no update service configured.");}
    public static async Task<SignedRelease> Announcement(string executable,CancellationToken cancel=default){
        var trust=Trust();using var key=Key();using var rsa=new RSACng(key);if(Convert.ToBase64String(rsa.ExportSubjectPublicKeyInfo())!=trust.PublicKey)throw new IOException("This computer does not own this pack's publishing key.");
        var manifest=WorldMode.Load(ReleasePackage.Prepare(),false);var hash=await PackEngine.Hash(executable);var size=new FileInfo(executable).Length;const int chunkSize=8*1024*1024;var hashes=new List<string>();var buffer=new byte[chunkSize];
        await using(var input=File.OpenRead(executable)){while(input.Position<input.Length){var read=await input.ReadAtLeastAsync(buffer,(int)Math.Min(chunkSize,input.Length-input.Position),false,cancel);hashes.Add(Convert.ToHexString(SHA256.HashData(buffer.AsSpan(0,read))).ToLowerInvariant());}}
        return UpdateProtocol.Sign(new ReleaseDescriptor(1,UpdateProtocol.Channel,trust.BuiltSequence,manifest.Version,hash,size,"Immersive Minecraft.exe","Immersion, performance and launcher improvements. Personal worlds and preferences stay yours.",hashes.ToArray()),rsa);
    }
    public static async Task Publish(string executable,Action<string> status,CancellationToken cancel=default) {
        var trust=Trust();if(!Uri.TryCreate(trust.Origin,UriKind.Absolute,out var origin)||origin.Scheme!="https")throw new IOException("Invalid update service.");
        using var key=Key();using var rsa=new RSACng(key);if(Convert.ToBase64String(rsa.ExportSubjectPublicKeyInfo())!=trust.PublicKey)throw new IOException("This computer does not own this pack's publishing key.");
        var package=ReleasePackage.Prepare();var manifest=WorldMode.Load(package,false);var hash=await PackEngine.Hash(executable);var size=new FileInfo(executable).Length;
        const int chunkSize=8*1024*1024;var chunkHashes=new List<string>();var buffer=new byte[chunkSize];
        await using(var hashing=File.OpenRead(executable)){while(hashing.Position<hashing.Length){var read=await hashing.ReadAtLeastAsync(buffer,(int)Math.Min(chunkSize,hashing.Length-hashing.Position),false,cancel);chunkHashes.Add(Convert.ToHexString(SHA256.HashData(buffer.AsSpan(0,read))).ToLowerInvariant());}}
        var release=new ReleaseDescriptor(1,UpdateProtocol.Channel,trust.BuiltSequence,manifest.Version,hash,size,"Immersive Minecraft.exe","Immersion, performance and launcher improvements. Personal worlds and preferences stay yours.",chunkHashes.ToArray());
        var signed=UpdateProtocol.Sign(release,rsa);Directory.CreateDirectory(Path.Combine(PrismBridge.Root,"publisher"));File.WriteAllText(Path.Combine(PrismBridge.Root,"publisher","latest-signed.json"),JsonSerializer.Serialize(signed,PackEngine.Json));
        using var http=new HttpClient{Timeout=TimeSpan.FromMinutes(15)};http.DefaultRequestHeaders.UserAgent.ParseAdd("ImmersiveMinecraft-Publisher/1.0");
        status("Uploading your launcher…");
        // Upload immutable chunks below edge request-size limits. Announce the
        // version only after the server verifies the assembled file hash.
        await using var input=File.OpenRead(executable);var count=(int)((size+chunkSize-1)/chunkSize);
        for(int i=0;i<count;i++){var read=await input.ReadAtLeastAsync(buffer,(int)Math.Min(chunkSize,size-input.Position),false,cancel);using var request=new HttpRequestMessage(HttpMethod.Put,new Uri(origin,$"/api/publish/{hash}/{i}")){Content=new ByteArrayContent(buffer,0,read)};
            request.Headers.Add("X-Release-Payload",signed.Payload);request.Headers.Add("X-Release-Signature",signed.Signature);using var response=await http.SendAsync(request,cancel);if(!response.IsSuccessStatusCode)throw new IOException("Release upload failed ("+(int)response.StatusCode+"). Your existing release stays available.");status($"Uploading your launcher {i+1}/{count}");}
        status("Verifying and publishing…");using var commit=await http.PostAsync(new Uri(origin,"/api/publish/commit"),new StringContent(JsonSerializer.Serialize(signed),System.Text.Encoding.UTF8,"application/json"),cancel);if(!commit.IsSuccessStatusCode)throw new IOException("Release verification failed ("+(int)commit.StatusCode+"). Your existing release stays available.");status("Published. Your friends receive this version on their next launch.");
    }
}
