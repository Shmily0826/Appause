const sources = ['reddit', 'v2ex', 'unknown'];
const events = ['visit', 'github_click', 'lanzou_click'];
const reply = (body, status = 200) => new Response(JSON.stringify(body), {status, headers: {'content-type': 'application/json', 'access-control-allow-origin': '*', 'cache-control': 'no-store'}});

// Only aggregate counters are persisted: no request headers, URLs or identifiers.
export class SourceMetrics {
  constructor(state) { this.state = state; }
  async fetch(request) {
    return this.state.blockConcurrencyWhile(async () => {
      const counts = await this.state.storage.get('counts') ?? Object.fromEntries(sources.map(s => [s, Object.fromEntries(events.map(e => [e, 0]))]));
      if (request.method === 'POST') {
        const body = await request.json().catch(() => ({}));
        if (!sources.includes(body.source) || !events.includes(body.event)) return reply({error: 'bad_request'}, 400);
        counts[body.source][body.event]++;
        await this.state.storage.put('counts', counts);
      }
      return reply({counts, generatedAt: new Date().toISOString(), meaning: 'Anonymous event counts, including repeats and automated traffic; not installs or people.'});
    });
  }
}

export async function sourceMetrics(request, env) {
  if (request.method === 'POST' && request.headers.get('origin') !== 'https://shmily0826.github.io') return reply({error: 'forbidden'}, 403);
  if (!['GET', 'POST'].includes(request.method)) return reply({error: 'method_not_allowed'}, 405);
  if (!env.SOURCE_METRICS) return reply({error: 'not_configured'}, 503);
  const id = env.SOURCE_METRICS.idFromName('landing-sources-v1');
  return env.SOURCE_METRICS.get(id).fetch(request);
}
