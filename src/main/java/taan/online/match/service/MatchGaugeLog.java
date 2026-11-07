package taan.online.match.service;

import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.gaugeAfter;
import static taan.online.logging.LogField.gaugeBefore;
import static taan.online.logging.LogField.gaugeLevel;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.team;
import static taan.online.logging.LogField.turnRevision;
import static taan.online.logging.LogField.worldRevision;

import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.TeamGaugeRules;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.model.RoomTeam;

final class MatchGaugeLog {
    private static final EventLogger LOG = EventLogger.forClass(MatchGaugeLog.class);

    private MatchGaugeLog() {
    }

    static void changed(MatchSnapshot before, MatchSnapshot after, String source) {
        for (var side : RoomTeam.values()) {
            int previous = before.world().teamGauges().get(side).committed();
            int current = after.world().teamGauges().get(side).committed();

            if (previous != current) {
                LOG.log(
                    MatchLogEvent.GAUGE_CHANGED,
                    matchId(after.id().value()),
                    channelId(after.channelId()),
                    turnRevision(after.turn().revision()),
                    worldRevision(after.world().revision()),
                    team(side.name()),
                    gaugeBefore(previous),
                    gaugeAfter(current),
                    gaugeLevel(TeamGaugeRules.level(after.participants().size(), current)),
                    reason(source)
                );
            }
        }
    }
}
