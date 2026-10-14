param([Parameter(Mandatory=$true)][string]$RequestFile,[int]$MaximumMinutes=240)
$ErrorActionPreference='Stop'
$request=Get-Content -LiteralPath $RequestFile -Raw | ConvertFrom-Json
$installGame=[IO.Path]::GetFullPath($request.game).TrimEnd('\')
$installMods=[IO.Path]::GetFullPath((Join-Path $installGame 'mods'))
$installWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')).TrimEnd('\')
$sourceParent=[IO.Path]::GetFullPath((Join-Path $installWorkspace 'tools\distribution\release\gameplay-reconstructed'))
$log=Join-Path $PSScriptRoot 'persistent-install.log'
$backupRoot=Join-Path $PSScriptRoot ('backups\'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
function Write-InstallLog([string]$text){Add-Content -LiteralPath $log -Value ((Get-Date -Format o)+' '+$text)}
function Get-ReviewedHash([string]$path){
    $stream=[IO.File]::OpenRead($path);$hash=[Security.Cryptography.SHA256]::Create()
    try{return [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','').ToLowerInvariant()}finally{$hash.Dispose();$stream.Dispose()}
}
function Game-IsRunning {
    $watched=Get-Process -Id $request.watchedPid -ErrorAction SilentlyContinue
    if($watched -and $watched.ProcessName -match '^javaw?$'){return $true}
    foreach($process in Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'"){
        # Command lines can include tokens; compare in memory and never log them.
        $match=[regex]::Match([string]$process.CommandLine,'(?:"--gameDir"|--gameDir)\s+(?:"([^"]+)"|(\S+))')
        if(!$match.Success){continue}
        $directory=if($match.Groups[1].Success){$match.Groups[1].Value}else{$match.Groups[2].Value}
        if([IO.Path]::IsPathRooted($directory) -and [string]::Equals([IO.Path]::GetFullPath($directory).TrimEnd('\'),$installGame,[StringComparison]::OrdinalIgnoreCase)){return $true}
    }
    return $false
}
try {
    if((Get-Item -LiteralPath $installMods).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked mod directory refused.'}
    foreach($file in $request.files){
        if($file.name -notin @('interactivestuff-0.7.0-26.2.jar','sound_physics_perfected-1.17.5+26.1-fabric-racefix.jar')){throw 'Unexpected patch target.'}
        $source=[IO.Path]::GetFullPath($file.source)
        if(![string]::Equals([IO.Path]::GetDirectoryName($source),$sourceParent,[StringComparison]::OrdinalIgnoreCase)){throw 'Patch source outside reviewed staging folder.'}
        if((Get-ReviewedHash $source) -ne $file.sha256){throw 'Staged patch hash mismatch.'}
    }
    Write-InstallLog 'Waiting for this Minecraft instance to close; no process is stopped.'
    $deadline=(Get-Date).AddMinutes($MaximumMinutes)
    while(Game-IsRunning){if((Get-Date) -gt $deadline){Write-InstallLog 'Expired; reviewed patches remain staged.';exit 2};Start-Sleep -Seconds 5}
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    foreach($file in $request.files){
        if(Game-IsRunning){throw 'Minecraft restarted; remaining patch deferred.'}
        $target=[IO.Path]::GetFullPath((Join-Path $installMods $file.name))
        if(![string]::Equals([IO.Path]::GetDirectoryName($target),$installMods,[StringComparison]::OrdinalIgnoreCase)){throw 'Patch target outside named mod directory.'}
        if((Get-Item -LiteralPath $target).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked mod file refused.'}
        $old=Get-ReviewedHash $target
        if($old -eq $file.sha256){Write-InstallLog ('Already installed: '+$file.name);continue}
        if($old -ne $file.originalSha256){throw ('Original mod changed independently: '+$file.name)}
        $backup=Join-Path $backupRoot $file.name;Copy-Item -LiteralPath $target -Destination $backup
        if((Get-ReviewedHash $backup) -ne $old){throw 'Original backup verification failed.'}
        $temporary=Join-Path $installMods ('.reviewed-patch-'+[guid]::NewGuid().ToString('N')+'.tmp')
        try {
            Copy-Item -LiteralPath $file.source -Destination $temporary
            if((Get-ReviewedHash $temporary) -ne $file.sha256){throw 'Copied patch verification failed.'}
            if(Game-IsRunning){throw 'Minecraft restarted before replacement.'}
            [IO.File]::Replace($temporary,$target,[System.Management.Automation.Language.NullString]::Value)
            if((Get-ReviewedHash $target) -ne $file.sha256){throw 'Installed patch verification failed.'}
            Write-InstallLog ('Installed with verified original backup: '+$file.name)
        }finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}}
    }
    Write-InstallLog 'COMPLETE: both reviewed patches persist for the next launch.'
}catch{Write-InstallLog ('STOPPED: '+$_.Exception.Message);exit 1}
