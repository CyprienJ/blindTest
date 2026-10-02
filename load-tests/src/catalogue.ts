import {
  addTrackToPlaylist,
  awaitImportDone,
  createPlaylist,
  generateAccount,
  login,
  provisionAdmin,
  publishTrack,
  registerFromInvitation,
  startImport,
  type AccountCredentials,
} from "./api-client.js";
import { config } from "./config.js";

function syntheticYoutubeUrl(index: number): string {
  // 11-char video ID matching YouTubeVideoUrl's [A-Za-z0-9_-]{11} pattern (backend has no
  // uniqueness constraint on youtube_url, and FakeProcessFactory ignores the URL content
  // entirely under the e2e profile, so any distinct well-formed ID is fine).
  return `https://www.youtube.com/watch?v=LT${String(index).padStart(9, "0")}`;
}

export interface Catalogue {
  adminToken: string;
  playlistId: string;
  trackCount: number;
}

/** Provisions one admin and seeds one playlist of published tracks, network-free (e2e fakes). */
export async function seedCatalogue(trackCount: number): Promise<Catalogue> {
  const admin = generateAccount("load-admin-");
  await registerFromInvitation(admin);
  await provisionAdmin(admin);
  const { accessToken: adminToken } = await login(admin);

  const trackIds: string[] = [];
  for (let index = 0; index < trackCount; index += 1) {
    const jobId = await startImport(adminToken, syntheticYoutubeUrl(index));
    const trackId = await awaitImportDone(adminToken, jobId);
    await publishTrack(adminToken, trackId);
    trackIds.push(trackId);
  }

  const playlistId = await createPlaylist(adminToken, `Load test playlist ${Date.now()}`);
  for (const [order, trackId] of trackIds.entries()) {
    await addTrackToPlaylist(adminToken, playlistId, trackId, order);
  }

  return { adminToken, playlistId, trackCount: trackIds.length };
}

/** Registers and verifies a pool of independent player accounts up front. */
export async function seedPlayers(count: number): Promise<AccountCredentials[]> {
  const accounts = Array.from({ length: count }, () => generateAccount("load-player-"));
  // Sequential on purpose: registration writes captured email state the /api/e2e/emails
  // lookup then reads back by address, and this only runs once during bootstrap — not on
  // the scenario hot path the concurrency measurements care about.
  for (const account of accounts) {
    await registerFromInvitation(account);
  }
  return accounts;
}

/** Imports and publishes additional tracks into the existing shared playlist mid-run. */
export async function importAdditionalTracks(
  catalogue: Catalogue,
  count: number,
  startIndex: number,
): Promise<void> {
  for (let offset = 0; offset < count; offset += 1) {
    const jobId = await startImport(catalogue.adminToken, syntheticYoutubeUrl(startIndex + offset));
    const trackId = await awaitImportDone(catalogue.adminToken, jobId, config.commandTimeoutMs);
    await publishTrack(catalogue.adminToken, trackId);
    await addTrackToPlaylist(
      catalogue.adminToken,
      catalogue.playlistId,
      trackId,
      catalogue.trackCount + offset,
    );
  }
}
