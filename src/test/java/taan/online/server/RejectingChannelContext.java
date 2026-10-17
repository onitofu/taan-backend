package taan.online.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.EventExecutor;
import java.lang.reflect.Proxy;
import java.util.concurrent.RejectedExecutionException;

final class RejectingChannelContext {
    private RejectingChannelContext() {
    }

    static ChannelHandlerContext wrap(ChannelHandlerContext actual) {
        var executor = (EventExecutor) Proxy.newProxyInstance(
            EventExecutor.class.getClassLoader(),
            new Class<?>[]{EventExecutor.class},
            (instance, method, arguments) -> {
                if (method.getName().equals("execute")) {
                    throw new RejectedExecutionException("Stopped executor");
                }

                return method.invoke(actual.executor(), arguments);
            }
        );
        return (ChannelHandlerContext) Proxy.newProxyInstance(
            ChannelHandlerContext.class.getClassLoader(),
            new Class<?>[]{ChannelHandlerContext.class},
            (instance, method, arguments) -> {
                if (method.getName().equals("executor")) {
                    return executor;
                }

                return method.invoke(actual, arguments);
            }
        );
    }
}
