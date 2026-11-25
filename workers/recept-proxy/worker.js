// Cloudflare Worker "recept-proxy": haalt een receptpagina op voor de Maaltijdplanner-webapp.
// Een browser mag andere sites niet zelf uitlezen (CORS); deze Worker doet het ophalen en geeft
// de HTML terug, enkel aan de webapp zelf.

const ALLOWED_ORIGIN = /^(https:\/\/robpoluspxl\.github\.io|http:\/\/(localhost|127\.0\.0\.1)(:\d+)?)$/;
const MAX_BYTES = 5 * 1024 * 1024;

export default {
  async fetch(request) {
    const origin = request.headers.get('Origin') || '';
    const allowed = ALLOWED_ORIGIN.test(origin);
    const cors = {
      'Access-Control-Allow-Origin': allowed ? origin : 'https://robpoluspxl.github.io',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Vary': 'Origin',
    };
    const reply = (body, status, type = 'text/plain; charset=utf-8') =>
      new Response(body, { status, headers: { ...cors, 'Content-Type': type } });

    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
    if (request.method !== 'GET') return reply('Alleen GET', 405);
    if (!allowed) return reply('Niet toegestaan', 403);

    let target;
    try { target = new URL(new URL(request.url).searchParams.get('url') || ''); } catch { return reply('Geen geldige link', 400); }
    if (target.protocol !== 'https:' && target.protocol !== 'http:') return reply('Geen geldige link', 400);

    let res;
    try {
      res = await fetch(target.toString(), {
        redirect: 'follow',
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36',
          'Accept': 'text/html,application/xhtml+xml',
          'Accept-Language': 'nl-BE,nl;q=0.9,en;q=0.6',
        },
      });
    } catch { return reply('Site niet bereikbaar', 502); }
    if (!res.ok) return reply(`Site gaf HTTP ${res.status}`, 502);
    const type = res.headers.get('Content-Type') || '';
    if (!type.includes('html')) return reply('Dat is geen webpagina', 415);
    const html = await res.text();
    if (html.length > MAX_BYTES) return reply('Pagina te groot', 413);
    return reply(html, 200, 'text/html; charset=utf-8');
  },
};
