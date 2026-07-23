$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "showcase\app\image-studio\page.tsx"
$content = [System.IO.File]::ReadAllText($path)

if ($content.Contains("const copy = {")) {
    Write-Host "Chinese Image Studio labels are already present."
    exit 0
}

$marker = 'const storageKey = "jenda-image-studio-session";'
$copy = @'
const copy = {
  sourceImage: "\u539f\u59cb\u56fe\u7247",
  optionalMask: "\u53ef\u9009\u906e\u7f69\u56fe",
  uploadSource: "\u4e0a\u4f20\u9700\u8981\u7f16\u8f91\u7684\u56fe\u7247",
  whiteEdit: "\u767d\u8272\u533a\u57df\u7f16\u8f91\uff0c\u9ed1\u8272\u533a\u57df\u4fdd\u7559",
  titleLead: "\u7cbe\u4fee\u6bcf\u4e00\u4e2a\u50cf\u7d20\u3002",
  titleEm: "\u4fdd\u7559\u6bcf\u4e00\u4efd\u8bc1\u636e\u3002",
  intro: "\u539f\u56fe\u3001\u906e\u7f69\u5f15\u5bfc\u7f16\u8f91\u3001SSE \u8fdb\u5ea6\u3001COS \u4ea7\u7269\u53ca\u53ef\u8ffd\u6eaf\u4f1a\u8bdd\u8bb0\u5f55\u3002",
  activeSession: "\u5f53\u524d\u4f1a\u8bdd",
  refreshAssets: "\u5237\u65b0\u8d44\u4ea7",
  inputs: "\u8f93\u5165\u56fe\u7247",
  maskProtocol: "\u906e\u7f69\u8bf4\u660e",
  maskDetail: "\u4e0a\u4f20\u9ed1\u767d\u906e\u7f69\u56fe\u4f5c\u4e3a\u7b2c\u4e8c\u5f20\u8f93\u5165\u56fe\u3002\u767d\u8272\u533a\u57df\u5141\u8bb8\u4fee\u6539\uff0c\u9ed1\u8272\u533a\u57df\u5e94\u4fdd\u6301\u4e0d\u53d8\u3002",
  editDirection: "\u7f16\u8f91\u6307\u4ee4",
  runEdit: "\u5f00\u59cb\u56fe\u50cf\u7f16\u8f91",
  compareCanvas: "\u5bf9\u6bd4\u753b\u5e03",
  beforeAfter: "\u7f16\u8f91\u524d\u540e\u5bf9\u6bd4",
  readyEdit: "\u7b49\u5f85\u7f16\u8f91",
  archived: "\u5df2\u5f52\u6863\u81f3 COS",
  startSource: "\u5148\u4e0a\u4f20\u539f\u59cb\u56fe\u7247",
  startSourceDetail: "\u4e0a\u4f20\u4eba\u50cf\u3001\u4ea7\u54c1\u56fe\u6216\u573a\u666f\u56fe\u3002\u53ea\u6709\u9700\u8981\u5c40\u90e8\u9650\u5b9a\u4fee\u6539\u65f6\u624d\u9700\u8981\u4e0a\u4f20\u906e\u7f69\u56fe\u3002",
  sourceReady: "\u539f\u59cb\u56fe\u5df2\u51c6\u5907\u597d\uff0c\u8bf7\u8f93\u5165\u7f16\u8f91\u6307\u4ee4\u3002",
  editing: "\u667a\u80fd\u4f53\u6b63\u5728\u7f16\u8f91\u56fe\u7247\u3002",
  editingDetail: "\u89c4\u5212\u3001\u5de5\u5177\u8c03\u7528\u548c COS \u5f52\u6863\u8fdb\u5ea6\u4f1a\u663e\u793a\u5728\u6267\u884c\u8f68\u8ff9\u4e2d\u3002",
  original: "\u539f\u56fe",
  edited: "\u7f16\u8f91\u540e",
  liveTrace: "\u5b9e\u65f6\u6267\u884c\u8f68\u8ff9",
  noEvents: "\u5c1a\u65e0\u5de5\u5177\u4e8b\u4ef6\u3002",
  cosAssets: "COS \u8d44\u4ea7",
  noAssets: "\u751f\u6210\u7684 COS \u56fe\u7247\u4f1a\u663e\u793a\u5728\u8fd9\u91cc\u3002",
  inspector: "\u8d44\u4ea7\u8be6\u60c5",
  preview: "\u9884\u89c8",
  download: "\u4e0b\u8f7d",
  storage: "\u5b58\u50a8",
  asset: "\u8d44\u4ea7 ID",
  resolution: "\u5206\u8fa8\u7387",
  run: "\u8fd0\u884c ID",
  loading: "\u52a0\u8f7d\u4e2d",
  defaultInstruction: "\u5c06\u80cc\u666f\u66ff\u6362\u4e3a\u96e8\u591c\u8d5b\u535a\u670b\u514b\u8857\u9053\uff0c\u4fdd\u6301\u4eba\u7269\u4e3b\u4f53\u4e0d\u53d8\u3002",
};
'@

if ($content.IndexOf($marker) -lt 0) {
    throw "Expected Image Studio marker was not found. No files were changed."
}
$updated = $content.Replace($marker, $marker + [Environment]::NewLine + [Environment]::NewLine + $copy)

