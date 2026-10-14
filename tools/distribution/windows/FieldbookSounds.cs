using System.Net.Http.Json;
using System.Text.Json;

// Explicit authoring command only. The game and friends play the finished files
// locally; they never need the author's service credential or a paid request.
static class FieldbookSounds
{
    public static async Task Generate(string folder)
    {
        Directory.CreateDirectory(folder);
        var report = new List<object>();
        using var handler = new HttpClientHandler { AllowAutoRedirect = false };
        using var http = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(120) };
        http.DefaultRequestHeaders.Add("xi-api-key", ElevenLabsCredentials.ReadForRequest());
        http.DefaultRequestHeaders.UserAgent.ParseAdd("ImmersiveMinecraft-Fieldbook/1.0");
        string tier = "unknown";
        using (var subscription = await http.GetAsync("https://api.elevenlabs.io/v1/user/subscription"))
        {
            if (subscription.IsSuccessStatusCode)
            {
                using var document = JsonDocument.Parse(await subscription.Content.ReadAsStringAsync());
                if (document.RootElement.TryGetProperty("tier", out var value)) tier = value.GetString() ?? "unknown";
            }
        }
        var jobs = new[] {
            ("book_open", 1.2, "One gentle opening of a small worn leather field journal. Soft leather flex followed by a delicate short paper rustle. Close dry foley, warm and intimate, a quiet game interface sound. Single action, immediate onset, clean silence after. No voice, music, ambience, echo, impact or magical chime."),
            ("page_turn", .8, "One single gentle turn of a parchment page in a small field journal. Delicate crisp paper flick and soft settling rustle. Close dry foley for a quiet game interface, immediate onset and clean silence after. No voice, music, ambience, echo, impact or magical chime."),
            ("book_close", 1.0, "One gentle closing of a small leather field journal. Soft paper settling and a muted leather cover touch, no heavy thump. Close dry intimate foley, quiet game interface sound. Single action, immediate onset, clean silence after. No voice, music, ambience, echo or magical chime."),
            ("bookmark", .6, "One delicate placement of a cloth ribbon bookmark between parchment pages. A tiny soft cloth and paper brush, concise dry foley for a quiet game interface, immediate onset and clean silence after. No voice, music, ambience, echo, impact or magical chime.")
        };
        await File.WriteAllTextAsync(Path.Combine(folder,"account-capability.json"), JsonSerializer.Serialize(new { tier, stored = ElevenLabsCredentials.Exists() }, FriendPack.PackEngine.Json));
        foreach (var (name, seconds, prompt) in jobs)
        {
            var path = Path.Combine(folder, name + ".mp3");
            if (File.Exists(path) && new FileInfo(path).Length > 256) continue;
            using var response = await http.PostAsJsonAsync("https://api.elevenlabs.io/v1/sound-generation?output_format=mp3_44100_128",
                new { text = prompt, duration_seconds = seconds, prompt_influence = .6, model_id = "eleven_text_to_sound_v2", loop = false });
            // Never log service bodies, headers containing credentials, or retry
            // a paid POST automatically after an uncertain response.
            if (!response.IsSuccessStatusCode) {
                string reason = "unreported";
                try {
                    using var error = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
                    if(error.RootElement.TryGetProperty("detail", out var detail) && detail.ValueKind == JsonValueKind.Object && detail.TryGetProperty("status",out var state)) {
                        var candidate = state.GetString();
                        if(candidate is not null && candidate.Length<80 && candidate.All(c=>char.IsAsciiLetterOrDigit(c)||c=='_')) reason=candidate;
                    }
                }catch(JsonException){}
                throw new InvalidOperationException($"Sound generation stopped: HTTP {(int)response.StatusCode} ({name}), reason={reason}. No automatic retry.");
            }
            var bytes = await response.Content.ReadAsByteArrayAsync();
            if (bytes.Length is < 256 or > 8_000_000) throw new InvalidDataException("Unexpected audio size.");
            await File.WriteAllBytesAsync(path + ".new", bytes);
            File.Move(path + ".new", path, true);
            var cost = response.Headers.TryGetValues("character-cost", out var values) ? string.Join(",", values) : "unreported";
            report.Add(new { name, seconds, prompt, model = "eleven_text_to_sound_v2", outputFormat = "mp3_44100_128", billedCharacters = cost, sha256 = await FriendPack.PackEngine.Hash(path), tier });
            await File.WriteAllTextAsync(Path.Combine(folder, name + ".provenance.json"), JsonSerializer.Serialize(report[^1], FriendPack.PackEngine.Json));
        }
        await File.WriteAllTextAsync(Path.Combine(folder, "generation-status.json"), JsonSerializer.Serialize(new { complete = jobs.All(j => File.Exists(Path.Combine(folder, j.Item1 + ".mp3"))), tier, requestedSeconds = 3.6, generated = report.Count }, FriendPack.PackEngine.Json));
    }
}
