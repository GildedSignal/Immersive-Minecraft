static class LaunchDetectionTests
{
    public static string Run(){
        int checks=0;var root=Path.Combine(Path.GetTempPath(),"Launch fixture space");var data=Path.Combine(root,"data");var engine=Path.Combine(root,"engine","prismlauncher.exe");var game=Path.Combine(data,"instances","immersive-minecraft",".minecraft");var now=DateTime.UtcNow;
        var parent=new GameGate.Entry(10,0,"prismlauncher.exe",engine,$"\"{engine}\" -d \"{data}\" -l immersive-minecraft",now);
        var java=new GameGate.Entry(20,10,"javaw.exe",null,"javaw.exe org.prismlauncher.EntryPoint",now.AddSeconds(1));
        void Check(GameGate.Entry[] rows,int? expected,string? target=null,int? scoped=null){if(GameGate.Match(rows,target??game,engine,data,scoped)!=expected)throw new Exception("Launch detection fixture "+(checks+1)+" failed");checks++;}
        Check([parent,java],20);Check([parent,java],20,scoped:10);Check([parent,java],null,scoped:11);
        Check([java],null);Check([parent with{Executable=Path.Combine(root,"other","prismlauncher.exe")},java],null);
        Check([parent with{Command=$"prism -d \"{data}\" -l immersive-minecraft-vanilla"},java],null);
        Check([parent with{Command=$"prism -d \"{root}"+"\\other\" -l immersive-minecraft"},java],null);
        Check([parent with{Created=now.AddSeconds(2)},java],null);
        Check([java with{Command=$"java --gameDir \"{game}\""}],20);
        Check([java with{Command=$"java \"--gameDir={game}\""}],20);
        Check([java with{Command="java --gameDir relative"}],null);
        Check([parent,java],null,Path.Combine(root,"unrelated"));
        Check([parent with{Command=$"prism --dir \"{data}\" --launch immersive-minecraft-vanilla"},java],20,Path.Combine(data,"instances","immersive-minecraft-vanilla",".minecraft"));
        Check([parent,java with{Name="other.exe"}],null);
        return $"PASS: {checks} launch association checks: direct and stdin launch, quoted paths, separate profiles, wrong engine/data/parent, scoped PID and parent PID reuse. No game started or process arguments saved.";
    }
}
