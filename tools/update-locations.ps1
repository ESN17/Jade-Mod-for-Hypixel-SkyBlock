param([string]$Html, [string]$Category)
$ErrorActionPreference = 'Stop'
$base = 'https://hypixelskyblock.minecraft.wiki'
$page = if ($Html) { Get-Content -LiteralPath $Html -Raw -Encoding utf8 } else { (Invoke-WebRequest "$base/w/Locations").Content }
$names = [ordered]@{}
function Plain([string]$htmlText) {
    [Net.WebUtility]::HtmlDecode(($htmlText -replace '<[^>]+>', '')) -replace '[\uE000-\uF8FF]', '' -replace '\s+', ' '
}
function Add([string]$title,[string]$label,[string]$region) {
    $title=$title.Trim()
    $label=$label.Trim()
    if (!$title -or !$label) { return }
    if (!$names.Contains($title) -or ($region -and !$names[$title].region)) {
        $names[$title]=[ordered]@{id=$title; name=$label; region=$region}
    }
}
$next = ''
do {
    $json = if ($Category) { Get-Content -LiteralPath $Category -Raw -Encoding utf8 } else {
        (Invoke-WebRequest "$base/api.php?action=query&list=categorymembers&cmtitle=Category:Locations&cmnamespace=0&cmlimit=500&format=json$next").Content
    }
    $data=$json | ConvertFrom-Json
    foreach ($m in $data.query.categorymembers) { if ($m.ns -eq 0) { Add $m.title ($m.title -replace ' \([^)]+\)$','') '' } }
    $next=if ($data.continue.cmcontinue) { '&cmcontinue='+[uri]::EscapeDataString($data.continue.cmcontinue) } else { '' }
    if ($Category -and $next) { throw 'Incomplete offline category snapshot' }
} while ($next)
function Read-Links($fragment,$region,$all) {
    foreach ($m in [regex]::Matches($fragment,'(?s)<a href="/w/[^"#?]+" title="([^"]+)"[^>]*>(.*?)</a>')) {
        if ($all -or $m.Groups[2].Value -match '&#xe(?:067|020);') {
            Add ([Net.WebUtility]::HtmlDecode($m.Groups[1].Value)) (Plain $m.Groups[2].Value) $region
        }
    }
}
Read-Links ($page.Substring(0,$page.IndexOf('id="Navigation"'))) '' $false
$nav=$page.Substring($page.IndexOf('id="Navigation"'))
foreach ($section in [regex]::Matches($nav,'(?s)<th class="navbox-header[^"]*"[^>]*>(.*?)</th>(.*?)</table>')) {
    $region=(Plain $section.Groups[1].Value).Trim()
    Read-Links $section.Groups[1].Value '' $true
    Read-Links $section.Groups[2].Value $region $true
}
[void]$names.Remove('Rift Time') # duration stat sharing the Rift location icon
if ($names.Count -lt 300 -or !$names.Contains('Wizard Tower (Rift)') -or !$names.Contains('Miria''s Hut')) { throw 'Unexpected wiki index format' }
$dest=Join-Path $PSScriptRoot '../src/main/resources/jade-locations.json'
[IO.File]::WriteAllText($dest,(ConvertTo-Json -InputObject @($names.Values | Sort-Object { $_.id }) -Depth 4))
Write-Output "Indexed $($names.Count) Wiki entries"
