# Установка/возврат загрузочной анимации SlimHome на приставку
param([switch]$Restore)

$ErrorActionPreference = 'Continue'
$env:TEMP = 'C:\Users\79157\Desktop\slimbox\tmp'
$env:TMP  = $env:TEMP
$adb = 'C:\Users\79157\Desktop\slimbox\platform-tools\adb.exe'
$d   = '192.168.0.48:5555'
$new = 'C:\Users\79157\Desktop\slimbox\bootanim\bootanimation_sig.zip'
$old = 'C:\Users\79157\Desktop\slimbox\bootanim\stock_bootanimation.zip'

& $adb connect $d 2>&1 | Out-Null
$devs = (& $adb devices) -join "`n"
if($devs -notmatch ([regex]::Escape($d) + '\s+device\b')) {
    Write-Host 'приставка недоступна (проверь VPN/сеть)'
    exit 1
}

& $adb -s $d root 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $adb connect $d 2>&1 | Out-Null
& $adb -s $d remount 2>&1 | Out-Null

$test = (& $adb -s $d shell "touch /system/media/_w 2>/dev/null && echo OK && rm -f /system/media/_w") -join ''
if($test -notmatch 'OK') {
    Write-Host 'раздел /system всё ещё только для чтения — нужен remount'
    exit 2
}

$src = if($Restore) { $old } else { $new }
$label = if($Restore) { 'стоковая' } else { 'SlimHome' }
& $adb -s $d push $src /sdcard/bootanim.pkg 2>&1 | Select-Object -Last 1
& $adb -s $d shell "cp /sdcard/bootanim.pkg /system/media/bootanimation.zip; chmod 644 /system/media/bootanimation.zip; chown root:root /system/media/bootanimation.zip; restorecon /system/media/bootanimation.zip 2>/dev/null; ls -l /system/media/bootanimation.zip" 2>&1
Write-Host "установлена $label анимация"
& $adb -s $d shell "rm -f /sdcard/bootanim.pkg"