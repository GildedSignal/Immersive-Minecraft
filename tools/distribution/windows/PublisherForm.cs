sealed class PublisherForm:Form
{
    readonly PixelLabel status=new(){ForeColor=Color.White,PixelScale=2,Bounds=new Rectangle(45,125,550,100)};
    readonly MenuButton publish=new(){Text="Publish update",Bounds=new Rectangle(140,275,360,48)};
    readonly ExperienceProgress progress=new(){Bounds=new Rectangle(140,246,360,12)};bool busy;
    public PublisherForm(){Text="Share your improvements";ClientSize=new Size(640,400);StartPosition=FormStartPosition.CenterScreen;FormBorderStyle=FormBorderStyle.FixedDialog;MaximizeBox=false;BackgroundImage=MenuScenery.Create(640,400);
        Controls.Add(new PixelLabel{Text="Share your improvements",PixelScale=3,ForeColor=Color.White,Bounds=new Rectangle(30,50,580,45)});status.Text="Publish this launcher version. Friends receive it the next time they play.";Controls.AddRange([status,progress,publish]);
        publish.Click+=async(_,_)=>{if(busy)return;busy=true;publish.Enabled=false;progress.Style=ProgressBarStyle.Marquee;try{await Publisher.Publish(Environment.ProcessPath!,SetStatus);progress.Style=ProgressBarStyle.Continuous;progress.Value=100;}catch(Exception e){SetStatus(e.Message);progress.Style=ProgressBarStyle.Continuous;progress.Value=0;}finally{busy=false;publish.Enabled=true;}};
        FormClosing+=(_,e)=>{if(busy){e.Cancel=true;SetStatus("Let publishing finish. Your previous release stays available.");}};
    }
    void SetStatus(string text){if(InvokeRequired){BeginInvoke(()=>SetStatus(text));return;}status.Text=text;}
}
