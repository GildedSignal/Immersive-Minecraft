using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;

namespace FriendPack;
public record PatchSource(string Sha256,long Size,string Recipe,string RecipeSha256);
public record ArchiveRecipe(int Schema,ArchiveEntryRecipe[] Entries);
public record ArchiveEntryRecipe(string Name,string? Source,string Sha256,int Size,PatchOperation[]? Operations);
public record PatchOperation(int[]? Copy,string? Insert);

// Downloads the author's original archive, then applies the pack's local edits.
// Unchanged contents are copied from that verified download, not redistributed.
public static class ArchivePatch
{
    public static void Apply(string original,string recipePath,string destination)
    {
        var recipe=JsonSerializer.Deserialize<ArchiveRecipe>(File.ReadAllText(recipePath),PackEngine.Json)
            ??throw new InvalidDataException("Empty customization recipe.");
        if(recipe.Schema!=1||recipe.Entries.Length is <1 or >50000)throw new InvalidDataException("Unsupported customization recipe.");
        using var source=ZipFile.OpenRead(original);
        using var output=ZipFile.Open(destination,ZipArchiveMode.Create);
        var names=new HashSet<string>(StringComparer.Ordinal);
        long total=0;
        foreach(var entry in recipe.Entries) {
            if(!names.Add(entry.Name)||entry.Size is <0 or >128_000_000||entry.Sha256.Length!=64)
                throw new InvalidDataException("Invalid customization entry.");
            // Validate even entries that Minecraft only reads without extracting.
            _=PackEngine.Resolve(Path.GetTempPath(),entry.Name.TrimEnd('/'));
            total+=entry.Size;if(total>768_000_000)throw new InvalidDataException("Customization is too large.");
            byte[] before=[];
            if(entry.Source!=null) {
                var old=source.GetEntry(entry.Source)??throw new InvalidDataException("Original archive entry is missing.");
                if(old.Length>128_000_000)throw new InvalidDataException("Original entry is too large.");
                using var input=old.Open();using var buffer=new MemoryStream();input.CopyTo(buffer);before=buffer.ToArray();
            }
            byte[] bytes;
            if(entry.Operations==null) bytes=before;
            else {
                if(entry.Operations.Length>50000)throw new InvalidDataException("Too many customization operations.");
                using var buffer=new MemoryStream(entry.Size);
                foreach(var operation in entry.Operations) {
                    if((operation.Copy==null)==(operation.Insert==null))throw new InvalidDataException("Ambiguous customization operation.");
                    if(operation.Copy is {} range) {
                        if(range.Length!=2||range[0]<0||range[1]<0||(long)range[0]+range[1]>before.Length)
                            throw new InvalidDataException("Customization copy is out of bounds.");
                        buffer.Write(before,range[0],range[1]);
                    } else {
                        if(operation.Insert!.Length>entry.Size*2L+8)throw new InvalidDataException("Customization insertion is too large.");
                        buffer.Write(Convert.FromBase64String(operation.Insert));
                    }
                    if(buffer.Length>entry.Size)throw new InvalidDataException("Customization exceeds its expected size.");
                }
                bytes=buffer.ToArray();
            }
            if(bytes.Length!=entry.Size||Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant()!=entry.Sha256.ToLowerInvariant())
                throw new InvalidDataException("Customization verification failed: "+entry.Name);
            // Stored ZIPs make output byte-for-byte reproducible across runtimes.
            var next=output.CreateEntry(entry.Name,CompressionLevel.NoCompression);
            next.LastWriteTime=new DateTimeOffset(2000,1,1,0,0,0,TimeSpan.Zero);
            next.ExternalAttributes=0;
            using var target=next.Open();target.Write(bytes);
        }
    }
}
