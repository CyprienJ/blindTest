import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParticipantScore } from '../../../core/models/game-protocol.model';

/** Live multiplayer scoreboard (MP-06), ranked by total score with shared ranks for ties. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-scoreboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './scoreboard.component.html',
  styleUrl: './scoreboard.component.scss',
})
export class ScoreboardComponent {
  @Input({ required: true }) scores: ParticipantScore[] = [];
  /** Participants who found the current title. */
  @Input() found: ReadonlySet<string> = new Set();
  @Input() meId: string | null = null;
  @Input() hostId: string | null = null;

  get ranked(): (ParticipantScore & { rank: number })[] {
    const sorted = [...this.scores].sort((a, b) => b.totalScore - a.totalScore);
    return sorted.map((score, index) => ({
      ...score,
      rank:
        index > 0 && sorted[index - 1].totalScore === score.totalScore
          ? sorted.findIndex((other) => other.totalScore === score.totalScore) + 1
          : index + 1,
    }));
  }

  initials(name: string): string {
    return name.slice(0, 2).toUpperCase();
  }
}
