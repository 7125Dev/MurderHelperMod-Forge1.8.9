package me.dev7125.murderhelper.core.listener;

import me.dev7125.murderhelper.core.annotation.PacketListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

public class PacketListenerRegistry {

    // 待处理的数据包队列
    private static final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();

    // 监听器映射
    private static final Map<Class<? extends Packet>, List<ListenerMethod>> listeners = new HashMap<>();

    private static class ListenerMethod {
        final Object instance;
        final Method method;

        ListenerMethod(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
            this.method.setAccessible(true);
        }
    }

    /**
     * 注册监听器对象
     */
    public static void register(Object instance) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(PacketListener.class)) {
                PacketListener annotation = method.getAnnotation(PacketListener.class);
                Class<? extends Packet> packetClass = annotation.value();

                if (!validateMethod(method, packetClass)) {
                    System.err.println("Invalid packet listener method: " + method.getName());
                    continue;
                }

                listeners.computeIfAbsent(packetClass, k -> new ArrayList<>())
                        .add(new ListenerMethod(instance, method));
            }
        }
    }

    private static boolean validateMethod(Method method, Class<? extends Packet> packetClass) {
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && params[0].isAssignableFrom(packetClass);
    }

    public static void unregister(Object instance) {
        listeners.values().forEach(list ->
                list.removeIf(lm -> lm.instance == instance)
        );
    }

    /**
     * 接收数据包（由Netty线程调用）
     * 将数据包加入队列，等待主线程处理
     */
    public static void handlePacket(Packet<?> packet) {
        List<ListenerMethod> methods = listeners.get(packet.getClass());
        // 只有当该数据包类型有监听器时才加入队列
        if (methods != null && !methods.isEmpty()) {
            packetQueue.offer(packet);
        }
    }

    /**
     * 处理队列中的数据包（由主线程每tick调用）
     */
    public static void processQueue() {
        // 确保在主线程中执行
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            return;
        }

        Packet<?> packet;
        int processed = 0;
        final int MAX_PER_TICK = 50; // 每tick最多处理50个数据包，防止卡顿

        while ((packet = packetQueue.poll()) != null && processed < MAX_PER_TICK) {
            processPacket(packet);
            processed++;
        }
    }

    /**
     * 处理单个数据包
     */
    private static void processPacket(Packet<?> packet) {
        List<ListenerMethod> methods = listeners.get(packet.getClass());
        if (methods != null) {
            for (ListenerMethod lm : methods) {
                try {
                    lm.method.invoke(lm.instance, packet);
                } catch (Exception e) {
                    System.err.println("Error invoking packet listener: " + lm.method.getName());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 清空队列
     */
    public static void clearQueue() {
        packetQueue.clear();
    }
}