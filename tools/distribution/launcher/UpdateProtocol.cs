using System.Security.Cryptography;
using System.Text.Json;
namespace FriendPack;

public record ReleaseDescriptor(int Schema,string Channel,long Sequence,string Version,string Sha256,long Size,string FileName,string Notes,string[]? ChunkHashes=null);
public record SignedRelease(string Payload,string Signature);
public static class UpdateProtocol
{
    public const string Channel="immersive-minecraft-windows";
    public static SignedRelease Sign(ReleaseDescriptor release,RSA key) {
        Validate(release);var bytes=JsonSerializer.SerializeToUtf8Bytes(release,PackEngine.Json);
        return new(Convert.ToBase64String(bytes),Convert.ToBase64String(key.SignData(bytes,HashAlgorithmName.SHA256,RSASignaturePadding.Pss)));
    }
    public static ReleaseDescriptor Verify(SignedRelease envelope,string publicKey) {
        if(envelope==null||envelope.Payload==null||envelope.Signature==null)throw new InvalidDataException("Missing update announcement.");
        if(envelope.Payload.Length>32768||envelope.Signature.Length>4096)throw new InvalidDataException("Update announcement is too large.");
        var bytes=Convert.FromBase64String(envelope.Payload);using var key=RSA.Create();var spki=Convert.FromBase64String(publicKey);key.ImportSubjectPublicKeyInfo(spki,out var used);
        if(used!=spki.Length||key.KeySize<2048||!key.VerifyData(bytes,Convert.FromBase64String(envelope.Signature),HashAlgorithmName.SHA256,RSASignaturePadding.Pss))throw new InvalidDataException("Update signature could not be verified.");
        var release=JsonSerializer.Deserialize<ReleaseDescriptor>(bytes,PackEngine.Json)??throw new InvalidDataException("Missing update announcement.");Validate(release);return release;
    }
    public static void Validate(ReleaseDescriptor release) {
        if(release.Schema!=1||release.Channel!=Channel||release.Sequence<=0||release.Size<=0||release.Size>512L*1024*1024||release.FileName!="Immersive Minecraft.exe"||release.Sha256==null||release.Sha256.Length!=64||!release.Sha256.All(Uri.IsHexDigit)||release.Notes==null||release.Notes.Length>2000||release.Version==null||!System.Text.RegularExpressions.Regex.IsMatch(release.Version,@"^\d{4}\.\d{2}\.\d{2}\.\d+$"))throw new InvalidDataException("Unsupported update announcement.");
        if(release.ChunkHashes is {} chunks&&(chunks.Length!=(release.Size+8*1024*1024-1)/(8*1024*1024)||chunks.Any(s=>s==null||s.Length!=64||!s.All(Uri.IsHexDigit))))throw new InvalidDataException("Invalid update chunk hashes.");
    }
    public static bool Newer(ReleaseDescriptor release,long builtSequence,long acceptedSequence)=>release.Sequence>builtSequence&&release.Sequence>=acceptedSequence;
}
