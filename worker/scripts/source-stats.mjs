const response = await fetch('https://appause-pro-worker.rng2018520.workers.dev/api/source-metrics', {signal: AbortSignal.timeout(15000), cache: 'no-store'});
if (!response.ok) throw new Error(`Source metrics unavailable (HTTP ${response.status}); no count reported. Deploy the source-metrics Worker first.`);
console.log(JSON.stringify(await response.json(), null, 2));
