import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParticipantScore } from '../../../core/models/game-protocol.model';

/** Live list of the players who found the current multiplayer title, in finding order. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-found-feed',
  standalone: true,
  templateUrl: './found-feed.component.html',
  styleUrl: './found-feed.component.scss',
})
export class FoundFeedComponent {
  /** Never carries the title: other players may be looking at this screen. */
  @Input({ required: true }) finders: ParticipantScore[] = [];
  @Input() meId: string | null = null;
}
