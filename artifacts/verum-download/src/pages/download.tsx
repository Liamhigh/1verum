import { useEffect, useRef, useState } from 'react';
import { Download, Copy, Check, AlertTriangle, FileDown, ShieldCheck, ExternalLink } from 'lucide-react';

const APK = '/api/releases/verum-omnis-5.2.8.apk';
const SHA_URL = '/api/releases/verum-omnis-5.2.8.apk.sha256';
const SHA = '27e9df0693a11739aedc12924cdb376ce76b08a2245473256b75aeecca348cb7';

const facts: [string, string][] = [
  ['Version', '5.2.8 (versionCode 1)'],
  ['Package', 'com.verumomnis.forensic'],
  ['File size', '93.6 MB · 89.2 MiB · 93,558,060 bytes'],
  ['Requires', 'Android 10 or newer (API 29+)'],
  ['Processor', 'ARM64 only (arm64-v8a)'],
];

const steps = [
  { t: 'Download on your phone', d: 'Tap the download button above. The file is about 93.6 MB, so Wi-Fi is a good idea.' },
  { t: 'Allow your browser to install apps', d: 'When Android asks, open Settings and turn on "Allow from this source" for the browser you used (for example Chrome). Android only asks once per browser.' },
  { t: 'Open the APK', d: 'Open the file from the download notification or your Downloads folder, then tap Install.' },
  { t: 'Turn the permission off again', d: 'Optional but sensible: go back to Settings > Apps > Special app access > Install unknown apps and switch your browser off.' },
];

type CopyState = 'idle' | 'ok' | 'fail';

async function copyText(text: string) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.setAttribute('readonly', '');
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  const ok = document.execCommand('copy');
  document.body.removeChild(ta);
  if (!ok) throw new Error('copy failed');
}

