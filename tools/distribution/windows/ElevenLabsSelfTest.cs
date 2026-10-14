using System.Net;
using System.Text;
using System.Text.Json;

static class ElevenLabsSelfTest
{
    static int checks;
    static void Check(bool value){checks++;if(!value)throw new InvalidOperationException("Connection self-test failed at check "+checks);}
    public static async Task<string> Run() {
        string target=ElevenLabsCredentials.Target+"/SelfTest/"+Guid.NewGuid().ToString("N");
        // Dummy values are never submitted to a real endpoint; production credentials untouched.
        string dummy="dummy_test_value_"+Guid.NewGuid().ToString("N");
        try {
            Check(!ElevenLabsCredentials.Exists(target));
            ElevenLabsCredentials.Save(dummy,target);Check(ElevenLabsCredentials.Exists(target));Check(ElevenLabsCredentials.ReadForRequest(target)==dummy);
            ElevenLabsCredentials.Save(dummy+"2",target);Check(ElevenLabsCredentials.ReadForRequest(target)==dummy+"2");
            ElevenLabsCredentials.Remove(target);Check(!ElevenLabsCredentials.Exists(target));
            Check(!ElevenLabsCredentials.LooksLikeKey("short"));Check(!ElevenLabsCredentials.LooksLikeKey(dummy+"\n"));Check(!ElevenLabsCredentials.LooksLikeKey(new string('x',2049)));
            foreach(var (code,body,state,verified) in new[]{(200,"{}","connected",true),(401,"{\"detail\":{\"status\":\"invalid_api_key\"}}","rejected",false),(403,"{\"detail\":{\"status\":\"missing_permissions\"}}","permission_required",false),(401,"{\"detail\":{\"status\":\"missing_permissions\"}}","permission_required",false),(429,"{}","check_unavailable",false),(302,"{}","check_unavailable",false)}) {
                var result=await ElevenLabsConnection.Check(dummy,testHandler:new FakeHandler(code,body,dummy));Check(result.State==state&&result.Verified==verified);
                string json=JsonSerializer.Serialize(result);Check(!json.Contains(dummy));
            }
            var unavailable=await ElevenLabsConnection.Check(dummy,testHandler:new FakeHandler(0,"",dummy));Check(unavailable.State=="check_unavailable");
            return $"PASS: {checks} credential round-trip/update/delete, input, HTTP permission/rejection/rate-limit/redirect/offline and secret-free status checks. Real credential was not changed; no network requests made.\n";
        } finally {ElevenLabsCredentials.Remove(target);}
    }
    sealed class FakeHandler(int code,string body,string expectedKey):HttpMessageHandler {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken token) {
            Check(request.Method==HttpMethod.Get&&request.RequestUri?.AbsoluteUri=="https://api.elevenlabs.io/v1/user");
            Check(request.Headers.GetValues("xi-api-key").Single()==expectedKey);
            if(code==0)throw new HttpRequestException("mock offline");
            return Task.FromResult(new HttpResponseMessage((HttpStatusCode)code){Content=new StringContent(body,Encoding.UTF8,"application/json")});
        }
    }
}
