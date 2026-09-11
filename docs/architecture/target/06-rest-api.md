# 6. REST API

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

> See `docs/swagger.yaml` for the full specification with schemas, examples and error codes.

Base URL: `/api`

### Auth

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/auth/register` | Register | No |
| POST | `/auth/login` | Login, returns JWT | No |
| POST | `/auth/logout` | Invalidates the refresh token | JWT |
| POST | `/auth/refresh` | Refreshes the JWT | Cookie |
| POST | `/auth/verify-email` | Email verification (token) | No |
| POST | `/auth/forgot-password` | Sends a password reset email | No |
| POST | `/auth/reset-password` | Resets the password | No |

### Users

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/users/me` | Full profile of the logged-in user | JWT |
| PATCH | `/users/me` | Update username / avatar / password | JWT |
| DELETE | `/users/me` | Delete account | JWT |
| GET | `/users/{userId}` | Public profile of a user | JWT |
| GET | `/users/search?q=` | Search users by username | JWT |

### Friends

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/users/me/friends` | Friend list | JWT |
| DELETE | `/users/me/friends/{userId}` | Remove a friend | JWT |
| GET | `/users/me/friends/requests` | Pending requests (received + sent) | JWT |
| POST | `/users/{userId}/friend-request` | Send a friend request | JWT |
| POST | `/users/me/friends/requests/{requestId}/accept` | Accept a request | JWT |
| POST | `/users/me/friends/requests/{requestId}/decline` | Decline a request | JWT |
| DELETE | `/users/me/friends/requests/{requestId}` | Cancel a sent request | JWT |

### History

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/users/me/history` | Game history of the logged-in player | JWT |
| GET | `/users/{userId}/history` | Public history of a player | JWT |
| GET | `/games/{gameId}` | Full details of a game | JWT |

### Sessions

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/sessions` | Create a session | JWT |
| GET | `/sessions/{code}` | Public session info | No |
| DELETE | `/sessions/{code}` | Delete the session | JWT (host) |
| POST | `/sessions/{code}/join` | Join (guest: body `{ guestName }`) | Optional |
| POST | `/sessions/{code}/invite/{friendId}` | Invite a friend (WS notification) | JWT (host) |

### Tracks & Playlists (public catalogue)

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/tracks?category=&decade=` | Filtered list of published tracks | No |
| GET | `/playlists` | List of available playlists | No |
| GET | `/playlists/{id}` | Playlist detail with its tracks | No |

### Administration — Tracks

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/admin/tracks` | All tracks (all statuses) | ADMIN |
| PUT | `/admin/tracks/{id}` | Update track metadata | ADMIN |
| DELETE | `/admin/tracks/{id}` | Delete a track | ADMIN |
| POST | `/admin/tracks/import` | Start a YouTube track import | ADMIN |
| GET | `/admin/tracks/import/{jobId}` | Import job status | ADMIN |
| POST | `/admin/tracks/{id}/validate` | Validate and publish the track | ADMIN |
| POST | `/admin/tracks/{id}/reject` | Reject the track | ADMIN |

### Administration — Playlists

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/admin/playlists` | All playlists | ADMIN |
| POST | `/admin/playlists` | Create a playlist | ADMIN |
| GET | `/admin/playlists/{id}` | Playlist detail | ADMIN |
| PUT | `/admin/playlists/{id}` | Update a playlist | ADMIN |
| DELETE | `/admin/playlists/{id}` | Delete a playlist | ADMIN |
| POST | `/admin/playlists/{id}/tracks` | Add a track to the playlist | ADMIN |
| DELETE | `/admin/playlists/{id}/tracks/{trackId}` | Remove a track | ADMIN |
| PUT | `/admin/playlists/{id}/tracks/order` | Atomically replace the complete track order | ADMIN |

Playlist positions are contiguous and zero-based. Only published tracks may be
added, duplicate membership is rejected, and an order replacement must contain
every current track ID exactly once. Playlist rows are locked while membership is
changed, and the database enforces non-negative unique positions. Rejecting or
deleting a published track removes it from every playlist and compacts the
remaining positions in the same transaction. Public and administrator playlist
details expose published tracks only.

### Administration — Users

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/admin/users` | User list | ADMIN |
| PATCH | `/admin/users/{userId}` | Update a user (role, status) | ADMIN |
| DELETE | `/admin/users/{userId}` | Delete a user | ADMIN |

---
