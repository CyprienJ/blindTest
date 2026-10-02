import { ValidatorFn } from '@angular/forms';
import { AdminTrack, StreamingPlatform, TrackStreamingLinks } from './admin-track.model';

/** Mirrors TrackStreamingLinks on the server: an empty value or a URL on the platform domain. */
export const STREAMING_LINK_PLATFORMS: { key: StreamingPlatform; label: string; hosts: string }[] =
  [
    {
      key: 'deezer',
      label: 'Deezer',
      hosts: 'deezer\\.com|deezer\\.page\\.link|dzr\\.page\\.link',
    },
    { key: 'youtube', label: 'YouTube', hosts: 'youtube\\.com|youtu\\.be' },
    { key: 'spotify', label: 'Spotify', hosts: 'spotify\\.com|spotify\\.link' },
    { key: 'qobuz', label: 'Qobuz', hosts: 'qobuz\\.com' },
    { key: 'tidal', label: 'Tidal', hosts: 'tidal\\.com' },
    { key: 'soundcloud', label: 'SoundCloud', hosts: 'soundcloud\\.com|snd\\.sc' },
  ];

function streamingLinkValidator(hosts: string): ValidatorFn {
  const pattern = new RegExp(`^https?://([a-z0-9-]+\\.)*(${hosts})(/\\S*)?$`, 'i');
  return (control) => {
    const value = String(control.value ?? '').trim();
    return value === '' || (value.length <= 500 && pattern.test(value))
      ? null
      : { streamingLink: true };
  };
}

/** Form-group configuration with one validated control per platform. */
export function streamingLinkControls(): Record<StreamingPlatform, [string, ValidatorFn]> {
  return Object.fromEntries(
    STREAMING_LINK_PLATFORMS.map(({ key, hosts }) => [key, ['', streamingLinkValidator(hosts)]]),
  ) as Record<StreamingPlatform, [string, ValidatorFn]>;
}

/** Form values for a track's stored links; missing links become empty strings. */
export function streamingLinkFormValue(track: AdminTrack): Record<StreamingPlatform, string> {
  return Object.fromEntries(
    STREAMING_LINK_PLATFORMS.map(({ key }) => [key, track.streamingLinks?.[key] ?? '']),
  ) as Record<StreamingPlatform, string>;
}

/** Update payload for the form values; blank links are sent as null to clear them. */
export function streamingLinksUpdate(
  value: Record<StreamingPlatform, string>,
): TrackStreamingLinks {
  return Object.fromEntries(
    STREAMING_LINK_PLATFORMS.map(({ key }) => [key, value[key]?.trim() || null]),
  ) as TrackStreamingLinks;
}
