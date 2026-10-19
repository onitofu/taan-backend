package taan.online.match.service.model;

public sealed interface MatchStateCommand extends MatchCommand
    permits ConfirmMatchLoadingCommand, ConfirmMatchPositionCommand, ReportMatchEnemiesCommand, StartMatchCommand,
        MatchDeliveryCompletedCommand, MatchDeadlineExpiredCommand {
}