$replacements = [ordered]@{
  'useState("Source image")' = 'useState(copy.sourceImage)'
  'useState("Optional mask")' = 'useState(copy.optionalMask)'
  'useState("Replace the background with a rain-soaked cyberpunk street. Keep the subject unchanged.")' = 'useState(copy.defaultInstruction)'
  '<h1>Direct the pixel. <em>Keep the proof.</em></h1>' = '<h1>{copy.titleLead} <em>{copy.titleEm}</em></h1>'
  '<span>Reference images, mask-guided edits, SSE progress, COS outputs, and an inspectable session trail.</span>' = '<span>{copy.intro}</span>'
  '<span>ACTIVE SESSION</span>' = '<span>{copy.activeSession}</span>'
  '>Refresh assets</Button>' = '>{copy.refreshAssets}</Button>'
  '<span>INPUTS</span>' = '<span>{copy.inputs}</span>'
  '<b>01 / Source image</b>' = '<b>01 / {copy.sourceImage}</b>'
  '<small>{source ? sourceName : "Upload the image to transform"}</small>' = '<small>{source ? sourceName : copy.uploadSource}</small>'
  '<b>02 / Optional mask</b>' = '<b>02 / {copy.optionalMask}</b>'
  '<small>{mask ? maskName : "White edits, black preserves"}</small>' = '<small>{mask ? maskName : copy.whiteEdit}</small>'
  '<span>MASK PROTOCOL</span><p>Attach a black and white mask as the second image. The model receives explicit white-edit / black-preserve guidance.</p>' = '<span>{copy.maskProtocol}</span><p>{copy.maskDetail}</p>'
  '<label htmlFor="edit-instruction">EDIT DIRECTION</label>' = '<label htmlFor="edit-instruction">{copy.editDirection}</label>'
  '>Run image edit</Button>' = '>{copy.runEdit}</Button>'
  '<span>COMPARE CANVAS</span><h2>{outputUrl ? "Before / after" : "Ready for an edit"}</h2>' = '<span>{copy.compareCanvas}</span><h2>{outputUrl ? copy.beforeAfter : copy.readyEdit}</h2>'
  '<Tag color="green">COS archived output</Tag>' = '<Tag color="green">{copy.archived}</Tag>'
  '<h2>Start with a source image.</h2><p>Upload a portrait, product image, or scene. Add a mask only when you need a constrained local edit.</p>' = '<h2>{copy.startSource}</h2><p>{copy.startSourceDetail}</p>'
  '<h2>{running ? "The agent is editing the image." : "Source ready. Write an edit direction."}</h2>' = '<h2>{running ? copy.editing : copy.sourceReady}</h2>'
  '<p>{running ? "Planning, tool calls, and COS archival will appear in the run trace." : "Use background replacement, style conversion, object removal, or a mask-directed local edit."}</p>' = '<p>{running ? copy.editingDetail : copy.startSourceDetail}</p>'
  '<span className={styles.beforeLabel}>BEFORE</span><span className={styles.afterLabel}>AFTER</span>' = '<span className={styles.beforeLabel}>{copy.original}</span><span className={styles.afterLabel}>{copy.edited}</span>'
  '<span>Original</span><Slider' = '<span>{copy.original}</span><Slider'
  '<span>Edited</span></div>' = '<span>{copy.edited}</span></div>'
  '<span>LIVE TRACE</span>' = '<span>{copy.liveTrace}</span>'
  '<p>No tool events yet.</p>' = '<p>{copy.noEvents}</p>'
  '<span>COS ASSETS</span>' = '<span>{copy.cosAssets}</span>'
  '<p className={styles.muted}>Generated COS images will appear here.</p>' = '<p className={styles.muted}>{copy.noAssets}</p>'
  '<span>ASSET INSPECTOR</span>' = '<span>{copy.inspector}</span>'
  '<dl><dt>Storage</dt><dd>COS signed URL</dd><dt>Asset</dt><dd>{detail.assetId}</dd><dt>Resolution</dt><dd>{dimensions[detail.imageUrl] ? `${dimensions[detail.imageUrl].width} x ${dimensions[detail.imageUrl].height}` : "Loading"}</dd><dt>Run</dt><dd>{detail.runId}</dd></dl>' = '<dl><dt>{copy.storage}</dt><dd>COS signed URL</dd><dt>{copy.asset}</dt><dd>{detail.assetId}</dd><dt>{copy.resolution}</dt><dd>{dimensions[detail.imageUrl] ? `${dimensions[detail.imageUrl].width} x ${dimensions[detail.imageUrl].height}` : copy.loading}</dd><dt>{copy.run}</dt><dd>{detail.runId}</dd></dl>'
  '>Preview</Button>' = '>{copy.preview}</Button>'
  '>Download</Button>' = '>{copy.download}</Button>'
}

foreach ($entry in $replacements.GetEnumerator()) {
    if ($updated.IndexOf($entry.Key) -lt 0) {
        throw "Expected label block was not found: $($entry.Key). No files were changed."
    }
    $updated = $updated.Replace($entry.Key, $entry.Value)
}

[System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path with ASCII-safe Chinese labels."
Write-Host "Step 24 Chinese Image Studio labels completed. Restart the frontend."
