package taan.online.server;

import java.time.Clock;
import taan.online.account.controller.CheckAccountPacketHandler;
import taan.online.account.controller.CreateAccountPacketHandler;
import taan.online.account.protocol.codec.CheckAccountRequestCodec;
import taan.online.account.protocol.codec.CheckAccountResponseCodec;
import taan.online.account.protocol.codec.CreateAccountRequestCodec;
import taan.online.account.protocol.codec.CreateAccountResponseCodec;
import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.account.protocol.packet.PrCheckAccount;
import taan.online.account.protocol.packet.PrCreateAccount;
import taan.online.protocol.PacketOutput;
import taan.online.protocol.connection.codec.FirstPacketRequestCodec;
import taan.online.protocol.connection.codec.FirstPacketResponseCodec;
import taan.online.protocol.connection.codec.KeepAliveCodec;
import taan.online.protocol.connection.packet.PqFirstPacket;
import taan.online.session.controller.UserLoginPacketHandler;
import taan.online.session.protocol.codec.UserLoginResponseCodec;
import taan.online.session.protocol.packet.PqUserLogin;
import taan.online.session.protocol.packet.PrUserLogin;

final class AccountRequestDispatcher {
    private final CheckAccountPacketHandler check;
    private final CreateAccountPacketHandler create;
    private final UserLoginPacketHandler login;
    private final Clock clock;

    AccountRequestDispatcher(
        CheckAccountPacketHandler check,
        CreateAccountPacketHandler create,
        UserLoginPacketHandler login,
        Clock clock
    ) {
        this.check = check;
        this.create = create;
        this.login = login;
        this.clock = clock;
    }

    RequestCompletion dispatch(PendingRequest request, PacketOutput output) {
        byte[] root = request.root();

        return switch (request.type()) {
            case PqCheckAccount.TYPE_ID -> RequestCompletion.reply(
                output,
                ProtocolEvent.ACCOUNT_CHECK_COMPLETED,
                new CheckAccountResponseCodec().encode(check.handle(new CheckAccountRequestCodec().decode(root)))
            );
            case PqCreateAccount.TYPE_ID -> {
                var reply = create.handle(new CreateAccountRequestCodec().decode(root));
                var event = reply.status() == 0
                    ? ProtocolEvent.REGISTRATION_CREATED : ProtocolEvent.REGISTRATION_REJECTED;
                yield RequestCompletion.reply(output, event, new CreateAccountResponseCodec().encode(reply));
            }
            case PqFirstPacket.TYPE_ID -> {
                new FirstPacketRequestCodec().decode(root);
                yield RequestCompletion.reply(
                    output,
                    ProtocolEvent.HANDSHAKE_COMPLETED,
                    new FirstPacketResponseCodec().encode(clock)
                );
            }
            case PqUserLogin.TYPE_ID -> login.handle(root).map(
                reply -> RequestCompletion.reply(
                    output,
                    reply.success() ? ProtocolEvent.LOGIN_SUCCEEDED : ProtocolEvent.LOGIN_REJECTED,
                    new UserLoginResponseCodec().encode(reply)
                )
            ).orElseGet(() -> RequestCompletion.reply(output, ProtocolEvent.LOGIN_IGNORED, null));
            default -> {
                new KeepAliveCodec().decode(root);
                yield RequestCompletion.reply(output, ProtocolEvent.KEEP_ALIVE_ACCEPTED, null);
            }
        };
    }

    RequestCompletion failedReply(PendingRequest request, PacketOutput output) {
        return switch (request.type()) {
            case PqCreateAccount.TYPE_ID -> RequestCompletion.reply(
                output,
                ProtocolEvent.REGISTRATION_REJECTED,
                new CreateAccountResponseCodec().encode(
                    new PrCreateAccount(255, new CreateAccountRequestCodec().decode(request.root()).selector())
                )
            );
            case PqCheckAccount.TYPE_ID -> RequestCompletion.reply(
                output,
                ProtocolEvent.ACCOUNT_CHECK_COMPLETED,
                new CheckAccountResponseCodec().encode(
                    new PrCheckAccount(255, new CheckAccountRequestCodec().decode(request.root()).selector())
                )
            );
            default -> RequestCompletion.reply(
                output,
                ProtocolEvent.LOGIN_REJECTED,
                new UserLoginResponseCodec().encode(new PrUserLogin(false, 0, 0, 0))
            );
        };
    }

}
