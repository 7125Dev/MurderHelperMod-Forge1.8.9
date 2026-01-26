package me.dev7125.murderhelper.core.listener;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Packet;

/**
 * Netty管道处理器，用于拦截服务器发来的数据包
 */
public class PacketInterceptor extends ChannelInboundHandlerAdapter {
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet) {
            // 异步处理数据包监听
            PacketListenerRegistry.handlePacket((Packet<?>) msg);
        }
        // 继续传递给下一个handler
        super.channelRead(ctx, msg);
    }
}