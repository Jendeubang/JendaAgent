$pagePath = Join-Path $PSScriptRoot "..\showcase\app\zh\page.tsx"
$cssPath = Join-Path $PSScriptRoot "..\showcase\app\zh\page.module.css"
$page = [System.IO.File]::ReadAllText($pagePath)
$css = [System.IO.File]::ReadAllText($cssPath)

$importNeedle = 'import { CrispixHeader } from "../../components/CrispixHeader";'
$importReplacement = @'
import { CrispixHeader } from "../../components/CrispixHeader";
import { useEffect, useState } from "react";
'@.Trim()
if ($page.IndexOf($importNeedle, [System.StringComparison]::Ordinal) -lt 0) { throw "Header import was not found." }
$page = $page.Replace($importNeedle, $importReplacement)

$constantNeedle = 'export default function ZhHomePage() {'
$constantReplacement = @'
const stageDetails = [
  "\u5206\u6790\u7528\u6237\u6307\u4ee4\uff0c\u63d0\u53d6\u4e3b\u9898\u3001\u98ce\u683c\u548c\u8272\u8c03\u8981\u6c42",
  "\u4ece\u5de5\u5177\u94fe\u4e0e\u53c2\u8003\u8d44\u4ea7\u4e2d\u9009\u62e9\u53ef\u7528\u7d20\u6750",
  "\u8c03\u7528\u56fe\u50cf\u751f\u6210\u6a21\u578b\u521b\u4f5c\u4e3b\u89c6\u89c9",
  "\u8c03\u6574\u914d\u8272\u3001\u5e03\u5c40\u4e0e\u7ec6\u8282\uff0c\u4ea4\u4ed8\u6210\u54c1"
];

export default function ZhHomePage() {
  const [tick, setTick] = useState(0);
  const activeStage = tick % copy.stages.length;
  const cycle = Math.floor(tick / copy.stages.length) + 1;

  useEffect(() => {
    const timer = window.setInterval(() => setTick((value) => value + 1), 2200);
    return () => window.clearInterval(timer);
  }, []);
'@.Trim()
if ($page.IndexOf($constantNeedle, [System.StringComparison]::Ordinal) -lt 0) { throw "Page component declaration was not found." }
$page = $page.Replace($constantNeedle, $constantReplacement)

$demoPattern = '<div className=\{styles\.agentDemo\}>.*?</div>\r?\n    </section>\r?\n    <section className=\{styles\.section\}>'
$demoReplacement = @'
<div className={styles.agentDemo} aria-live="polite">
        <div className={styles.demoTop}><span><i className={styles.pulse} /> AUTOPLAN-AGENT-CORE</span><small>{String(cycle).padStart(2, "0")} / AUTO</small></div>
        <div className={styles.quote}><span>U</span>{copy.demoPrompt}</div>
        <div className={styles.liveDetail}>{stageDetails[activeStage]}</div>
        <div className={styles.demoSteps}>{copy.stages.map((item, index) => {
          const complete = index < activeStage;
          const active = index === activeStage;
          const status = complete ? "Complete" : active ? "Running" : "Waiting";
          return <div className={`${styles.demoStep} ${complete ? styles.completeStep : ""} ${active ? styles.activeStep : ""}`} key={item}>
            <span>{complete ? <CheckCircleFilled /> : <i />}</span><b>{item}</b><small>{status}</small>
          </div>;
        })}</div>
        <div className={`${styles.demoOutput} ${activeStage === copy.stages.length - 1 ? styles.outputActive : ""}`}>
          <img src="/crispix/tool-01.webp" alt="Jenda generated result" /><span><b>jenda_poster_final.png</b><small>{activeStage === copy.stages.length - 1 ? "Generating preview..." : "Waiting for final output"}</small><i /></span>
        </div>
      </div>
    </section>
    <section className={styles.section}>
'@
$updatedPage = [System.Text.RegularExpressions.Regex]::Replace($page, $demoPattern, $demoReplacement, [System.Text.RegularExpressions.RegexOptions]::Singleline)
if ($updatedPage -eq $page) { throw "Hero demo block was not found." }
$page = $updatedPage

$cssAddition = @'

.demoTop>span{display:flex;align-items:center;gap:7px}.pulse{display:inline-block;width:7px;height:7px;background:#3a86ff;border-radius:50%;box-shadow:0 0 0 0 #3a86ff8c;animation:agentPulse 1.5s infinite}.quote{display:flex;align-items:flex-start;gap:9px}.quote>span{display:grid;place-items:center;flex:0 0 21px;width:21px;height:21px;color:#fff;background:#3a86ff;border-radius:50%;font-size:10px;font-weight:800}.liveDetail{min-height:34px;margin:-4px 0 7px;color:#94a3b8;font-size:10px;line-height:1.65}.demoSteps{position:relative}.demoStep{transition:opacity .35s ease,transform .35s ease,color .35s ease}.demoStep>span{display:grid;place-items:center;width:16px;height:16px}.demoStep.completeStep{opacity:.58}.demoStep.activeStep{margin:3px -8px;padding:10px 8px;color:#164fa6;background:#eaf3ff;border-color:transparent;border-radius:7px;transform:translateX(2px);opacity:1}.demoStep.activeStep b{font-weight:800}.demoStep.activeStep small{color:#3a86ff;font-weight:700}.demoStep.activeStep span i{border-color:#3a86ff;animation:spin 1s linear infinite;border-top-color:transparent}.demoOutput{position:relative;overflow:hidden;transition:background .35s ease,box-shadow .35s ease}.demoOutput>span{flex:1}.demoOutput i{display:block;width:100%;height:2px;margin-top:8px;background:#dbe7f4;border-radius:99px}.demoOutput i:after{display:block;width:18%;height:100%;background:#3a86ff;border-radius:99px;content:"";transition:width .45s ease}.demoOutput.outputActive{padding:9px;background:#edf6ff;border-radius:8px;box-shadow:inset 0 0 0 1px #c9dff8}.demoOutput.outputActive i:after{width:76%;animation:progressSweep 1.2s ease-in-out infinite alternate}@keyframes agentPulse{70%{box-shadow:0 0 0 8px transparent}}@keyframes spin{to{transform:rotate(360deg)}}@keyframes progressSweep{from{width:40%}to{width:87%}}
'@
if ($css.IndexOf('.demoTop{display:flex', [System.StringComparison]::Ordinal) -lt 0) { throw "Demo CSS anchor was not found." }
$css += $cssAddition

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($pagePath, $page, $utf8)
[System.IO.File]::WriteAllText($cssPath, $css, $utf8)
Write-Output "Applied Step 37 auto-plan homepage demo."
