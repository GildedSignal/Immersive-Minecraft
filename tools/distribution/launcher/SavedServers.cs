using System.Buffers.Binary;
using System.IO.Compression;
using System.Text;
using System.Text.Json;
namespace FriendPack;

public record ServerImportResult(int Added,int Total);
public static class SavedServers
{
    const int Limit=32*1024*1024;
    record Entry(string Address,byte[] Bytes);
    sealed class Reader
    {
        public readonly byte[] Bytes;public int Position;
        public Reader(byte[] bytes){Bytes=bytes;}
        public void Skip(int length){if(length<0||length>Bytes.Length-Position)throw new InvalidDataException("Truncated saved server data.");Position+=length;}
        public byte Byte(){int p=Position;Skip(1);return Bytes[p];}
        public int Count(){int p=Position;Skip(4);int n=BinaryPrimitives.ReadInt32BigEndian(Bytes.AsSpan(p,4));if(n<0||n>1_000_000)throw new InvalidDataException("Invalid saved server data length.");return n;}
        public string String()
        {
            int p=Position;Skip(2);int length=BinaryPrimitives.ReadUInt16BigEndian(Bytes.AsSpan(p,2));p=Position;Skip(length);
            // Minecraft's Java DataInput UTF encodes UTF-16 surrogates separately.
            var output=new StringBuilder();int end=p+length;
            while(p<end){int a=Bytes[p++];
                if(a<128)output.Append((char)a);
                else if((a&224)==192){if(p>=end||(Bytes[p]&192)!=128)throw new InvalidDataException("Invalid saved server text.");output.Append((char)(((a&31)<<6)|(Bytes[p++]&63)));}
                else if((a&240)==224){if(p+1>=end||(Bytes[p]&192)!=128||(Bytes[p+1]&192)!=128)throw new InvalidDataException("Invalid saved server text.");output.Append((char)(((a&15)<<12)|((Bytes[p++]&63)<<6)|(Bytes[p++]&63)));}
                else throw new InvalidDataException("Invalid saved server text.");
            }
            return output.ToString();
        }
        public void Payload(byte type,int depth=0)
        {
            if(depth>64)throw new InvalidDataException("Saved server data is nested too deeply.");
            switch(type){
                case 1:Skip(1);break;case 2:Skip(2);break;case 3:case 5:Skip(4);break;case 4:case 6:Skip(8);break;
                case 7:Skip(Count());break;case 8:String();break;
                case 9:var child=Byte();var count=Count();if(child==0&&count!=0)throw new InvalidDataException("Invalid NBT list.");for(int i=0;i<count;i++)Payload(child,depth+1);break;
                case 10:byte next;while((next=Byte())!=0){String();Payload(next,depth+1);}break;
                case 11:Skip(checked(Count()*4));break;case 12:Skip(checked(Count()*8));break;
                default:throw new InvalidDataException("Unknown saved server data tag.");
            }
        }
    }
    sealed record Document(byte[] Bytes,int Start,int End,List<Entry> Entries);
    static byte[] Expand(byte[] bytes)
    {
        if(bytes.Length>Limit)throw new InvalidDataException("Saved server list is too large.");
        if(bytes.Length<2||!(bytes[0]==31&&bytes[1]==139)&&!(bytes[0]==120&&(bytes[0]*256+bytes[1])%31==0))return bytes;
        using var original=new MemoryStream(bytes);using Stream decompressor=bytes[0]==31?new GZipStream(original,CompressionMode.Decompress):new ZLibStream(original,CompressionMode.Decompress);
        using var result=new MemoryStream();var buffer=new byte[8192];int n;
        while((n=decompressor.Read(buffer))>0){if(result.Length+n>Limit)throw new InvalidDataException("Saved server list is too large.");result.Write(buffer,0,n);}return result.ToArray();
    }
    static Document Parse(byte[] input)
    {
        var bytes=Expand(input);var r=new Reader(bytes);if(r.Byte()!=10)throw new InvalidDataException("Saved server list is not an NBT compound.");r.String();
        int start=-1,end=-1;var entries=new List<Entry>();byte type;
        while((type=r.Byte())!=0) {
            var name=r.String();
            if(name!="servers"){r.Payload(type);continue;}
            if(type!=9||start!=-1)throw new InvalidDataException("Invalid or duplicated server list.");
            start=r.Position;byte child=r.Byte();int count=r.Count();if(child!=10&&!(child==0&&count==0))throw new InvalidDataException("Invalid server entry list.");
            for(int i=0;i<count;i++) {
                int from=r.Position;string address="";byte field;
                while((field=r.Byte())!=0){string key=r.String();if(key=="ip"&&field==8)address=r.String();else r.Payload(field);}
                entries.Add(new(address,bytes[from..r.Position]));
            }
            end=r.Position;
        }
        if(r.Position!=bytes.Length)throw new InvalidDataException("Unexpected trailing saved server data.");
        return new(bytes,start,end,entries);
    }
    static string AddressKey(string address)
    {
        var key=address.Trim().ToLowerInvariant();
        if(key.EndsWith(":25565",StringComparison.Ordinal)&&(key.Count(c=>c==':')==1||key.StartsWith('[')))key=key[..^6];
        return key;
    }
    public static int? WorldDataVersion(byte[] level)
    {
        var reader=new Reader(Expand(level));if(reader.Byte()!=10)throw new InvalidDataException("Invalid Java world metadata.");reader.String();byte type;
        while((type=reader.Byte())!=0) {
            var name=reader.String();if(name!="Data"||type!=10){reader.Payload(type);continue;}
            while((type=reader.Byte())!=0){name=reader.String();if(name=="DataVersion"&&type==3)return reader.Count();reader.Payload(type);}
        }
        return null;
    }
    public static HashSet<string> WorldIdentifiers(byte[] level)
    {
        var reader=new Reader(Expand(level));if(reader.Byte()!=10)throw new InvalidDataException("Invalid Java world metadata.");reader.String();var identifiers=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        void Walk(byte type,string field,int depth){if(depth>64)throw new InvalidDataException("World metadata is nested too deeply.");
            if(type==10){byte next;while((next=reader.Byte())!=0){var name=reader.String();if(name.Contains(':'))identifiers.Add(name);Walk(next,name,depth+1);}}
            else if(type==9){byte next=reader.Byte();int count=reader.Count();if(next==0&&count!=0)throw new InvalidDataException("Invalid world list.");for(int i=0;i<count;i++)Walk(next,field,depth+1);}
            else if(type==8){var value=reader.String();if(field!="LevelName"&&(value.Contains(':')||field=="Enabled"))identifiers.Add(value);}
            else reader.Payload(type,depth);
        }
        Walk(10,"",0);return identifiers;
    }
    public static (byte[] Bytes,ServerImportResult Result) Merge(byte[] existing,IEnumerable<byte[]> originals)
    {
        var current=Parse(existing);var entries=new List<Entry>(current.Entries);var known=entries.Select(e=>AddressKey(e.Address)).ToHashSet(StringComparer.Ordinal);int added=0;
        foreach(var original in originals)foreach(var entry in Parse(original).Entries)
            if(string.IsNullOrWhiteSpace(entry.Address)||known.Add(AddressKey(entry.Address))){entries.Add(entry);added++;}
        using var output=new MemoryStream();
        if(current.Start>=0)output.Write(current.Bytes.AsSpan(0,current.Start));
        else {output.Write(current.Bytes.AsSpan(0,current.Bytes.Length-1));output.Write(new byte[]{9,0,7});output.Write(Encoding.ASCII.GetBytes("servers"));}
        output.WriteByte(10);Span<byte> length=stackalloc byte[4];BinaryPrimitives.WriteInt32BigEndian(length,entries.Count);output.Write(length);
        foreach(var entry in entries)output.Write(entry.Bytes);
        if(current.Start>=0)output.Write(current.Bytes.AsSpan(current.End));else output.WriteByte(0);
        if(output.Length>Limit)throw new InvalidDataException("Merged saved server list is too large.");
        return (output.ToArray(),new(added,entries.Count));
    }
    public static async Task<ServerImportResult> Import(IEnumerable<string> sourceFolders,string destination,bool once=true)
    {
        var receipt=PackEngine.Resolve(destination,".friendpack-servers-import.json");
        if(once&&File.Exists(receipt))return new(0,0);
        var target=PackEngine.Resolve(destination,"servers.dat");Directory.CreateDirectory(destination);
        var originals=new List<byte[]>();
        foreach(var source in sourceFolders.Distinct(StringComparer.OrdinalIgnoreCase)) {
            if(string.Equals(Path.GetFullPath(source).TrimEnd(Path.DirectorySeparatorChar),Path.GetFullPath(destination).TrimEnd(Path.DirectorySeparatorChar),StringComparison.OrdinalIgnoreCase))continue;
            var path=Path.Combine(source,"servers.dat");if(File.Exists(path)){if(new FileInfo(path).Length>Limit)throw new InvalidDataException("Saved server list is too large.");originals.Add(await File.ReadAllBytesAsync(path));}
        }
        var existing=File.Exists(target)?await File.ReadAllBytesAsync(target):new byte[]{10,0,0,0};
        var merged=Merge(existing,originals);
        if(merged.Result.Added>0) {
            if(File.Exists(target)){
                var backup=PackEngine.Resolve(destination,".friendpack-server-backups/"+DateTime.UtcNow.ToString("yyyyMMdd-HHmmss")+"-"+Guid.NewGuid().ToString("N")+"/servers.dat");
                Directory.CreateDirectory(Path.GetDirectoryName(backup)!);File.Copy(target,backup);
            }
            var temp=target+".friendpack-new";await File.WriteAllBytesAsync(temp,merged.Bytes);File.Move(temp,target,true);
        }
        await File.WriteAllTextAsync(receipt,JsonSerializer.Serialize(merged.Result,PackEngine.Json));return merged.Result;
    }
}
