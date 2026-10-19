package taan.online.match.controller;

import java.util.UUID;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.service.ActivateSpecialAction;
import taan.online.match.service.RecordMatchSpendAction;
import taan.online.match.service.ApplyScenarioAction;
import taan.online.match.service.ConfirmScenarioAction;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.protocol.codec.EndScenarioCodec;
import taan.online.match.protocol.codec.GiveUpCodec;
import taan.online.match.protocol.codec.LoadingDoneCodec;
import taan.online.match.protocol.codec.ReportEnemiesCodec;
import taan.online.match.protocol.codec.ResultSelectionCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.MoveCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.packet.PcGamePlayMove;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.service.MoveMatchActorAction;
import taan.online.match.service.PassMatchTurnAction;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.PassMatchTurnCommand;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.protocol.codec.UnsupportedGameplayCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayGiveUp;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.protocol.packet.PqGameSessionReportEnemies;
import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.view.ProtocolMatchView;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.PacketOutput;
import taan.online.room.service.model.RoomMatchEndReason;

public final class MatchPacketHandler {
    private final MatchRegistry matches;
    private final MatchItemPacketHandler items;
    private final MoveMatchActorAction movement;
    private final PassMatchTurnAction passing;
    private final TerminateMatchAction terminate;
    private final ConsumeMatchResultAction consume;
    private final ConfirmMatchLoadingAction loading;
    private final ConfirmMatchPositionAction position;
    private final ReportMatchEnemiesAction reports;
    private final ApplyScenarioPacketHandler scenarios;
    private final ConfirmScenarioAction acknowledgements;

    public MatchPacketHandler(
        MatchRegistry matches,
        TerminateMatchAction terminate,
        ConsumeMatchResultAction consume,
        ConfirmMatchLoadingAction loading,
        ConfirmMatchPositionAction position,
        ReportMatchEnemiesAction reports
    ) {
        this(
            matches,
            terminate,
            consume,
            loading,
            position,
            reports,
            new ApplyScenarioPacketHandler(new ApplyScenarioAction(matches)),
            new ConfirmScenarioAction(matches)
        );
    }

    public MatchPacketHandler(
        MatchRegistry matches,
        TerminateMatchAction terminate,
        ConsumeMatchResultAction consume,
        ConfirmMatchLoadingAction loading,
        ConfirmMatchPositionAction position,
        ReportMatchEnemiesAction reports,
        ApplyScenarioPacketHandler scenarios,
        ConfirmScenarioAction acknowledgements
    ) {
        this(
            matches,
            terminate,
            consume,
            loading,
            position,
            reports,
            scenarios,
            acknowledgements,
            new MoveMatchActorAction(matches),
            new PassMatchTurnAction(matches)
        );
    }

    public MatchPacketHandler(
        MatchRegistry matches,
        TerminateMatchAction terminate,
        ConsumeMatchResultAction consume,
        ConfirmMatchLoadingAction loading,
        ConfirmMatchPositionAction position,
        ReportMatchEnemiesAction reports,
        ApplyScenarioPacketHandler scenarios,
        ConfirmScenarioAction acknowledgements,
        MoveMatchActorAction movement,
        PassMatchTurnAction passing
    ) {
        this(
            matches,
            terminate,
            consume,
            loading,
            position,
            reports,
            scenarios,
            acknowledgements,
            movement,
            passing,
            new MatchItemPacketHandler(new ActivateSpecialAction(matches), new RecordMatchSpendAction(matches))
        );
    }

    public MatchPacketHandler(
        MatchRegistry matches,
        TerminateMatchAction terminate,
        ConsumeMatchResultAction consume,
        ConfirmMatchLoadingAction loading,
        ConfirmMatchPositionAction position,
        ReportMatchEnemiesAction reports,
        ApplyScenarioPacketHandler scenarios,
        ConfirmScenarioAction acknowledgements,
        MoveMatchActorAction movement,
        PassMatchTurnAction passing,
        MatchItemPacketHandler items
    ) {
        this.items = items;
        this.movement = movement;
        this.passing = passing;
        this.scenarios = scenarios;
        this.acknowledgements = acknowledgements;
        this.matches = matches;
        this.terminate = terminate;
        this.consume = consume;
        this.loading = loading;
        this.position = position;
        this.reports = reports;
    }

    public void open(UUID id, PacketOutput output) {
        matches.subscribe(id, new ProtocolMatchView(output));
    }

    public void close(UUID id) {
        matches.unsubscribe(id);
    }

    public MatchRequestContext capture(UUID id) {
        var binding = matches.capture(id);
        return new MatchRequestContext(
            binding.roomContext(),
            binding.turnRevision(),
            binding.worldRevision(),
            binding.fingerprint(),
            binding.phaseRevision()
        );
    }

