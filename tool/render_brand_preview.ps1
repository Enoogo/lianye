$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$workspace = Split-Path $PSScriptRoot -Parent
$geometry = Get-Content -Raw -LiteralPath (Join-Path $workspace 'design/brand/lianye/refined-01/geometry.json') | ConvertFrom-Json
$palette = (Get-Content -Raw -LiteralPath (Join-Path $workspace 'design/tokens/tokens.json') | ConvertFrom-Json).brand
$outputDirectory = Join-Path $workspace 'build/brand-palette-alignment/previews'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

function Get-BrandPath($segments) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $currentX = [single]0
    $currentY = [single]0
    foreach ($segment in $segments) {
        switch ($segment[0]) {
            'M' { $path.StartFigure(); $currentX = [single]$segment[1]; $currentY = [single]$segment[2] }
            'L' {
                $path.AddLine($currentX, $currentY, [single]$segment[1], [single]$segment[2])
                $currentX = [single]$segment[1]; $currentY = [single]$segment[2]
            }
            'C' {
                $path.AddBezier($currentX, $currentY, [single]$segment[1], [single]$segment[2],
                    [single]$segment[3], [single]$segment[4], [single]$segment[5], [single]$segment[6])
                $currentX = [single]$segment[5]; $currentY = [single]$segment[6]
            }
            'Z' { $path.CloseFigure() }
            default { throw "Unsupported path command: $($segment[0])" }
        }
    }
    return ,$path
}

function Save-BrandPreview($filename, [int]$size, $mask, $background, $foreground, $accent) {
    $highSize = $size * 4
    $bitmap = [System.Drawing.Bitmap]::new($highSize, $highSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $result = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $resultGraphics = [System.Drawing.Graphics]::FromImage($result)
    $clip = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($background))
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        if ($mask -eq 'circle') {
            $clip.AddEllipse(0, 0, $highSize, $highSize)
        } else {
            $diameter = [single]($highSize * .5)
            $clip.AddArc(0, 0, $diameter, $diameter, 180, 90)
            $clip.AddArc($highSize - $diameter, 0, $diameter, $diameter, 270, 90)
            $clip.AddArc($highSize - $diameter, $highSize - $diameter, $diameter, $diameter, 0, 90)
            $clip.AddArc(0, $highSize - $diameter, $diameter, $diameter, 90, 90)
            $clip.CloseFigure()
        }
        $graphics.SetClip($clip)
        $graphics.FillPath($brush, $clip)
        # Adaptive launchers display the central 72-unit viewport of the 108-unit layers.
        $scale = [single]($highSize / 72 * $geometry.adaptive.scale)
        $graphics.TranslateTransform([single]($highSize / 2 - 50 * $scale), [single]($highSize / 2 - 53 * $scale))
        $graphics.ScaleTransform($scale, $scale)
        foreach ($item in $geometry.optical.paths) {
            $color = if ($item.role -eq 'page') { $accent } else { $foreground }
            $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml($color), [single]$geometry.optical.stroke)
            $path = Get-BrandPath $item.segments
            try {
                $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
                $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat
                $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
                $graphics.DrawPath($pen, $path)
            } finally { $path.Dispose(); $pen.Dispose() }
        }
        $resultGraphics.Clear([System.Drawing.Color]::Transparent)
        $resultGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $resultGraphics.DrawImage($bitmap, 0, 0, $size, $size)
        $result.Save((Join-Path $outputDirectory $filename), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $brush.Dispose(); $clip.Dispose(); $resultGraphics.Dispose(); $result.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
    }
}

foreach ($mask in @('rounded', 'circle')) {
    Save-BrandPreview "launcher-$mask-512.png" 512 $mask $palette.paper $palette.ink $palette.accent
}
foreach ($size in @(24, 32, 48, 64)) {
    Save-BrandPreview "launcher-$size.png" $size 'rounded' $palette.paper $palette.ink $palette.accent
}
Save-BrandPreview 'reverse-reference-512.png' 512 'rounded' $palette.darkBackground $palette.paper $palette.darkAccent
Write-Output 'Rendered 7 brand previews; desktop masks are simulations, not device screenshots.'
