import { login } from "./api-client.js";
import { seedCatalogue, seedPlayers } from "./catalogue.js";
import { config } from "./config.js";
import { MetricsRecorder } from "./metrics.js";
import { printReport, type ScenarioReport } from "./report.js";
import { runBaseline, runConcurrentGames, runImportIsolation } from "./scenarios.js";

async function main(): Promise<void> {
  console.log(`Seeding catalogue (${config.catalogueTrackCount} published tracks)...`);
  const catalogue = await seedCatalogue(config.catalogueTrackCount);

  const totalPlayersNeeded = 1 + config.concurrentGames + config.headroomGames + config.concurrentGames;
  console.log(`Registering ${totalPlayersNeeded} player accounts...`);
  const accounts = await seedPlayers(totalPlayersNeeded);
  console.log("Logging in all player accounts...");
  const tokens = await Promise.all(accounts.map((account) => login(account).then((session) => session.accessToken)));

  let cursor = 0;
  const take = (count: number): string[] => {
    const slice = tokens.slice(cursor, cursor + count);
    cursor += count;
    return slice;
  };

  const metrics = new MetricsRecorder(catalogue.adminToken);
  metrics.start();

  const reports: ScenarioReport[] = [];
  try {
    console.log("\nRunning scenario 1/4: baseline single game...");
    reports.push(await runBaseline(take(1)[0], catalogue, metrics));

    console.log(`Running scenario 2/4: ${config.concurrentGames} concurrent games (required minimum)...`);
    const { report: concurrencyReport } = await runConcurrentGames(
      `2. Concurrency (${config.concurrentGames} concurrent games)`,
      take(config.concurrentGames),
      catalogue,
      metrics,
    );
    reports.push(concurrencyReport);

    console.log(`Running scenario 3/4: ${config.headroomGames} concurrent games (non-production headroom)...`);
    const { report: headroomReport } = await runConcurrentGames(
      `3. Headroom (${config.headroomGames} concurrent games)`,
      take(config.headroomGames),
      catalogue,
      metrics,
    );
    reports.push(headroomReport);

    console.log(`Running scenario 4/4: import isolation (${config.concurrentGames} concurrent games + concurrent imports)...`);
    reports.push(await runImportIsolation(take(config.concurrentGames), catalogue, metrics));
  } finally {
    metrics.stop();
  }

  printReport(reports);

  const failed = reports.some((report) => report.gamesOk !== report.gameCount || report.errors.length > 0);
  if (failed) {
    console.error("\nFAILED: one or more scenarios reported a correctness violation.");
    process.exit(1);
  }
  console.log("\nOK: all scenarios completed with no correctness violations.");
}

main().catch((error) => {
  console.error("Load test run crashed:", error);
  process.exit(1);
});
