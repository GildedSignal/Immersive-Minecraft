using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Net;
using FriendPack;

static class UpdateTests
{
    sealed class Handler(Func<HttpRequestMessage,HttpResponseMessage> respond):HttpMessageHandler{public int Requests;protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken token){Requests++;return Task.FromResult(respond(request));}}
    public static async Task<string> Run() {
        var folder=Path.Combine(Path.GetTempPath(),"friendpack-update-test-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(folder);int checks=0;void Check(bool valid,string name){if(!valid)throw new Exception("FAIL: "+name);checks++;}
        using var key=RSA.Create(3072);var pub=Convert.ToBase64String(key.ExportSubjectPublicKeyInfo());var data=Encoding.UTF8.GetBytes("verified fixture executable — never launched");var hash=Convert.ToHexString(SHA256.HashData(data)).ToLowerInvariant();
        var descriptor=new ReleaseDescriptor(1,UpdateProtocol.Channel,100,"2026.10.13.1",hash,data.Length,"Immersive Minecraft.exe","Fixture",[hash]);var signed=UpdateProtocol.Sign(descriptor,key);
        var verified=UpdateProtocol.Verify(signed,pub);Check(verified.Sequence==descriptor.Sequence&&verified.Sha256==descriptor.Sha256&&verified.Version==descriptor.Version&&verified.ChunkHashes!.SequenceEqual(descriptor.ChunkHashes!),"signed descriptor round trip");
        using var other=RSA.Create(2048);bool invalid=false;try{UpdateProtocol.Verify(signed,Convert.ToBase64String(other.ExportSubjectPublicKeyInfo()));}catch{invalid=true;}Check(invalid,"different publisher rejected");
        invalid=false;try{UpdateProtocol.Verify(signed with {Payload=Convert.ToBase64String(Encoding.UTF8.GetBytes("tampered"))},pub);}catch{invalid=true;}Check(invalid,"changed payload rejected");
        Check(!UpdateProtocol.Newer(descriptor,100,100)&&!UpdateProtocol.Newer(descriptor,101,0)&&!UpdateProtocol.Newer(descriptor,1,101)&&UpdateProtocol.Newer(descriptor,1,100),"version and rollback boundaries");
        var trust=new UpdateTrust("https://updates.example.test",pub,1);
        Handler Responses(byte[] body,SignedRelease envelope)=>new(request=>new HttpResponseMessage(HttpStatusCode.OK){Content=request.RequestUri!.AbsolutePath=="/api/channel"?new StringContent(JsonSerializer.Serialize(envelope)):new ByteArrayContent(body)});
        var handler=Responses(data,signed);var path=await AutomaticUpdates.Prepare(_=>{},default,new HttpClient(handler),trust,folder);
        Check(path!=null&&File.ReadAllBytes(path).SequenceEqual(data),"verified bytes staged");Check(handler.Requests==2,"announcement then immutable asset");
        var state=Path.Combine(folder,"client/update-state.json");using(var saved=JsonDocument.Parse(File.ReadAllText(state)))Check(saved.RootElement.GetProperty("Sequence").GetInt64()==100,"accepted state written after verification");
        var cached=Responses([],signed);var same=await AutomaticUpdates.Prepare(_=>{},default,new HttpClient(cached),trust,folder);Check(same==path&&cached.Requests==1,"verified local artifact reused");
        var current=Responses([],signed);Check(await AutomaticUpdates.Prepare(_=>{},default,new HttpClient(current),trust with {BuiltSequence=100},folder)==null&&current.Requests==1,"up-to-date performs no download");
        var newer=UpdateProtocol.Sign(descriptor with {Sequence=101},key);var corrupt=Responses(Encoding.UTF8.GetBytes("WRONG"),newer);var empty=Path.Combine(folder,"corrupt");invalid=false;try{await AutomaticUpdates.Prepare(_=>{},default,new HttpClient(corrupt),trust,empty);}catch{invalid=true;}
        Check(!invalid&&!File.Exists(Path.Combine(empty,"client/update-state.json")),"bad file permits current version without advancing accepted sequence");Check(!Directory.EnumerateFiles(empty,"*.exe",SearchOption.AllDirectories).Any()&&!Directory.EnumerateFiles(empty,"*.partial-*",SearchOption.AllDirectories).Any(),"failed download leaves no runnable/partial file");
        var badEnvelope=Responses(data,signed with {Signature=Convert.ToBase64String(new byte[384])});Check(await AutomaticUpdates.Prepare(_=>{},default,new HttpClient(badEnvelope),trust,Path.Combine(folder,"bad-signature"))==null&&badEnvelope.Requests==1,"bad signature keeps current version and never downloads");
        var unavailable=new Handler(_=>new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));Check(await AutomaticUpdates.Prepare(_=>{},default,new HttpClient(unavailable),trust,folder)==null,"unavailable service permits installed version");
        return $"PASS: {checks} signature, tamper, rollback, verified download/cache, current version, partial cleanup and offline checks. Fixture bytes never executed; real publisher key unchanged.";
    }
}
