package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.character.protocol.packet.PqSelectCharacter;
import taan.online.inventory.protocol.packet.PqMoveInventoryItem;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.session.protocol.packet.PcUpdateCash;
import taan.online.session.protocol.packet.PqUserLogin;

class RequestExecutorsTest {
    @Test
    void routesEveryWorkClassAndDoesNotBlockGameplayBehindAuthenticationOrSql() throws Exception {
        var release = new CountDownLatch(1);

        try (var authentication = pool(); var database = pool(); var requests = pool()) {
            var executors = new RequestExecutors(authentication, database, requests);
            assertSame(authentication, executors.forType(PqUserLogin.TYPE_ID));
            assertSame(authentication, executors.forType(PqCreateAccount.TYPE_ID));

            for (int type : new int[]{
                PqCheckAccount.TYPE_ID,
                PqSelectCharacter.TYPE_ID,
                PcUpdateCash.TYPE_ID,
                PqGameSessionCreate.TYPE_ID,
                PqGameSessionEnter.TYPE_ID,
                PqGameSessionQuickJoin.TYPE_ID,
                PqNewCharacter.TYPE_ID,
                PqMoveInventoryItem.TYPE_ID
            }) {
                assertSame(database, executors.forType(type));
            }
            assertEquals(RequestExecutionClass.REQUEST, RequestExecutors.classification(PcGamePlayScenario.TYPE_ID));
            assertThrows(MalformedPacketException.class, () -> executors.forType(0));

            var occupied = new CountDownLatch(2);

            for (var executor : List.of(authentication, database)) {
                executor.execute(() -> {
                    occupied.countDown();

                    try {
                        release.await();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            try {
                assertTrue(occupied.await(5, TimeUnit.SECONDS));
                assertTrue(executors.forType(PcGamePlayScenario.TYPE_ID).submit(() -> true).get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    private ThreadPoolExecutor pool() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    }
}
