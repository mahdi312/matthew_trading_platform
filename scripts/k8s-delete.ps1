# Remove MTP Kubernetes resources.
. (Join-Path $PSScriptRoot "_lib/common.ps1")

$Root = Get-MtpRoot
Set-Location $Root
Test-MtpCommand kubectl

Write-Host "Deleting MTP namespace (all resources)..."
kubectl delete namespace mtp --ignore-not-found
Assert-MtpLastExitCode "kubectl delete namespace mtp"
Write-Host "Done."
