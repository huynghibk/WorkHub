# Push WorkHub len GitHub bang Personal Access Token.
# 1) Tao token: https://github.com/settings/tokens  (classic, tick "repo")
# 2) Chay: .\push-to-github.ps1
# 3) Dan token khi duoc hoi.

$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot
$env:Path = "C:\Program Files\GitHub CLI;$env:Path"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "Chua co GitHub CLI. Cai: winget install GitHub.cli"
    exit 1
}

function Test-GhAuth {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    $null = gh auth status 2>&1
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $old
    return $ok
}

if (-not (Test-GhAuth)) {
    Write-Host ""
    Write-Host "Chua login gh. Dung TOKEN (trinh duyet van tao duoc)."
    Write-Host "Tao tai: https://github.com/settings/tokens"
    Write-Host "  Classic token -> tick 'repo' -> Generate -> Copy"
    Write-Host ""
    $secure = Read-Host "Dan Personal Access Token" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
    if ([string]::IsNullOrWhiteSpace($token)) {
        Write-Host "Khong co token — dung."
        exit 1
    }
    $ErrorActionPreference = 'Continue'
    $token | gh auth login --hostname github.com --with-token 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Login that bai. Kiem tra token / mang."
        exit 1
    }
    gh auth setup-git 2>&1 | Out-Null
}

if (-not (Test-GhAuth)) {
    Write-Host "Van chua login duoc."
    exit 1
}

$user = (gh api user -q .login 2>$null)
if ([string]::IsNullOrWhiteSpace($user)) {
    Write-Host "Khong lay duoc username GitHub (mang/proxy?)."
    exit 1
}
Write-Host "Dang nhap voi: $user"

$repoName = "WorkHub"
$full = "$user/$repoName"

$ErrorActionPreference = 'Continue'
$null = gh repo view $full 2>&1
$exists = ($LASTEXITCODE -eq 0)

if (-not $exists) {
    Write-Host "Tao repo public $full va push..."
    gh repo create $repoName --public --source=. --remote=origin --push --description "WorkHub Android + Ktor API"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Tao/push that bai."
        exit 1
    }
} else {
    Write-Host "Repo da ton tai. Push main..."
    $url = "https://github.com/$full.git"
    git remote remove origin 2>$null
    git remote add origin $url
    git push -u origin main
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Push that bai."
        exit 1
    }
}

Write-Host ""
Write-Host "OK: https://github.com/$full"
try { gh repo view $full --web } catch { }
