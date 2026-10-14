using System.Drawing.Drawing2D;

// Original bitmap lettering and scenery: no game assets or installed fonts required.
static class PixelText
{
    static readonly string[] Rows = [
        "A:01110,10001,10001,11111,10001,10001,10001", "B:11110,10001,10001,11110,10001,10001,11110",
        "C:01111,10000,10000,10000,10000,10000,01111", "D:11110,10001,10001,10001,10001,10001,11110",
        "E:11111,10000,10000,11110,10000,10000,11111", "F:11111,10000,10000,11110,10000,10000,10000",
        "G:01111,10000,10000,10111,10001,10001,01111", "H:10001,10001,10001,11111,10001,10001,10001",
        "I:111,010,010,010,010,010,111", "J:00111,00010,00010,00010,10010,10010,01100",
        "K:10001,10010,10100,11000,10100,10010,10001", "L:10000,10000,10000,10000,10000,10000,11111",
        "M:10001,11011,10101,10101,10001,10001,10001", "N:10001,11001,10101,10011,10001,10001,10001",
        "O:01110,10001,10001,10001,10001,10001,01110", "P:11110,10001,10001,11110,10000,10000,10000",
        "Q:01110,10001,10001,10001,10101,10010,01101", "R:11110,10001,10001,11110,10100,10010,10001",
        "S:01111,10000,10000,01110,00001,00001,11110", "T:11111,00100,00100,00100,00100,00100,00100",
        "U:10001,10001,10001,10001,10001,10001,01110", "V:10001,10001,10001,10001,10001,01010,00100",
        "W:10001,10001,10001,10101,10101,10101,01010", "X:10001,10001,01010,00100,01010,10001,10001",
        "Y:10001,10001,01010,00100,00100,00100,00100", "Z:11111,00001,00010,00100,01000,10000,11111",
        "0:01110,10001,10011,10101,11001,10001,01110", "1:010,110,010,010,010,010,111",
        "2:01110,10001,00001,00010,00100,01000,11111", "3:11110,00001,00001,01110,00001,00001,11110",
        "4:10010,10010,10010,11111,00010,00010,00010", "5:11111,10000,10000,11110,00001,00001,11110",
        "6:01110,10000,10000,11110,10001,10001,01110", "7:11111,00001,00010,00100,01000,01000,01000",
        "8:01110,10001,10001,01110,10001,10001,01110", "9:01110,10001,10001,01111,00001,00001,01110",
        ".:0,0,0,0,0,1,1", ",:00,00,00,00,01,01,10", "::0,1,1,0,1,1,0",
        "!:1,1,1,1,1,0,1", "?:01110,10001,00001,00010,00100,00000,00100",
        "':1,1,0,0,0,0,0", "-:000,000,000,111,000,000,000", "/:00001,00001,00010,00100,01000,10000,10000"
    ];
    static readonly Dictionary<char,string[]> Glyphs=Rows.ToDictionary(r=>r[0],r=>r[2..].Split(','));
    public static int Width(string text,int scale)=>text.Sum(c=>((c==' '?3:Glyphs.GetValueOrDefault(char.ToUpperInvariant(c),Glyphs['?'])[0].Length)+1)*scale)-scale;
    public static void Draw(Graphics g,string text,int x,int y,int scale,Color color,bool shadow=true)
    {
        if(shadow)Draw(g,text,x+scale,y+scale,scale,Color.FromArgb(180,0,0,0),false);
        using var brush=new SolidBrush(color);
        foreach(char ch in text){
            if(ch==' '){x+=4*scale;continue;}
            var rows=Glyphs.GetValueOrDefault(char.ToUpperInvariant(ch),Glyphs['?']);
            for(int j=0;j<rows.Length;j++)for(int i=0;i<rows[j].Length;i++)if(rows[j][i]=='1')g.FillRectangle(brush,x+i*scale,y+j*scale,scale,scale);
            x+=(rows[0].Length+1)*scale;
        }
    }
}

sealed class PixelLabel:Label
{
    public int PixelScale {get;set;}=2;
    public bool Centered {get;set;}=true;
    public PixelLabel(){BackColor=Color.Transparent;SetStyle(ControlStyles.UserPaint|ControlStyles.SupportsTransparentBackColor|ControlStyles.OptimizedDoubleBuffer,true);}
    protected override void OnPaint(PaintEventArgs e)
    {
        var line="";var lines=new List<string>();
        foreach(var word in Text.Replace('…','.').Replace('—','-').Split(' ')){
            if(PixelText.Width(line+" "+word,PixelScale)>Width-12&&line.Length>0){lines.Add(line);line=word;}
            else line+=(line.Length>0?" ":"")+word;
        }
        lines.Add(line);int y=2;
        foreach(var s in lines){PixelText.Draw(e.Graphics,s,Centered?Math.Max(0,(Width-PixelText.Width(s,PixelScale))/2):0,y,PixelScale,ForeColor);y+=10*PixelScale;if(y>=Height)break;}
    }
}