export default function DownloadPage() {
  const [copy, setCopy] = useState<CopyState>('idle');
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const onCopy = async () => {
    try { await copyText(SHA); setCopy('ok'); } catch { setCopy('fail'); }
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setCopy('idle'), 4000);
  };

  return (
    <div className="vo-page relative min-h-[100dvh] overflow-x-hidden">
      <div className="dl-bg" aria-hidden />
      <div className="dl-grid" aria-hidden />

      <main className="relative z-10 mx-auto w-full max-w-[640px] px-5 pb-10 pt-12 sm:pt-20">
        <header className="text-center">
          <div className="dl-rise relative mx-auto mb-8 h-28 w-28" style={{ animationDelay: '0ms' }}>
            <svg className="dl-orbit absolute -inset-4" viewBox="0 0 100 100" aria-hidden>
              <circle cx="50" cy="50" r="48" fill="none" stroke="rgba(74,126,199,.45)" strokeWidth=".4" strokeDasharray="1 3" />
              <circle cx="50" cy="2" r="1.2" fill="#D4A843" />
            </svg>
            <img src={`${import.meta.env.BASE_URL}brand/vo-badge.png`} alt="Verum Omnis logo" width={112} height={112}
              className="h-28 w-28 rounded-full" style={{ filter: 'drop-shadow(0 0 40px rgba(74,126,199,.25))' }} data-testid="img-logo" />
          </div>
          <p className="vo-eyebrow dl-rise" style={{ animationDelay: '80ms' }}>Android release · v5.2.8</p>
          <h1 className="vo-h1 dl-rise" style={{ animationDelay: '140ms' }}>Verum Omnis</h1>
          <p className="dl-rise mx-auto mt-4 max-w-[460px] text-[1.05rem] leading-relaxed" style={{ animationDelay: '200ms' }}>
            A native app for preserving and verifying documents. Someone shared it with you; here is the exact file, and everything you need to check it.
          </p>
        </header>

        <section className="dl-rise mt-10" style={{ animationDelay: '280ms' }} aria-labelledby="dl-h">
          <h2 id="dl-h" className="sr-only">Download</h2>
          <a href={APK} download="verum-omnis-5.2.8.apk" className="dl-btn" data-testid="link-download-apk">
            <Download size={20} aria-hidden /> Download APK · 93.6 MB
          </a>
          <p className="mt-3 text-center font-mono text-[11px] uppercase tracking-[0.1em]" style={{ color: 'var(--vo-blue)' }}>
            Android 10+ · ARM64 phones · direct download
          </p>
        </section>

        <section className="vo-card dl-rise mt-10" style={{ animationDelay: '340ms' }} aria-labelledby="f-h">
          <h3 id="f-h" className="vo-card__title" style={{ color: 'var(--vo-offwhite)' }}>Release details</h3>
          {facts.map(([k, v]) => (
            <div className="vo-id-field" key={k}>
              <span className="vo-id-label">{k}</span>
              <span className="vo-id-value" data-testid={`text-${k.toLowerCase().replace(/\s/g, '-')}`}>{v}</span>
            </div>
          ))}
          <p className="mt-4 text-sm" style={{ color: 'var(--vo-muted)' }}>
            Most phones from the last several years are ARM64. Older 32-bit devices, x86 tablets and Chromebooks with x86 processors cannot install this build.
          </p>
        </section>

        <section className="vo-card dl-rise" style={{ animationDelay: '400ms' }} aria-labelledby="h-h">
          <h3 id="h-h" className="vo-card__title" style={{ color: 'var(--vo-offwhite)' }}>SHA-256 checksum</h3>
          <p className="mb-3 text-sm" style={{ color: 'var(--vo-muted)' }}>
            Compare this with the hash of the file you downloaded. If even one character differs, do not install it.
          </p>
          <div className="vo-hash select-all text-[12.5px] leading-relaxed" data-testid="text-sha256">{SHA}</div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button type="button" onClick={onCopy} className="dl-ghost" data-testid="button-copy-sha256">
              {copy === 'ok' ? <Check size={16} aria-hidden /> : <Copy size={16} aria-hidden />}
              {copy === 'ok' ? 'Copied' : 'Copy SHA-256'}
            </button>
            <a href={SHA_URL} download="verum-omnis-5.2.8.apk.sha256" className="dl-ghost" data-testid="link-download-sha256">
              <FileDown size={16} aria-hidden /> Checksum file
            </a>
          </div>
          <p role="status" aria-live="polite" className="mt-3 min-h-[1.25rem] text-sm" data-testid="status-copy">
            {copy === 'ok' && <span style={{ color: 'var(--vo-success-bright)' }}>Checksum copied to clipboard.</span>}
            {copy === 'fail' && (
              <span className="inline-flex items-start gap-2" style={{ color: 'var(--vo-error-bright)' }}>
                <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
                Your browser blocked copying. Press and hold the hash above to select it instead.
              </span>
            )}
          </p>
        </section>

        <section className="dl-rise mt-12" style={{ animationDelay: '460ms' }} aria-labelledby="i-h">
          <p className="vo-eyebrow" style={{ color: 'var(--vo-blue)' }}>Install on your phone</p>
          <h2 id="i-h" className="vo-section-heading" style={{ color: 'var(--vo-offwhite)', fontWeight: 300 }}>Four steps, about two minutes</h2>
          <ol className="mt-6 space-y-0">
            {steps.map((s, i) => (
              <li key={s.t} className="relative flex gap-4 pb-7 last:pb-0" data-testid={`step-install-${i + 1}`}>
                {i < steps.length - 1 && <span aria-hidden className="absolute left-[17px] top-10 bottom-1 w-px" style={{ background: 'var(--vo-border-dim)' }} />}
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full font-mono text-xs"
                  style={{ border: '1px solid var(--vo-blue-border)', color: 'var(--vo-blue-soft)', background: 'var(--vo-well)' }}>
                  {String(i + 1).padStart(2, '0')}
                </span>
                <div className="pt-1">
                  <p className="font-serif text-[1.3rem] leading-tight" style={{ color: 'var(--vo-offwhite)' }}>{s.t}</p>
                  <p className="mt-1 text-[0.95rem]">{s.d}</p>
                </div>
              </li>
            ))}
          </ol>
          <div className="vo-note mt-8 mb-0">
            Google Play Protect may show a warning for an app downloaded outside the Play Store. <strong>Keep Play Protect switched on</strong> and read any warning before proceeding. A matching checksum confirms the file is unchanged; it does not prove the app is safe.
          </div>
        </section>

        <section className="vo-card vo-card--dim dl-rise mt-10" style={{ animationDelay: '520ms' }} aria-labelledby="s-h">
          <h3 id="s-h" className="mb-2 flex items-center gap-2 font-serif text-[1.25rem]" style={{ color: 'var(--vo-offwhite)' }}>
            <ShieldCheck size={18} style={{ color: 'var(--vo-blue)' }} aria-hidden /> What the signature means
          </h3>
          <p className="text-[0.95rem]">
            This APK is signed by its publisher, so Android can tell whether a later update came from the same key. A signature is not an independent security audit or certification of safety.
          </p>
          <a href="https://verumglobal.foundation/verify.html" target="_blank" rel="noopener noreferrer"
            className="mt-4 inline-flex items-center gap-2 font-mono text-xs uppercase tracking-[0.1em]" data-testid="link-verify">
            Verify a sealed document <ExternalLink size={14} aria-hidden />
          </a>
        </section>
      </main>

      <footer className="vo-seal-footer relative z-10 mt-6">
        Verum Omnis · Android 5.2.8<br />com.verumomnis.forensic
      </footer>
    </div>
  );
}
