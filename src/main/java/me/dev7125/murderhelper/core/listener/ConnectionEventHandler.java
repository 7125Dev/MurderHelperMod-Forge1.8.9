package me.dev7125.murderhelper.core.listener;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * 监听连接事件，在连接建立时注入PacketInterceptor
 */
public class ConnectionEventHandler {
    
    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // 在packet_handler之前插入我们的拦截器
        event.manager.channel().pipeline()
            .addBefore("packet_handler", "packet_listener_interceptor", new PacketInterceptor());
    }
}