param([string]$Path, [int]$MaxRows = 80)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$work = Join-Path $env:TEMP ('xlsxread-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Force -Path $work
[System.IO.Compression.ZipFile]::ExtractToDirectory($Path, $work)

$shared = @()
$sharedPath = Join-Path $work 'xl\sharedStrings.xml'
if (Test-Path $sharedPath) {
  [xml]$sx = Get-Content $sharedPath -Encoding UTF8
  foreach ($si in $sx.sst.si) {
    if ($si.t -is [string]) { $shared += $si.t }
    elseif ($si.t.'#text') { $shared += $si.t.'#text' }
    else {
      $txt = ''
      foreach ($r in $si.r) { if ($r.t -is [string]) { $txt += $r.t } elseif ($r.t.'#text') { $txt += $r.t.'#text' } }
      $shared += $txt
    }
  }
}

Get-ChildItem (Join-Path $work 'xl\worksheets') -Filter *.xml | ForEach-Object {
  $sheetFile = $_.Name
  Write-Output ("==== 工作表: " + $sheetFile)
  [xml]$sheet = Get-Content $_.FullName -Encoding UTF8
  $rowNo = 0
  foreach ($row in $sheet.worksheet.sheetData.row) {
    $rowNo++
    if ($rowNo -gt $MaxRows) { break }
    $cells = @()
    foreach ($c in $row.c) {
      $ref = $c.r
      $val = ''
      if ($c.t -eq 's') {
        $idx = [int]$c.v
        if ($idx -lt $shared.Count) { $val = $shared[$idx] }
      } elseif ($c.t -eq 'inlineStr') {
        $val = $c.is.t
      } else {
        $val = $c.v
      }
      if ($val -ne $null -and "$val".Trim() -ne '') { $cells += ($ref + '=' + ("$val" -replace '\s+', ' ').Trim()) }
    }
    if ($cells.Count -gt 0) { Write-Output ("r$rowNo | " + ($cells -join ' | ')) }
  }
}
Remove-Item $work -Recurse -Force
