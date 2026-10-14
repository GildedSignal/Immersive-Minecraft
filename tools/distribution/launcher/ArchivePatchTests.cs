using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
namespace FriendPack;
static class ArchivePatchTests
{
 public static async Task Run(string root) {
  string before=Path.Combine(root,"original.zip"),recipeFile=Path.Combine(root,"edits.json"),after=Path.Combine(root,"customized.zip");
  using(var zip=ZipFile.Open(before,ZipArchiveMode.Create)){using var writer=new StreamWriter(zip.CreateEntry("sample.txt").Open());writer.Write("before");}
  var data=Encoding.UTF8.GetBytes("before and after");
  var entry=new ArchiveEntryRecipe("sample.txt","sample.txt",Convert.ToHexString(SHA256.HashData(data)).ToLowerInvariant(),data.Length,[new([0,6],null),new(null,Convert.ToBase64String(data[6..]))]);
  async Task Build(ArchiveEntryRecipe value,string file) {
   await File.WriteAllTextAsync(recipeFile,JsonSerializer.Serialize(new ArchiveRecipe(1,[value]),PackEngine.Json));ArchivePatch.Apply(before,recipeFile,file);
  }
  await Build(entry,after);using(var zip=ZipFile.OpenRead(after)){using var reader=new StreamReader(zip.GetEntry("sample.txt")!.Open());if(await reader.ReadToEndAsync()!="before and after")throw new Exception("Archive reconstruction failed.");}
  var again=Path.Combine(root,"again.zip");await Build(entry,again);if(await PackEngine.Hash(after)!=await PackEngine.Hash(again))throw new Exception("Archive reconstruction is not reproducible.");
  async Task Reject(ArchiveEntryRecipe value,string suffix) {
   try {await Build(value,Path.Combine(root,suffix+".zip"));}catch(InvalidDataException){return;}throw new Exception("Accepted malformed archive customization: "+suffix);
  }
  await Reject(entry with {Name="../escape.txt"},"traversal");
  await Reject(entry with {Operations=[new([3,100],null)]},"copy-range");
  await Reject(entry with {Sha256=new string('0',64)},"bad-result");
  await Reject(entry with {Operations=[new([0,6],"AAAA")]},"ambiguous");
  Console.WriteLine("PASS 6 archive customization checks (output, repeatability, traversal, ranges, hashes, ambiguity).");
 }
}
