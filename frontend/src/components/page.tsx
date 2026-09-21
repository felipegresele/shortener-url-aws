import { useState, useEffect, useRef } from "react";
import type { SubmitEvent } from "react";

const COOLDOWN_SECONDS = 10;

const SHORTENER_ENDPOINT = import.meta.env.VITE_SHORTENER_ENDPOINT;
const REDIRECT_BASE = import.meta.env.VITE_REDIRECT_BASE;

if (!SHORTENER_ENDPOINT || !REDIRECT_BASE) {
  // AJUSTE: erro explícito no console caso as env vars não estejam configuradas
  // (esquecimento do .env local, ou não configuradas no painel da Vercel)
  console.error(
    "Variáveis VITE_SHORTENER_ENDPOINT / VITE_REDIRECT_BASE não estão definidas."
  );
}

const EXPIRATION_OPTIONS = [
  { label: "1 hora", value: "1" },
  { label: "24 horas", value: "24" },
  { label: "7 dias", value: "168" },
  { label: "30 dias", value: "720" },
];

type Status = "idle" | "loading" | "success" | "error";

export function UrlShortener() {
  const [originalUrl, setOriginalUrl] = useState("");
  const [expirationTime, setExpirationTime] = useState(EXPIRATION_OPTIONS[1].value);
  const [status, setStatus] = useState<Status>("idle");
  const [errorMessage, setErrorMessage] = useState("");
  const [shortCode, setShortCode] = useState("");
  const [copied, setCopied] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const shortUrl = shortCode ? `${REDIRECT_BASE}${shortCode}` : "";

  // AJUSTE: contagem regressiva do cooldown, um tick por segundo
  useEffect(() => {
    if (cooldown <= 0) return;

    intervalRef.current = setInterval(() => {
      setCooldown((current) => {
        if (current <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          return 0;
        }
        return current - 1;
      });
    }, 1000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [cooldown]);

  function isValidUrl(value: string) {
    try {
      const url = new URL(value);
      return url.protocol === "http:" || url.protocol === "https:";
    } catch {
      return false;
    }
  }

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();

    // AJUSTE: bloqueia envio enquanto o cooldown estiver ativo
    if (cooldown > 0) return;

    if (!isValidUrl(originalUrl)) {
      setStatus("error");
      setErrorMessage("Cole uma URL válida, começando com http:// ou https://");
      return;
    }

    setStatus("loading");
    setErrorMessage("");
    setShortCode("");
    setCopied(false);

    try {
      const response = await fetch(SHORTENER_ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ originalUrl, expirationTime }),
      });

      if (!response.ok) {
        throw new Error(`A lambda respondeu com status ${response.status}`);
      }

      const data = await response.json();

      if (!data.code) {
        throw new Error("Resposta da lambda veio sem o código encurtado");
      }

      setShortCode(data.code);
      setStatus("success");
    } catch (error) {
      setStatus("error");
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Não foi possível encurtar essa URL. Tenta de novo."
      );
    } finally {
      // AJUSTE: cooldown começa depois da resposta, com sucesso ou erro,
      // pra evitar clique repetido enquanto a lambda ainda está processando
      setCooldown(COOLDOWN_SECONDS);
    }
  }

  async function handleCopy() {
    await navigator.clipboard.writeText(shortUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="min-h-screen bg-[#0B1220] text-slate-100 flex items-center justify-center px-4 py-16">
      <div className="w-full max-w-xl">
        <div className="mb-10">
          <p className="text-sm font-mono text-amber-400/80 mb-2">
            aws · lambda · s3
          </p>
          <h1 className="text-3xl sm:text-4xl font-semibold tracking-tight text-white">
            Encurtador de URL
          </h1>
          <p className="mt-3 text-slate-400 leading-relaxed max-w-md">
            Cole um link, escolha por quanto tempo ele deve funcionar, e receba
            uma URL curta gerada e servida direto por duas funções Lambda.
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="bg-[#111a2e] border border-white/5 rounded-2xl p-6 sm:p-8 space-y-5"
        >
          <div>
            <label
              htmlFor="originalUrl"
              className="block text-sm text-slate-300 mb-2"
            >
              URL original
            </label>
            <input
              id="originalUrl"
              type="text"
              placeholder="https://exemplo.com/pagina-bem-longa"
              value={originalUrl}
              onChange={(event) => setOriginalUrl(event.target.value)}
              className="w-full rounded-lg bg-[#0B1220] border border-white/10 px-4 py-3 text-slate-100 placeholder:text-slate-600 outline-none focus:border-amber-400/60 focus:ring-1 focus:ring-amber-400/60 transition-colors"
            />
          </div>

          <div>
            <label
              htmlFor="expirationTime"
              className="block text-sm text-slate-300 mb-2"
            >
              Expira em
            </label>
            <select
              id="expirationTime"
              value={expirationTime}
              onChange={(event) => setExpirationTime(event.target.value)}
              className="w-full rounded-lg bg-[#0B1220] border border-white/10 px-4 py-3 text-slate-100 outline-none focus:border-amber-400/60 focus:ring-1 focus:ring-amber-400/60 transition-colors"
            >
              {EXPIRATION_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <button
            type="submit"
            disabled={status === "loading" || cooldown > 0}
            className="w-full rounded-lg bg-amber-400 text-[#0B1220] font-medium py-3 hover:bg-amber-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {status === "loading"
              ? "Encurtando..."
              : cooldown > 0
              ? `Aguarde ${cooldown}s`
              : "Encurtar URL"}
          </button>

          {status === "error" && (
            <p className="text-sm text-red-400 border border-red-400/20 bg-red-400/5 rounded-lg px-4 py-3">
              {errorMessage}
            </p>
          )}
        </form>

        {status === "success" && shortUrl && (
          <div className="mt-6 bg-[#111a2e] border border-amber-400/20 rounded-2xl p-6 sm:p-8">
            <p className="text-sm text-slate-400 mb-3">Sua URL está pronta</p>
            <div className="flex items-center gap-3 flex-wrap">
              <code className="flex-1 min-w-0 font-mono text-amber-300 text-sm sm:text-base break-all bg-[#0B1220] rounded-lg px-4 py-3 border border-white/5">
                {shortUrl}
              </code>
              <button
                onClick={handleCopy}
                className="shrink-0 rounded-lg border border-white/10 px-4 py-3 text-sm text-slate-200 hover:bg-white/5 transition-colors"
              >
                {copied ? "Copiado!" : "Copiar"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}