    public CompletionStage<Void> handle(UUID id, int type, byte[] root, MatchRequestContext request) {
        var context = request == null ? null : request.roomContext();

        if (type == PcGamePlayGiveUp.TYPE_ID) {
            new GiveUpCodec().decode(root);
            return terminate.execute(id, context).thenCompose(result -> result.delivery());
        }

        if (type == PcGamePlayLoadingDone.TYPE_ID) {
            var message = new LoadingDoneCodec().decode(root);

            if (message.positions().size() != 1) {
                throw new MalformedPacketException("INVALID_LOCAL_SLOT_COUNT");
            }

            var loaded = message.positions().getFirst();
            return loading.execute(id, context, loaded.slotIndex(), loaded.x(), loaded.y());
        }

        if ((type == PcGamePlayMove.TYPE_ID || type == PcGamePlayPassTurn.TYPE_ID
            || type == PcGamePlaySetPos.TYPE_ID) && matches.scenarioEnabled(context)) {
            return motion(id, type, root, request);
        }

        if (type == PcGamePlaySetPos.TYPE_ID) {
            var message = new SetPosCodec().decode(root);
            return position.execute(id, context, message.slotIndex(), message.x(), message.y(), message.facing())
                .thenApply(ignored -> null);
        }

        if (type == PqGameSessionReportEnemies.TYPE_ID) {
            new ReportEnemiesCodec().decode(root);
            return reports.execute(id, context).thenApply(ignored -> null);
        }

        if (type == PcGamePlayEndScenario.TYPE_ID) {
            var message = new EndScenarioCodec().decode(root);

            if (context == null || !context.membership().sessionId().equals(id)) {
                return CompletableFuture.completedFuture(null);
            }

            return acknowledgements.execute(new ConfirmScenarioCommand(
                MatchCommandContext.from(context),
                request.turnRevision(),
                request.worldRevision(),
                request.fingerprint(),
                message.slotIndex(),
                request.phaseRevision()
            )).thenApply(ignored -> null);
        }

        if (type == PcGamePlayScenario.TYPE_ID && matches.scenarioEnabled(context)) {
            if (!context.membership().sessionId().equals(id)) {
                return CompletableFuture.completedFuture(null);
            }

            return scenarios.handle(
                MatchCommandContext.from(context),
                request.turnRevision(),
                request.worldRevision(),
                request.phaseRevision(),
                root
            ).thenApply(ignored -> null);
        }

        if ((type == PcGamePlayUseItem.TYPE_ID || type == PcGamePlaySpendItem.TYPE_ID)
            && matches.scenarioEnabled(context)) {
            return items.handle(id, type, root, request);
        }

        if (UnsupportedGameplayCodec.accepts(type)) {
            new UnsupportedGameplayCodec().decode(type, root);
            return terminate.execute(id, context, RoomMatchEndReason.UNSUPPORTED_GAMEPLAY)
                .thenCompose(result -> result.delivery());
        }

        var codec = new ResultSelectionCodec();

        if (type == PqResultRandSelect.TYPE_ID) {
            codec.randomSelect(root);
        } else if (type == PqResultSelectCard.TYPE_ID) {
            codec.selectCard(root);
        } else {
            throw new MalformedPacketException("UNSUPPORTED_MATCH_PACKET");
        }

        consume.execute(id, context);
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> motion(UUID id, int type, byte[] root, MatchRequestContext request) {
        var context = request.roomContext();

        if (!context.membership().sessionId().equals(id)) {
            return CompletableFuture.completedFuture(null);
        }

        var sender = MatchCommandContext.from(context);

        if (type == PcGamePlayPassTurn.TYPE_ID) {
            var packet = new PassTurnCodec().decode(root);
            return passing.execute(new PassMatchTurnCommand(
                sender,
                request.turnRevision(),
                request.phaseRevision(),
                packet.slotIndex(),
                packet.x(),
                packet.y(),
                packet.facing(),
                packet.scalar20()
            )).thenApply(ignored -> null);
        }

        if (type == PcGamePlayMove.TYPE_ID) {
            var packet = new MoveCodec().decode(root);
            return movement.execute(new MoveMatchActorCommand(
                sender,
                request.turnRevision(),
                request.phaseRevision(),
                packet.slotIndex(),
                packet.x(),
                packet.y(),
                packet.facing(),
                MatchMovementKind.MOVE
            )).thenApply(ignored -> null);
        }

        var packet = new SetPosCodec().decode(root);
        return movement.execute(new MoveMatchActorCommand(
            sender,
            request.turnRevision(),
            request.phaseRevision(),
            packet.slotIndex(),
            packet.x(),
            packet.y(),
            packet.facing(),
            MatchMovementKind.SET_POS
        )).thenApply(ignored -> null);
    }

}
