# Android SKK Bulk Test Input Script
param (
    [Parameter(Mandatory=$true, Position=0)]
    [string]$FilePath,          # テスト文字列が書かれたファイルのパス

    [int]$Delay = 30,           # 1文字ごとの待機時間(ms)
    [int]$LineDelay = 500      # 行ごとの待機時間(ms)
)

if (-not (Test-Path $FilePath)) {
    Write-Error "File not found: $FilePath"
    return
}

# adb shell のベース引数を構築
$adbBase = @("shell", "input")

# 純粋な文字送信を行う関数
function Send-Raw-Char {
    param([char]$c)
    $charStr = $c.ToString()
    # Android 側のシェルで記号 (< > ; & | * 等) が解釈されるのを防ぐためシングルクォートで囲む
    if ($charStr -eq "'") {
        & adb $adbBase text "'""'" > $null 2>&1
    } else {
        & adb $adbBase text "'$charStr'" > $null 2>&1
    }
}

# Ctrl-J を送信する関数
function Send-Control-J {
    # 12288 = META_CTRL_ON | META_CTRL_LEFT_ON, 38 = KEYCODE_J
    & adb $adbBase keyevent --meta 12288 38 > $null 2>&1
}

# Enter を送信する関数
function Send-Enter {
    & adb $adbBase keyevent 66 > $null 2>&1
}

# 1行ずつ読み込む
$lines = Get-Content $FilePath

foreach ($line in $lines) {
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    if ($line.StartsWith("#")) {
        continue
    }

    # 行末のマーカー '$' を検知（確定+改行）
    $forceNewline = $false
    if ($line.EndsWith("$")) {
        $line = $line.Substring(0, $line.Length - 1)
        $forceNewline = $true
    }

    Write-Host "--- Processing Line: $line ---" -ForegroundColor Yellow

    $chars = $line.ToCharArray()
    for ($i = 0; $i -lt $chars.Length; $i++) {
        $c = $chars[$i]

        if ($c -eq "^") {
            # '^^' ならばリテラルの '^' を入力
            if ($i + 1 -lt $chars.Length -and $chars[$i+1] -eq "^") {
                Send-Raw-Char -c "^"
                $i++
            } else {
                # 単一の '^' ならば Ctrl-J を送信
                Send-Control-J
            }
        } elseif ($c -eq "\") {
            # '\n' ならば Enter を送信
            if ($i + 1 -lt $chars.Length -and $chars[$i+1] -eq "n") {
                Send-Enter
                $i++
            } elseif ($i + 1 -lt $chars.Length -and $chars[$i+1] -eq "\") {
                # '\\' ならばリテラルの '\' を入力
                Send-Raw-Char -c "\"
                $i++
            } else {
                Send-Raw-Char -c "\"
            }
        } else {
            Send-Raw-Char -c $c
        }
        Start-Sleep -m $Delay
    }

    # 1回目の Enter (各行の末尾での確定用)
    Send-Enter

    # マーカー '$' があった場合は 2回目の Enter (改行用) を送る
    if ($forceNewline) {
        Start-Sleep -m 100
        Send-Enter
    }

    Write-Host "Line completed." -ForegroundColor Gray
    Write-Host ""
    Start-Sleep -m $LineDelay
}

Write-Host "`nAll lines processed!" -ForegroundColor Green