sealed class MenuButton:Button
{
    bool hover,pressed;
    public MenuButton(){FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;Cursor=Cursors.Hand;DoubleBuffered=true;}
    protected override void OnMouseEnter(EventArgs e){hover=true;Invalidate();base.OnMouseEnter(e);}
    protected override void OnMouseLeave(EventArgs e){hover=pressed=false;Invalidate();base.OnMouseLeave(e);}
    protected override void OnMouseDown(MouseEventArgs e){pressed=true;Invalidate();base.OnMouseDown(e);}
    protected override void OnMouseUp(MouseEventArgs e){pressed=false;Invalidate();base.OnMouseUp(e);}
    protected override void OnPaint(PaintEventArgs e){
        var g=e.Graphics;g.Clear(Color.FromArgb(15,16,17));
        using var face=new SolidBrush(!Enabled?Color.FromArgb(48,49,51):hover?Color.FromArgb(104,113,135):Color.FromArgb(88,89,94));
        g.FillRectangle(face,2,2,Width-4,Height-4);
        using var light=new Pen(pressed?Color.FromArgb(40,41,46):Color.FromArgb(172,174,182),2);
        using var dark=new Pen(pressed?Color.FromArgb(172,174,182):Color.FromArgb(38,39,45),2);
        g.DrawLine(light,3,3,Width-4,3);g.DrawLine(light,3,3,3,Height-4);
        g.DrawLine(dark,3,Height-4,Width-4,Height-4);g.DrawLine(dark,Width-4,3,Width-4,Height-4);
        if(hover&&Enabled)g.DrawRectangle(Pens.White,0,0,Width-1,Height-1);
        PixelText.Draw(g,Text,(Width-PixelText.Width(Text,3))/2,(Height-21)/2+(pressed?2:0),3,!Enabled?Color.Gray:hover?Color.FromArgb(255,255,160):Color.White);
        if(Focused)g.DrawRectangle(Pens.LightGray,6,6,Width-13,Height-13);
    }
}

sealed class ExperienceProgress:Control
{
    readonly System.Windows.Forms.Timer animation=new(){Interval=100};int phase,amount;
    ProgressBarStyle style;
    public ProgressBarStyle Style {get=>style;set {style=value;animation.Enabled=style==ProgressBarStyle.Marquee;Invalidate();}}
    public int Value {get=>amount;set{amount=Math.Clamp(value,0,100);Invalidate();}}
    public ExperienceProgress(){DoubleBuffered=true;animation.Tick+=(_,_)=>{phase=(phase+7)%100;Invalidate();};}
    protected override void OnPaint(PaintEventArgs e){e.Graphics.Clear(Color.FromArgb(22,35,12));using var brush=new SolidBrush(Color.FromArgb(130,193,47));int start=style==ProgressBarStyle.Marquee?(Width-8)*phase/100:0;int size=style==ProgressBarStyle.Marquee?60:(Width-8)*amount/100;e.Graphics.FillRectangle(brush,4+start,3,Math.Min(size,Width-8-start),Height-6);e.Graphics.DrawRectangle(Pens.Black,0,0,Width-1,Height-1);}
    protected override void Dispose(bool disposing){if(disposing)animation.Dispose();base.Dispose(disposing);}
}

static class MenuScenery
{
    public static Bitmap Create(int width,int height)
    {
        var small=new Bitmap(200,125);using(var g=Graphics.FromImage(small)){
            g.Clear(Color.FromArgb(61,94,108));using var glow=new SolidBrush(Color.FromArgb(234,207,133));g.FillRectangle(glow,145,18,13,13);
            var random=new Random(731);
            for(int layer=0;layer<3;layer++){
                int baseline=58+layer*17;Color c=layer==0?Color.FromArgb(66,85,91):layer==1?Color.FromArgb(42,64,57):Color.FromArgb(25,46,35);
                using var ground=new SolidBrush(c);
                for(int x=0;x<200;x+=4){int top=baseline+(int)(Math.Sin(x*.06+layer)*10+Math.Sin(x*.15)*3);g.FillRectangle(ground,x,top,4,125-top);}
                for(int i=0;i<13;i++){int x=random.Next(0,200),y=baseline-8+random.Next(0,10);using var trunk=new SolidBrush(Color.FromArgb(36,37,25));g.FillRectangle(trunk,x+3,y-15,2,24);g.FillRectangle(ground,x-2,y-23,12,10);g.FillRectangle(ground,x,y-28,8,8);}
            }
            using var water=new SolidBrush(Color.FromArgb(42,77,90));g.FillPolygon(water,new Point[]{new(110,75),new(120,75),new(148,125),new(66,125)});
            using var glint=new SolidBrush(Color.FromArgb(113,133,108));for(int i=0;i<18;i++)g.FillRectangle(glint,random.Next(75,139),random.Next(95,124),random.Next(1,4),1);
            using var dim=new SolidBrush(Color.FromArgb(65,0,0,0));g.FillRectangle(dim,0,0,200,125);
        }
        var result=new Bitmap(width,height);using(var g=Graphics.FromImage(result)){g.InterpolationMode=InterpolationMode.NearestNeighbor;g.PixelOffsetMode=PixelOffsetMode.Half;g.DrawImage(small,new Rectangle(0,0,width,height));}small.Dispose();return result;
    }
}
