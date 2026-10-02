import { randomUUID } from "node:crypto";
import { config, DUMMY_TURNSTILE_TOKEN } from "./config.js";

export class ApiError extends Error {
  constructor(
    public readonly method: string,
    public readonly path: string,
    public readonly status: number,
    public readonly body: string,
  ) {
    super(`${method} ${path} -> ${status}: ${body.slice(0, 300)}`);
  }
}

async function request<T>(
  method: string,
  path: string,
  options: { token?: string; body?: unknown; query?: Record<string, string> } = {},
): Promise<T> {
  const url = new URL(path, config.apiBaseUrl);
  if (options.query) {
    for (const [key, value] of Object.entries(options.query)) {
      url.searchParams.set(key, value);
    }
  }
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers["content-type"] = "application/json";
  }
  if (options.token) {
    headers.authorization = `Bearer ${options.token}`;
  }
  const response = await fetch(url, {
    method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
  const text = await response.text();
  if (!response.ok) {
    throw new ApiError(method, path, response.status, text);
  }
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

export interface AccountCredentials {
  username: string;
  email: string;
  password: string;
}

export function generateAccount(prefix: string): AccountCredentials {
  const suffix = randomUUID().replace(/-/g, "").slice(0, 12);
  return {
    username: `${prefix}${suffix}`.slice(0, 50),
    email: `${prefix}${suffix}@load-test.local`,
    password: "Load-Test-Password-1!",
  };
}

/**
 * Registers through the invitation-only flow: the e2e backdoor issues an invitation, and the
 * resulting account is already verified, so no mailbox round trip is needed.
 */
export async function registerFromInvitation(account: AccountCredentials): Promise<void> {
  const invitation = await request<{ token: string }>("POST", "/api/e2e/invitations", {
    query: { email: account.email },
  });
  await request("POST", "/api/auth/register", {
    body: {
      username: account.username,
      inviteToken: invitation.token,
      password: account.password,
      turnstileToken: DUMMY_TURNSTILE_TOKEN,
    },
  });
}

export interface Session {
  accessToken: string;
  accountId: string;
}

export async function login(account: AccountCredentials): Promise<Session> {
  const response = await request<{ accessToken: string; user: { id: string } }>(
    "POST",
    "/api/auth/login",
    { body: { email: account.email, password: account.password } },
  );
  return { accessToken: response.accessToken, accountId: response.user.id };
}

/** Resets any prior admin (the isolated stack allows only one bootstrap promotion) and promotes. */
export async function provisionAdmin(account: AccountCredentials): Promise<void> {
  await request("DELETE", "/api/e2e/admin-provisioning");
  await request("POST", "/api/e2e/admin-provisioning", { query: { email: account.email } });
}

export interface ImportJobStatus {
  jobId: string;
  status: string;
  progress: number;
  track: { id: string } | null;
}

export async function startImport(token: string, youtubeUrl: string): Promise<string> {
  const response = await request<{ jobId: string }>("POST", "/api/admin/tracks/import", {
    token,
    body: { youtubeUrl },
  });
  return response.jobId;
}

export async function awaitImportDone(
  token: string,
  jobId: string,
  timeoutMs = 30_000,
): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const job = await request<ImportJobStatus>("GET", `/api/admin/tracks/import/${jobId}`, {
      token,
    });
    if (job.status === "DONE" && job.track) {
      return job.track.id;
    }
    if (job.status === "FAILED") {
      throw new Error(`Import job ${jobId} failed`);
    }
    if (Date.now() > deadline) {
      throw new Error(`Timed out waiting for import job ${jobId} (status=${job.status})`);
    }
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
}

export async function publishTrack(token: string, trackId: string): Promise<void> {
  await request("POST", `/api/admin/tracks/${trackId}/validate`, { token });
}

export async function createPlaylist(token: string, name: string): Promise<string> {
  const response = await request<{ id: string }>("POST", "/api/admin/playlists", {
    token,
    body: { name },
  });
  return response.id;
}

export async function addTrackToPlaylist(
  token: string,
  playlistId: string,
  trackId: string,
  order: number,
): Promise<void> {
  await request("POST", `/api/admin/playlists/${playlistId}/tracks`, {
    token,
    body: { trackId, order },
  });
}

export interface SoloSessionResponse {
  sessionId: string;
  version: number;
  connection: {
    webSocketEndpoint: string;
    commandDestination: string;
    eventDestination: string;
    privateDestination: string;
  };
}

export async function createSoloSession(
  token: string,
  playlistId: string,
  trackCount: number,
  maxRoundSeconds: number,
): Promise<SoloSessionResponse> {
  return request<SoloSessionResponse>("POST", "/api/sessions", {
    token,
    body: { playlistId, trackCount, maxRoundSeconds, extensions: {} },
  });
}

export interface GameSummaryRound {
  roundIndex: number;
  outcome: "TITLE_FOUND" | "TIMEOUT" | "SKIPPED";
  points: number;
  title: string;
}

export interface GameSummary {
  sessionId: string;
  totalScore: number;
  totalRounds: number;
  titlesFound: number;
  skippedRounds: number;
  timedOutRounds: number;
  rounds: GameSummaryRound[];
}

export async function getSummary(token: string, sessionId: string): Promise<GameSummary> {
  return request<GameSummary>("GET", `/api/sessions/${sessionId}/summary`, { token });
}

/** Polls the summary endpoint briefly: durable history is written just after the GAME_FINISHED
 * broadcast (SessionGameplayService sends the event, then calls
 * GameHistoryCompletionService#complete), so a client reading the summary immediately on
 * receiving that event can legitimately see a 404 for a few milliseconds. */
export async function awaitSummary(
  token: string,
  sessionId: string,
  timeoutMs = 5_000,
): Promise<GameSummary> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    try {
      return await getSummary(token, sessionId);
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 404 || Date.now() > deadline) {
        throw cause;
      }
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }
}

/** Reads one Actuator metric by name, admin-only (SecurityConfig restricts /actuator/metrics/**). */
export async function readMetric(adminToken: string, name: string): Promise<number | undefined> {
  const url = new URL(`/actuator/metrics/${name}`, config.metricsBaseUrl);
  const response = await fetch(url, {
    headers: { authorization: `Bearer ${adminToken}` },
  });
  if (!response.ok) {
    return undefined;
  }
  const body = (await response.json()) as { measurements?: { statistic: string; value: number }[] };
  return body.measurements?.[0]?.value;
}
