/*
 * Runtime API configuration.
 * Local development: frontend on localhost:5500 -> Spring Boot on localhost:8080.
 * Production: same-origin /api requests are reverse-proxied by Vercel to Railway.
 */
(function () {
  const host = window.location.hostname;
  const isLocal = host === "localhost" || host === "127.0.0.1";
  const defaultApiBase = isLocal ? "http://localhost:8080" : window.location.origin;
  window.SILENTECHO_API_BASE = window.SILENTECHO_API_BASE || defaultApiBase;
})();
