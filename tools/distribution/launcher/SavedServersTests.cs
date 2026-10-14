using System.Buffers.Binary;
using System.IO.Compression;
using System.Text;
namespace FriendPack;
static class SavedServersTests
{
    static byte[] Text(string text)
    {
        using var stream=new MemoryStream();foreach(char c in text){if(c is >'\0' and <'\u0080')stream.WriteByte((byte)c);else if(c<'\u0800'){stream.WriteByte((byte)(192|(c>>6)));stream.WriteByte((byte)(128|(c&63)));}else{stream.WriteByte((byte)(224|(c>>12)));stream.WriteByte((byte)(128|((c>>6)&63)));stream.WriteByte((byte)(128|(c&63)));}}
        var bytes=stream.ToArray();var result=new byte[bytes.Length+2];BinaryPrimitives.WriteUInt16BigEndian(result,(ushort)bytes.Length);bytes.CopyTo(result,2);return result;
    }
    static byte[] Servers(params (string Name,string Address)[] rows)
    {
        using var stream=new MemoryStream();stream.Write(new byte[]{10,0,0,9});stream.Write(Text("servers"));stream.WriteByte(10);
        Span<byte> count=stackalloc byte[4];BinaryPrimitives.WriteInt32BigEndian(count,rows.Length);stream.Write(count);
        foreach(var row in rows){stream.WriteByte(8);stream.Write(Text("name"));stream.Write(Text(row.Name));stream.WriteByte(8);stream.Write(Text("ip"));stream.Write(Text(row.Address));stream.WriteByte(1);stream.Write(Text("acceptTextures"));stream.WriteByte(1);stream.WriteByte(0);}
        stream.WriteByte(8);stream.Write(Text("unknown_metadata"));stream.Write(Text("retain this"));stream.WriteByte(0);return stream.ToArray();
    }
    public static async Task Run(string root)
    {
        int checks=0;void Check(bool okay,string note){if(!okay)throw new Exception("FAIL servers: "+note);checks++;}
        var existing=Servers(("Current name","play.example.invalid"));
        var original=Servers(("Old name","PLAY.EXAMPLE.INVALID:25565"),("Unicode ♥ 🌲\0","other.example.invalid:25566"));
        var result=SavedServers.Merge(existing,[original]);Check(result.Result==new ServerImportResult(1,2),"address/default-port deduplication");
        Check(result.Bytes.AsSpan().IndexOf(Text("Current name"))>=0&&result.Bytes.AsSpan().IndexOf(Text("Old name"))<0,"existing server names and metadata win");
        Check(result.Bytes.AsSpan().IndexOf(Text("Unicode ♥ 🌲\0"))>=0,"modified UTF, surrogate pairs and NUL preserved byte-for-byte");
        Check(result.Bytes.AsSpan().IndexOf(Text("unknown_metadata"))>=0,"unknown root metadata retained");
        var repeated=SavedServers.Merge(result.Bytes,[original]);Check(repeated.Result.Added==0&&repeated.Bytes.SequenceEqual(result.Bytes),"repeat merge stable");
        var minimal=SavedServers.Merge(new byte[]{10,0,0,0},[original]);Check(minimal.Result.Total==2,"absent list is created");
        using var compressed=new MemoryStream();using(var gzip=new GZipStream(compressed,CompressionLevel.Fastest,true))gzip.Write(original);
        Check(SavedServers.Merge(existing,[compressed.ToArray()]).Result.Total==2,"compressed source handled");
        bool rejected=false;try{SavedServers.Merge(existing,[original[..^2]]);}catch{rejected=true;}Check(rejected,"truncated source rejected");
        var source=Path.Combine(root,"server-source");var destination=Path.Combine(root,"server-destination");Directory.CreateDirectory(source);Directory.CreateDirectory(destination);
        await File.WriteAllBytesAsync(Path.Combine(source,"servers.dat"),original);await File.WriteAllBytesAsync(Path.Combine(destination,"servers.dat"),existing);
        var sourceHash=await PackEngine.Hash(Path.Combine(source,"servers.dat"));var imported=await SavedServers.Import([source],destination);
        Check(imported.Added==1&&await PackEngine.Hash(Path.Combine(source,"servers.dat"))==sourceHash,"source remains untouched");
        Check(Directory.GetFiles(Path.Combine(destination,".friendpack-server-backups"),"servers.dat",SearchOption.AllDirectories).Length==1,"existing destination list backed up");
        var edited=Servers(("User changed their list","only.example.invalid"));await File.WriteAllBytesAsync(Path.Combine(destination,"servers.dat"),edited);
        await SavedServers.Import([source],destination);Check((await File.ReadAllBytesAsync(Path.Combine(destination,"servers.dat"))).SequenceEqual(edited),"one-time import does not undo subsequent deletions");
        var snapshot=await File.ReadAllBytesAsync(Path.Combine(destination,"servers.dat"));await File.WriteAllBytesAsync(Path.Combine(source,"servers.dat"),original[..^2]);
        rejected=false;try{await SavedServers.Import([source],destination,false);}catch{rejected=true;}Check(rejected&&(await File.ReadAllBytesAsync(Path.Combine(destination,"servers.dat"))).SequenceEqual(snapshot),"bad input causes no partial write");
        Console.WriteLine($"PASS {checks} saved-server migration checks.");
    }
}
